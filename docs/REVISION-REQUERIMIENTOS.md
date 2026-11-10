# DeliGo: revisión crítica y decisiones de producto

Fecha: 10 de noviembre de 2026. Estado: revisión inicial con decisiones posteriores registradas abajo. Proyecto individual de Kevin Sanchez.

## Decisiones posteriores a la revisión

El propietario confirmó: demo académica **multicategoría**, Java/Spring Boot/MySQL obligatorios, frontend libre, sin obligación de pasarela real ni SUNAT. Delegó la elección del modelo de cobro. Se adopta Yape centralizado en DeliGo con verificación manual y liquidación semanal registrada; efectivo cobrado por el repartidor y rendido íntegramente antes de liquidar. Comisión: 15 % de delivery + lluvia, redondeada HALF_UP a dos decimales. Comercio: 100 % de productos; repartidor: 85 % del delivery + lluvia, más 100 % de propinas. No hay segunda comisión comercial ni membresías activas en esta primera implementación.

El enlace editable de referencia fue proporcionado: https://www.figma.com/design/7cyOJmbNQqn7Nsv7cu3SAy/Food-Delivery-App-Design---FREE--Community-?node-id=212-9000 . La referencia pudo verse en navegador; la integración devolvió repetidamente un error de autorización. La interfaz funcional provisional no se presenta como reproducción fiel ni usa recursos exportados de esa plantilla. La adaptación exacta queda pendiente de resolver el acceso.

El resto de este documento conserva los hallazgos y recomendaciones originales para trazabilidad. Las recomendaciones de reducir a restaurantes y las preguntas sobre frontend/pasarela quedan supersedidas por estas decisiones. La fecha de entrega continúa sin confirmar.

## Dictamen

La idea es viable como proyecto académico, pero el documento actual mezcla una primera versión con una empresa de delivery nacional. No está listo para implementarse literalmente: contiene contradicciones de pagos, cancelaciones, asignación y contabilidad. La calidad de este proyecto dependerá de completar y demostrar un circuito de pedido confiable, no del número de módulos que aparezcan en el menú.

La instrucción actual del propietario establece **web responsive, Spring Boot, Java y MySQL**. Sustituye las aplicaciones Flutter de la sección 3.1. Los documentos son material de análisis; no autorizan por sí mismos ninguna operación externa.

## Problemas que deben corregirse

