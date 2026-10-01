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
entregó Quinde POS, o genéralo desde la carpeta principal de Quinde POS con
`ant -f build_working.xml sri` (lo deja directamente en `sri-conector/`).

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

## 3. Configurar la facturación electrónica

Abre EcoPos (`start.bat`), entra como **Administrador** y en el menú
**Sistema** abre **Facturación electrónica**. Todo está en esa pantalla:

- **Emisión**: marca **"Emitir factura electrónica en cada venta"** cuando
  quieras empezar a facturar.
- **Datos del negocio**: RUC, razón social, nombre comercial (opcional),
  dirección matriz y del establecimiento.
- **Punto de emisión y ambiente**: establecimiento y punto de emisión (3
  dígitos cada uno, por ejemplo `001`). Ambiente **Pruebas** primero; cambia
  a **Producción** solo cuando el negocio esté listo para facturas reales.
- **Firma electrónica (.p12)**: elige el archivo y escribe la clave. EcoPos
  te muestra al instante de quién es la firma y **hasta cuándo es válida**
  (y te avisa si vence en menos de 30 días).
- **Correo** (opcional): el servidor SMTP para enviar las facturas a los
  clientes.
- **Verificación**: revisa la lista y presiona **"Probar conexión con el
  SRI"**.

Presiona **Guardar cambios**. No hace falta reiniciar EcoPos.

## 4. Probar

1. Cobra una venta normal. Si quieres la factura a nombre de alguien, elige
   **"Factura con datos"** en la pantalla de cobro y escribe su cédula o RUC
   (EcoPos la valida al instante).
2. Abajo a la derecha aparece un aviso: "Enviando la factura al SRI…" y
   luego **"Factura 001-001-000000001 autorizada"**.
3. En **Ventas → Comprobantes electrónicos** ves todas las facturas y notas
   de crédito con su estado, y desde ahí puedes ver el RIDE, reenviarlo por
   correo, reintentar o emitir una nota de crédito. También desde **Editar
   ventas**, al abrir una venta, ves su factura.

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

- **No aparece el menú de facturación**: cierra EcoPos y vuelve a abrirlo;
  los menús nuevos se agregan solos al iniciar.
- **La pantalla dice que la facturación no está instalada**: revisa que el
  archivo se llame exactamente `ecopos-sri-connector.jar` y esté dentro de
  `sri-conector/`, junto a `start.bat`, y que EcoPos corra con Java 11 o más
  nuevo (paso 0).
- **Las ventas no generan factura**: revisa que "Emitir factura electrónica
  en cada venta" esté marcado (paso 3). El indicador de la barra superior
  dice "Facturación: apagada" cuando no lo está.
- **Una factura queda "Rechazada" o "Con error"**: ábrela en Comprobantes
  electrónicos; al lado se explica el motivo en palabras simples (por
  ejemplo, una cédula mal escrita o una firma vencida). Corrígelo y presiona
  **Reintentar**. Las que fallan por falta de internet se reintentan solas
  cada 15 minutos mientras EcoPos esté abierto.
