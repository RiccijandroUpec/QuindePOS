-- Agrega soporte de Nota de Credito (anulacion de facturas) a la tabla
-- ecopos_sri_comprobantes existente. No se crea una tabla nueva: una NC es,
-- para efectos de firma/envio/consulta, un comprobante mas (mismo ciclo de
-- vida PENDIENTE->ENVIADO->AUTORIZADO/RECHAZADO/ERROR), asi que reusa la
-- misma tabla en vez de duplicar el CRUD.
--
-- Ejecutar contra la misma base de datos MySQL que usa ECOPos, despues de
-- 001_create_ecopos_sri_comprobantes.sql.

ALTER TABLE ecopos_sri_comprobantes
    ADD COLUMN tipo_comprobante VARCHAR(2) NOT NULL DEFAULT '01'
        COMMENT 'Codigo SRI: 01=factura, 04=nota de credito'
        AFTER ticket_id,
    ADD COLUMN comprobante_original_id VARCHAR(36) NULL
        COMMENT 'Para una NC: id (de esta misma tabla) de la factura que anula. NULL para facturas.'
        AFTER tipo_comprobante,
    ADD COLUMN motivo VARCHAR(300) NULL
        COMMENT 'Motivo de la nota de credito (obligatorio para tipo_comprobante=04, NULL para facturas)'
        AFTER mensaje_error,
    ADD KEY idx_ecopos_sri_tipo_comprobante (tipo_comprobante),
    ADD KEY idx_ecopos_sri_comprobante_original (comprobante_original_id);

-- El secuencial de 9 digitos que exige el SRI es independiente POR TIPO DE
-- COMPROBANTE (cada codDoc lleva su propia numeracion consecutiva, ficha
-- tecnica seccion 4) - antes de esta migracion, ComprobanteRepository.
-- siguienteSecuencial() calculaba un unico MAX+1 global; ahora filtra por
-- tipo_comprobante (ver el codigo Java).

-- Nota: una fila de Nota de Credito NO corresponde a ningun TICKETS.ID real
-- de ECOPos (se emite desde una pantalla propia del conector, no desde una
-- venta) - para no violar la restriccion NOT NULL/UNIQUE de ticket_id, esas
-- filas usan un valor sintetico "NC-<id>" (ver AnulacionService). El JOIN de
-- listarHistorial() con TICKETS simplemente no encuentra fila para esos
-- casos (LEFT JOIN), lo cual es el comportamiento esperado.
