# Backlog DeliGo

No confundir este backlog con funcionalidades ya implementadas. La versión 0.1 desarrolla el circuito básico de pedido. La especificación original sigue teniendo trabajo pendiente.

| Estado | Entrega | Criterio de cierre |
|---|---|---|
| Implementado | Catálogo multicategoría y perfiles fijos | Cliente, comercio, repartidor y administrador usan endpoints autorizados. |
| Implementado | Pedido/stock/cotización | Transacción, bloqueo, idempotencia y rechazo de total desactualizado. |
| Implementado | Entrega manual | Un pedido activo por repartidor; dos aceptaciones concurrentes no crean dos asignaciones. |
| Implementado | Pagos manuales de demo | Yape declarado requiere verificación; efectivo requiere cobro y rendición; cancelado pagado requiere devolución. |
| Implementado | Operación mínima | CRUD de productos, apertura, calificación, tres direcciones, soporte y totales. |
| En curso | Adaptación de Figma | Acceso recuperado; fuentes y fundamentos de Cart aplicados. Falta adaptación completa de pantallas/assets. Detalle de pedido revisado a 360/390/768/1440 px. |
| Pendiente | Identidad completa | Segundo factor real por correo/autenticador, recuperación con tokens de un uso, revocación, rate limiting y tests de abuso. |
| Pendiente | Despacho por ofertas | Ubicación reciente, ranking por cercanía, oferta exclusiva 20 s, expiración, rechazo, timeout global y reasignación antes de recogida. |
| Pendiente | Automatismos | Cancelación de pendientes vencidos con reloj inyectable, liberación de stock y creación de devolución según cobro. |
| Pendiente | Dinero/contabilidad | Libro de movimientos inmutable, conciliación parcial, lotes semanales y reportes por período; prueba de balances. |
| Pendiente | Mantenimiento | CRUD de comercios, áreas y zonas con historial y permisos. Provisión segura de usuarios de operación. |
| Pendiente | Experiencia avanzada | Mapa integrado, ubicación reciente visible, notificaciones y recuperación tras desconexión. |
| Pendiente | Afiliación y soporte | Postulaciones, evidencias privadas, resoluciones detalladas, apelaciones y libro de reclamaciones diferenciado. |
| Posterior | Monetización secundaria | Membresías y patrocinio con precios definidos; orden patrocinado no altera filtros explícitos. |
| Posterior | Encargos y capacidad múltiple | Autopedidos como servicio distinto, responsabilidades, capacidad y custodia. |
| Pendiente | Entrega LP3 | Confirmar fecha, tablero Kanban, informe, capturas, exposición y despliegue cloud demostrable. |

## Decisiones que sí están cerradas

Demo académica multicategoría, frontend libre, Java/Spring Boot/MySQL. Sin pasarela real ni integración SUNAT exigidas. Yape centralizado en DeliGo y liquidación semanal manual; 15 % de delivery + lluvia, 100 % de propina al repartidor y 100 % de productos al comercio. Estas reglas sustituyen las ambigüedades del análisis inicial.

## Secuencia sugerida

Cerrar diseño/acceso y revisar la versión 0.1; identidad y automatismos; despacho/cobertura; contabilidad/reportes; documentación y despliegue final. No añadir rubros regulados con reglas ficticias: la demo usa cuidado personal sin medicamentos y no vende alcohol.
