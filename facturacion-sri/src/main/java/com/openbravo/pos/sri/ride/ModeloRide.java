package com.openbravo.pos.sri.ride;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos de un RIDE ya sacados del XML autorizado (factura o nota de credito),
 * para que {@link RideRenderer} dibuje los dos con el mismo formato.
 */
final class ModeloRide {

    /** "FACTURA" o "NOTA DE CRÉDITO". */
    String tipo;
    String ruc;
    String numero;
    String claveAcceso;
    String ambiente;
    String emision;
    LocalDateTime fechaAutorizacion;

    String razonSocial;
    String nombreComercial;
    String dirMatriz;
    String dirEstablecimiento;
    String contribuyenteEspecial;
    String obligadoContabilidad;
    String agenteRetencion;
    String contribuyenteRimpe;

    String compradorRazonSocial;
    String compradorIdentificacion;
    String fechaEmision;
    String guiaRemision;
    String compradorDireccion;

    /** Solo nota de credito: documento que modifica. */
    String docModificado;
    String fechaDocModificado;
    String motivo;

    final List<Linea> detalle = new ArrayList<>();
    final List<String[]> infoAdicional = new ArrayList<>();
    final List<Pago> pagos = new ArrayList<>();

    /** Base imponible de IVA por codigoPorcentaje (tabla 17 del SRI). */
    final Map<String, BigDecimal> baseIva = new LinkedHashMap<>();
    /** Valor de IVA por codigoPorcentaje. */
    final Map<String, BigDecimal> valorIva = new LinkedHashMap<>();
    BigDecimal ice = BigDecimal.ZERO;
    BigDecimal irbpnr = BigDecimal.ZERO;
    BigDecimal subtotalSinImpuestos;
    BigDecimal totalDescuento;
    BigDecimal propina;
    BigDecimal total;
    String etiquetaTotal = "VALOR TOTAL";

    static final class Linea {
        String codigo;
        String descripcion;
        final List<String> adicionales = new ArrayList<>();
        BigDecimal cantidad;
        BigDecimal precioUnitario;
        BigDecimal descuento;
        BigDecimal total;
    }

    static final class Pago {
        String descripcion;
        BigDecimal valor;
        String plazo;
        String tiempo;
    }

    void sumarImpuesto(String codigo, String codigoPorcentaje, BigDecimal base, BigDecimal valor) {
        BigDecimal b = base == null ? BigDecimal.ZERO : base;
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor;
        if ("2".equals(codigo)) {
            baseIva.merge(codigoPorcentaje, b, BigDecimal::add);
            valorIva.merge(codigoPorcentaje, v, BigDecimal::add);
        } else if ("3".equals(codigo)) {
            ice = ice.add(v);
        } else if ("5".equals(codigo)) {
            irbpnr = irbpnr.add(v);
        }
    }

    static String ambiente(String codigo) {
        return "2".equals(codigo) ? "PRODUCCIÓN" : "PRUEBAS";
    }

    static String emision(String codigo) {
        return "2".equals(codigo) ? "INDISPONIBILIDAD DEL SISTEMA" : "NORMAL";
    }

    /** Tabla 24 del SRI: formas de pago. */
    static String formaPago(String codigo) {
        return com.openbravo.pos.sri.dominio.FormaPago.descripcionDe(codigo);
    }

    /** Tabla 17 del SRI: porcentaje de IVA por codigo (null si no es una tarifa con porcentaje). */
    static String tarifaIva(String codigoPorcentaje) {
        if (codigoPorcentaje == null) {
            return null;
        }
        switch (codigoPorcentaje) {
            case "0": return "0%";
            case "2": return "12%";
            case "3": return "14%";
            case "4": return "15%";
            case "5": return "5%";
            case "8": return "diferenciado";
            case "10": return "13%";
            default: return null;
        }
    }
}
