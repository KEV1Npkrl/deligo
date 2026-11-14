package pe.deligo.catalog.importer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.deligo.shared.BusinessException;
import pe.deligo.shared.Store;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Imports a reviewed, normalized catalog snapshot without trusting client-side prices. */
@Service
public class CatalogImportService {
    private final Store db;
    public CatalogImportService(Store db) { this.db = db; }

    @Transactional
    public Map<String,Object> importSnapshot(Map<String,Object> snapshot, String ownerId) {
        if (((Number)snapshot.getOrDefault("schemaVersion", 0)).intValue() != 1)
            throw new BusinessException(400,"Versión de catálogo no compatible.");
        var source = map(snapshot.get("source"));
        var merchant = map(snapshot.get("merchant"));
        String provider = required(source,"provider",30);
        String externalId = required(merchant,"externalId",80);
        String merchantId = existingId("SELECT id FROM merchant WHERE source_provider=? AND source_external_id=?",provider,externalId);
        if (merchantId == null) merchantId = Store.id();
        String name = required(merchant,"name",100);
        String category = categoryLabel(value(merchant,"category","Otros",40));
        String description = value(merchant,"description","Catálogo importado para demostración académica.",300);
        Instant captured = instant(source.get("capturedAt"));
        var expiredProducts=new java.util.HashSet<String>();
        for(Object warningValue:list(snapshot.get("warnings"))) {
            var warning=map(warningValue);
            if("EXPIRED_PROMOTION_TEXT".equals(text(warning.get("code")))) expiredProducts.add(text(warning.get("productExternalId")));
        }
        boolean exists = !db.list("SELECT id FROM merchant WHERE id=?",merchantId).isEmpty();
        if (exists) {
            db.update("UPDATE merchant SET owner_id=?,name=?,category=?,description=?,source_page_name=?,address=?,latitude=?,longitude=?,delivery_base_fee=?,logo_url=?,cover_url=?,catalog_captured_at=? WHERE id=?",
                ownerId,name,category,description,text(merchant.get("pageName")),text(merchant.get("address")),decimalOrNull(merchant.get("latitude")),decimalOrNull(merchant.get("longitude")),decimalOrNull(merchant.get("deliveryBaseFee")),text(merchant.get("logoUrl")),text(merchant.get("coverUrl")),Timestamp.from(captured),merchantId);
        } else {
            db.update("INSERT INTO merchant(id,owner_id,name,category,description,image_key,source_provider,source_external_id,source_page_name,address,latitude,longitude,delivery_base_fee,logo_url,cover_url,catalog_captured_at) VALUES(?,?,?,?,?,'food',?,?,?,?,?,?,?,?,?,?)",
                merchantId,ownerId,name,category,description,provider,externalId,text(merchant.get("pageName")),text(merchant.get("address")),decimalOrNull(merchant.get("latitude")),decimalOrNull(merchant.get("longitude")),decimalOrNull(merchant.get("deliveryBaseFee")),text(merchant.get("logoUrl")),text(merchant.get("coverUrl")),Timestamp.from(captured));
        }

        db.update("UPDATE product SET active=FALSE WHERE merchant_id=? AND source_external_id IS NOT NULL",merchantId);
        db.update("UPDATE product_category SET active=FALSE WHERE merchant_id=? AND source_external_id IS NOT NULL",merchantId);
        int categories=0, products=0, options=0;
        for (Object categoryValue : list(snapshot.get("categories"))) {
            var c=map(categoryValue); String categoryExternal=required(c,"externalId",80);
            String categoryId=existingId("SELECT id FROM product_category WHERE merchant_id=? AND source_external_id=?",merchantId,categoryExternal);
            if(categoryId==null) { categoryId=Store.id(); db.update("INSERT INTO product_category(id,merchant_id,name,description,display_order,source_external_id) VALUES(?,?,?,?,?,?)",categoryId,merchantId,required(c,"name",100),value(c,"description","",300),integer(c.get("position")),categoryExternal); }
            else db.update("UPDATE product_category SET name=?,description=?,display_order=?,active=TRUE WHERE id=?",required(c,"name",100),value(c,"description","",300),integer(c.get("position")),categoryId);
            categories++;
            for(Object productValue:list(c.get("products"))) {
                var p=map(productValue); String productExternal=required(p,"externalId",80);
                String productId=existingId("SELECT id FROM product WHERE merchant_id=? AND source_external_id=?",merchantId,productExternal);
                boolean soldOut=Boolean.TRUE.equals(p.get("soldOut")); boolean available=!soldOut&&!expiredProducts.contains(productExternal); BigDecimal price=decimal(p.get("currentPrice"));
                if(productId==null) { productId=Store.id(); db.update("INSERT INTO product(id,merchant_id,category_id,name,description,price,original_price,stock,image_url,thumbnail_url,source_external_id,display_order,active) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",productId,merchantId,categoryId,required(p,"name",100),value(p,"description","",300),price,decimalOrNull(p.get("originalPrice")),available?9999:0,text(p.get("imageUrl")),text(p.get("thumbnailUrl")),productExternal,integer(p.get("position")),available); }
                else db.update("UPDATE product SET category_id=?,name=?,description=?,price=?,original_price=?,stock=?,image_url=?,thumbnail_url=?,display_order=?,active=? WHERE id=?",categoryId,required(p,"name",100),value(p,"description","",300),price,decimalOrNull(p.get("originalPrice")),available?9999:0,text(p.get("imageUrl")),text(p.get("thumbnailUrl")),integer(p.get("position")),available,productId);
                db.update("UPDATE product_option_group SET active=FALSE WHERE product_id=?",productId);
                for(Object groupValue:list(p.get("optionGroups"))) {
                    var g=map(groupValue); String groupExternal=required(g,"externalId",80);
                    String groupId=existingId("SELECT id FROM product_option_group WHERE product_id=? AND source_external_id=?",productId,groupExternal);
                    int min=integer(g.get("minimum")), max=Math.max(min,integer(g.get("maximum")));
                    if(groupId==null) { groupId=Store.id(); db.update("INSERT INTO product_option_group(id,product_id,name,min_selected,max_selected,display_order,source_external_id) VALUES(?,?,?,?,?,?,?)",groupId,productId,required(g,"name",120),min,max,integer(g.get("position")),groupExternal); }
                    else db.update("UPDATE product_option_group SET name=?,min_selected=?,max_selected=?,display_order=?,active=TRUE WHERE id=?",required(g,"name",120),min,max,integer(g.get("position")),groupId);
                    db.update("UPDATE product_option SET active=FALSE WHERE group_id=?",groupId);
                    for(Object optionValue:list(g.get("options"))) {
                        var o=map(optionValue); String optionExternal=required(o,"externalId",80);
                        String optionId=existingId("SELECT id FROM product_option WHERE group_id=? AND source_external_id=?",groupId,optionExternal);
                        if(optionId==null) db.update("INSERT INTO product_option(id,group_id,name,additional_price,display_order,source_external_id) VALUES(?,?,?,?,?,?)",Store.id(),groupId,required(o,"name",120),decimal(o.getOrDefault("additionalPrice",0)),integer(o.get("position")),optionExternal);
                        else db.update("UPDATE product_option SET name=?,additional_price=?,display_order=?,active=TRUE WHERE id=?",required(o,"name",120),decimal(o.getOrDefault("additionalPrice",0)),integer(o.get("position")),optionId);
                        options++;
                    }
                }
                products++;
            }
        }
        return Map.of("merchantId",merchantId,"merchant",name,"categories",categories,"products",products,"options",options,"capturedAt",captured.toString());
    }

