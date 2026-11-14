package pe.deligo.catalog.importer;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captura un catálogo público de Llamafood como referencia académica.
 *
 * <p>No usa credenciales, cookies ni endpoints de pedidos. La instantánea resultante debe
 * revisarse antes de importarse a la base de DeliGo porque un catálogo publicado puede
 * contener promociones vencidas, datos incompletos o cambios hechos por el comercio.</p>
 */
public final class LlamafoodCatalogScraper {
    static final String SITE = "https://order.llamafood.com/";
    static final String API = "https://customer.llama-apis.com/customer";
    static final String DELIVERY = "lld_serve_type_delivery";
    private static final Pattern SAFE_PAGE_NAME = Pattern.compile("[A-Za-z0-9_.%-]{1,160}");
    private static final Pattern VALID_UNTIL = Pattern.compile(
        "(?i)(?:v[áa]lid[ao]|vigente)[^.]{0,80}?hasta(?: el)?\\s+(\\d{1,2}/\\d{1,2}/\\d{4})"
    );
    private static final DateTimeFormatter PERUVIAN_DATE = DateTimeFormatter.ofPattern("d/M/uuuu");
    private static final ZoneId PERU_TIME = ZoneId.of("America/Lima");
    private static final ObjectMapper JSON = new ObjectMapper();

