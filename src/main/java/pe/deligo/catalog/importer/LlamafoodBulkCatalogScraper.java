package pe.deligo.catalog.importer;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * Converts a branch-list response exported from the user's own Llamafood session into
 * independent public catalog snapshots. Authentication data is never read or stored.
 */
public final class LlamafoodBulkCatalogScraper {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final double TARAPOTO_LAT=-6.4889, TARAPOTO_LNG=-76.3597, MAX_KM=30;
    private LlamafoodBulkCatalogScraper() {}

    public static void main(String[] args) throws Exception {
        if(args.length<1||args.length>2) throw new IllegalArgumentException("Uso: LlamafoodBulkCatalogScraper <respuesta-lista.json|directorio-de-respuestas> [directorio-salida]");
        Path input=Path.of(args[0]); Path output=args.length==2?Path.of(args[1]):Path.of("data","imports","llamafood",LocalDate.now().toString());
        var branches=new LinkedHashMap<String,Map<String,Object>>();
        var sourceFiles=new ArrayList<Path>();
        if(Files.isDirectory(input)) try(var files=Files.walk(input)) { sourceFiles.addAll(files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".json")).sorted().toList()); }
        else sourceFiles.add(input);
        for(Path sourceFile:sourceFiles) collect(JSON.readValue(sourceFile.toFile(),new TypeReference<Object>(){}),branches);
        if(branches.isEmpty()) throw new IllegalArgumentException("No encontré negocios con page_name y branch_id en el archivo exportado.");
        Files.createDirectories(output); int ok=0,failed=0;
        var manifest=new ArrayList<Map<String,Object>>();
        for(var branch:branches.values()) {
            String page=text(branch.get("page_name"));
            if(!inTarapoto(branch)) continue;
            Path target=output.resolve(page+".json");
            try {
                var snapshot=LlamafoodCatalogScraper.capture(page,target); var summary=map(snapshot.get("summary"));
                manifest.add(Map.of("pageName",page,"file",target.getFileName().toString(),"status","OK","products",summary.getOrDefault("products",0))); ok++;
                Thread.sleep(350);
            } catch(Exception e) {
                manifest.add(Map.of("pageName",page,"status","ERROR","message",Objects.toString(e.getMessage(),e.getClass().getSimpleName()))); failed++;
            }
        }
        var result=new LinkedHashMap<String,Object>(); result.put("source",input.toAbsolutePath().toString()); result.put("sourceFiles",sourceFiles.size()); result.put("businessesFound",branches.size()); result.put("captured",ok); result.put("failed",failed); result.put("catalogs",manifest);
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("manifest.json").toFile(),result);
        System.out.printf(Locale.ROOT,"Captura terminada: %d catálogos, %d fallos. Manifiesto: %s%n",ok,failed,output.resolve("manifest.json").toAbsolutePath());
    }

    private static void collect(Object value,Map<String,Map<String,Object>> result) {
        if(value instanceof Map<?,?> raw) {
            var current=new LinkedHashMap<String,Object>(); raw.forEach((k,v)->current.put(String.valueOf(k),v));
            String page=text(current.get("page_name")); if(page.isBlank()) page=text(current.get("pageName"));
            String id=text(current.get("branch_id")); if(id.isBlank()) id=page;
            if(!page.isBlank()) { current.put("page_name",page); result.putIfAbsent(id,current); }
            current.values().forEach(v->collect(v,result));
        } else if(value instanceof List<?> list) list.forEach(v->collect(v,result));
    }
    private static boolean inTarapoto(Map<String,Object> branch) {
        Double lat=number(branch,"location_lat","latitude","lat"),lng=number(branch,"location_lng","longitude","lng");
        if(lat==null||lng==null) return true; // The exported endpoint is already address-filtered.
        double a=Math.toRadians(TARAPOTO_LAT),b=Math.toRadians(lat),dl=Math.toRadians(lng-TARAPOTO_LNG);
        double h=Math.pow(Math.sin((b-a)/2),2)+Math.cos(a)*Math.cos(b)*Math.pow(Math.sin(dl/2),2);
        return 6371*2*Math.asin(Math.sqrt(Math.min(1,h)))<=MAX_KM;
    }
    private static Double number(Map<String,Object> m,String... keys) { for(String k:keys) if(m.get(k)!=null) try{return Double.valueOf(text(m.get(k)));}catch(Exception ignored){} return null; }
    private static String text(Object v){return v==null?"":v.toString().trim();}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object v){return v instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();}
}