| ID | Origen | Problema y consecuencia | Corrección propuesta | Prioridad |
|---|---|---|---|---|
| R01 | 3.1 | Dos aplicaciones Flutter y dos paneles multiplican trabajo, pruebas y despliegues. | Una web responsive con cuatro áreas por rol y una API REST. PWA opcional posterior. | Alta |
| R02 | 11 y 9.3 | El 15 % de «ganancia» carece de base; contabilidad además descuenta una comisión al comercio que no está definida. | Especificar base, impuestos, redondeo, beneficiario de lluvia, membresía y comisión comercial por separado. No programar liquidaciones definitivas sin resolverlo. | Crítica |
| R03 | 5, 8.2, 9.3 | No se identifica al receptor de Yape ni cuándo se considera cobrado. Un número de operación no prueba un pago. | Registrar receptor, importe, referencia y verificador. Estado pendiente hasta verificación humana real. Referencia no reutilizable; conservar evidencia privada. | Crítica |
| R04 | 8.2 | Permite cancelar en preparación y luego afirma que toda cancelación ocurre antes de preparación. | Cliente: pendiente. Comercio: pendiente/preparación con motivo. Cancelación y devolución son procesos separados; si hubo cobro se abre devolución. | Crítica |
| R05 | 7 y 8.1 | Competencia por asignación y oferta exclusiva al más cercano son algoritmos distintos. | Elegir ofertas exclusivas con vencimiento, aceptación atómica y versión. Un solo ganador; reintento de la misma solicitud no duplica asignación. | Crítica |
| R06 | 8.1 | Reintentos ilimitados dejan pedidos preparados sin salida. | Tiempo máximo de búsqueda configurable, aviso, intervención y resolución por falta de repartidor. No prometer entrega indefinida. | Alta |
| R07 | 8 y 8.1 | Buscar repartidor solo al terminar preparación aumenta espera y deterioro. | MVP conserva ese flujo explícitamente; fase posterior despacha según ETA de preparación y traslado, medido con datos. | Media |
| R08 | 8 | Un único estado mezcla cocina, logística y pago. «Aceptado» no aclara quién aceptó. | Separar estado del pedido, entrega, pago y devolución. Mostrar una línea de tiempo simplificada al cliente. | Alta |
| R09 | 8.1 y 9 | Reasignar después de recogida no transfiere físicamente los productos. | Antes de recogida: reasignación auditada. Después: incidencia y transferencia documentada de custodia, nunca cambio libre de repartidor. | Crítica |
| R10 | 9 | Edición libre de productos, dirección y estados puede alterar importes y falsificar historial. | Comandos por acción, motivos, autorización y eventos inmutables. Corregir mediante ajustes; preservar pedido y precio originales. | Crítica |
| R11 | 9.3 | «Deuda en efectivo» no distingue dinero del comercio, del repartidor y de DeliGo. | Libro de movimientos por beneficiario y rendiciones conciliadas. El saldo se deriva; no editar un contador sin respaldo. | Crítica |
| R12 | 9.3 | Suspensión por deuda podría bloquear una entrega en curso. | Bloquear nuevas ofertas, permitir finalizar las activas. Definir antigüedad desde el primer importe pendiente y zona horaria. | Alta |
| R13 | 5.3 | Si una dirección no tiene cobertura, sugerir locales cercanos no garantiza que puedan entregarle. | Solo habilitar compra a comercios que cubran el punto exacto. Cercanos sin cobertura solo como información, o proponer cambiar dirección/recojo. | Alta |
| R14 | 3 y 9.6 | Tarapoto, Morales y La Banda son tratados ambiguamente como ciudad, conurbación y zonas. | Área operativa metropolitana con zonas/distritos diferenciados; separar división administrativa de polígono comercial. | Alta |
| R15 | 9.6 | Dar de alta una ciudad no basta para operar: faltan tarifas, horarios, personal y comercios. | Activación condicionada a cobertura, tarifas, comercios y capacidad operativa. Mantener configuración multiárea desde el modelo. | Media |
| R16 | 6 | Eliminar productos rompe pedidos históricos; actualizar stock no define reservas. | Baja lógica y snapshot de nombre/precio. Reservar stock en transacción; liberar al cancelar y evitar sobreventa concurrente. | Crítica |
| R17 | 5 | Varios carritos no aclaran pago, tarifa, propina y devolución. | Un checkout y pedido por comercio. Cada pedido tiene su total, entrega, cobro y devolución independientes. | Alta |
| R18 | 9.2 | Tarifa mínima sin fórmula de distancia, radio, redondeo o límite. | Cotización con vigencia, moneda PEN, componentes explícitos y snapshot al confirmar; el servidor calcula. | Alta |
| R19 | 6 | Autopedidos omiten destinatario, artículos restringidos, valor, pagador y prueba de entrega. | Modelar encargo como servicio distinto; posponer en primera entrega si el docente no lo exige. | Media |
| R20 | 9 | Doble asignación forzada omite capacidad, rutas, alimento y tiempos prometidos. | Una entrega activa por repartidor en MVP; capacidad múltiple solo con reglas y pruebas propias. | Alta |
| R21 | 12 | «Recordar dispositivo» no define vencimiento ni revocación. | Token aleatorio revocable almacenado con hash, caducidad, rotación y revocación al cambiar contraseña. No confiar en fingerprint del navegador. | Alta |
| R22 | 9 y 12.1 | El administrador puede conocer contraseñas nuevas; la cuenta suprema no tiene recuperación definida. | Restablecimiento de un solo uso, cierre de sesiones y auditoría. Recuperación de emergencia del propietario documentada y protegida. | Alta |
| R23 | 5, 6, 7 y 9.5 | Calificación al cliente permite represalias y reportes duplicados. | Una calificación por actor y pedido entregado; incidentes separados de reputación, revisión humana y apelación. | Media |
| R24 | 5.1 | «Rapidez» y «mejor calificados» carecen de fórmula y tratamiento de locales sin reseñas. | Definir tiempo total estimado, mínimo de reseñas/desempate y ubicación de nuevos comercios; patrocinio nunca anula orden explícito. | Media |
| R25 | 6 | Apertura manual no resuelve horarios, feriados, cierres durante checkout y saturación. | Horarios + pausa manual; revalidación al confirmar. Los pedidos activos siguen accesibles al cerrar. | Alta |
| R26 | 8 y 14 | GPS «en tiempo real» y push no son equivalentes en web móvil y segundo plano. | Definir actualización en primer plano, edad de ubicación y fallback; verificar permisos y soporte del navegador. No mostrar coordenadas simuladas como reales. | Alta |
| R27 | 14 | «Sin degradar rendimiento» y «24 horas» no son criterios comprobables. | Definir carga, entorno, percentiles, disponibilidad objetivo, backups y prueba de restauración. Son objetivos a medir, no garantías declarativas. | Alta |
| R28 | 15 | Se atribuye todo comprobante al comercio, aunque DeliGo también cobraría servicios propios. | Separar comprobantes por productos de los de servicios/membresías de plataforma; validar tratamiento con contador antes de operar. | Alta |
| R29 | 15 | Cifrado y 2FA no equivalen a cumplimiento legal; el oficial de datos no debe presentarse como obligación universal inmediata sin análisis de aplicabilidad. | Inventario de datos, finalidad, retención, controles, derechos y obligaciones aplicables con revisión profesional antes de usuarios reales. | Alta |
| R30 | 1 y 5 | Farmacia y licores se tratan como comida, aunque tienen reglas de producto, edad o documentación distintas. | Primera demo de restaurantes; extensibilidad de categorías sin declarar operación real de rubros regulados. | Alta |
| R31 | 9.4 | Documentos personales subidos pueden quedar públicos o ser archivos maliciosos. | Almacenamiento privado, límite/tipo real, antivirus si procede, acceso temporal, retención y nunca incorporarlos al repositorio. | Alta |
| R32 | 13 | Ventas, ingresos, cobros y ganancia aparecen mezclados. | Diccionario de métricas, fecha relevante, zona America/Lima, devolución neta y estados incluidos. | Alta |

