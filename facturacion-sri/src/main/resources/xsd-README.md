# XSD del comprobante (Factura v2.1.0)

**Ya en su lugar** (`src/main/resources/xsd/`):

- `factura_V2.1.0.xsd`
- `xmldsig-core-schema.xsd` (dependencia de firma XML que el anterior importa)

## De dónde salió

`www.sri.gob.ec` (el portal que sirve estos `.xsd` directamente) no responde
desde este entorno de red (timeout total, a diferencia de
`celcer.sri.gob.ec` que sí es alcanzable). Como alternativa se usó el mirror
de código abierto
[xprl-gjf/sri-efactura-core](https://github.com/xprl-gjf/sri-efactura-core)
(licencia Apache-2.0), que vendoriza los XSD oficiales del SRI para
generación JAXB. Se verificó campo por campo contra la ficha técnica oficial
v2.32 (Anexo 3, factura v1.1.0/2.1.0) compartida por el usuario — coincide
exactamente, incluyendo `agenteRetencion` y `contribuyenteRimpe` (adiciones
recientes de los Anexos 21/22).

Antes de emitir comprobantes reales, sería prudente descargar tú mismo el
`.xsd` directo de `www.sri.gob.ec/facturacion-electronica` (cuando sea
alcanzable) y diff-earlo contra este, por si hay una revisión más nueva.

## Cómo se usa

El plugin `jaxb2-maven-plugin` (configurado en el `pom.xml` raíz) genera
automáticamente las clases Java en `com.openbravo.pos.sri.xml.generado` a
partir de `factura_V2.1.0.xsd` en cada `mvn compile` — no hay que escribir
las clases de la factura a mano. Ya verificado: `Factura.java`,
`InfoTributaria.java` y 36 clases más se generan y compilan correctamente
(`mvn clean test` en verde).

## Por qué así

Generar las clases desde el XSD real garantiza que la estructura del XML que
produce este módulo sea *exactamente* la que el SRI espera, incluyendo
elementos opcionales, tipos de dato, y restricciones de longitud/formato que
el propio XSD ya valida en tiempo de compilación/serialización.

Si el SRI publica una nueva versión del esquema, basta con reemplazar el
`.xsd` aquí y volver a generar — no hay que tocar el código de mapeo de
`com.openbravo.pos.sri.xml` (esa capa mapea `TicketInfo` → los objetos JAXB
generados, y debería sobrevivir a la mayoría de cambios de versión menores).
