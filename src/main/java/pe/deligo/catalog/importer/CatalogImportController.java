package pe.deligo.catalog.importer;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.deligo.identity.Identity;

import java.util.Map;

@RestController
public class CatalogImportController {
    private final CatalogImportService importer; private final Identity identity;
    public CatalogImportController(CatalogImportService importer,Identity identity) { this.importer=importer; this.identity=identity; }

    @PostMapping("/api/admin/catalog/import")
    Map<String,Object> importCatalog(Authentication auth,@RequestBody Map<String,Object> snapshot) {
        var admin=identity.user(auth); identity.role(admin,"ADMIN");
        return importer.importSnapshot(snapshot,admin.get("id").toString());
    }
}
