# Verificación — actualizada el 11 de noviembre de 2026

## Evidencia obtenida

- Compilación y empaquetado ejecutable de Spring Boot con Java 25.0.1.
- JavaScript revisado con `node --check`.
- 17 pruebas iniciales aprobadas con H2 y con MySQL real 8.0.42.
- Suite ampliada: 20 pruebas aprobadas contra MySQL 8.0.42, cero fallos/errores/omitidas. Incluye dos pruebas HTTP con cookies, login, CSRF, roles y creación de pedido, más 18 pruebas de dominio/integración.
- MySQL de prueba se inicializó en un directorio aislado bajo `work/`, con base exclusiva. No se utilizaron bases preexistentes del propietario.

## Qué comprueban las pruebas

Conservación de importes y propina; snapshots; idempotencia y claves reutilizadas incorrectamente; checkout concurrente; stock insuficiente; cobertura; carrito de otro comercio; comercio cerrado; precio cambiado; propiedad de pedidos; permisos de estado; versión obsoleta; cancelación sin doble devolución de stock; Yape pendiente/verificado/devuelto; entrega en efectivo, rendición y liquidación; límite de entrega activa; dos aceptaciones simultáneas; endpoints privados; CSRF; sesión real y registro sin escalamiento de rol.

## Qué NO se ha verificado

- No hay prueba de carga ni garantía de rendimiento/alta disponibilidad.
- Queda pendiente el recorrido visual completo de Yape/cancelación/devolución, aunque sus reglas sí están cubiertas por las pruebas de integración.
- Docker Compose y la imagen de contenedor están preparados, pero Docker Desktop no estuvo operativo; no se declara prueba de contenedor exitosa.
- Se recuperó el acceso a Figma y se extrajeron los fundamentos de Cart (164:4765). La adaptación completa de pantallas y assets sigue pendiente.
- No se ejecutaron operaciones bancarias, envío de correo/SMS, SUNAT, GPS real ni despliegue cloud.

Los resultados se refieren a esta versión, no al cumplimiento completo de los requisitos originales. El workflow de CI se comprobará por separado después de publicar.

## Verificación visual y funcional en navegador

Se completó un pedido ficticio de efectivo desde la interfaz, pasando por cliente, comercio, repartidor y administración: creación, preparación, listo, asignación, ruta, recogida, entrega/cobro, rendición y liquidación. Total S/24: comercio S/18, repartidor S/5.10 y DeliGo S/0.90. El pedido persistió tras reiniciar la aplicación.

Se verificó después del reinicio el historial en español, la ocultación del carrito en administración y la tipografía Montserrat. El detalle del pedido se comprobó a 360, 390, 768 y 1440 px sin desbordamiento horizontal; revisión visual de móvil y escritorio. Inicio revisado a 360 y 390 px. Esto no equivale a una revisión de todas las pantallas.

Se corrigió la disponibilidad visual de las acciones: el comercio espera verificación de Yape antes de preparar, y administración espera la rendición de efectivo antes de liquidar. El servidor ya aplicaba estas restricciones; no se modificaron sus reglas.
