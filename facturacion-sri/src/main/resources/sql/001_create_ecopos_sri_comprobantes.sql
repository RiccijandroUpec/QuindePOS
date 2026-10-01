-- Tabla NUEVA e independiente para ecopos-sri-connector.
-- No modifica ninguna tabla original de ECOPos/uniCenta oPOS (RECEIPTS,
-- TICKETS, TICKETLINES, etc.) - solo referencia TICKETS.ID por FK.
--
-- Ejecutar contra la misma base de datos MySQL que usa ECOPos.

CREATE TABLE IF NOT EXISTS ecopos_sri_comprobantes (
    id                  VARCHAR(36)     NOT NULL,               -- UUID propio del comprobante
    ticket_id           VARCHAR(255)    NOT NULL,               -- FK a TICKETS.ID (RECEIPTS.ID)
    secuencial          VARCHAR(9)      NULL,                   -- secuencial de 9 digitos asignado a este comprobante
    clave_acceso        VARCHAR(49)     NULL,                   -- clave de acceso de 49 digitos (una vez generada)
    numero_autorizacion VARCHAR(49)     NULL,                   -- numero de autorizacion del SRI (si autorizado)
    ambiente            ENUM('PRUEBAS','PRODUCCION') NOT NULL,
    estado              ENUM('PENDIENTE','ENVIADO','AUTORIZADO','RECHAZADO','ERROR')
                                        NOT NULL DEFAULT 'PENDIENTE',
    xml_generado        MEDIUMTEXT      NULL,                   -- XML antes de firmar
    xml_firmado         MEDIUMTEXT      NULL,                   -- XML firmado (XAdES-BES) enviado al SRI
    xml_respuesta_sri   MEDIUMTEXT      NULL,                   -- ultima respuesta cruda del SRI (recepcion o autorizacion)
    mensaje_error       TEXT            NULL,                   -- detalle del ultimo error, si lo hubo
    intentos            INT             NOT NULL DEFAULT 0,     -- cuantas veces se ha intentado enviar/consultar
    fecha_emision        DATETIME       NOT NULL,               -- fecha del ticket/venta original
    fecha_autorizacion   DATETIME       NULL,                   -- fecha en que el SRI autorizo (si aplica)
    fecha_creacion       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ecopos_sri_ticket (ticket_id),
    KEY idx_ecopos_sri_estado (estado),
    KEY idx_ecopos_sri_clave_acceso (clave_acceso),
    KEY idx_ecopos_sri_secuencial (secuencial)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Nota: no se declara FOREIGN KEY (ticket_id) REFERENCES TICKETS(ID) a
-- proposito. ECOPos no debe verse afectado si este modulo se desinstala, y
-- una FK real forzaria borrados/cascadas cruzados entre dos sistemas que
-- deben poder evolucionar por separado. La relacion se mantiene por
-- convencion (ticket_id) y se valida en la capa de aplicacion.
