package pe.deligo.catalog;

import pe.deligo.shared.*;
import pe.deligo.identity.Identity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

@RestController
public class CatalogController {
    private final Store db; private final Identity identity;
    public CatalogController(Store db, Identity identity) { this.db=db; this.identity=identity; }
    @GetMapping("/api/catalog/merchants") List<Map<String,Object>> merchants() {
        return db.list("SELECT m.*, (SELECT AVG(r.score) FROM review r JOIN delivery_order o ON o.id=r.order_id WHERE o.merchant_id=m.id) rating, (SELECT COUNT(*) FROM review r JOIN delivery_order o ON o.id=r.order_id WHERE o.merchant_id=m.id) review_count FROM merchant m ORDER BY m.name");
    }
    @GetMapping("/api/catalog/products") List<Map<String,Object>> products(@RequestParam String merchantId) {
        var products=db.list("SELECT p.*,c.name category_name,c.display_order category_order FROM product p LEFT JOIN product_category c ON c.id=p.category_id WHERE p.merchant_id=? AND p.active=TRUE ORDER BY COALESCE(c.display_order,9999),p.display_order,p.name",merchantId);
        for(var product:products) {
            var groups=db.list("SELECT id,name,min_selected,max_selected,display_order FROM product_option_group WHERE product_id=? AND active=TRUE ORDER BY display_order,id",product.get("id"));
            for(var group:groups) group.put("options",db.list("SELECT id,name,additional_price,display_order FROM product_option WHERE group_id=? AND active=TRUE ORDER BY display_order,id",group.get("id")));
            product.put("option_groups",groups);
        }
        return products;
    }
    @GetMapping("/api/catalog/zones") List<Map<String,Object>> zones() { return db.list("SELECT * FROM zone WHERE active=TRUE ORDER BY name"); }
    @GetMapping("/api/catalog/settings") Map<String,Object> settings() { return db.one("SELECT rain_fee FROM platform_setting WHERE id=1"); }
    @GetMapping("/api/merchant/catalog") Map<String,Object> own(Authentication auth) {
        var u=identity.user(auth); identity.role(u,"MERCHANT","ADMIN");
        var shops=u.get("user_role").equals("ADMIN")?db.list("SELECT * FROM merchant"):db.list("SELECT * FROM merchant WHERE owner_id=?",u.get("id"));
        var items=u.get("user_role").equals("ADMIN")?db.list("SELECT * FROM product"):db.list("SELECT p.* FROM product p JOIN merchant m ON m.id=p.merchant_id WHERE m.owner_id=?",u.get("id"));
        return Map.of("merchants",shops,"products",items);
    }
    void owns(Map<String,Object> u,String merchantId) {
        identity.role(u,"MERCHANT","ADMIN");
        var m=db.one("SELECT owner_id FROM merchant WHERE id=?",merchantId);
        if (!u.get("user_role").equals("ADMIN")&&!u.get("id").equals(m.get("owner_id"))) throw new BusinessException(403,"Comercio ajeno.");
    }
    public record ProductInput(@NotBlank String merchantId,@NotBlank @Size(max=100) String name,
        @NotNull @Size(max=300) String description,@NotNull @DecimalMin("0.01") @DecimalMax("10000") @Digits(integer=5,fraction=2) BigDecimal price,
        @Min(0) @Max(100000) int stock, boolean active) {}
    @PostMapping("/api/merchant/products") Map<String,String> create(Authentication auth,@Valid @RequestBody ProductInput p) {
        owns(identity.user(auth),p.merchantId()); var id=Store.id();
        db.update("INSERT INTO product(id,merchant_id,name,description,price,stock,active) VALUES(?,?,?,?,?,?,?)",id,p.merchantId(),p.name(),p.description(),p.price(),p.stock(),p.active());
        return Map.of("id",id);
    }
    @PutMapping("/api/merchant/products/{id}") void edit(Authentication auth,@PathVariable String id,@Valid @RequestBody ProductInput p) {
        var old=db.one("SELECT merchant_id FROM product WHERE id=?",id); owns(identity.user(auth),Store.str(old,"merchant_id"));
        BusinessException.require(p.merchantId().equals(old.get("merchant_id")),"No se puede trasladar un producto a otro comercio.");
        db.update("UPDATE product SET name=?,description=?,price=?,stock=?,active=? WHERE id=?",p.name(),p.description(),p.price(),p.stock(),p.active(),id);
    }
    @DeleteMapping("/api/merchant/products/{id}") void hide(Authentication auth,@PathVariable String id) {
        var old=db.one("SELECT merchant_id FROM product WHERE id=?",id); owns(identity.user(auth),Store.str(old,"merchant_id"));
        db.update("UPDATE product SET active=FALSE WHERE id=?",id);
    }
    public record OpenInput(boolean open) {}
    @PutMapping("/api/merchant/{id}/open") void open(Authentication auth,@PathVariable String id,@RequestBody OpenInput p) {
        owns(identity.user(auth),id); db.update("UPDATE merchant SET is_open=? WHERE id=?",p.open(),id);
    }
}
