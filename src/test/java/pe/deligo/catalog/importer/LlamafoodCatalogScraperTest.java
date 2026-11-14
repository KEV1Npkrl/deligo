package pe.deligo.catalog.importer;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlamafoodCatalogScraperTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void normalizesCatalogAndFlagsExpiredPromotionText() throws Exception {
        Map<String, Object> branch = json("""
            {"status":"OK","branch":{"branch_id":11394,"page_name":"BembosTarapoto"}}
            """);
        Map<String, Object> catalog = json("""
            {
              "status":"OK",
              "branch":{"branch_id":11394,"branch_name":"Bembos - Tarapoto","page_name":"BembosTarapoto","ll_d_delivery_base_fee":3.5},
              "items":[{
                "item_category_id":10,"category_name":"PROMOCIONES","display_order_index":1,"items":[{
                  "item_id":20,"item_name":"Promo Royal","price":23.8,"final_price":16.9,"original_price":23.8,
                  "description":"Promoción válida hasta el 31/12/2024.","is_sold_out":0,
                  "options_set":[{"item_option_set_id":30,"description":"Tamaño","min_selected":1,"max_selected":1,
                    "options":[{"item_option_id":40,"label":"Regular","additional_price":0}]}]
                }]
              }]
            }
            """);

        Map<String, Object> result = LlamafoodCatalogScraper.normalize(
            "BembosTarapoto",
            URI.create("https://example.test/branch"),
            URI.create("https://example.test/catalog"),
            branch,
            catalog,
            Instant.parse("2026-11-13T12:00:00Z")
        );

        Map<String, Object> summary = castMap(result.get("summary"));
        assertEquals(1, summary.get("categories"));
        assertEquals(1, summary.get("products"));
        assertEquals(1, summary.get("warnings"));
        List<?> categories = (List<?>) result.get("categories");
        List<?> products = (List<?>) castMap(categories.getFirst()).get("products");
        Map<String, Object> product = castMap(products.getFirst());
        assertEquals(16.9, product.get("currentPrice"));
        assertEquals(false, product.get("soldOut"));
        assertTrue(product.containsKey("optionGroups"));
    }

    private static Map<String, Object> json(String value) throws Exception {
        return JSON.readValue(value, new TypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
