package pe.deligo.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import pe.deligo.shared.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final Store db; private final Identity identity; private final PasswordEncoder encoder; private final boolean demo;
    public AuthController(Store db, Identity identity, PasswordEncoder encoder, @Value("${deligo.demo}") boolean demo) {
        this.db=db; this.identity=identity; this.encoder=encoder; this.demo=demo;
    }
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"header",token.getHeaderName()); }
    @GetMapping("/me") Map<String,Object> me(Authentication auth) {
        var result=new HashMap<String,Object>(); result.put("demo",demo);
        result.put("user",auth==null?null:identity.user(auth)); return result;
    }
    public record Registration(@NotBlank @Size(max=100) String name, @Email @NotBlank @Size(max=160) String email,
                               @NotBlank @Size(min=12,max=64) String password) {}
    @PostMapping("/register") Map<String,Boolean> register(@Valid @RequestBody Registration r) {
        db.update("INSERT INTO app_user(id,email,full_name,password_hash,user_role) VALUES(?,?,?,?,?)",
            Store.id(),r.email().toLowerCase(Locale.ROOT).trim(),r.name().trim(),encoder.encode(r.password()),"CUSTOMER");
        return Map.of("ok",true);
    }
}
