package pe.deligo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real cookies, login, CSRF, validation and role authorization over HTTP. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HttpWorkflowTest {
    @LocalServerPort int port;
    @Autowired pe.deligo.shared.Store db;
    @BeforeEach void prepare(){db.update("UPDATE platform_setting SET rain_fee=0");db.update("UPDATE product SET stock=30 WHERE id='milk'");db.update("UPDATE app_user SET active=TRUE");}
    final ObjectMapper json=new ObjectMapper();
    HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();}
    HttpResponse<String> send(HttpClient c,String path,String method,String body,String token) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(10));
        if(token!=null)b.header("X-CSRF-TOKEN",token);
        if(body!=null)b.header("Content-Type",path.endsWith("/login")?"application/x-www-form-urlencoded":"application/json");
        return c.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String token(HttpClient c) throws Exception {return json.readTree(send(c,"/api/auth/csrf","GET",null,null).body()).get("token").asString();}
    void login(HttpClient c,String user) throws Exception {
        var r=send(c,"/api/auth/login","POST","username="+user+"%40deligo.demo&password=DeligoDemo2026%21",token(c));assertEquals(200,r.statusCode(),r.body());
    }
    @Test void customerSessionCanOrderButCannotAdminister() throws Exception {
        var c=client();login(c,"customer");var csrf=token(c);
        var me=send(c,"/api/auth/me","GET",null,null);assertEquals("CUSTOMER",json.readTree(me.body()).get("user").get("user_role").asString());
        assertEquals(403,send(c,"/api/admin/summary","GET",null,null).statusCode());
        assertEquals(403,send(c,"/api/admin/rain","PUT","{\"amount\":1}",csrf).statusCode());
        assertEquals(400,send(c,"/api/orders","POST","{}",csrf).statusCode());
        var payload="""
          {"merchantId":"market","zoneId":"tarapoto","address":"Dirección ficticia 123","latitude":-6.4889,"longitude":-76.3597,
           "paymentMethod":"CASH","tip":0,"expectedTotal":10.8,"idempotencyKey":"%s","items":[{"productId":"milk","quantity":1}]}
          """.formatted(UUID.randomUUID());
        var result=send(c,"/api/orders","POST",payload,csrf);assertEquals(200,result.statusCode(),result.body());
        var orderId=json.readTree(result.body()).get("id").asString();
        assertEquals(200,send(c,"/api/orders/"+orderId,"GET",null,null).statusCode());
        assertEquals(204,send(c,"/api/auth/logout","POST",null,csrf).statusCode());
        assertEquals(401,send(c,"/api/orders","GET",null,null).statusCode());
    }
    @Test void registeredUserCannotChooseTheirRole() throws Exception {
        var c=client();var email="test-"+UUID.randomUUID()+"@example.test";
        var payload=json.writeValueAsString(Map.of("name","Cliente ficticio","email",email,"password","NewCustomer2026!","role","ADMIN"));
        var result=send(c,"/api/auth/register","POST",payload,token(c));assertEquals(200,result.statusCode(),result.body());
        assertEquals(200,send(c,"/api/auth/login","POST","username="+URLEncoder.encode(email,java.nio.charset.StandardCharsets.UTF_8)+"&password=NewCustomer2026%21",token(c)).statusCode());
        assertEquals("CUSTOMER",json.readTree(send(c,"/api/auth/me","GET",null,null).body()).get("user").get("user_role").asString());
    }
}
