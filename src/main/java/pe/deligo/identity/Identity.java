package pe.deligo.identity;

import pe.deligo.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import java.util.Map;

@Service
public class Identity {
    private final Store db;
    public Identity(Store db) { this.db = db; }
    public Map<String,Object> user(Authentication auth) {
        if (auth == null) throw new BusinessException(401,"Inicia sesión para continuar.");
        var u = db.one("SELECT id,email,full_name,user_role,active,available FROM app_user WHERE email=?",auth.getName());
        if (!Store.yes(u,"active")) throw new BusinessException(403,"Esta cuenta está suspendida.");
        return u;
    }
    public void role(Map<String,Object> u, String... roles) {
        for (var role : roles) if (role.equals(u.get("user_role"))) return;
        throw new BusinessException(403,"Tu cuenta no tiene permiso para esta acción.");
    }
}
