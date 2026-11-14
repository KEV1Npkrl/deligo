# Catálogos públicos de referencia

## Objetivo

DeliGo puede capturar catálogos visibles públicamente para construir una demostración académica más realista. La captura no crea una integración comercial, no consulta pedidos ni clientes y no acredita que el comercio esté afiliado a DeliGo.

La herramienta `LlamafoodCatalogScraper` recibe el nombre público de una página, hace una consulta para resolver la sucursal y otra para leer el menú de delivery. No usa credenciales, cookies, sesiones ni rutas de compra. El resultado queda bajo `data/imports/llamafood/`, que Git ignora deliberadamente.

## Formato normalizado

Cada instantánea JSON incluye:

- `source`: proveedor, URL visible, rutas consultadas y fecha UTC de captura.
- `merchant`: identificador externo, nombre, ubicación, tarifa publicada, calificación y enlaces de imagen.
- `categories[].products[]`: categoría, producto, descripción, precio publicado, precio actual, estado de agotado y variantes.
- `warnings`: inconsistencias detectables sin interpretar el negocio, como una promoción cuya descripción conserva una fecha vencida.
- `summary`: cantidad de categorías, productos, agotados y advertencias.

Los enlaces de imágenes se registran como procedencia; el extractor no descarga ni redistribuye los archivos. Antes de llevar una instantánea a MySQL se deben revisar nombres, duplicados, categorías, promociones, disponibilidad y fecha de captura. Los pedidos históricos de DeliGo mantienen sus propios snapshots de nombre y precio, por lo que una actualización futura del catálogo no altera ventas anteriores.

## Fuente verificada

El 13 de noviembre de 2026 se verificó `https://order.llamafood.com/BembosTarapoto`. La página pública resolvió la sucursal externa `11394` y expuso el menú mediante el servicio de cliente que usa la propia web. Algunas descripciones publicadas contenían vigencias terminadas en 2024; la herramienta las marca para revisión en vez de presentarlas como promociones actuales.

La lista de restaurantes para la dirección seleccionada en Tarapoto mostró 74 tiendas. La captura en lote resolvió 73 catálogos, 619 categorías y 3,701 productos; el enlace público de `CAFE_28-_FAST_FOOD` fue el único que respondió `status=WRONG`. Este fallo se conserva en el manifiesto y no se inventan datos para completarlo. La cifra describe lo disponible para ese punto y momento, no un padrón oficial de todos los negocios de la ciudad.

## Ejecución

```sh
mvn -q -DskipTests exec:java -Dexec.mainClass=pe.deligo.catalog.importer.LlamafoodCatalogScraper -Dexec.args="BembosTarapoto"
```

Para elegir otro destino:

```sh
mvn -q -DskipTests exec:java -Dexec.mainClass=pe.deligo.catalog.importer.LlamafoodCatalogScraper -Dexec.args="BembosTarapoto data/imports/llamafood/bembos.json"
```

`LlamafoodBulkCatalogScraper` acepta una respuesta de listado o un directorio de respuestas, deduplica negocios y escribe un manifiesto. `CatalogImportService` convierte las instantáneas revisadas en comercios, categorías, productos, grupos y opciones de MySQL. Un administrador puede importar por API y el perfil demo procesa automáticamente la carpeta local ignorada por Git.

La importación es una copia revisable, no una sincronización en vivo: actualiza por identificadores externos, oculta productos ausentes/agotados y promociones con vigencia textual vencida, y nunca confía en precios enviados desde el navegador al confirmar pedidos. No conviene acoplar el catálogo operativo directamente a una fuente externa que puede cambiar sin aviso.
