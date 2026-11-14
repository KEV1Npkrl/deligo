# DeliGo ↗

**Tu barrio. En tu puerta.** Plataforma web responsive de delivery multicategoría para una demostración académica en Tarapoto, Morales y La Banda de Shilcayo.

Proyecto individual de **Kevin Sanchez — [KEV1Npkrl](https://github.com/KEV1Npkrl)**, Lenguaje de Programación III. Java 25, Spring Boot 4.1.1, MySQL y frontend HTML/CSS/JavaScript del mismo origen.

> Versión inicial funcional, en desarrollo. No es una operación comercial ni implementa todavía todos los módulos del documento original. Comercios, cuentas y pagos de ejemplo son ficticios. No realizar transferencias ni introducir datos personales reales.

## Qué permite esta versión

- Explorar seis comercios de restaurantes, minimarket, panadería, cuidado personal y flores; filtrar categorías y ordenar por preparación o calificación.
- Crear una cuenta de cliente e iniciar/cerrar sesión mediante Spring Security, contraseña BCrypt, cookie HttpOnly y protección CSRF.
- Añadir productos a una bolsa por comercio, revisar delivery/lluvia/propina, comprobar cobertura geográfica y confirmar un pedido.
- Reservar stock dentro de una transacción, evitar duplicados por reintentos y conservar precios históricos. Rechazar cambios de precio no revisados por el cliente.
- Gestionar catálogo, disponibilidad del comercio y preparación desde el rol comercio.
- Aceptar una entrega, impedir dobles asignaciones y recorrer sus estados desde el rol repartidor.
- Registrar/verificar manualmente una referencia Yape de demo; registrar cobro en efectivo, rendición, devolución y liquidación desde administración.
- Calificar pedidos entregados, guardar hasta tres direcciones y abrir/resolver casos de soporte.
- Consultar ventas, comisiones, ganancias del repartidor y efectivo pendiente por separado.

Los listados de pedidos se actualizan con el botón **Actualizar**. La asignación de esta versión es aceptación manual protegida por transacción, no oferta automática por cercanía. Las coordenadas se introducen explícitamente; el enlace a OpenStreetMap muestra el destino, no GPS en vivo.

## Ejecutar con MySQL (ruta principal)

Requisitos: JDK 25, Maven 3.9+ y MySQL 8.0/8.4; alternativamente Docker y Docker Compose.

Con Docker:

```sh
cp .env.example .env
# Editar las tres contraseñas locales de .env antes de iniciar.
docker compose up --build
```

Abrir http://localhost:8080. MySQL se expone solo en `127.0.0.1:3307`; la aplicación usa la red interna de Compose. El perfil predeterminado es `mysql`; las cuentas de ejemplo solo se crean cuando `DELIGO_DEMO=true`. El volumen `mysql-data` conserva datos entre reinicios.

Con MySQL ya instalado, crear una **base nueva** llamada `deligo` y un usuario dedicado con permisos sobre esa base. No ejecutar contra una base existente de otro proyecto. Configurar en el entorno:

```text
SPRING_PROFILES_ACTIVE=mysql
DB_URL=jdbc:mysql://localhost:3306/deligo?connectionTimeZone=UTC
DB_USER=deligo
DB_PASSWORD=<contraseña de tu usuario MySQL>
DELIGO_DEMO=true
DELIGO_SEED_PASSWORD=<contraseña de demo de al menos 12 caracteres>
```

```sh
mvn spring-boot:run
```

En PowerShell, usar `$env:DB_USER='deligo'`, por ejemplo. Maven no carga `.env` automáticamente; ese archivo es para Compose. Si `java -version` muestra otra versión, configurar `JAVA_HOME` al JDK 25.

## Demo local rápida sin instalar MySQL

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Este perfil alternativo utiliza **H2 en archivo** bajo `data/`; no sustituye MySQL en la entrega del curso. Contraseña predeterminada de las cuentas ficticias: `DeligoDemo2026!`. Se puede reemplazar con `DELIGO_SEED_PASSWORD` antes del primer arranque. Los botones de ayuda rellenan la contraseña predeterminada; si la cambiaste, introdúcela manualmente. Cambiar la variable después del primer arranque no reemplaza contraseñas ya almacenadas.

| Rol | Cuenta de demo |
|---|---|
| Cliente | customer@deligo.demo |
| Comercio | merchant@deligo.demo |
| Repartidor | courier@deligo.demo |
| Administración | admin@deligo.demo |

El comercio de demo administra los seis locales. Una cuenta de comercio real debe tener su propia relación de pertenencia. La aplicación no crea usuarios privilegiados cuando el modo demo está desactivado; la provisión de producción está pendiente.

## Recorrido para la sustentación

1. Como cliente, seleccionar **La Mesa de la Selva**, añadir un juane y confirmar un pedido en efectivo usando el punto ficticio de Tarapoto.
2. Cerrar sesión. Como comercio, abrir **Pedidos**, aceptar y marcar listo.
3. Como repartidor, activar disponibilidad, aceptar, avanzar cada etapa y confirmar entrega/cobro.
4. Como administrador, consultar el pedido, registrar rendición y después liquidación con constancias de demo.
5. Como cliente, calificar y comprobar el historial.
6. Repetir con Yape: declarar referencia ficticia; administración la verifica; recién entonces el comercio puede preparar.
7. Demostrar cancelación de un pedido pagado: queda **Devolución pendiente** hasta que administración registra la devolución.

Cada acción registra un evento. Los botones no llaman a bancos ni mueven dinero real.

## Modelo de ingresos elegido

El 15 % se calcula sobre **delivery + adicional por lluvia**, nunca sobre los productos ni la propina. El comercio recibe el 100 % de los productos. El repartidor recibe el 85 % del delivery/lluvia y el 100 % de la propina. Redondeo `HALF_UP` a dos decimales, conservando la suma total. La liquidación semanal es un procedimiento manual registrado por pedido; no existe aún lote bancario ni scheduler de transferencias.

Ejemplo: productos S/ 30 + delivery S/ 5 + lluvia S/ 1 + propina S/ 2 = **S/ 38**. Comercio S/ 30, repartidor S/ 7.10 y DeliGo S/ 0.90. El ingreso bruto de DeliGo no equivale a utilidad. El documento de revisión explica costos y puntos por validar.

## Arquitectura

```text
Web HTML/CSS/JS → controladores REST → servicios transaccionales → Store/JdbcTemplate → MySQL
                         ↓                  ↓
                  Spring Security     reglas de pedidos/precios
```

Monolito organizado por `identity`, `catalog`, `orders`, `operations` y `shared`. Se eligió JDBC explícito para mostrar SQL, restricciones y bloqueos en la sustentación; no se afirma usar JPA. Sesión del mismo origen y CSRF, sin tokens persistidos en localStorage. `Pricing` es una política pura; `OrderService` concentra las transiciones y la unidad transaccional.

El esquema inicial está en `src/main/resources/db/schema.sql`. Es idempotente para instalaciones nuevas (`CREATE TABLE IF NOT EXISTS`); **no es un sistema de migraciones evolutivas**. Incorporar Flyway antes de modificar una base con datos persistentes.

## Pruebas

```sh
mvn test
```

Las pruebas rápidas usan H2 con modo MySQL. Para ejecutar la misma suite contra una base **exclusiva de pruebas**, previamente creada en MySQL:

```sh
mvn test -Dspring.datasource.url=jdbc:mysql://localhost:3306/deligo_test -Dspring.datasource.username=deligo_test -Dspring.datasource.password=YOUR_TEST_PASSWORD
```

La suite borra pedidos de esa base entre casos; nunca apuntarla a datos de uso real. Las credenciales del comando son de ejemplo: en CI usar variables/secretos del entorno. Se comprueban conservación de importes, snapshots, idempotencia, concurrencia, stock, cobertura, propiedad, estados, cobros, rendiciones y seguridad HTTP real.

## Capturar un catálogo público de referencia

El proyecto incluye un extractor Java para estudiar catálogos públicos sin iniciar sesión ni consultar pedidos. Por ejemplo, para Bembos Tarapoto:

```sh
mvn -q -DskipTests exec:java -Dexec.mainClass=pe.deligo.catalog.importer.LlamafoodCatalogScraper -Dexec.args="BembosTarapoto"
```

Realiza dos consultas: identifica la sucursal por su nombre de página y obtiene su menú de delivery. La instantánea normalizada se guarda en `data/imports/llamafood/`, directorio excluido de Git. Contiene procedencia y fecha, comercio, categorías, productos, precios publicados, disponibilidad, variantes y advertencias de vigencia encontradas en las descripciones.

La captura es una entrada de revisión, no una importación automática a MySQL. Los precios pueden cambiar y la fuente puede conservar textos desactualizados. DeliGo no presenta estos comercios como afiliados propios ni descarga sus imágenes al repositorio. Ver [política y formato de catálogos](docs/CATALOGOS-REFERENCIA.md).

## Estado del diseño y límites actuales

Referencia solicitada: [Food Delivery App Design — FREE](https://www.figma.com/community/file/1364162990537420776/food-delivery-app-design-free). [Archivo de referencia](https://www.figma.com/design/7cyOJmbNQqn7Nsv7cu3SAy/Food-Delivery-App-Design---FREE--Community-?node-id=212-9000).

Se recuperó el acceso a la integración y se extrajo la pantalla Cart (164:4765). Se aplicaron Montserrat e Inter (archivos locales con licencia SIL OFL), colores y fundamentos de tarjetas y totales. La interfaz sigue siendo una adaptación inicial, con monogramas e ilustración CSS originales: no se presenta como reproducción fiel de todas las pantallas. El recorrido de efectivo se verificó en navegador; el detalle de pedido se revisó a 360, 390, 768 y 1440 px. Ver [diseño y pendientes](docs/DISENO-FIGMA.md).

Pendiente antes de declarar completa la especificación: 2FA y recuperación, ofertas automáticas de 20 segundos, cancelación automática por tiempo, GPS/push, roles dinámicos, postulaciones/documentos, autopedidos, membresías/publicidad, libro contable completo, liquidaciones por lotes, reportes temporales, mantenimiento completo de ciudades/zonas, cumplimiento operativo y despliegue cloud. Ver [backlog](docs/BACKLOG.md).

Esta versión no es apta para usuarios reales: faltan, entre otros, límites de intentos de login, TLS y secretos gestionados en despliegue, segundo factor y validación de contactos. Las cuentas de demostración no deben exponerse con información real.

## Documentación

- [Revisión crítica de 32 hallazgos y decisiones posteriores](docs/REVISION-REQUERIMIENTOS.md)
- [Backlog y criterios de cierre](docs/BACKLOG.md)
- [Modelo de datos y API](docs/ARQUITECTURA-API.md)
- [Verificación de esta versión](docs/VERIFICACION.md)
- [Catálogos públicos de referencia](docs/CATALOGOS-REFERENCIA.md)

Solo se publica código y documentación propia. Los PDFs originales, datos personales, bases locales y credenciales privadas quedan fuera del repositorio. No se añaden colaboradores ni coautores.
