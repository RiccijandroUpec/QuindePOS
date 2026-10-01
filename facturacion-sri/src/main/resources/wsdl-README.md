# WSDL de los web services del SRI

**Ya descargados y en su lugar** (`src/main/resources/wsdl/`):

- `RecepcionComprobantesOffline.wsdl`
- `AutorizacionComprobantesOffline.wsdl`

Descargados el 2026-07-06 directamente de
`https://celcer.sri.gob.ec/comprobantes-electronicos-ws/...?wsdl`
(ambiente de pruebas), URLs confirmadas como oficiales por la ficha
técnica del SRI v2.32 (sección 7.2.1). El `cxf-codegen-plugin` ya genera
los stubs Java a partir de ellos y `mvn clean test` compila en verde.

**Ambiente de producción** (mismo servicio, dominio distinto — usar solo
cuando el emisor esté certificado en producción):
```
https://cel.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline?wsdl
https://cel.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline?wsdl
```

## Por qué WSDL descargado y no la URL en vivo

El plugin `cxf-codegen-plugin` (configurado en el `pom.xml`) genera los
clientes Java (`com.openbravo.pos.sri.soap.recepcion` /
`...soap.autorizacion`) a partir de estos archivos **en tiempo de build**,
no en cada arranque. Guardar el WSDL localmente evita que el build dependa
de que el SRI esté disponible en ese momento, y deja registro exacto de
contra qué versión de la interfaz SOAP se generó el cliente.

Si el SRI actualiza el contrato del servicio, reemplaza el `.wsdl` aquí y
corre `mvn generate-sources` de nuevo.
