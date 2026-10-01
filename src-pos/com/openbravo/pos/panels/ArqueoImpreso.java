package com.openbravo.pos.panels;

import com.openbravo.format.Formats;
import java.util.ArrayList;
import java.util.List;

/**
 * Resultado del arqueo listo para el reporte impreso del cierre de caja
 * ($arqueo en Printer.CloseCash): valores con formato de moneda y el detalle
 * de billetes y monedas, una denominacion por linea.
 */
public final class ArqueoImpreso {

    private final ArqueoCaja.Resultado r;

    ArqueoImpreso(ArqueoCaja.Resultado r) {
        this.r = r;
    }

    public String getFondo() {
        return Formats.CURRENCY.formatValue(r.fondo);
    }

    public String getContado() {
        return Formats.CURRENCY.formatValue(r.contado);
    }

    public String getEsperado() {
        return Formats.CURRENCY.formatValue(r.esperado);
    }

    /** Lo que deberia haber en el cajon: fondo + efectivo esperado. */
    public String getDeberiaHaber() {
        return Formats.CURRENCY.formatValue(r.fondo + r.esperado);
    }

    public String getDiferencia() {
        return Formats.CURRENCY.formatValue(Math.abs(r.diferencia()));
    }

    /** "CUADRA", "SOBRANTE" o "FALTANTE". */
    public String getEstado() {
        double d = r.diferencia();
        return d == 0 ? "CUADRA" : (d > 0 ? "SOBRANTE" : "FALTANTE");
    }

    public boolean isCuadra() {
        return r.diferencia() == 0;
    }

    /** "3 x $20,00" ... una denominacion por linea. */
    public List<String> getDetalle() {
        List<String> lineas = new ArrayList<>();
        if (r.detalle != null) {
            // La coma de "$20,00" no separa: solo la ", " que va antes de "N x ".
            for (String parte : r.detalle.split(",\\s+(?=\\d+ x )")) {
                if (!parte.trim().isEmpty()) {
                    lineas.add(com.openbravo.pos.util.StringUtils.encodeXML(parte.trim()));
                }
            }
        }
        return lineas;
    }
}
