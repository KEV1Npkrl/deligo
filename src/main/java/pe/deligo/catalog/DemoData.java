package pe.deligo.catalog;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pe.deligo.shared.Store;

@Component
public class DemoData implements CommandLineRunner {
    private final Store db; private final PasswordEncoder encoder; private final boolean demo; private final String password;
    public DemoData(Store db, PasswordEncoder encoder, @Value("${deligo.demo}") boolean demo,
                    @Value("${deligo.seed-password}") String password) {
        this.db=db; this.encoder=encoder; this.demo=demo; this.password=password;
    }
    @Override @Transactional public void run(String... args) {
        if (db.list("SELECT id FROM platform_setting WHERE id=1").isEmpty())
            db.update("INSERT INTO platform_setting(id,rain_fee) VALUES(1,0)");
        if (!demo || !db.list("SELECT id FROM app_user WHERE id='demo-admin'").isEmpty()) return;
        if (password.length()<12) throw new IllegalArgumentException("DELIGO_SEED_PASSWORD debe tener al menos 12 caracteres.");
        var hash=encoder.encode(password);
        String[][] users={{"customer","Cliente demo","CUSTOMER"},{"merchant","Comercio demo","MERCHANT"},
            {"courier","Repartidor demo","COURIER"},{"admin","Administrador demo","ADMIN"}};
        for(var u:users) db.update("INSERT INTO app_user(id,email,full_name,password_hash,user_role) VALUES(?,?,?,?,?)",
            "demo-"+u[0],u[0]+"@deligo.demo",u[1],hash,u[2]);
        db.update("INSERT INTO zone(id,name,center_lat,center_lng,radius_km,delivery_fee) VALUES('tarapoto','Tarapoto',-6.4889,-76.3597,2.5,5),('morales','Morales',-6.4815,-76.3860,2,6),('banda','La Banda de Shilcayo',-6.4960,-76.3470,2,6)");
        String[][] merchants={
            {"selva","La Mesa de la Selva","Restaurantes","Sabores de casa, hechos en Tarapoto.","food","25"},
            {"burger","Barrio Burger","Restaurantes","Hamburguesas a la parrilla y papas crujientes.","burger","20"},
            {"market","Mercadito Verde","Minimarket","Lo que falta en tu despensa, cerca de ti.","market","15"},
            {"bakery","Miga & Café","Panadería","Pan recién hecho y una buena pausa.","bakery","15"},
            {"care","Bienestar Local","Cuidado personal","Higiene y cuidado diario. Sin medicamentos.","care","15"},
            {"flowers","Flor de Mayo","Flores","Un detalle que llega justo a tiempo.","flowers","25"}};
        for(var m:merchants) db.update("INSERT INTO merchant(id,owner_id,name,category,description,image_key,prep_minutes) VALUES(?,?,?,?,?,?,?)",
            m[0],"demo-merchant",m[1],m[2],m[3],m[4],Integer.parseInt(m[5]));
        String[][] products={
            {"juane","selva","Juane tradicional","Arroz, gallina, huevo y aceituna en hoja de bijao.","18.00"},
            {"tacacho","selva","Tacacho con cecina","Dos tacachos con cecina y ensalada fresca.","26.00"},
            {"chaufa","selva","Chaufa amazónico","Arroz salteado con cecina, chorizo y plátano.","24.00"},
            {"refresco","selva","Refresco de cocona","Preparado al momento, botella de 500 ml.","6.00"},
            {"classic","burger","La Clásica","Carne a la parrilla, queso, lechuga y salsa de casa.","19.90"},
            {"double","burger","Doble Barrio","Doble carne, doble queso y cebolla caramelizada.","27.90"},
            {"fries","burger","Papas de la casa","Papas crujientes con nuestras salsas.","8.00"},
            {"fruit","market","Canasta de frutas","Selección de frutas de temporada, 1 kg.","14.50"},
            {"milk","market","Leche entera","Presentación de 1 litro.","5.80"},
            {"rice","market","Arroz extra","Bolsa de 1 kg.","5.00"},
            {"bread","bakery","Pan artesanal","Seis panes recién horneados.","8.00"},
            {"coffee","bakery","Café de San Martín","Café molido de origen local, 250 g.","22.00"},
            {"cake","bakery","Torta de chocolate","Una porción para alegrar tu día.","12.00"},
            {"soap","care","Jabón de manos","Jabón líquido, 250 ml.","9.90"},
            {"shampoo","care","Shampoo de uso diario","Presentación de 400 ml.","19.50"},
            {"flowers1","flowers","Ramo de temporada","Flores frescas seleccionadas y tarjeta.","45.00"},
            {"plant","flowers","Pequeña suculenta","Planta decorativa con maceta.","22.00"}};
        for(var p:products) db.update("INSERT INTO product(id,merchant_id,name,description,price,stock) VALUES(?,?,?,?,?,?)",p[0],p[1],p[2],p[3],new java.math.BigDecimal(p[4]),30);
    }
}
