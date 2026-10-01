package com.openbravo.pos.promociones;

import com.openbravo.data.loader.Session;
import com.openbravo.pos.ticket.TicketInfo;
import com.openbravo.pos.ticket.TicketLineInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Promociones automaticas de la venta: "lleva N, paga M" (2x1, 3x2...) y "%
 * de descuento", por producto o por categoria, opcionalmente solo en ciertas
 * horas y dias (happy hour). Se aplican bajando el precio unitario de la
 * linea, asi el ticket, los totales, los reportes y la factura SRI salen con
 * el precio realmente cobrado. El precio de lista queda guardado en la linea
 * (propiedad {@value #PRECIO_LISTA}) para poder recalcular cuando cambia la
 * cantidad o termina el horario de la promocion.
 *
 * Las reglas viven en la tabla ecopos_promociones (se editan en el menu
 * "Promociones") y se releen como maximo cada 60 segundos.
 */
public final class MotorPromociones {

    public static final String PRECIO_LISTA = "ecopos.precioLista";
    /** Descuento manual de la linea en % (boton Descuento), que se aplica encima de la promocion. */
    public static final String DESCUENTO_MANUAL = "ecopos.descuento";
    public static final String PROMOCION = "ecopos.promocion";

    public static final String TIPO_NXM = "NXM";
    public static final String TIPO_PORCENTAJE = "PORCENTAJE";

    private static final Logger LOG = Logger.getLogger(MotorPromociones.class.getName());
    private static final long RELEER_CADA_MS = 60000;

    /** Una regla de promocion. */
    public static final class Regla {
        public final String nombre;
        public final String tipo;
        public final String producto;
        public final String categoria;
        public final int n;
        public final int m;
        public final double porcentaje;
        public final int horaDesde;
        public final int horaHasta;
        public final String dias;

        public Regla(String nombre, String tipo, String producto, String categoria, int n, int m, double porcentaje,
                     int horaDesde, int horaHasta, String dias) {
            this.nombre = nombre;
            this.tipo = tipo;
            this.producto = producto;
            this.categoria = categoria;
            this.n = n;
            this.m = m;
            this.porcentaje = porcentaje;
            this.horaDesde = horaDesde;
            this.horaHasta = horaHasta;
            this.dias = dias;
        }

        boolean aplicaA(String idProducto, String idCategoria) {
            return (producto != null && producto.equals(idProducto))
                    || (producto == null && categoria != null && categoria.equals(idCategoria));
        }

        /** Dias: "1234567" (1 = lunes ... 7 = domingo); vacio = todos. Horas 0-24; desde == hasta = todo el dia. */
        boolean vigente(Calendar ahora) {
            int diaSemana = ((ahora.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1; // lunes = 1
            if (dias != null && !dias.trim().isEmpty() && dias.indexOf(Character.forDigit(diaSemana, 10)) < 0) {
                return false;
            }
            if (horaDesde == horaHasta) {
                return true;
            }
            int hora = ahora.get(Calendar.HOUR_OF_DAY);
            return horaDesde < horaHasta ? hora >= horaDesde && hora < horaHasta
                    : hora >= horaDesde || hora < horaHasta; // cruza la medianoche
        }
    }

    private final Session session;
    private List<Regla> reglas = new ArrayList<Regla>();
    private long leidas;

    /** Para pruebas: reglas fijas en memoria, sin base de datos. */
    MotorPromociones(List<Regla> reglasFijas) {
        this.session = null;
        this.reglas = new ArrayList<Regla>(reglasFijas);
        this.leidas = Long.MAX_VALUE / 2;
    }

    public MotorPromociones(Session session) {
        this.session = session;
    }

    /** Precio unitario efectivo de una linea con la regla (null = sin promocion). Puro, para tests. */
    public static double precioConPromocion(double precioLista, double cantidad, Regla regla) {
        if (regla == null || cantidad <= 0) {
            return precioLista;
        }
        if (TIPO_PORCENTAJE.equals(regla.tipo)) {
            double p = Math.max(0, Math.min(100, regla.porcentaje));
            return redondear4(precioLista * (1 - p / 100.0));
        }
        if (TIPO_NXM.equals(regla.tipo) && regla.n > 0 && regla.m >= 0 && regla.m < regla.n) {
            long enteras = (long) Math.floor(cantidad + 1e-9);
            if (enteras < regla.n || Math.abs(cantidad - enteras) > 1e-9) {
                return precioLista; // no llega al minimo, o cantidad fraccionaria (balanza)
            }
            long grupos = enteras / regla.n;
            long pagadas = grupos * regla.m + (enteras % regla.n);
            return redondear4(precioLista * pagadas / enteras);
        }
        return precioLista;
    }

    /**
     * Recalcula los precios de las lineas del ticket segun las promociones
     * vigentes. Devuelve los indices de las lineas cuyo precio cambio.
     */
    public List<Integer> aplicar(TicketInfo ticket) {
        List<Integer> cambiadas = new ArrayList<Integer>();
        if (ticket == null || ticket.getTicketType() != TicketInfo.RECEIPT_NORMAL) {
            return cambiadas;
        }
        List<Regla> vigentes = reglasVigentes(Calendar.getInstance());
        for (int i = 0; i < ticket.getLinesCount(); i++) {
            TicketLineInfo linea = ticket.getLine(i);
            if (linea.getProductID() == null || linea.isProductCom() || linea.getMultiply() <= 0) {
                continue;
            }
            String lista = linea.getProperty(PRECIO_LISTA);
            Regla regla = mejorRegla(vigentes, linea);
            double descuento = descuentoManual(linea);
            if (regla == null && lista == null && descuento == 0) {
                continue; // nunca tuvo promocion ni descuento: no se toca
            }
            double precioLista = lista == null ? linea.getPrice() : Double.parseDouble(lista);
            double nuevo = precioConPromocion(precioLista, linea.getMultiply(), regla);
            if (descuento > 0) {
                nuevo = redondear4(nuevo * (1 - descuento / 100.0));
            }
            if (lista == null) {
                linea.setProperty(PRECIO_LISTA, Double.toString(precioLista));
            }
            String nombrePromo = regla != null && nuevo != precioLista ? regla.nombre : null;
            boolean cambioPromo = nombrePromo == null ? linea.getProperty(PROMOCION) != null
                    : !nombrePromo.equals(linea.getProperty(PROMOCION));
            if (Math.abs(nuevo - linea.getPrice()) > 1e-9 || cambioPromo) {
                linea.setPrice(nuevo);
                if (nombrePromo == null) {
                    linea.getProperties().remove(PROMOCION);
                } else {
                    linea.setProperty(PROMOCION, nombrePromo);
                }
                cambiadas.add(i);
            }
        }
        return cambiadas;
    }

    /** Descuento manual de la linea (0 si no tiene). */
    public static double descuentoManual(TicketLineInfo linea) {
        String d = linea.getProperty(DESCUENTO_MANUAL);
        try {
            return d == null ? 0 : Math.max(0, Math.min(100, Double.parseDouble(d)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** La regla que deja el precio mas bajo para esa linea (el cliente siempre recibe la mejor promocion). */
    private static Regla mejorRegla(List<Regla> vigentes, TicketLineInfo linea) {
        Regla mejor = null;
        double mejorPrecio = Double.MAX_VALUE;
        String lista = linea.getProperty(PRECIO_LISTA);
        double precioLista = lista == null ? linea.getPrice() : Double.parseDouble(lista);
        for (Regla r : vigentes) {
            if (r.aplicaA(linea.getProductID(), linea.getProductCategoryID())) {
                double p = precioConPromocion(precioLista, linea.getMultiply(), r);
                if (p < mejorPrecio - 1e-9) {
                    mejorPrecio = p;
                    mejor = r;
                }
            }
        }
        return mejor != null && mejorPrecio < precioLista - 1e-9 ? mejor : null;
    }

    private List<Regla> reglasVigentes(Calendar ahora) {
        if (System.currentTimeMillis() - leidas > RELEER_CADA_MS) {
            reglas = leerReglas();
            leidas = System.currentTimeMillis();
        }
        List<Regla> vigentes = new ArrayList<Regla>();
        for (Regla r : reglas) {
            if (r.vigente(ahora)) {
                vigentes.add(r);
            }
        }
        return vigentes;
    }

    /** Obliga a releer las reglas en el proximo calculo (despues de editarlas). */
    public void invalidar() {
        leidas = 0;
    }

    private List<Regla> leerReglas() {
        List<Regla> lista = new ArrayList<Regla>();
        try {
            Connection con = session.getConnection();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT nombre, tipo, producto, categoria, n, m, porcentaje, hora_desde, hora_hasta, dias "
                    + "FROM ecopos_promociones WHERE activo = 1");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Regla(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getInt(5), rs.getInt(6), rs.getDouble(7), rs.getInt(8), rs.getInt(9), rs.getString(10)));
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Sin promociones (tabla ecopos_promociones no disponible)", e);
        }
        return lista;
    }

    private static double redondear4(double valor) {
        return Math.round(valor * 10000.0) / 10000.0;
    }
}