## El negocio aún necesita demostrar su viabilidad

Tres clientes distintos deben recibir valor: el consumidor quiere entregas confiables, el comercio ventas incrementales y el repartidor una ganancia razonable por hora. Una membresía desde el primer día puede dificultar captar comercios si aún no hay demanda. Probar primero si hay disposición a pagar y capacidad de reparto en una zona pequeña.

**Ejemplo ilustrativo, no tarifa aprobada:** productos S/ 30, delivery S/ 5, lluvia S/ 1, propina S/ 2. Total S/ 38. Si el 15 % aplica a delivery + lluvia, DeliGo recibe S/ 0.90, repartidor S/ 5.10 + S/ 2, comercio S/ 30. Esos S/ 0.90 son ingreso bruto de plataforma, no utilidad. Si lluvia no está en la base, el reparto cambia: debe decidirse explícitamente. No se descuenta una segunda comisión al comercio sin contrato/regla definida.

Margen de contribución por pedido = comisión + otros cargos aprobados - costo variable de cobro - subsidios - soporte variable - costo esperado de incidentes. Punto de equilibrio = costos fijos / margen positivo por pedido, considerando aparte membresías. No inventar volumen, precio ni rentabilidad sin datos.

Validar con entrevistas a comercios, repartidores y clientes: pedidos esperados, tiempo de preparación, costo por kilómetro/hora, zonas, medio de pago, rechazo por precio, y responsables de incidencias. Definir qué diferencia a DeliGo de pedir por WhatsApp o teléfono.

## Alcance propuesto y trazabilidad al curso

La propuesta siguiente es un recorte recomendado, no un cambio ya aprobado de todos los requisitos.

| Entrega | Resultado comprobable | Evidencia del sílabo |
|---|---|---|
| 1. Base | Análisis corregido, modelo ER, decisiones de arquitectura, API inicial y pruebas | Unidad 1: MVC, REST, JSON, análisis individual, seguridad |
| 2. Circuito completo | Cliente compra a un comercio; comercio acepta/prepara; repartidor entrega; cliente consulta historial | Unidad 2: CRUD de mantenimiento, seguridad y procesos |
| 3. Operación | Cancelación, stock concurrente, verificación de pago, rendición, soporte, reportes mínimos | Unidad 2 y vistas de Unidad 3 |
| 4. Portafolio | Responsive validado, integración externa real o sandbox identificada, nube, Kanban, documentación y demo | Unidad 3: servicios externos, integración, nube y sustentación |

El sílabo termina el 11/12/2026, pero esa fecha **no demuestra la fecha exacta de tu sustentación**. Debes confirmar el plazo y si exige pasarela de pago o frontend particular. Yape manual es un proceso operativo, no una integración API de pagos. Un mapa o correo transaccional podría servir como integración externa, pero no sustituye automáticamente una exigencia explícita de pagos/redes sociales del docente.

Para el circuito inicial: restaurantes, una zona piloto, un pedido por comercio, una entrega activa por repartidor, cuatro roles fijos, precios del servidor, auditoría, efectivo y un flujo Yape por definir. Posponer publicidad pagada, autopedidos, rutas múltiples, membresías automatizadas, roles dinámicos y expansión nacional. Mantenerlos trazados en backlog.

## Arquitectura propuesta

Monolito modular Spring Boot: identity, catalog, coverage, orders, dispatch, payments, settlements, support y reporting. Controladores REST -> servicios de aplicación transaccionales -> dominio -> repositorios JPA/MySQL. DTO separados de entidades, migraciones versionadas y validación. No requiere microservicios para demostrar una aplicación distribuida: frontend y API desplegados ya consumen servicios por red.

