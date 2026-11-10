package pe.deligo.operations;

import pe.deligo.shared.*;
import pe.deligo.identity.Identity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api")
public class OperationsController {
    private final Store db; private final Identity identity;
    public OperationsController(Store db,Identity identity) { this.db=db; this.identity=identity; }
    void admin(Authentication auth) { identity.role(identity.user(auth),"ADMIN"); }
    @GetMapping("/admin/summary") Map<String,Object> summary(Authentication auth) {
        admin(auth);
        return Map.of("totals",db.one("SELECT COUNT(*) orders_count,COALESCE(SUM(CASE WHEN order_status='DELIVERED' THEN subtotal ELSE 0 END),0) merchant_sales,COALESCE(SUM(CASE WHEN order_status='DELIVERED' THEN platform_share ELSE 0 END),0) platform_revenue,COALESCE(SUM(CASE WHEN order_status='DELIVERED' AND payment_method='CASH' AND cash_rendered_at IS NULL THEN total ELSE 0 END),0) cash_pending FROM delivery_order"),
            "byMerchant",db.list("SELECT m.name,COUNT(o.id) orders_count,COALESCE(SUM(CASE WHEN o.order_status='DELIVERED' THEN o.subtotal ELSE 0 END),0) sales FROM merchant m LEFT JOIN delivery_order o ON o.merchant_id=m.id GROUP BY m.id,m.name ORDER BY m.name"),
            "users",db.list("SELECT id,full_name,email,user_role,active FROM app_user ORDER BY full_name"),
            "cases",db.list("SELECT * FROM support_case ORDER BY created_at DESC LIMIT 100"));
    }
    @GetMapping("/courier/earnings") Map<String,Object> earnings(Authentication auth) {
        var u=identity.user(auth); identity.role(u,"COURIER");
        return db.one("SELECT COUNT(*) deliveries,COALESCE(SUM(courier_share),0) earned,COALESCE(SUM(CASE WHEN settled_at IS NULL THEN courier_share ELSE 0 END),0) pending,COALESCE(SUM(CASE WHEN payment_method='CASH' AND cash_rendered_at IS NULL THEN total ELSE 0 END),0) cash_debt FROM delivery_order WHERE courier_id=? AND order_status='DELIVERED'",u.get("id"));
    }
    public record Rain(@NotNull @DecimalMin("0") @DecimalMax("20") @Digits(integer=2,fraction=2) BigDecimal amount) {}
    @PutMapping("/admin/rain") void rain(Authentication auth,@Valid @RequestBody Rain r) { admin(auth); db.update("UPDATE platform_setting SET rain_fee=? WHERE id=1",r.amount()); }
    public record Resolution(@NotBlank @Size(min=10,max=500) String resolution) {}
    @PutMapping("/admin/cases/{id}") void resolve(Authentication auth,@PathVariable String id,@Valid @RequestBody Resolution r) {
        admin(auth); BusinessException.require(db.update("UPDATE support_case SET case_status='RESOLVED',resolution=? WHERE id=? AND case_status='OPEN'",r.resolution(),id)==1,"El caso ya está resuelto o no existe.");
    }
    public record Suspension(boolean active) {}
    @PutMapping("/admin/users/{id}") void suspend(Authentication auth,@PathVariable String id,@RequestBody Suspension s) {
        admin(auth); var target=db.one("SELECT user_role FROM app_user WHERE id=?",id);
        BusinessException.require(!target.get("user_role").equals("ADMIN"),"No se puede suspender al administrador desde esta pantalla.");
        db.update("UPDATE app_user SET active=? WHERE id=?",s.active(),id);
    }
    @GetMapping("/support") List<Map<String,Object>> cases(Authentication auth) {
        var u=identity.user(auth); return db.list("SELECT * FROM support_case WHERE author_id=? ORDER BY created_at DESC LIMIT 100",u.get("id"));
    }
    public record Address(@NotBlank @Size(max=40) String label,@NotBlank @Size(min=8,max=250) String address,
                          @DecimalMin("-90") @DecimalMax("90") double latitude,@DecimalMin("-180") @DecimalMax("180") double longitude) {}
    @GetMapping("/addresses") List<Map<String,Object>> addresses(Authentication auth) {
        var u=identity.user(auth); identity.role(u,"CUSTOMER"); return db.list("SELECT * FROM customer_address WHERE customer_id=? ORDER BY label",u.get("id"));
    }
    @PostMapping("/addresses") @Transactional Map<String,String> saveAddress(Authentication auth,@Valid @RequestBody Address a) {
        var u=identity.user(auth); identity.role(u,"CUSTOMER"); db.one("SELECT id FROM app_user WHERE id=? FOR UPDATE",u.get("id"));
        BusinessException.require(db.list("SELECT id FROM customer_address WHERE customer_id=?",u.get("id")).size()<3,"Puedes guardar hasta tres direcciones.");
        String id=Store.id(); db.update("INSERT INTO customer_address(id,customer_id,label,address_text,latitude,longitude) VALUES(?,?,?,?,?,?)",id,u.get("id"),a.label(),a.address(),a.latitude(),a.longitude()); return Map.of("id",id);
    }
    @DeleteMapping("/addresses/{id}") void deleteAddress(Authentication auth,@PathVariable String id) {
        var u=identity.user(auth); identity.role(u,"CUSTOMER"); db.update("DELETE FROM customer_address WHERE id=? AND customer_id=?",id,u.get("id"));
    }
}
