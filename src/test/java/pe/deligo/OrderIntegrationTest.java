package pe.deligo;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import pe.deligo.shared.*;
import pe.deligo.orders.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderIntegrationTest {
    @Autowired Store db;
    @Autowired OrderService orders;
    @LocalServerPort int port;
    Map<String,Object> customer,merchant,courier,admin;
    @BeforeEach void reset() {
        for(var table:List.of("support_case","review","order_event","order_item","delivery_order","customer_address")) db.update("DELETE FROM "+table);
        db.update("UPDATE product SET stock=30,active=TRUE");db.update("UPDATE product SET price=18 WHERE id='juane'");
        db.update("UPDATE merchant SET is_open=TRUE");db.update("UPDATE platform_setting SET rain_fee=0");
        db.update("UPDATE app_user SET active=TRUE,available=TRUE");
        customer=user("customer");merchant=user("merchant");courier=user("courier");admin=user("admin");
    }
    Map<String,Object> user(String id){return db.one("SELECT * FROM app_user WHERE id=?","demo-"+id);}
    OrderInput input(String payment,String key){return new OrderInput("selva","tarapoto","Dirección ficticia 123",-6.4889,-76.3597,payment,new BigDecimal("2.00"),new BigDecimal("25.00"),key,List.of(new OrderInput.Item("juane",1)));}
    Map<String,Object> create(String payment){return orders.create(customer,input(payment,UUID.randomUUID().toString()));}
    Map<String,Object> act(Map<String,Object> actor,Map<String,Object> o,String action,String reason){return orders.action(actor,Store.str(o,"id"),new OrderController.Action(action,((Number)o.get("version")).intValue(),reason));}
    @Test void allocationConservesMoneyAndProtectsTips(){
        var p=Pricing.calculate(new BigDecimal("30.00"),new BigDecimal("5.00"),new BigDecimal("1.00"),new BigDecimal("2.00"));
        assertEquals(new BigDecimal("0.90"),p.platformShare());assertEquals(new BigDecimal("7.10"),p.courierShare());
        assertEquals(p.total(),p.subtotal().add(p.platformShare()).add(p.courierShare()));
    }
    @Test void pricesAndCommissionAreServerSnapshots(){var o=create("CASH");db.update("UPDATE product SET price=99 WHERE id='juane'");db.update("UPDATE platform_setting SET rain_fee=2");
        var saved=orders.detail(customer,Store.str(o,"id"));assertEquals(new BigDecimal("25.00"),Store.money(saved,"total"));assertEquals(new BigDecimal("0.75"),Store.money(saved,"platform_share"));}
    @Test void sameCheckoutIsIdempotent(){var i=input("CASH","same-key-123");var a=orders.create(customer,i);var b=orders.create(customer,i);assertEquals(a.get("id"),b.get("id"));assertEquals(29,db.one("SELECT stock FROM product WHERE id='juane'").get("stock"));}
    @Test void idempotencyKeyCannotBeReusedForDifferentCart(){orders.create(customer,input("CASH","same-key-123"));assertThrows(BusinessException.class,()->orders.create(customer,input("YAPE","same-key-123")));}
    @Test void simultaneousCheckoutCreatesOneOrder() throws Exception {
        var i=input("CASH","concurrent-key-123");try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);Callable<Object> call=()->{start.await();return orders.create(customer,i).get("id");};var a=pool.submit(call);var b=pool.submit(call);start.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }assertEquals(29,db.one("SELECT stock FROM product WHERE id='juane'").get("stock"));
    }
    @Test void noOversellingAndRollback(){db.update("UPDATE product SET stock=0 WHERE id='juane'");assertThrows(BusinessException.class,()->create("CASH"));assertTrue(db.list("SELECT id FROM delivery_order").isEmpty());}
    @Test void outsideCoverageCannotOrder(){var i=new OrderInput("selva","tarapoto","Dirección fuera de zona",-12.0,-77.0,"CASH",BigDecimal.ZERO,new BigDecimal("23.00"),"outside-123",List.of(new OrderInput.Item("juane",1)));assertThrows(BusinessException.class,()->orders.create(customer,i));}
    @Test void foreignMerchantProductRejected(){var i=new OrderInput("selva","tarapoto","Dirección ficticia 123",-6.4889,-76.3597,"CASH",BigDecimal.ZERO,new BigDecimal("23.00"),"foreign-123",List.of(new OrderInput.Item("classic",1)));assertThrows(BusinessException.class,()->orders.create(customer,i));}
    @Test void closedMerchantCannotAcceptCheckout(){db.update("UPDATE merchant SET is_open=FALSE WHERE id='selva'");assertThrows(BusinessException.class,()->create("CASH"));}
    @Test void changedQuoteCannotBeSilentlyCharged(){db.update("UPDATE platform_setting SET rain_fee=1");assertThrows(BusinessException.class,()->create("CASH"));assertEquals(30,db.one("SELECT stock FROM product WHERE id='juane'").get("stock"));}
    @Test void unrelatedCustomerCannotReadOrder(){var o=create("CASH");var other=new HashMap<>(customer);other.put("id","someone-else");assertThrows(BusinessException.class,()->orders.detail(other,Store.str(o,"id")));}
    @Test void customerCannotPrepareAndStaleVersionCannotMutate(){var o=create("CASH");assertThrows(BusinessException.class,()->act(customer,o,"PREPARE",""));act(merchant,o,"PREPARE","");assertThrows(BusinessException.class,()->act(customer,o,"CANCEL","Cambio de idea"));}
    @Test void cancelledOrderReleasesStockExactlyOnce(){var o=create("CASH");var cancelled=act(customer,o,"CANCEL","Cambio de planes");assertEquals("VOID",cancelled.get("payment_status"));assertEquals(30,db.one("SELECT stock FROM product WHERE id='juane'").get("stock"));assertThrows(BusinessException.class,()->act(customer,cancelled,"CANCEL","Otra cancelación"));assertEquals(30,db.one("SELECT stock FROM product WHERE id='juane'").get("stock"));}
    @Test void yapeNeedsVerificationAndCancellationCreatesRefund(){var initial=create("YAPE");assertThrows(BusinessException.class,()->act(merchant,initial,"PREPARE",""));var o=initial;o=act(customer,o,"YAPE_SUBMIT","DEMO123456");assertEquals("VERIFYING",o.get("payment_status"));o=act(admin,o,"YAPE_VERIFY","Conciliación ficticia");o=act(customer,o,"CANCEL","Cambio de planes");assertEquals("REFUND_PENDING",o.get("payment_status"));o=act(admin,o,"REFUND","Devolución DEMO-1");assertEquals("REFUNDED",o.get("payment_status"));}
    @Test void cashLifecycleRequiresCollectionAndRenderingBeforeSettlement(){var o=create("CASH");o=act(merchant,o,"PREPARE","");o=act(merchant,o,"READY","");o=act(courier,o,"CLAIM","");for(var a:List.of("TO_STORE","AT_STORE","PICKED_UP","ON_THE_WAY","ARRIVED"))o=act(courier,o,a,"");
        var arrived=o;assertThrows(BusinessException.class,()->act(courier,arrived,"DELIVERED",""));o=act(courier,o,"DELIVERED","EFECTIVO COBRADO");assertEquals("PAID",o.get("payment_status"));var delivered=o;assertThrows(BusinessException.class,()->act(admin,delivered,"SETTLE","Liquidación demo"));o=act(admin,o,"RENDER_CASH","Rendición DEMO-1");o=act(admin,o,"SETTLE","Liquidación DEMO-1");assertNotNull(o.get("settled_at"));}
    @Test void courierCannotHoldTwoDeliveries(){var a=act(merchant,act(merchant,create("CASH"),"PREPARE",""),"READY","");var b=act(merchant,act(merchant,create("CASH"),"PREPARE",""),"READY","");act(courier,a,"CLAIM","");assertThrows(BusinessException.class,()->act(courier,b,"CLAIM",""));}
    @Test void simultaneousClaimsHaveExactlyOneWinner() throws Exception {
        var o=act(merchant,act(merchant,create("CASH"),"PREPARE",""),"READY","");
        try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);Callable<Boolean> call=()->{start.await();try{act(courier,o,"CLAIM","");return true;}catch(BusinessException ex){return false;}};var a=pool.submit(call);var b=pool.submit(call);start.countDown();assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));}
    }
    @Test void httpProtectsPrivateEndpointsAndCsrf() throws Exception {
        var client=HttpClient.newHttpClient();String base="http://localhost:"+port;
        assertEquals(200,client.send(HttpRequest.newBuilder(URI.create(base+"/api/catalog/merchants")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401,client.send(HttpRequest.newBuilder(URI.create(base+"/api/orders")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(403,client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/register")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{} ")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
