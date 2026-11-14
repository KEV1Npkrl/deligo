package pe.deligo.catalog.importer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
@Order(100)
public class CatalogSnapshotImportRunner implements CommandLineRunner {
    private final CatalogImportService importer; private final boolean demo; private final String directory;
    private final ObjectMapper json=new ObjectMapper();
    public CatalogSnapshotImportRunner(CatalogImportService importer,@Value("${deligo.demo}") boolean demo,@Value("${deligo.catalog-import-directory:}") String directory) { this.importer=importer; this.demo=demo; this.directory=directory; }
    @Override public void run(String... args) throws Exception {
        if(!demo||directory.isBlank()) return;
        Path root=Path.of(directory); if(!Files.isDirectory(root)) return;
        try(var files=Files.walk(root)) {
            for(Path file:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".json")).filter(p->!p.getFileName().toString().equals("manifest.json")).sorted().toList()) {
                Map<String,Object> snapshot=json.readValue(file.toFile(),new TypeReference<Map<String,Object>>(){});
                if(snapshot.containsKey("schemaVersion")) importer.importSnapshot(snapshot,"demo-admin");
            }
        }
    }
}
