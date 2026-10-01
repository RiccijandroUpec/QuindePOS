# Íconos de los botones "Facturar SRI: SI/NO" de EcoPos

`img_sriinvoiceon.png` / `img_sriinvoiceoff.png` — íconos de los botones de
la pantalla de venta de EcoPos (ver `Ticket.Buttons.xml` en el repo de
EcoPos, `image="img.sriinvoiceon"` / `image="img.sriinvoiceoff"`).

Generados en 256×256 (degradado verde/gris + un solo trazo grueso blanco:
check o X) para que se vean nítidos una vez reducidos a 24×24, el tamaño
real que usa `ThumbNailBuilder` en la barra de botones — un icono con
mucho detalle (documento, líneas, texto) se vuelve ilegible/pixelado a ese
tamaño; un solo símbolo grueso y de alto contraste es lo único que sigue
leyéndose bien tan chico.

**EcoPos no carga imágenes desde el classpath** (`DataLogicSystem.getResource`
es DB-only, sin fallback) — estos PNG viven aquí solo como fuente/respaldo
para poder regenerarlos; el que realmente usa la app en tiempo de ejecución
es el `CONTENT` de las filas `img.sriinvoiceon`/`img.sriinvoiceoff` en la
tabla `RESOURCES` de EcoPos.
