package pe.deligo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pe.deligo.catalog.importer.CatalogImportService;
import pe.deligo.orders.OrderInput;
import pe.deligo.orders.OrderService;
import pe.deligo.shared.BusinessException;
import pe.deligo.shared.Store;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class CatalogOptionsIntegrationTest {
    @Autowired CatalogImportService importer; @Autowired Store db; @Autowired OrderService orders;

    @Test void importsOptionsAndUsesServerPriceWithHistoricalSnapshot() {
        var result=importer.importSnapshot(snapshot(),"demo-admin"); String merchant=result.get("merchantId").toString();
        var product=db.one("SELECT id FROM product WHERE merchant_id=? AND source_external_id='p-1'",merchant);
        var option=db.one("SELECT o.id FROM product_option o JOIN product_option_group g ON g.id=o.group_id WHERE g.product_id=? AND o.source_external_id='large'",product.get("id"));
        var input=new OrderInput(merchant,"tarapoto","Jr. Prueba 123",-6.4889,-76.3597,"CASH",BigDecimal.ZERO,new BigDecimal("17.00"),"options-test-123",List.of(new OrderInput.Item(product.get("id").toString(),1,List.of(new OrderInput.Selection(option.get("id").toString(),1)))));
        var order=orders.create(Map.of("id","demo-customer","user_role","CUSTOMER"),input);
        assertEquals(new BigDecimal("12.00"),order.get("subtotal"));
        assertEquals("Tamaño",db.one("SELECT group_name FROM order_item_option").get("group_name"));
    }

    @Test void rejectsMissingRequiredChoice() {
        var result=importer.importSnapshot(snapshot(),"demo-admin"); String merchant=result.get("merchantId").toString();
        String product=db.one("SELECT id FROM product WHERE merchant_id=? AND source_external_id='p-1'",merchant).get("id").toString();
        var input=new OrderInput(merchant,"tarapoto","Jr. Prueba 123",-6.4889,-76.3597,"CASH",BigDecimal.ZERO,new BigDecimal("15.00"),"options-test-456",List.of(new OrderInput.Item(product,1,List.of())));
        assertThrows(BusinessException.class,()->orders.create(Map.of("id","demo-customer","user_role","CUSTOMER"),input));
    }

    private Map<String,Object> snapshot() {
        var options=List.of(
            Map.of("externalId","regular","name","Regular","additionalPrice",0),
            Map.of("externalId","large","name","Grande","additionalPrice",2));
        var group=Map.of("externalId","g-1","name","Tamaño","minimum",1,"maximum",1,"options",options);
        var product=Map.of("externalId","p-1","name","Combo","description","Producto de prueba","currentPrice",10,"soldOut",false,"optionGroups",List.of(group));
        var category=Map.of("externalId","c-1","name","Combos","products",List.of(product));
        return Map.of("schemaVersion",1,
            "source",Map.of("provider","Llamafood","capturedAt",Instant.now().toString()),
            "merchant",Map.of("externalId","branch-test","pageName","PruebaTarapoto","name","Prueba Tarapoto","category","restaurant"),
            "categories",List.of(category));
    }
}