    private LlamafoodCatalogScraper() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                "Uso: LlamafoodCatalogScraper <nombre-de-pagina> [archivo-salida.json]"
            );
        }
        String pageName = validatePageName(args[0]);
        Path output = args.length == 2
            ? Path.of(args[1])
            : Path.of("data", "imports", "llamafood",
                pageName + "-" + LocalDate.now(PERU_TIME) + ".json");

        Map<String,Object> normalized=capture(pageName,output);

        Map<String, Object> summary = map(normalized.get("summary"));
        System.out.printf(
            Locale.ROOT,
            "Catálogo guardado en %s: %s categorías, %s productos, %s advertencias.%n",
            output.toAbsolutePath(), summary.get("categories"), summary.get("products"),
            summary.get("warnings")
        );
    }

    public static Map<String,Object> capture(String requestedPageName,Path output) throws Exception {
        String pageName=validatePageName(requestedPageName);
        Instant capturedAt = Instant.now();
        URI branchUri = URI.create(API + "/v1/llamafood_branches/" + pageName);
        Map<String, Object> branchResponse = fetchJson(branchUri, pageName);
        Map<String, Object> branch = map(branchResponse.get("branch"));
        long branchId = longValue(branch.get("branch_id"), "branch.branch_id");
        URI catalogUri = URI.create(API + "/v2/branches/" + branchId
            + "/branch_items?serve_type=" + DELIVERY);
        Map<String, Object> catalogResponse = fetchJson(catalogUri, pageName);

        Map<String, Object> normalized = normalize(
            pageName, branchUri, catalogUri, branchResponse, catalogResponse, capturedAt
        );
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), normalized);

        return normalized;
    }

    static Map<String, Object> normalize(
        String pageName,
        URI branchUri,
        URI catalogUri,
        Map<String, Object> branchResponse,
        Map<String, Object> catalogResponse,
        Instant capturedAt
    ) {
        requireOk(branchResponse, "sucursal");
        requireOk(catalogResponse, "catálogo");
        Map<String, Object> branch = map(catalogResponse.get("branch"));
        if (branch.isEmpty()) branch = map(branchResponse.get("branch"));
        List<Object> rawCategories = list(catalogResponse.get("categories"));
        if (rawCategories.isEmpty()) rawCategories = list(catalogResponse.get("items"));

        List<Map<String, Object>> categories = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();
        int productCount = 0;
        int soldOutCount = 0;

        for (Object rawCategory : rawCategories) {
            Map<String, Object> sourceCategory = map(rawCategory);
            Map<String, Object> category = new LinkedHashMap<>();
            copy(category, "externalId", sourceCategory, "item_category_id");
            copy(category, "name", sourceCategory, "category_name");
            copy(category, "description", sourceCategory, "description");
            copy(category, "position", sourceCategory, "display_order_index");
            category.put("scheduled", one(sourceCategory.get("is_scheduled")));
            copy(category, "scheduledFrom", sourceCategory, "category_scheduled_from_delivery_date");
            copy(category, "scheduledTo", sourceCategory, "category_scheduled_to_delivery_date");

            List<Map<String, Object>> products = new ArrayList<>();
            for (Object rawProduct : list(sourceCategory.get("items"))) {
                Map<String, Object> sourceProduct = map(rawProduct);
                Map<String, Object> product = normalizeProduct(sourceProduct);
                products.add(product);
                productCount++;
                if (Boolean.TRUE.equals(product.get("soldOut"))) soldOutCount++;

                String description = text(sourceProduct.get("description"));
                Matcher matcher = VALID_UNTIL.matcher(description);
                while (matcher.find()) {
                    try {
                        LocalDate validUntil = LocalDate.parse(matcher.group(1), PERUVIAN_DATE);
                        LocalDate captureDate = capturedAt.atZone(PERU_TIME).toLocalDate();
                        if (validUntil.isBefore(captureDate)) {
                            Map<String, Object> warning = new LinkedHashMap<>();
                            warning.put("code", "EXPIRED_PROMOTION_TEXT");
                            warning.put("productExternalId", sourceProduct.get("item_id"));
                            warning.put("productName", sourceProduct.get("item_name"));
                            warning.put("validUntil", validUntil.toString());
                            warning.put("message", "La descripción publicada contiene una vigencia vencida.");
                            warnings.add(warning);
                        }
                    } catch (DateTimeParseException ignored) {
                        // Una fecha ilegible se conserva en la descripción sin inventar su significado.
                    }
                }
            }
            category.put("products", products);
            categories.add(category);
        }

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("provider", "Llamafood");
        source.put("pageUrl", SITE + pageName);
        source.put("branchEndpoint", branchUri.toString());
        source.put("catalogEndpoint", catalogUri.toString());
        source.put("capturedAt", capturedAt.toString());
        source.put("purpose", "Referencia académica para una demo; no acredita afiliación con DeliGo.");

        Map<String, Object> merchant = new LinkedHashMap<>();
        copy(merchant, "externalId", branch, "branch_id");
        copy(merchant, "pageName", branch, "page_name");
        copy(merchant, "name", branch, "branch_name");
        copy(merchant, "category", branch, "branch_type");
        copy(merchant, "description", branch, "page_motto");
        copy(merchant, "address", branch, "address");
        copy(merchant, "latitude", branch, "location_lat");
        copy(merchant, "longitude", branch, "location_lng");
        copy(merchant, "deliveryBaseFee", branch, "ll_d_delivery_base_fee");
        copy(merchant, "rating", branch, "ll_d_avg_branch_rating");
        copy(merchant, "ratingOrders", branch, "ll_d_avg_branch_rating_orders");
        copy(merchant, "logoUrl", branch, "logo_img_url");
        copy(merchant, "coverUrl", branch, "llamafood_cover_img_url");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("categories", categories.size());
        summary.put("products", productCount);
        summary.put("soldOutProducts", soldOutCount);
        summary.put("warnings", warnings.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("source", source);
        result.put("merchant", merchant);
        result.put("summary", summary);
        result.put("warnings", warnings);
        result.put("categories", categories);
        return result;
    }

    private static Map<String, Object> normalizeProduct(Map<String, Object> source) {
        Map<String, Object> product = new LinkedHashMap<>();
        copy(product, "externalId", source, "item_id");
        copy(product, "name", source, "item_name");
        copy(product, "description", source, "description");
        copy(product, "price", source, "price");
        Object finalPrice = source.get("final_price");
        product.put("currentPrice", finalPrice != null ? finalPrice : source.get("price"));
        copy(product, "originalPrice", source, "original_price");
        product.put("soldOut", one(source.get("is_sold_out")));
        copy(product, "position", source, "display_order_index_category");
        copy(product, "imageUrl", source, "img_url_normal");
        copy(product, "thumbnailUrl", source, "img_url_small");

        List<Map<String, Object>> optionGroups = new ArrayList<>();
        for (Object rawGroup : list(source.get("options_set"))) {
            Map<String, Object> sourceGroup = map(rawGroup);
            Map<String, Object> group = new LinkedHashMap<>();
            copy(group, "externalId", sourceGroup, "item_option_set_id");
            copy(group, "name", sourceGroup, "description");
            copy(group, "minimum", sourceGroup, "min_selected");
            copy(group, "maximum", sourceGroup, "max_selected");
            copy(group, "position", sourceGroup, "display_order_index_item");
            List<Map<String, Object>> options = new ArrayList<>();
            for (Object rawOption : list(sourceGroup.get("options"))) {
                Map<String, Object> sourceOption = map(rawOption);
                Map<String, Object> option = new LinkedHashMap<>();
                copy(option, "externalId", sourceOption, "item_option_id");
                copy(option, "name", sourceOption, "label");
                copy(option, "additionalPrice", sourceOption, "additional_price");
                copy(option, "position", sourceOption, "display_order_index");
                options.add(option);
            }
            group.put("options", options);
            optionGroups.add(group);
        }
        product.put("optionGroups", optionGroups);
        return product;
    }

    private static Map<String, Object> fetchJson(URI uri, String pageName)
        throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Origin", "https://order.llamafood.com")
            .header("Referer", SITE + pageName)
            .header("User-Agent", "DeliGoAcademicCatalogResearch/0.1")
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("La fuente respondió HTTP " + response.statusCode() + " para " + uri);
        }
        Map<String, Object> parsed = JSON.readValue(
            response.body(), new TypeReference<Map<String, Object>>() {}
        );
        requireOk(parsed, uri.toString());
        return parsed;
    }

    private static String validatePageName(String value) {
        String trimmed = Objects.requireNonNull(value, "nombre de página").trim();
        if (!SAFE_PAGE_NAME.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Nombre de página inválido: " + value);
        }
        return trimmed;
    }

    private static void requireOk(Map<String, Object> response, String label) {
        if (!"OK".equalsIgnoreCase(text(response.get("status")))) {
            throw new IllegalArgumentException("Respuesta inválida de " + label + ": status="
                + response.get("status"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    private static void copy(Map<String, Object> target, String targetKey,
                             Map<String, Object> source, String sourceKey) {
        Object value = source.get(sourceKey);
        if (value != null && !(value instanceof String string && string.isBlank())) {
            target.put(targetKey, value);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static boolean one(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() == 1;
        return "1".equals(text(value)) || "true".equalsIgnoreCase(text(value));
    }

    private static long longValue(Object value, String label) {
        if (value instanceof Number number) return number.longValue();
        try {
            return new BigDecimal(text(value)).longValueExact();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Falta un valor numérico válido para " + label, exception);
        }
    }
}
