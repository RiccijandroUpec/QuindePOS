# Cómo instalar la facturación electrónica SRI en EcoPos

Esta guía es para quien va a instalar la facturación electrónica en la
computadora de un negocio. No hace falta saber programar. Si quieres
entender cómo está construido por dentro, mira `README.md`.

La facturación funciona **dentro del mismo EcoPos**: no hay un segundo
programa ni un servicio de Windows que mantener. Cuando EcoPos está abierto,
factura. Cuando lo cierras, deja de facturar, y lo que haya quedado
pendiente se reintenta solo la próxima vez que lo abras.

## 0. Antes de empezar

- **EcoPos ya instalado y funcionando** en esa computadora, con su base de
  datos MySQL encendida (por ejemplo, desde el panel de XAMPP).
- **Java 11 o más nuevo.** Para comprobarlo, abre una ventana de CMD y
  escribe:
  ```
  java -version
  ```
  Tiene que decir `11` o un número mayor. Si dice `1.8` o da error, instala
  Java 11 (por ejemplo, Eclipse Temurin 11) antes de seguir.
- **El certificado de firma electrónica (`.p12`)** del negocio y su
  contraseña (Security Data, BCE, etc.). Puedes instalar todo primero y
  agregar el certificado después.

## 1. Copiar el archivo del conector

Dentro de la carpeta de EcoPos (donde está `start.bat`), crea una carpeta
llamada **`sri-conector`** y copia ahí el archivo
**`ecopos-sri-connector.jar`**. Si no lo tienes, pídeselo a quien te
entregó EcoPos, o genéralo desde este repositorio con `mvn clean package`.

Debe quedar así:

```
EcoPos/
  start.bat
  sri-conector/
    ecopos-sri-connector.jar
```

## 2. Preparar la base de datos

Cierra EcoPos si está abierto. Abre CMD **como Administrador** (clic
derecho en CMD > "Ejecutar como administrador"), entra a la carpeta
`sri-conector` y corre:

```bat
cd C:\ruta\a\EcoPos\sri-conector
java -cp ecopos-sri-connector.jar com.openbravo.pos.sri.instalador.InstaladorEcoPos
```

Esto agrega a la base de datos de EcoPos los botones, el menú y la tabla
que la facturación necesita. **Puedes correrlo las veces que quieras**: lo
que ya está instalado lo deja como está.

Vas a ver algo así:

```
[+] Tabla ecopos_sri_comprobantes creada.
[+] Menu.Root actualizado con el hook de ecopos-sri-connector.
[+] Ticket.Buttons actualizado con el hook de ecopos-sri-connector.
...
Listo. ecopos-sri-connector esta instalado/actualizado en esta base de datos.
[=] No hay servicio de Windows viejo del conector instalado.
```

> Por defecto se conecta a `localhost:3306`, base `ecopos`, usuario `root`
> sin contraseña (lo normal en XAMPP). Si tu base de datos usa otros datos,
> crea antes `sri-conector/config/conexion.properties` con las líneas
> `host=`, `puerto=`, `baseDatos=`, `usuario=` y `clave=`. La clave se
> cifra sola la primera vez.

## 3. Configurar los datos del negocio

Abre EcoPos (`start.bat`), entra como **Administrador** y ve a
**Administración > Sistema**. Presiona el botón de **configuración del
conector SRI** y completa:

- RUC, razón social y nombre comercial (opcional)
- Dirección matriz y dirección del establecimiento
- Establecimiento y punto de emisión (3 dígitos cada uno, por ejemplo `001`)
- Ambiente: **PRUEBAS** primero. Cambia a **PRODUCCIÓN** solo cuando el
  negocio esté listo para emitir facturas reales.
- El certificado `.p12` (botón "Examinar...") y su contraseña

Presiona **Guardar**. No hace falta reiniciar EcoPos: la próxima venta ya
usa estos datos.

## 4. (Opcional) Configurar el envío por correo

En **Administración > Sistema**, abre el **Historial de facturación** y
presiona **"Configurar correo..."**. Completa los datos del servidor de
correo (SMTP) del negocio. Sin esto, las facturas se emiten igual, solo que
no se envían por correo al cliente.

## 5. Probar

1. En EcoPos, en la pantalla de venta, presiona **"SRI: SI"** (verde). Es
   un interruptor para **todas** las ventas desde ahora, no solo para esta.
2. Cierra una venta normal.
3. Abre el **Historial de facturación** (Administración > Sistema). La venta
   aparece primero como PENDIENTE o ENVIADO y, unos segundos después, como
   **AUTORIZADO** (en verde).

## Si ya tenías una versión anterior (con servicio de Windows)

Las versiones anteriores funcionaban con un servicio de Windows aparte
("EcoPos SRI Connector"). Ya no hace falta, y **no debe quedar instalado**,
porque reintentaría las mismas facturas al mismo tiempo que EcoPos.

1. Cierra EcoPos.
2. Reemplaza `sri-conector/ecopos-sri-connector.jar` por el nuevo.
3. Corre el paso 2 (el instalador) **en un CMD como Administrador**. El
   instalador detiene y borra el servicio viejo por su cuenta y limpia de
   la base de datos el mecanismo anterior. Si no lo abriste como
   Administrador, te va a mostrar dos comandos (`sc stop ...` y
   `sc delete ...`): córrelos en un CMD como Administrador.
4. Abre EcoPos.

Tus datos del negocio, el certificado, la configuración de correo y el
historial de facturas se mantienen. Puedes borrar de `sri-conector/` los
archivos que ya no se usan: `ecopos-sri-connector-service.exe`,
`ecopos-sri-connector-service.xml`, y las carpetas `pendientes/` y `logs/`.

## Problemas comunes

- **No aparecen los botones o el menú de facturación**: ¿abriste EcoPos
  después de correr el instalador (paso 2)? Los menús se cargan al iniciar
  sesión.
- **El botón de configuración dice que no encuentra el conector**: revisa
  que el archivo se llame exactamente `ecopos-sri-connector.jar` y esté
  dentro de `sri-conector/`, junto a `start.bat`. Revisa también que
  EcoPos esté corriendo con Java 11 o más nuevo (paso 0).
- **Las ventas no aparecen en el Historial**: ¿está el interruptor en
  "SRI: SI"? Revisa también que el paso 3 esté guardado.
- **La factura queda en RECHAZADO o ERROR**: lee la columna "Error" del
  Historial. Casi siempre es un dato del negocio mal escrito (paso 3) o un
  certificado vencido o equivocado. Corrígelo y presiona **"Reintentar
  envío"**. Las que quedan en ERROR por falta de internet o porque el SRI no
  responde se reintentan solas cada 15 minutos mientras EcoPos esté
  abierto.
