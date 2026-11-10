package pe.deligo.orders;

import pe.deligo.shared.*;
import pe.deligo.identity.Identity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderService orders; private final Identity identity; private final Store db;
    public OrderController(OrderService orders,Identity identity,Store db) { this.orders=orders; this.identity=identity; this.db=db; }
    @GetMapping("/orders") List<Map<String,Object>> list(Authentication auth) { return orders.list(identity.user(auth)); }
    @GetMapping("/orders/{id}") Map<String,Object> detail(Authentication auth,@PathVariable String id) { return orders.detail(identity.user(auth),id); }
    @PostMapping("/orders") Map<String,Object> create(Authentication auth,@Valid @RequestBody OrderInput input) { return orders.create(identity.user(auth),input); }
    public record Action(@NotBlank String action,@Min(0) int version,@Size(max=500) String reason) {}
    @PostMapping("/orders/{id}/actions") Map<String,Object> action(Authentication auth,@PathVariable String id,@Valid @RequestBody Action input) { return orders.action(identity.user(auth),id,input); }
    @GetMapping("/courier/available") List<Map<String,Object>> available(Authentication auth) {
        identity.role(identity.user(auth),"COURIER");
        return db.list("SELECT o.id,o.version,o.created_at,o.courier_share,m.name merchant_name,z.name zone_name FROM delivery_order o JOIN merchant m ON m.id=o.merchant_id JOIN zone z ON z.id=o.zone_id WHERE o.order_status='READY' AND o.courier_id IS NULL ORDER BY o.created_at LIMIT 50");
    }
    public record Availability(boolean available) {}
    @PutMapping("/courier/availability") void availability(Authentication auth,@RequestBody Availability a) {
        var u=identity.user(auth); identity.role(u,"COURIER"); db.update("UPDATE app_user SET available=? WHERE id=?",a.available(),u.get("id"));
    }
    public record Review(@Min(1) @Max(4) int score,@NotNull @Size(max=500) String comment) {}
    @PostMapping("/orders/{id}/review") @Transactional void review(Authentication auth,@PathVariable String id,@Valid @RequestBody Review r) {
        var u=identity.user(auth); identity.role(u,"CUSTOMER"); var o=orders.detail(u,id);
        BusinessException.require(o.get("customer_id").equals(u.get("id"))&&o.get("order_status").equals("DELIVERED"),"Solo puedes calificar tus pedidos entregados.");
        db.update("INSERT INTO review(id,order_id,customer_id,score,comment_text) VALUES(?,?,?,?,?)",Store.id(),id,u.get("id"),r.score(),r.comment());
    }
    public record CaseInput(@NotBlank @Size(min=10,max=500) String subject) {}
    @PostMapping("/orders/{id}/support") Map<String,String> support(Authentication auth,@PathVariable String id,@Valid @RequestBody CaseInput c) {
        var u=identity.user(auth); orders.detail(u,id); var caseId=Store.id();
        db.update("INSERT INTO support_case(id,order_id,author_id,subject) VALUES(?,?,?,?)",caseId,id,u.get("id"),c.subject()); return Map.of("id",caseId);
    }
}
