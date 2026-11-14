# Adaptación del diseño de Figma

Referencia: https://www.figma.com/design/7cyOJmbNQqn7Nsv7cu3SAy

El nodo proporcionado 212:9000 corresponde a la página Typography. El acceso a la integración se recuperó el 10 de noviembre de 2026. Se obtuvo código y captura de Cart (164:4765).

Fundamentos aplicados: Inter para texto, Montserrat para títulos y botones; negro #040404, gris #696969, fondo #F2F2F1, tarjetas blancas de radio 10 px y total sobre fondo gris de radio 5 px. Las fuentes se sirven localmente desde assets/fonts, con sus licencias SIL OFL. Fuente de los archivos: repositorio oficial google/fonts, carpetas ofl/inter y ofl/montserrat.

## Adaptación de pantallas — 11 de noviembre

Se extrajeron contextos de alta fidelidad y capturas de comercios (136:2305), pedidos (150:4866), ingreso (122:3112), registro (178:5517) y secciones del inicio: banner (145:4288), categorías (246:4844), búsqueda (234:4849), encabezado (145:4162), productos (203:4234) y restaurantes (167:5692).

Implementación guardada:

- Inicio con búsqueda, categorías horizontales, banner con ilustración original de reparto y listado responsive de comercios.
- Catálogo en tarjetas y detalle de producto con precio, stock y acción de añadir a la bolsa.
- Carrito como página propia, con cantidad y subtotal; checkout conserva cobertura, propina y validación de precios.
- Ingreso con ilustración original de bienvenida y formularios de ingreso/registro funcionales.
- Pedidos con buscador por comercio, identificador o estado.
- Aplicación del sistema visual a los paneles de operación existentes.

Los SVG de las ilustraciones se conservan sin modificar sus trazados ni sus dimensiones intrínsecas; se componen con las posiciones del contexto de Figma. Las imágenes se sirven desde assets/figma. Ver la atribución en esa carpeta. No se usan enlaces temporales en ejecución.

El contenido multicategoría, comercios e importes siguen viniendo de la API. Las fotografías de hamburguesas son referenciales para el comercio correspondiente. Los productos y rubros sin fotografía compatible conservan un marcador de texto; no se usa una foto de otro alimento como si fuera el producto vendido. La marca DeliGo es propia. Cupones, descuentos, acceso Google/Apple y controles nativos de iOS no se muestran como funciones inexistentes.

## Límites y verificación

La consulta de Product Detail (151:6096) fue rechazada por el límite del plan Starter de Figma. Su modal se adaptó con los componentes ya extraídos del catálogo; no se afirma fidelidad exacta a ese nodo. Esto no impide añadir productos ni comprar.

Las cuatro pruebas de interfaz en src/test/frontend/flows.cjs pasan. El 11 de noviembre se revisó una captura PDF del inicio en escritorio proporcionada por el usuario: búsqueda, categorías, banner de reparto y tarjetas cargan sin solapamientos visibles. Quedan pendientes móvil, ingreso, catálogo y checkout de esta ampliación. Cinco comercios conservan iniciales por falta de fotografías compatibles. El lanzador usa los archivos estáticos del código fuente en modo demo, por lo que los ajustes visuales posteriores se ven recargando sin recompilar Java. El JAR y Docker conservan los recursos empaquetados para su distribución.

La adaptación web no es una copia de las dimensiones fijas de iPhone: usa las proporciones y componentes de la referencia, con distribución propia para escritorio.
