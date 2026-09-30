# 🌱 EcoPos

![Java](https://img.shields.io/badge/Java-11-ED8B00?logo=openjdk&logoColor=white)
![Swing](https://img.shields.io/badge/UI-Java%20Swing-red)
![MySQL](https://img.shields.io/badge/MySQL%2FMariaDB-4479A1?logo=mysql&logoColor=white)
![JasperReports](https://img.shields.io/badge/Reports-JasperReports-orange)
![Ant](https://img.shields.io/badge/Build-Apache%20Ant-A81C7D?logo=apacheant&logoColor=white)
![License](https://img.shields.io/badge/License-GPLv3-blue.svg)

EcoPos es un sistema de Punto de Venta (POS) de escritorio para negocios de retail y hostelería, construido en Java Swing.

**Hecho en Ecuador, para negocios ecuatorianos** — con soporte de
facturación electrónica SRI vía el módulo complementario
[ecopos-sri-connector](https://github.com/RiccijandroUpec/EcoPos_SRI_conector).

Licenciado bajo [GNU GPL v3](https://www.gnu.org/licenses/gpl-3.0.html).

## ✨ Características

- 🖱️ Pantalla de venta táctil y moderna: buscador de productos (F2), categorías en pestañas, tarjetas de producto con foto o iniciales, total destacado y cobro con F12
- 🎨 Tema moderno "EcoPos Claro/Oscuro" (FlatLaf) con iconos vectoriales en toda la aplicación
- 🧾 Facturación electrónica SRI integrada (factura y nota de crédito) con indicador de estado en la barra superior
- 🇪🇨 Cobro pensado para Ecuador: "Consumidor final / Factura con datos" con validación de cédula y RUC, billetes y monedas en dólares, Transferencia y DeUna
- 👥 Roles multiusuario (Administrador, Gerente, Empleado, Invitado) con permisos
- 🧾 Impresión de tickets/recibos con plantillas personalizables (JasperReports)
- 📦 Gestión de inventario, clientes, proveedores e impuestos
- 🗄️ Compatible con MySQL/MariaDB, PostgreSQL, Oracle y bases de datos embebidas Derby/HSQLDB
- 📠 Integración con lector de código de barras, cajón de dinero e impresora de tickets (JavaPOS)

## ⚙️ Requisitos

| Herramienta | Uso | Enlace |
|---|---|---|
| ![Java](https://img.shields.io/badge/-Java%2011-ED8B00?logo=openjdk&logoColor=white) | Ejecutar y compilar la app | [Adoptium Temurin 11](https://adoptium.net/temurin/releases/?version=11) |
| ![MariaDB](https://img.shields.io/badge/-MariaDB%2FMySQL-4479A1?logo=mysql&logoColor=white) | Base de datos (recomendada) | [XAMPP](https://www.apachefriends.org/) · [MariaDB](https://mariadb.org/) |
| ![Ant](https://img.shields.io/badge/-Apache%20Ant-A81C7D?logo=apacheant&logoColor=white) | Build original del proyecto | [ant.apache.org](https://ant.apache.org/) |
| ![JasperReports](https://img.shields.io/badge/-JasperReports-F28E1C) | Motor de tickets/reportes | [community.jaspersoft.com](https://community.jaspersoft.com/) |

Compatible con Windows, Linux o macOS.

## 📁 Estructura del proyecto

| Ruta | Contenido |
|---|---|
| `src-pos/` | Código fuente principal de la aplicación (`com.openbravo.pos.*`) |
| `src-beans/` | Componentes Swing reutilizables |
| `src-data/` | Capa de acceso a datos (`com.openbravo.data.*`) |
| `lib/` | Dependencias de terceros (`.jar`) incluidas en el repo |
| `locales/` | Traducciones de la interfaz (~90 idiomas) |
| `reports/` | Plantillas JasperReports para tickets y reportes |
| `build_working.xml` | Script de build Ant autocontenido (compila y empaqueta el jar) |

> 💡 Los paquetes Java internos usan el namespace `com.openbravo.*`.

## 🔨 Compilación

El `build.xml` original (NetBeans + Ant) depende de metadatos `nbproject/` que no están en este repositorio. En su lugar, usa **`build_working.xml`**, un build Ant autocontenido que sí compila y empaqueta el proyecto de punta a punta:

```sh
# Instala Apache Ant si no lo tienes (https://ant.apache.org/bindownload.cgi)
ant -f build_working.xml jar
```

Esto genera `build/jar/ecopos.jar`. Si no tienes Ant a mano, el equivalente manual con solo el JDK es:

```sh
# Desde la raíz del proyecto
mkdir -p build/classes

# Compilar los tres módulos fuente juntos (se referencian entre sí)
find src-beans src-data src-pos -name "*.java" > sources.txt
javac -encoding UTF-8 -d build/classes -cp "lib/*" \
  -sourcepath "src-beans;src-data;src-pos" @sources.txt

# Copiar recursos no-Java (íconos, .properties, etc.) al directorio de clases
for d in src-beans src-data src-pos; do
  (cd "$d" && find . -type f ! -name "*.java" ! -name "*.form" -print0 \
    | tar --null -T - -cf -) | (cd build/classes && tar -xf -)
done

# Empaquetar el jar ejecutable
mkdir -p build/jar
printf 'Main-Class: com.openbravo.pos.forms.StartPOS\n' > manifest.txt
jar cfm build/jar/ecopos.jar manifest.txt -C build/classes .
```

> 💡 `src-beans`, `src-data` y `src-pos` se referencian entre sí (por ejemplo, componentes en `src-beans` usan `com.openbravo.pos.forms.AppConfig`), así que cualquier compilación necesita ver los tres directorios en el `sourcepath`, y cualquiera de los tres módulos puede terminar necesitando `lib/*.jar` (p. ej. `RXTXcomm.jar` para el soporte de puerto serie) — por eso las tres reglas de compilación en `build_working.xml` comparten el mismo classpath.

## ▶️ Ejecución

Ejecuta el jar con las librerías necesarias en el classpath (ver `start.bat` / `start.sh` para la lista completa — JasperReports, POI, iText, Substance L&F, el driver JDBC de tu base de datos, etc.):

```sh
java -cp "build/jar/ecopos.jar;lib/jasperreports-4.5.1.jar;lib/jcommon-1.0.15.jar;lib/jfreechart-1.0.12.jar;lib/swing-layout-1.0.4.jar;lib/AbsoluteLayout.jar;lib/trident.jar;lib/substance.jar;lib/substance-swingx.jar;lib/substance-extras.jar;lib/swingx-all-1.6.4.jar;lib/flatlaf-3.5.4.jar;lib/flatlaf-extras-3.5.4.jar;lib/flatlaf-swingx-3.5.4.jar;lib/jsvg-1.4.0.jar;lib/mysql-connector-java-5.1.49.jar;locales/;reports/" \
  -Ddirname.path="./" com.openbravo.pos.forms.StartPOS
```

O más simple, usa el script ya armado con el classpath completo (todos los idiomas incluidos):

```sh
./start.sh        # Linux/macOS
start.bat         # Windows
```

Al primer arranque, EcoPos escribe su configuración en `~/ecopos.properties`. Por defecto apunta a una base de datos Derby embebida; edita ese archivo (o usa la pantalla **Configuración → Base de datos** dentro de la app) para apuntar a MySQL/MariaDB, PostgreSQL, etc. Si apunta a un esquema vacío, EcoPos crea automáticamente todas las tablas y datos iniciales (roles, categoría/producto/impuestos por defecto) en el siguiente arranque.

> 💡 `ResourceBundle` solo busca archivos de traducción en la raíz del classpath, no en subcarpetas — por eso `start.bat`/`start.sh` agregan explícitamente `locales/<Idioma>/locales/` y `locales/<Idioma>/reports/` de los 15 idiomas incluidos. Si armas tu propio classpath a mano (como el comando de arriba), sin esas rutas la app cae siempre a inglés sin importar `user.language`.

## ✅ Tests

Tests JUnit para las clases de lógica pura (sin GUI ni base de datos): `AltEncrypter` (cifrado ida y vuelta), `LuhnAlgorithm` (validación de tarjetas), `StringUtils` y `ValidadorIdentificacion` (cédula, RUC de persona natural, sociedad y entidad pública, consumidor final y pasaporte).

```sh
ant -f build_working.xml test
```

## 🗄️ Datos por defecto

Una base de datos nueva viene con:

- 👤 Cuatro roles: Administrador, Gerente, Empleado, Invitado
- 🏷️ Una categoría por defecto (`General`) y un producto interno (`***`, usado para líneas sin producto)
- 💵 Dos tarifas de IVA de Ecuador: `IVA 0%` e `IVA 15%`

Renómbralos según tu negocio — **no los elimines**, otros registros pueden depender de sus IDs.

> ⚠️ Si tu base se creó con una versión anterior de EcoPos, puede tener un impuesto "Tax Standard" al **10%**. Esa tarifa no existe en Ecuador y el SRI rechaza las facturas que la usan: cámbiala a 15% en **Administración → Impuestos**.

## 🆕 Mejoras recientes

Cada fase se documenta aquí al terminarla. Lo que falta está en **Pendientes / Hoja de ruta**, más abajo.

### Fase I — Facturación electrónica integrada (2026-09-30)
Antes la facturación se abría en ventanas aparte ("EcoPos SRI Connector - …") y se sentía como otro programa. Ahora es parte de EcoPos, como en otros POS:
- **Sistema → Facturación electrónica**: una sola pantalla con el interruptor "Emitir factura electrónica en cada venta", datos del negocio, punto de emisión, ambiente (con aviso claro de Pruebas/Producción), **firma electrónica con su titular y fecha de vencimiento** (avisa si vence en menos de 30 días), correo de envío y una lista de verificación con **"Probar conexión con el SRI"**.
- **Ventas → Comprobantes electrónicos**: resumen del periodo (autorizados, en proceso, por revisar, total), filtros por periodo, estado y tipo, búsqueda por número, cliente, cédula o ticket, estados en color y un panel de detalle que **explica los errores del SRI en palabras simples**, con las acciones Ver RIDE, Enviar por correo, Reintentar, Nota de crédito y Ver XML.
- **Aviso después de cobrar**: abajo a la derecha, "Enviando la factura al SRI…" y luego "✓ Factura 001-001-000000123 autorizada" (o el motivo si hay que revisarla). Al tocarlo abre Comprobantes electrónicos.
- **Editar ventas** muestra la factura de la venta abierta con "Ver factura" y "Nota de crédito" (esta última pide autorización de supervisor a quien no la tenga). Además, sus botones ya no dicen todos "Imprimir": ahora son Buscar, Editar, Devolver y Reimprimir.
- El indicador de la barra superior dice "Facturación al día / enviando / N por revisar / apagada" y al tocarlo abre Comprobantes electrónicos. El Panel del negocio suma la tarjeta "Facturas electrónicas hoy".
- Se quitaron los botones "SRI: SI / SRI: NO" de la pantalla de venta. Las bases existentes se actualizan solas al abrir EcoPos.

### Fase H — Promociones automáticas (2026-09-30)
- Nuevo menú **Promociones** (Administrador y Gerente) para crear reglas sin programar: **"Lleva N paga M"** (2x1, 3x2...) y **"% de descuento"**, por producto o por categoría, con horario y días opcionales (**happy hour**, por ejemplo 20% de 17 a 19 h de lunes a viernes).
- La venta las aplica sola al agregar productos o cambiar cantidades, bajando el precio de la línea: el ticket, los totales, los reportes y la factura SRI salen con el precio realmente cobrado. Si varias reglas aplican, el cliente recibe la mejor.

### Fase G — Nota de crédito parcial (2026-09-30)
- Desde el Historial de facturación, "Anular factura" ahora permite **devolver solo algunos productos o parte de las cantidades**. EcoPos recalcula base e IVA y no deja devolver dos veces lo mismo (resta las notas de crédito anteriores de esa factura).

### Fase F — Clientes, pago dividido y resumen tributario (2026-09-30)
- **Historial del cliente al atenderlo**: cuando la venta tiene cliente, debajo del número de ticket se ve cuántas compras lleva, cuánto ha gastado y cuándo fue la última; lo mismo aparece en "Factura con datos" al escribir su cédula o RUC.
- **Pago dividido visible**: el cobro muestra "+ Dividir pago" y "− Quitar pago" (antes eran solo "+" y "−").
- **Resumen tributario del mes** (Administrador y Gerente): ventas por tarifa de IVA con base imponible, IVA y número de comprobantes, devoluciones aparte, estado de los comprobantes SRI del mes y exportación a CSV para el contador. Es la base de la sección de ventas del formulario 104.
- El Panel del negocio queda **después** de Ventas en el menú, para que al iniciar sesión se siga abriendo la pantalla de venta.

### Fase E — Caja y seguridad (2026-09-30)
- **Autorización de supervisor**: si un usuario sin el permiso `sales.SinAutorizacion` (Administrador y Gerente lo tienen) quiere eliminar una línea, eliminar una venta, aplicar un descuento, hacer una devolución o abrir el cajón, se pide la clave de un supervisor. Cada autorización queda registrada en la tabla `ecopos_auditoria` (quién, quién autorizó, qué y cuándo). Solo se activa cuando al menos un supervisor tiene clave, para no bloquear instalaciones donde nadie la tiene.
- **Arqueo de caja con cierre ciego**: al cerrar caja, el cajero cuenta billetes y monedas de dólar sin ver cuánto debería haber; después se muestra lo esperado en efectivo (ventas + entradas − salidas) y el sobrante o faltante. Se guarda en `ecopos_arqueos` con el detalle por denominación. Se apaga con `caja.arqueo=false`.

### Fase D — Panel del negocio, stock bajo y copias de seguridad (2026-09-30)
- **Panel del negocio** (primera opción del menú, para Administrador y Gerente): ventas de hoy comparadas con ayer a la misma hora, tickets, ticket promedio, gráfico de ventas por hora, los 5 más vendidos, formas de pago del día y productos por agotarse.
- **Alertas de stock bajo**: productos con stock actual en o por debajo del mínimo definido por almacén (Inventario → Stock).
- **Copia de seguridad automática**: una vez al día, al abrir EcoPos y en segundo plano, se guarda un volcado comprimido de la base (MySQL/MariaDB) en `~/EcoPos-respaldos` y se conservan los últimos 14. Opciones en `ecopos.properties`: `backup.enabled`, `backup.dir`, `backup.mysqldump`, `backup.keep`. La fecha de la última copia aparece en el Panel del negocio.
- **Actualización automática de bases existentes**: al arrancar, EcoPos agrega por su cuenta las opciones de menú y permisos nuevos a instalaciones creadas con versiones anteriores (sin SQL a mano).

### Fase C — Cobro pensado para Ecuador (2026-09-30)
- **Comprobante al cobrar**: "Consumidor final" o "Factura con datos". Con datos, la cédula o el RUC se valida al instante (módulo 10 y 11 del Registro Civil y el SRI); si el cliente ya existe se autocompletan su nombre y correo, y si no, se crea al cobrar. La venta queda a su nombre, que es a quien el SRI le emite la factura. Con una identificación inválida no deja cobrar.
- **Efectivo en dólares**: billetes de $50, $20, $10, $5 y $1, y monedas de 50, 25, 10, 5 y 1 centavos (antes eran libras esterlinas), más un botón **Exacto**. El cambio se muestra en grande.
- **Nuevas formas de pago**: "Banco" pasa a llamarse **Transferencia** y se agrega **DeUna**. Las dos, y el cheque, se informan al SRI con el código 20 ("con utilización del sistema financiero"); antes se informaban como efectivo.
- Ventana de cambio en español ("Pago en efectivo · Cambio").

### Fase B — Pantalla de venta moderna (2026-09-30)
- Buscador de productos por nombre, código de barras o referencia (**F2**; Enter con un código exacto agrega el producto; Esc limpia).
- Categorías como pestañas y productos como tarjetas grandes con foto (o iniciales de color), nombre y precio con IVA.
- Tocar el mismo producto seguido suma cantidad a la línea ("x2") en vez de repetirla.
- **F12** cobra la venta.

### Fase A — Estilo moderno (2026-09-30)
- Tema **EcoPos Claro / Oscuro** (FlatLaf) por defecto; los temas anteriores siguen disponibles en Configuración → General.
- Iconos vectoriales (Tabler Icons, licencia MIT) en toda la aplicación, menú lateral plano, botones de la venta con texto y teclado numérico con tecla verde **Cobrar**.
- Inicio de sesión con tarjetas de usuario (iniciales en color) y el aviso de licencia en "Acerca de EcoPos".
- Indicador del estado de la facturación SRI en la barra superior (apagado / al día / enviando / a revisar).
- Textos que faltaban traducidos al español y datos iniciales con IVA 0% / 15%.

### Facturación SRI integrada (2026-07 / 2026-09)
- El conector SRI corre dentro de EcoPos (ya no hace falta un servicio de Windows aparte).
- Corregidos tres fallos que impedían que funcionara dentro de EcoPos: conexión a la base de datos al arrancar, librería de firma (JAXB) y cliente SOAP (conflicto de librerías viejas).

## 📋 Pendientes / Hoja de ruta

Estado: ✅ hecho · 🟡 parcial · ⬜ pendiente. Comparado con otros POS (Square, Loyverse, Odoo, Shopify, Contífico, Alegra).

**Ecuador**
- ✅ Validación de cédula y RUC al facturar
- ✅ "Consumidor final / Factura con datos" al cobrar
- 🟡 Pagos locales: Transferencia y DeUna ya se registran; falta integración directa con DeUna, Payphone y datáfonos Datafast/Medianet (requiere cuentas de comercio)
- ⬜ Enviar factura o ticket por WhatsApp
- ⬜ Registro de retenciones recibidas
- 🟡 Reportes tributarios: resumen mensual de ventas por tarifa de IVA ✅ (base del 104); ATS pendiente
- ⬜ Varias cajas o locales con su propio punto de emisión
- ⬜ Guía de remisión, nota de débito, liquidación de compra
- 🟡 Nota de crédito parcial por productos/cantidades ✅; falta una emisión real de prueba contra el SRI
- ⬜ Prueba real de punta a punta con el SRI (ambiente de pruebas) desde la pantalla de cobro nueva

**Cobro y caja**
- ✅ Pantalla de pago moderna (efectivo rápido en dólares, Exacto, cambio en grande)
- ✅ Pago dividido visible ("+ Dividir pago" en el cobro)
- ✅ Arqueo por denominación y cierre ciego
- ✅ Autorización de supervisor con clave para eliminar, descontar, devolver o abrir el cajón (con registro de auditoría)
- ⬜ Propina / 10% de servicio

**Inventario y compras**
- ⬜ Proveedores y órdenes de compra
- ✅ Alertas de stock bajo (en el Panel del negocio)
- ⬜ Kardex con costo promedio
- ⬜ Lotes y fechas de caducidad
- ⬜ Carga de productos desde Excel y edición masiva de precios

**Ventas y clientes**
- ✅ Promociones automáticas (2x1, 3x2, % por producto o categoría, happy hour); combos de productos distintos pendientes
- ⬜ Programa de puntos / fidelidad
- ✅ Historial de compras del cliente al atenderlo
- ⬜ Tarjetas de regalo con saldo

**Restaurante**
- ⬜ Pantalla de cocina (KDS)
- ⬜ Comandas desde celular o tablet
- ⬜ Pedidos para llevar y delivery

**Dueño y gestión**
- ✅ Panel del negocio (ventas de hoy, ticket promedio, más vendidos, ventas por hora)
- ⬜ Ver ventas desde el celular
- ⬜ Varias sucursales centralizadas
- ⬜ Exportar a contabilidad

**Operación**
- ✅ Estilo moderno, iconos y pantalla de venta nueva
- 🟡 Copia de seguridad automática: diaria y local ✅; copia en la nube pendiente
- 🟡 Configuración de facturación en una sola pantalla con verificación ✅; asistente de primera configuración para el resto (negocio, impresora, impuestos) pendiente
- ⬜ Ticket impreso con la marca y datos del negocio (la vista previa todavía muestra el logo y textos en inglés de la plantilla original)
- ⬜ Actualizaciones automáticas
- ⬜ Versión para tablet / Android
- ⬜ Tienda en línea integrada

## 🔗 Enlaces importantes

- 📦 Repositorio: [github.com/RiccijandroUpec/EcoPos](https://github.com/RiccijandroUpec/EcoPos)
- 📜 Licencia GPL v3: [gnu.org/licenses/gpl-3.0](https://www.gnu.org/licenses/gpl-3.0.html)
- ☕ Java 11 (Temurin): [adoptium.net](https://adoptium.net/temurin/releases/?version=11)
- 🐘 XAMPP (MariaDB/MySQL local): [apachefriends.org](https://www.apachefriends.org/)
- 🧾 JasperReports: [community.jaspersoft.com](https://community.jaspersoft.com/)

## 📜 Licencia

GNU GPL v3 — ver las cabeceras de licencia en los archivos fuente individuales.

## ☕ Apoya este proyecto / Contacto

- 📸 Instagram (también sirve como "invítame un café"): [@riccijandro](https://instagram.com/riccijandro)
- 🐙 GitHub: [@RiccijandroUpec](https://github.com/RiccijandroUpec)
- 💼 LinkedIn: [@riccijandro](https://linkedin.com/in/riccijandro)
- 🐦 X/Twitter: [@riccijandro](https://x.com/riccijandro)

Contribuciones bienvenidas — abre un *issue* o *pull request* en el repo.