Web del mismo origen para simplificar sesiones seguras, CSRF y despliegue. Decidir frontend tras confirmar restricciones del curso y leer Figma. No agregar React, JWT ni microservicios solo por apariencia. Si se exige token, documentar almacenamiento, vencimiento y revocación. Roles y propiedad deben verificarse en el servidor: un comercio no puede consultar pedidos de otro cambiando un ID.

Patrones defendibles: MVC para transporte/presentación, Repository para persistencia, Strategy para tarifas/asignación cuando haya variantes, Adapter para proveedor externo, políticas de transición explícitas para estados. Añadir patrones solo donde solucionen una variación real.

## Modelo conceptual que falta formalizar

User y Role; Merchant y MerchantMember; Product; OperatingArea y CoverageZone; CustomerAddress; Order y OrderItemSnapshot; Delivery y AssignmentOffer; Payment y Refund; LedgerEntry y Settlement; SupportCase; Review; AuditEvent.

Invariantes: un pedido pertenece a un cliente y comercio; total calculado en servidor; importes DECIMAL/BigDecimal y PEN; snapshots históricos; aceptación y stock atómicos; idempotencia de checkout y cobro; actor/propiedad autorizados; entregado/cancelado no se reabren por actualización genérica. Fechas de servidor en UTC, presentación/reportes en America/Lima.

## Criterios de aceptación imprescindibles

1. Dos compras simultáneas de la última unidad: exactamente una reserva exitosa.
2. Doble clic/reintento del checkout con la misma clave: mismo pedido, ningún cobro duplicado.
3. Cliente cambia comercio/precio/rol en la solicitud: servidor rechaza o recalcula según contrato, sin confiar en el navegador.
4. Comercio intenta leer un pedido ajeno: acceso denegado sin filtrar datos.
5. Dos repartidores aceptan: uno solo adquiere la asignación, el otro recibe conflicto.
6. Oferta vencida no puede aceptarse. Un repartidor suspendido no recibe nueva asignación.
7. Cancelación y aceptación concurrentes: una única transición válida; pago y stock quedan consistentes.
8. Yape declarado no se marca pagado automáticamente. Cancelación cobrada conserva deuda de devolución hasta resolución.
9. Cambio de precio/tarifa no altera pedidos existentes. Propina no se mezcla con comisión.
10. Dirección fuera de cobertura bloquea confirmar compra; no basta con mostrar un local cercano.
11. Navegación usable con teclado y a 360, 390, 768 y 1440 px; estados vacíos, carga, error, desconexión y validación visibles.
12. Base MySQL real probada en integración, despliegue limpio y restauración de backup comprobada antes de operación real.

## Figma y publicación

Referencia solicitada: https://www.figma.com/community/file/1364162990537420776/food-delivery-app-design-free

Pendiente obtener enlace editable con node-id. Hasta inspeccionarlo no se atribuye ninguna fuente, color, layout ni licencia a la plantilla. Registrar autor/licencia y procedencia de recursos antes de redistribuirlos. Adaptar patrones móviles a web: navegación, ancho de contenido, carrito lateral en escritorio y flujo accesible en móvil. No estirar capturas de teléfono para escritorio.

Repositorio público bajo KEV1Npkrl, sin añadir colaboradores ni coautores. Solo commits con identidad verificada del propietario. Esto no impide forks ni propuestas externas; mantener un solo contributor depende también de no integrar commits de otros autores. No publicar PDFs originales con información académica/contactos, credenciales, DNI, evidencias de pago ni bases reales.

## Preguntas abiertas

- Fecha real, rúbrica y restricciones de frontend/pagos.
- Demo académica o servicio real; restaurantes o multicategoría desde el inicio.
- Base del 15 %, destino de lluvia, comisión comercial adicional y membresía.
- Receptor de Yape, momento del pago, verificador y responsable de devolver.
- Quién adelanta el dinero al comercio en efectivo y cuándo se rinde.
- Costo por zona/distancia, límite de entrega, ausencia del cliente y devoluciones de productos.
- Criterio de éxito: demostrar LP3 y portafolio o validar operación comercial.

## Fuentes

Documentos locales revisados: análisis DeliGo (14 páginas) y sílabo LP3 (6 páginas), proporcionados por el propietario. Referencias legales verificadas como contexto, no certificación de cumplimiento:

- Spring Boot: https://docs.spring.io/spring-boot/system-requirements.html
- Reglamento de protección de datos: https://www.gob.pe/institucion/smv/normas-legales/6426760-016-2024-jus
- Texto oficial: https://www3.congreso.gob.pe/Docs/DGP/DIDP/files/ds_016-2024-jus.pdf
- Comprobantes y prestación de servicios: https://www.sunat.gob.pe/legislacion/comprob/regla/capituloI.pdf