    private String existingId(String sql,Object... args) { var rows=db.list(sql,args); return rows.isEmpty()?null:rows.getFirst().get("id").toString(); }
    private static String required(Map<String,Object> m,String key,int max) { String value=text(m.get(key)); if(value.isBlank()) throw new BusinessException(400,"Falta "+key+" en la instantánea."); return cut(value,max); }
    private static String value(Map<String,Object> m,String key,String fallback,int max) { String value=text(m.get(key)); return cut(value.isBlank()?fallback:value,max); }
    private static String cut(String s,int max) { return s.length()>max?s.substring(0,max):s; }
    private static String categoryLabel(String value) { return switch(value.toLowerCase(java.util.Locale.ROOT)) { case "restaurant","restaurante" -> "Restaurantes"; case "market","minimarket" -> "Minimarket"; case "pharmacy","farmacia" -> "Farmacia"; default -> value; }; }
    private static String text(Object v) { return v==null?"":v.toString().trim(); }
    private static BigDecimal decimal(Object v) { try { var n=new BigDecimal(text(v)); if(n.signum()<0) throw new NumberFormatException(); return n.setScale(2,java.math.RoundingMode.HALF_UP); } catch(Exception e) { throw new BusinessException(400,"Importe inválido en el catálogo: "+v); } }
    private static BigDecimal decimalOrNull(Object v) { return text(v).isBlank()?null:new BigDecimal(text(v)); }
    private static int integer(Object v) { return text(v).isBlank()?0:new BigDecimal(text(v)).intValue(); }
    private static Instant instant(Object v) { try { return Instant.parse(text(v)); } catch(Exception e) { throw new BusinessException(400,"capturedAt inválido."); } }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object v) { return v instanceof Map<?,?> m?(Map<String,Object>)m:Map.of(); }
    @SuppressWarnings("unchecked") private static List<Object> list(Object v) { return v instanceof List<?> l?(List<Object>)l:List.of(); }
}
