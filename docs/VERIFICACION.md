# Verificación — 10 de noviembre de 2026

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
- La vista previa de navegador quedó bloqueada por aislamiento de procesos y permisos de red del entorno. No se certifica todavía el recorrido visual end-to-end ni ausencia de problemas en todos los breakpoints.
- Docker Compose y la imagen de contenedor están preparados, pero Docker Desktop no estuvo operativo; no se declara prueba de contenedor exitosa.
- La integración Figma devolvió reiteradamente un problema de autorización. No se certifica fidelidad a tipografía/componentes originales.
- No se ejecutaron operaciones bancarias, envío de correo/SMS, SUNAT, GPS real ni despliegue cloud.

Los resultados se refieren a esta versión, no al cumplimiento completo de los requisitos originales. El workflow de CI se comprobará por separado después de publicar.
