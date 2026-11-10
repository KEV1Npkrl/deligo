package pe.deligo.orders;

import pe.deligo.shared.*;
import pe.deligo.identity.Identity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static pe.deligo.shared.Store.*;
import static pe.deligo.shared.BusinessException.require;

@Service
public class OrderService {
    private final Store db; private final Identity identity;
    public OrderService(Store db,Identity identity) { this.db=db; this.identity=identity; }
    public List<Map<String,Object>> list(Map<String,Object> u) {
        String where=switch(str(u,"user_role")) {
            case "ADMIN" -> "1=1";
            case "MERCHANT" -> "m.owner_id=?";
            case "COURIER" -> "o.courier_id=?";
            default -> "o.customer_id=?";
        };
        return db.list("SELECT o.*,m.name merchant_name FROM delivery_order o JOIN merchant m ON o.merchant_id=m.id WHERE "+where+" ORDER BY o.created_at DESC LIMIT 100",
            u.get("user_role").equals("ADMIN")?new Object[]{}:new Object[]{u.get("id")});
    }
    public Map<String,Object> detail(Map<String,Object> u,String id) {
        var o=db.one("SELECT o.*,m.name merchant_name,m.owner_id FROM delivery_order o JOIN merchant m ON o.merchant_id=m.id WHERE o.id=?",id);
        authorize(u,o);
        o.put("items",db.list("SELECT product_name,unit_price,quantity FROM order_item WHERE order_id=? ORDER BY product_name",id));
        o.put("events",db.list("SELECT event_type,detail,created_at FROM order_event WHERE order_id=? ORDER BY created_at,id",id));
        o.put("review",db.list("SELECT score,comment_text FROM review WHERE order_id=?",id));
        return o;
    }
    public void authorize(Map<String,Object> u,Map<String,Object> o) {
        var uid=u.get("id");
        var owner=db.one("SELECT owner_id FROM merchant WHERE id=?",o.get("merchant_id")).get("owner_id");
        if(!u.get("user_role").equals("ADMIN")&&!uid.equals(o.get("customer_id"))&&!uid.equals(o.get("courier_id"))&&!uid.equals(owner))
            throw new BusinessException(403,"Este pedido no pertenece a tu cuenta.");
    }
    @Transactional
    public Map<String,Object> create(Map<String,Object> u,OrderInput input) {
        identity.role(u,"CUSTOMER");
        // Serializes same-customer retries, including the first insert (no absent-row race).
        db.one("SELECT id FROM app_user WHERE id=? FOR UPDATE",u.get("id"));
        var hash=hash(input);
        var previous=db.list("SELECT * FROM delivery_order WHERE customer_id=? AND idempotency_key=?",u.get("id"),input.idempotencyKey());
        if(!previous.isEmpty()) {
            require(hash.equals(previous.getFirst().get("request_hash")),"Esta clave ya corresponde a otro carrito.");
            return previous.getFirst();
        }
        var merchant=db.one("SELECT * FROM merchant WHERE id=? FOR UPDATE",input.merchantId());
        require(yes(merchant,"is_open"),"El comercio está cerrado. Elige otro o intenta más tarde.");
        var zone=db.one("SELECT * FROM zone WHERE id=?",input.zoneId());
        require(yes(zone,"active")&&covers(zone,input.latitude(),input.longitude()),"Aún no llegamos a esa dirección. Cambia el punto de entrega.");
        var quantities=new TreeMap<String,Integer>();
        for(var item:input.items()) quantities.merge(item.productId(),item.quantity(),Integer::sum);
        var items=new ArrayList<Map<String,Object>>(); var subtotal=BigDecimal.ZERO.setScale(2);
        for(var entry:quantities.entrySet()) {
            require(entry.getValue()<=50,"Máximo 50 unidades de un producto.");
            var p=db.one("SELECT * FROM product WHERE id=? FOR UPDATE",entry.getKey());
            require(input.merchantId().equals(p.get("merchant_id"))&&yes(p,"active"),"El carrito contiene un producto no disponible o de otro comercio.");
            require(((Number)p.get("stock")).intValue()>=entry.getValue(),"Stock insuficiente: "+p.get("name"));
            db.update("UPDATE product SET stock=stock-? WHERE id=?",entry.getValue(),entry.getKey());
            p.put("quantity",entry.getValue()); items.add(p);
            subtotal=subtotal.add(money(p,"price").multiply(BigDecimal.valueOf(entry.getValue())));
        }
        var rain=money(db.one("SELECT rain_fee FROM platform_setting WHERE id=1"),"rain_fee");
        var quote=Pricing.calculate(subtotal,money(zone,"delivery_fee"),rain,input.tip());
        require(quote.total().compareTo(input.expectedTotal())==0,"El precio o la tarifa cambiaron. Revisa la bolsa antes de confirmar nuevamente.");
        String id=id();
        db.update("INSERT INTO delivery_order(id,customer_id,merchant_id,zone_id,address_text,latitude,longitude,order_status,payment_method,payment_status,subtotal,delivery_fee,rain_fee,tip,platform_share,courier_share,total,idempotency_key,request_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id,u.get("id"),input.merchantId(),input.zoneId(),input.address(),input.latitude(),input.longitude(),"PENDING",input.paymentMethod(),"UNPAID",quote.subtotal(),quote.delivery(),quote.rain(),quote.tip(),quote.platformShare(),quote.courierShare(),quote.total(),input.idempotencyKey(),hash);
        for(var p:items) db.update("INSERT INTO order_item(id,order_id,product_id,product_name,unit_price,quantity) VALUES(?,?,?,?,?,?)",id(),id,p.get("id"),p.get("name"),p.get("price"),p.get("quantity"));
        event(id,u.get("id"),"PENDING","Pedido recibido. Importes calculados y stock reservado.");
        return detail(u,id);
    }
    public static boolean covers(Map<String,Object> z,double lat,double lng) {
        if(!Double.isFinite(lat)||!Double.isFinite(lng)) return false;
        double a=Math.toRadians(((Number)z.get("center_lat")).doubleValue()),b=Math.toRadians(lat);
        double dl=Math.toRadians(lng-((Number)z.get("center_lng")).doubleValue());
        double h=Math.pow(Math.sin((b-a)/2),2)+Math.cos(a)*Math.cos(b)*Math.pow(Math.sin(dl/2),2);
        return 6371*2*Math.asin(Math.sqrt(Math.min(1,h)))<=((Number)z.get("radius_km")).doubleValue();
    }
    static String hash(OrderInput i) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(i.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public void event(String id,Object actor,String type,String detail) {
        db.update("INSERT INTO order_event(id,order_id,actor_id,event_type,detail) VALUES(?,?,?,?,?)",id(),id,actor,type,detail);
    }
    @Transactional
    public Map<String,Object> action(Map<String,Object> u,String id,OrderController.Action input) {
        var o=db.one("SELECT * FROM delivery_order WHERE id=? FOR UPDATE",id);
        if (!input.action().equals("CLAIM")) authorize(u,o);
        require(((Number)o.get("version")).intValue()==input.version(),"El pedido cambió. Actualiza antes de continuar.");
        String status=str(o,"order_status"),payment=str(o,"payment_status"),next=status;
        String role=str(u,"user_role"),note=input.reason()==null?"":input.reason().trim();
        boolean admin=role.equals("ADMIN"),merchant=role.equals("MERCHANT"),courier=role.equals("COURIER");
        switch(input.action()) {
            case "PREPARE" -> { require(merchant&&status.equals("PENDING"),"No puedes aceptar este pedido."); require(o.get("payment_method").equals("CASH")||payment.equals("PAID"),"Yape debe ser verificado por administración antes de preparar."); next="PREPARING"; }
            case "READY" -> { require(merchant&&status.equals("PREPARING"),"El pedido no está en preparación."); next="READY"; }
            case "CLAIM" -> {
                require(courier&&status.equals("READY")&&o.get("courier_id")==null,"El pedido ya no está disponible.");
                var c=db.one("SELECT * FROM app_user WHERE id=? FOR UPDATE",u.get("id"));
                require(yes(c,"available")&&yes(c,"active"),"Activa tu disponibilidad para aceptar entregas.");
                require(db.list("SELECT id FROM delivery_order WHERE courier_id=? AND order_status NOT IN ('DELIVERED','CANCELLED')",u.get("id")).isEmpty(),"Ya tienes una entrega activa.");
                var debt=db.one("SELECT COALESCE(SUM(total),0) debt,MIN(updated_at) oldest FROM delivery_order WHERE courier_id=? AND payment_method='CASH' AND payment_status='PAID' AND cash_rendered_at IS NULL",u.get("id"));
                require(money(debt,"debt").compareTo(new BigDecimal("100"))<=0,"Debes rendir tu efectivo antes de aceptar otro pedido.");
                if(debt.get("oldest") instanceof java.sql.Timestamp t) require(t.toInstant().plusSeconds(48*3600).isAfter(java.time.Instant.now()),"Tienes una rendición pendiente de más de 48 horas.");
                db.update("UPDATE delivery_order SET courier_id=? WHERE id=?",u.get("id"),id); next="ASSIGNED";
            }
            case "TO_STORE","AT_STORE","PICKED_UP","ON_THE_WAY","ARRIVED","DELIVERED" -> {
                require(courier&&u.get("id").equals(o.get("courier_id")),"Solo el repartidor asignado puede avanzar.");
                var sequence=List.of("ASSIGNED","TO_STORE","AT_STORE","PICKED_UP","ON_THE_WAY","ARRIVED","DELIVERED");
                int position=sequence.indexOf(status);
                require(position>=0&&position<sequence.size()-1&&sequence.get(position+1).equals(input.action()),"Transición de entrega inválida.");
                next=input.action();
                if(next.equals("DELIVERED")) {
                    require(o.get("payment_method").equals("YAPE")?payment.equals("PAID"):note.equals("EFECTIVO COBRADO"),"Confirma el cobro completo antes de entregar.");
                    if(o.get("payment_method").equals("CASH")) db.update("UPDATE delivery_order SET payment_status='PAID' WHERE id=?",id);
                }
            }
            case "CANCEL" -> {
                require(note.length()>=5,"Indica el motivo de cancelación (mínimo 5 caracteres).");
                require((role.equals("CUSTOMER")&&status.equals("PENDING"))||((merchant||admin)&&List.of("PENDING","PREPARING").contains(status)),"Ya no puedes cancelar en este estado. Abre un caso de soporte.");
                next="CANCELLED";
                for(var item:db.list("SELECT product_id,quantity FROM order_item WHERE order_id=? ORDER BY product_id",id))
                    db.update("UPDATE product SET stock=stock+? WHERE id=?",item.get("quantity"),item.get("product_id"));
                db.update("UPDATE delivery_order SET payment_status=? WHERE id=?",payment.equals("PAID")?"REFUND_PENDING":"VOID",id);
            }
            case "YAPE_SUBMIT" -> {
                require(role.equals("CUSTOMER")&&!status.equals("CANCELLED")&&o.get("payment_method").equals("YAPE")&&payment.equals("UNPAID"),"No se puede declarar este pago.");
                require(note.matches("[A-Za-z0-9-]{6,50}"),"Usa una referencia de operación de 6 a 50 caracteres.");
                db.update("UPDATE delivery_order SET payment_reference=?,payment_status='VERIFYING' WHERE id=?",note,id);
            }
            case "YAPE_VERIFY" -> {
                require(admin&&payment.equals("VERIFYING")&&!status.equals("CANCELLED"),"No hay pago pendiente de verificación.");
                require(note.length()>=5,"Registra la evidencia de conciliación manual.");
                db.update("UPDATE delivery_order SET payment_status='PAID' WHERE id=?",id);
            }
            case "YAPE_REJECT" -> {
                require(admin&&payment.equals("VERIFYING")&&note.length()>=5,"Registra el motivo del rechazo.");
                db.update("UPDATE delivery_order SET payment_status='UNPAID',payment_reference=NULL WHERE id=?",id);
            }
            case "REFUND" -> {
                require(admin&&payment.equals("REFUND_PENDING")&&note.length()>=5,"No hay devolución pendiente o falta su referencia.");
                db.update("UPDATE delivery_order SET payment_status='REFUNDED' WHERE id=?",id);
            }
            case "RENDER_CASH" -> {
                require(admin&&status.equals("DELIVERED")&&o.get("payment_method").equals("CASH")&&o.get("cash_rendered_at")==null&&note.length()>=5,"La rendición no procede o falta constancia.");
                db.update("UPDATE delivery_order SET cash_rendered_at=CURRENT_TIMESTAMP WHERE id=?",id);
            }
            case "SETTLE" -> {
                require(admin&&status.equals("DELIVERED")&&payment.equals("PAID")&&o.get("settled_at")==null&&note.length()>=5,"No se puede liquidar este pedido.");
                require(o.get("payment_method").equals("YAPE")||o.get("cash_rendered_at")!=null,"Primero concilia el efectivo recibido.");
                db.update("UPDATE delivery_order SET settled_at=CURRENT_TIMESTAMP WHERE id=?",id);
            }
            default -> throw new BusinessException(400,"Acción desconocida.");
        }
        db.update("UPDATE delivery_order SET order_status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",next,id);
        event(id,u.get("id"),input.action(),note.isEmpty()?"Estado actualizado por el responsable.":note);
        return detail(u,id);
    }
}
