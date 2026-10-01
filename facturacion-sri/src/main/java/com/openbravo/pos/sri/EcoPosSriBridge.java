package com.openbravo.pos.sri;

/**
 * Contrato minimo entre Quinde POS y el modulo facturacion-sri cuando este ultimo
 * corre fusionado en el mismo proceso/JVM (en vez de como servicio de
 * Windows separado). Solo tipos JDK en las firmas a proposito: esta misma
 * interfaz se compila dentro de Quinde POS y dentro del jar del modulo
 * (com.openbravo.pos.sri.EcoPosSriBridgeImpl la implementa alli), y el
 * classloader que carga ese jar (ClassLoaderConector en Quinde POS) toma esta
 * interfaz siempre de Quinde POS para que el cast funcione.
 *
 * Ver EcoPosSriGlue para como se instancia una implementacion de esta
 * interfaz.
 */
public interface EcoPosSriBridge {

    /**
     * Version de este contrato. Se sube en 1 cada vez que se agrega o cambia un
     * metodo. Este archivo es el UNICO: lo compilan tanto Quinde POS (build_working.xml)
     * como el modulo facturacion-sri (Maven), asi no hay copias que mantener iguales.
     */
    int VERSION_CONTRATO = 2;

    /**
     * Version del contrato con la que se compilo el modulo (devuelve su
     * VERSION_CONTRATO). Quinde POS la compara con la suya al cargar el modulo
     * y, si no coinciden, pide actualizarlo en vez de fallar a mitad de una venta.
     */
    int versionContrato();

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

    /**
     * Datos de la factura de un ticket para imprimirlos en el ticket, con las
     * claves: numero, claveAcceso (tambien es el numero de autorizacion),
     * ambiente, emision, fechaEmision, estado, compradorRazonSocial,
     * compradorIdentificacion, compradorDireccion, compradorEmail y formasPago
     * (descripciones del SRI separadas por "|"). Espera como maximo unos
     * segundos; null si no hay factura o no se pudo preparar a tiempo.
     *
     * @param reservarSiFalta true al cobrar: asigna numero y clave de acceso si
     *                        el ticket aun no tiene factura (sin enviarla todavia;
     *                        el envio sigue con procesarTicketAsync). false para
     *                        reimprimir: solo consulta, nunca crea una factura.
     */
    java.util.Map<String, String> facturaParaTicket(String ticketId, boolean reservarSiFalta);

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
