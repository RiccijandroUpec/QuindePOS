package com.openbravo.pos.sri.anulacion;

import com.openbravo.pos.sri.dominio.Cliente;
import com.openbravo.pos.sri.dominio.ClaveAccesoGenerator;
import com.openbravo.pos.sri.dominio.Comprobante;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import com.openbravo.pos.sri.dominio.DetalleFactura;
import com.openbravo.pos.sri.dominio.EstadoComprobante;
import com.openbravo.pos.sri.dominio.ImpuestoDetalle;
import com.openbravo.pos.sri.dominio.TipoComprobante;
import com.openbravo.pos.sri.envio.EnvioComprobanteService;
import com.openbravo.pos.sri.firma.XadesBesSigner;
import com.openbravo.pos.sri.repository.ComprobanteRepository;
import com.openbravo.pos.sri.soap.SoapClient;
import com.openbravo.pos.sri.xml.FacturaXmlReader;
import com.openbravo.pos.sri.xml.NotaCreditoXmlMapper;
import com.openbravo.pos.sri.xml.NotaCreditoXmlWriter;
import com.openbravo.pos.sri.xml.generado.Factura;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Anula una factura ya AUTORIZADA emitiendo una Nota de Credito que la
 * referencia (el SRI, en el esquema offline que usa este conector, no tiene
 * ninguna operacion de "anular" - la unica forma de dejar sin efecto una
 * factura ya autorizada es esta, ver README para la investigacion de la
 * ficha tecnica). Cubre el caso de anulacion TOTAL: toma el detalle y el
 * valor completo de la factura original tal como el SRI la autorizo (leyendo
 * de vuelta el XML guardado, no recalculando desde ECOPos) y los repite en
 * la Nota de Credito - no soporta (todavia) acreditar solo parte de una
 * factura con lineas distintas a las originales.
 *
 * Reusa exactamente el mismo firmado/envio/consulta que las facturas
 * ({@link EnvioComprobanteService}) - el SRI no distingue el tipo de
 * comprobante en ese flujo.
 */
