package com.openbravo.pos.sri;

/**
 * Contrato minimo entre ECOPos y ecopos-sri-connector cuando este ultimo
 * corre fusionado en el mismo proceso/JVM (en vez de como servicio de
 * Windows separado). Solo tipos JDK en las firmas a proposito: esta misma
 * interfaz se compila tambien dentro del jar sombreado del conector
 * (com.openbravo.pos.sri.EcoPosSriBridgeImpl la implementa alli), y el
 * classloader que carga ese jar (ClassLoaderConector en ECOPos) toma esta
 * interfaz siempre de ECOPos para que el cast funcione - la copia del lado
 * del conector solo existe para que ese modulo compile por su cuenta.
 * Mantener ambas copias identicas byte a byte.
 *
 * Ver EcoPosSriGlue para como se instancia una implementacion de esta
 * interfaz.
 */
public interface EcoPosSriBridge {

    /** Procesa un ticket ya cerrado de forma asincrona (no bloquea al llamador). */
    void procesarTicketAsync(String ticketId);

    /** Pantalla "Facturacion electronica" (configuracion completa) para mostrar DENTRO de ECOPos. */
    javax.swing.JComponent crearPanelFacturacion();

    /** Pantalla "Comprobantes electronicos" (lista, filtros, detalle y acciones) para mostrar DENTRO de ECOPos. */
    javax.swing.JComponent crearPanelComprobantes();

    /**
     * Estado de la factura electronica de un ticket, para mostrarlo en la
     * venta: {estado (AUTORIZADO, ENVIADO, ...), numero legible
     * (001-001-000000123), explicacion en palabras simples o null}; null si
     * el ticket no tiene factura.
     */
    String[] estadoFacturaDeTicket(String ticketId);

    /** Abre el RIDE (PDF) de la factura de un ticket. */
    void verRideDeTicket(java.awt.Component padre, String ticketId);

    /** Abre la nota de credito (total o parcial) de la factura de un ticket. */
    void notaCreditoDeTicket(java.awt.Component padre, String ticketId);

    /** Abre la pantalla de configuracion del emisor en una ventana aparte (compatibilidad con menus viejos). */
    void abrirConfiguracionEmisor();

    /** Igual que abrirConfiguracionEmisor (el correo ahora esta en la misma pantalla). */
    void abrirConfiguracionCorreo();

    /** Abre los comprobantes en una ventana aparte (compatibilidad con menus viejos). */
    void abrirHistorial();

    /** Arranca (si no estaba arrancado) el reintento periodico de comprobantes en ERROR/ENVIADO. */
    void iniciarReintentosPeriodicos();

    /** Detiene el reintento periodico. */
    void detenerReintentosPeriodicos();

    /** Libera los recursos del puente (executor, conexion dedicada). Llamar una sola vez, al cerrar ECOPos. */
    void cerrar();
}
