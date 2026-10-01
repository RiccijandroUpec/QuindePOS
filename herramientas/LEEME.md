# Herramientas

Scripts de Python (3.8+) que generan o actualizan archivos del proyecto. No forman parte de la app.

| Script | Qué hace |
|---|---|
| `ticket_quinde.py` | Genera las plantillas del ticket impreso (venta, vista previa, reimpresión y visor) |
| `plantillas_caja.py` | Genera el cierre de caja (Z), el corte parcial (X) y la comanda de cocina |
| `plantillas_varias.py` | Abono a cuenta, movimiento de inventario, mensajes del visor, etiqueta de producto y traducción de las plantillas antiguas |
| `scripts_venta.py` | Genera los scripts de la venta (aviso de cambio, descuentos, nota, mesero, cocina, stock) |
| `scripts_es.py` | Traduce los mensajes de los scripts de la venta (los mismos pares los aplica `ActualizacionesEcoPos` a las bases existentes) |
| `estilo_reportes.py` | Aplica el estilo de Quinde POS a los reportes `.jrxml` (encabezado con el negocio y el logo, paleta, gráficos) |
| `arreglos_reportes.py` | Corrige problemas de los reportes originales (expresiones, SQL, gráficos SVG, reportes sin datos) |

Se ejecutan desde la raíz del proyecto, por ejemplo: `python herramientas/plantillas_caja.py`.
Después de cambiar una plantilla, recompila con `ant -f build_working.xml jar`; las bases existentes
reciben los cambios al abrir la app (ver `ActualizacionesEcoPos`).

La imagen de la paleta y los billetes/monedas se generan desde `branding/` (`GenerarPaleta.java`, `generar_dinero.py`).

## Instalador de Windows (`instalador/`)

| Archivo | Qué hace |
|---|---|
| `instalador/quinde.iss` | Script de Inno Setup: Java incluido, base de datos MariaDB incluida o un servidor existente, accesos directos, actualización y desinstalación |
| `instalador/jars.txt` | Jars de `lib/` que se instalan (los mismos de `start.bat`) |
| `instalador/generar_imagenes.py` | Imágenes del instalador con la marca, en todas las escalas de Windows |

Se arma con `ant -f build_working.xml instalador` (ver el README principal). La base la prepara
`com.openbravo.pos.instalacion.ConfigurarBase`, que el instalador ejecuta con el Java incluido.