public final class AnulacionService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String TIPO_EMISION_NORMAL = "1";
    private static final int MOTIVO_LONGITUD_MAXIMA = 300;

    private final DatosEmisor emisor;
    private final ComprobanteRepository comprobanteRepository;
    private final EnvioComprobanteService envioComprobanteService;

    public AnulacionService(DatosEmisor emisor, javax.sql.DataSource dataSource) throws SQLException {
        this(emisor, dataSource.getConnection());
    }

    public AnulacionService(DatosEmisor emisor, java.sql.Connection connection) {
        this.emisor = emisor;
        this.comprobanteRepository = new ComprobanteRepository(connection);
        XadesBesSigner firmador = new XadesBesSigner(emisor.getRutaCertificadoP12(), emisor.getClaveCertificado());
        SoapClient soapClient = new SoapClient(emisor.getAmbiente());
        this.envioComprobanteService = new EnvioComprobanteService(firmador, soapClient, comprobanteRepository);
    }

    /**
     * Emite la Nota de Credito que anula la factura del ticket
     * {@code ticketIdFactura} (debe estar AUTORIZADA). Sincrono: firma,
     * envia a Recepcion y consulta Autorizacion antes de devolver el
     * control - igual de bloqueante que un reintento manual desde el
     * Historial, pensado para llamarse desde un boton de la UI con su
     * propio dialogo de progreso.
     *
     * @return el {@code ticket_id} sintetico ("NC-<claveAcceso>") bajo el
     *         que quedo guardada la Nota de Credito en
     *         {@code ecopos_sri_comprobantes} - con eso el Historial puede
     *         mostrarla, ver su XML y generar su RIDE igual que una factura.
     */
    /** Una linea de la factura con lo que todavia se puede devolver (para la pantalla de Nota de Credito). */
    public static final class LineaAnulable {
        public final String descripcion;
        public final BigDecimal vendido;
        public final BigDecimal disponible;
        public final BigDecimal precioUnitario;

        LineaAnulable(String descripcion, BigDecimal vendido, BigDecimal disponible, BigDecimal precioUnitario) {
            this.descripcion = descripcion;
            this.vendido = vendido;
            this.disponible = disponible;
            this.precioUnitario = precioUnitario;
        }
    }

    /**
     * Lineas de la factura del ticket con lo que queda por devolver (restando
     * las notas de credito anteriores). No firma ni contacta al SRI.
     */
    public static List<LineaAnulable> lineasAnulables(java.sql.Connection connection, String ticketIdFactura) throws Exception {
        ComprobanteRepository repositorio = new ComprobanteRepository(connection);
        ComprobanteRepository.FacturaParaAnular original = repositorio.buscarFacturaAutorizadaParaAnular(ticketIdFactura)
                .orElseThrow(() -> new NoSuchElementException("El ticket " + ticketIdFactura + " no tiene una factura AUTORIZADA"));
        List<DetalleFactura> detalles = mapDetalles(FacturaXmlReader.leer(original.xmlAutorizado));
        List<BigDecimal> disponibles = CalculoNotaCreditoParcial.disponiblePorLinea(detalles,
                CalculoNotaCreditoParcial.yaAcreditadoPorCodigo(repositorio.xmlNotasCreditoVigentesDe(original.id)));
        List<LineaAnulable> lineas = new ArrayList<>();
        for (int i = 0; i < detalles.size(); i++) {
            DetalleFactura d = detalles.get(i);
            lineas.add(new LineaAnulable(d.getDescripcion(), d.getCantidad(), disponibles.get(i), d.getPrecioUnitario()));
        }
        return lineas;
    }

    /**
     * Nota de Credito PARCIAL: devuelve solo las cantidades indicadas (una por
     * linea de la factura, en su orden; 0 = esa linea no se devuelve). No
     * permite devolver mas de lo que queda despues de notas anteriores.
     */
    public String anularParcial(String ticketIdFactura, String motivo, List<BigDecimal> cantidades) throws Exception {
        validarMotivo(motivo);
        ComprobanteRepository.FacturaParaAnular facturaOriginal = comprobanteRepository
                .buscarFacturaAutorizadaParaAnular(ticketIdFactura)
                .orElseThrow(() -> new NoSuchElementException(
                        "El ticket " + ticketIdFactura + " no tiene una factura AUTORIZADA"));
        Factura factura = FacturaXmlReader.leer(facturaOriginal.xmlAutorizado);
        rechazarConsumidorFinal(factura);

        List<DetalleFactura> originales = mapDetalles(factura);
        List<BigDecimal> disponibles = CalculoNotaCreditoParcial.disponiblePorLinea(originales,
                CalculoNotaCreditoParcial.yaAcreditadoPorCodigo(comprobanteRepository.xmlNotasCreditoVigentesDe(facturaOriginal.id)));
        for (int i = 0; i < originales.size(); i++) {
            BigDecimal pedido = i < cantidades.size() && cantidades.get(i) != null ? cantidades.get(i) : BigDecimal.ZERO;
            if (pedido.compareTo(disponibles.get(i)) > 0) {
                throw new IllegalArgumentException("De \"" + originales.get(i).getDescripcion() + "\" solo quedan "
                        + disponibles.get(i).stripTrailingZeros().toPlainString() + " por devolver");
            }
        }
        CalculoNotaCreditoParcial.Resultado calculo = CalculoNotaCreditoParcial.calcular(originales, cantidades);

        Comprobante comprobante = construirComprobanteNotaCredito(factura, facturaOriginal.id, motivo,
                calculo.detalles, calculo.totalesPorImpuesto, calculo.totalSinImpuestos, calculo.importeTotal);
        comprobanteRepository.insertar(comprobante);
        comprobante.incrementarIntentos();
        String xmlSinFirmar = NotaCreditoXmlWriter.toXml(NotaCreditoXmlMapper.map(comprobante));
        envioComprobanteService.firmarEnviarYConsultar(comprobante, xmlSinFirmar);
        return comprobante.getTicketId();
    }

    private static void validarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo de la nota de credito es obligatorio");
        }
        if (motivo.length() > MOTIVO_LONGITUD_MAXIMA) {
            throw new IllegalArgumentException("El motivo no puede superar " + MOTIVO_LONGITUD_MAXIMA + " caracteres");
        }
    }

    private static void rechazarConsumidorFinal(Factura factura) {
        if (Cliente.IDENTIFICACION_CONSUMIDOR_FINAL.equals(factura.getInfoFactura().getIdentificacionComprador())) {
            throw new IllegalStateException(
                    "El SRI no permite una Nota de Credito contra una factura emitida a CONSUMIDOR FINAL. "
                    + "Solo se puede con facturas emitidas a un comprador identificado (cedula/RUC).");
        }
    }

    public String anular(String ticketIdFactura, String motivo) throws Exception {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo de la nota de credito es obligatorio");
        }
        if (motivo.length() > MOTIVO_LONGITUD_MAXIMA) {
            throw new IllegalArgumentException("El motivo no puede superar " + MOTIVO_LONGITUD_MAXIMA + " caracteres");
        }

        ComprobanteRepository.FacturaParaAnular facturaOriginal = comprobanteRepository
                .buscarFacturaAutorizadaParaAnular(ticketIdFactura)
                .orElseThrow(() -> new NoSuchElementException(
                        "El ticket " + ticketIdFactura + " no tiene una factura AUTORIZADA para anular"));

        Factura factura = FacturaXmlReader.leer(facturaOriginal.xmlAutorizado);

        // Confirmado con una prueba real (ver README): el SRI RECHAZA
        // ("69: ERROR EN LA IDENTIFICACION DEL RECEPTOR") una Nota de Credito
        // contra una factura emitida a CONSUMIDOR FINAL - solo acepta
        // anular facturas emitidas a un comprador identificado (cedula/RUC
        // real). Cortar aqui evita gastar una llamada real al SRI para
        // terminar en un rechazo ya conocido.
        if (Cliente.IDENTIFICACION_CONSUMIDOR_FINAL.equals(factura.getInfoFactura().getIdentificacionComprador())) {
            throw new IllegalStateException(
                    "El SRI no permite anular una factura emitida a CONSUMIDOR FINAL (confirmado con una prueba real - "
                    + "ver README, hallazgo \"el SRI rechaza una Nota de Credito a CONSUMIDOR FINAL\"). "
                    + "Solo se puede anular una factura emitida a un comprador identificado (cedula/RUC).");
        }

        Comprobante comprobante = construirComprobanteNotaCredito(factura, facturaOriginal.id, motivo);

        comprobanteRepository.insertar(comprobante);
        comprobante.incrementarIntentos();

        String xmlSinFirmar = NotaCreditoXmlWriter.toXml(NotaCreditoXmlMapper.map(comprobante));
        envioComprobanteService.firmarEnviarYConsultar(comprobante, xmlSinFirmar);

        return comprobante.getTicketId();
    }

    private Comprobante construirComprobanteNotaCredito(Factura factura, String facturaOriginalId, String motivo) throws SQLException {
        return construirComprobanteNotaCredito(factura, facturaOriginalId, motivo, mapDetalles(factura),
                mapTotalesPorImpuesto(factura), factura.getInfoFactura().getTotalSinImpuestos(),
                factura.getInfoFactura().getImporteTotal());
    }

    private Comprobante construirComprobanteNotaCredito(Factura factura, String facturaOriginalId, String motivo,
                                                        List<DetalleFactura> detalles, List<ImpuestoDetalle> totalesPorImpuesto,
                                                        BigDecimal totalSinImpuestos, BigDecimal importeTotal) throws SQLException {
        var infoTributaria = factura.getInfoTributaria();
        var infoFactura = factura.getInfoFactura();

        Cliente cliente = new Cliente(
                infoFactura.getTipoIdentificacionComprador(),
                infoFactura.getIdentificacionComprador(),
                infoFactura.getRazonSocialComprador(),
                infoFactura.getDireccionComprador(), null, null);

        String secuencial = comprobanteRepository.siguienteSecuencial(TipoComprobante.NOTA_CREDITO);
        LocalDateTime fechaEmision = LocalDateTime.now();

        String claveAcceso = ClaveAccesoGenerator.generar(
                fechaEmision.toLocalDate(), TipoComprobante.NOTA_CREDITO, emisor.getRuc(),
                emisor.getAmbiente(), emisor.getEstablecimiento(), emisor.getPuntoEmision(),
                secuencial, TIPO_EMISION_NORMAL);

        // No hay TICKETS.ID real detras de una nota de credito (se emite desde
        // esta pantalla, no desde una venta) - se usa un ticket_id sintetico y
        // unico (la propia clave de acceso ya es unica) para poder reusar la
        // misma tabla/UNIQUE KEY sin inventar un esquema paralelo.
        String ticketIdSintetico = "NC-" + claveAcceso;

        Comprobante comprobante = new Comprobante(
                ticketIdSintetico, TipoComprobante.NOTA_CREDITO, emisor.getAmbiente(),
                fechaEmision, emisor, cliente, detalles, totalesPorImpuesto, List.of(),
                totalSinImpuestos, BigDecimal.ZERO, importeTotal, secuencial);

        comprobante.setClaveAcceso(claveAcceso);
        comprobante.setComprobanteOriginalId(facturaOriginalId);
        comprobante.setMotivo(motivo);
        comprobante.setCodDocModificado(infoTributaria.getCodDoc());
        comprobante.setNumDocModificado(infoTributaria.getEstab() + "-" + infoTributaria.getPtoEmi() + "-" + infoTributaria.getSecuencial());
        comprobante.setFechaEmisionDocSustento(java.time.LocalDate.parse(infoFactura.getFechaEmision(), FORMATO_FECHA).atStartOfDay());
        return comprobante;
    }

    private static List<DetalleFactura> mapDetalles(Factura factura) {
        List<DetalleFactura> detalles = new ArrayList<>();
        for (Factura.Detalles.Detalle d : factura.getDetalles().getDetalle()) {
            List<ImpuestoDetalle> impuestos = new ArrayList<>();
            for (var impuesto : d.getImpuestos().getImpuesto()) {
                impuestos.add(new ImpuestoDetalle(impuesto.getCodigo(), tarifaFraccion(impuesto.getTarifa()),
                        impuesto.getBaseImponible(), impuesto.getValor()));
            }
            detalles.add(new DetalleFactura(d.getCodigoPrincipal(), d.getDescripcion(), d.getCantidad(),
                    d.getPrecioUnitario(), d.getDescuento(), d.getPrecioTotalSinImpuesto(), impuestos));
        }
        return detalles;
    }

    private static List<ImpuestoDetalle> mapTotalesPorImpuesto(Factura factura) {
        List<ImpuestoDetalle> totales = new ArrayList<>();
        if (factura.getInfoFactura().getTotalConImpuestos() != null) {
            for (var t : factura.getInfoFactura().getTotalConImpuestos().getTotalImpuesto()) {
                totales.add(new ImpuestoDetalle(t.getCodigo(), tarifaFraccion(t.getTarifa()), t.getBaseImponible(), t.getValor()));
            }
        }
        return totales;
    }

    /** El XML guarda la tarifa como porcentaje (15.00); el modelo de dominio la maneja como fraccion (0.15). */
    private static BigDecimal tarifaFraccion(BigDecimal tarifaPorcentaje) {
        return tarifaPorcentaje == null ? BigDecimal.ZERO : tarifaPorcentaje.divide(BigDecimal.valueOf(100));
    }
}
