package com.openbravo.pos.payment;

import com.openbravo.data.loader.Session;
import com.openbravo.pos.customers.CustomerInfoExt;
import com.openbravo.pos.customers.ValidadorIdentificacion;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Seccion "Comprobante" de la pantalla de cobro: Consumidor final o Factura
 * con datos (cedula/RUC validada al instante, nombre y correo). Si la cedula
 * ya existe en Clientes se autocompletan sus datos; si no, al confirmar el
 * cobro se crea el cliente. El conector SRI factura a nombre del cliente de la
 * venta, asi que esto es lo que decide a quien sale la factura.
 */
public class PanelComprobante extends JPanel {

    /** Que eligio el cajero al confirmar. */
    public enum Eleccion { CONSUMIDOR_FINAL, CON_DATOS }

    private static final Color VERDE = new Color(0x2E7D32);
    private static final Color ROJO = new Color(0xC62828);
    private static final Color AMBAR = new Color(0xB26A00);

    private final Session session;

    private final JToggleButton consumidorFinal = new JToggleButton("Consumidor final");
    private final JToggleButton conDatos = new JToggleButton("Factura con datos");
    private final JTextField identificacion = new JTextField(13);
    private final JTextField nombre = new JTextField(20);
    private final JTextField correo = new JTextField(18);
    private final JLabel estado = new JLabel(" ");
    private final JPanel datos = new JPanel(new GridBagLayout());

    /** Cliente existente encontrado por la cedula/RUC escrita (null = cliente nuevo). */
    private String idClienteExistente;
    private boolean autocompletando;

    public PanelComprobante(Session session) {
        this.session = session;
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        JLabel titulo = new JLabel("Comprobante:");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 14f));
        ButtonGroup grupo = new ButtonGroup();
        for (JToggleButton b : new JToggleButton[]{consumidorFinal, conDatos}) {
            b.setFocusable(false);
            b.setFont(b.getFont().deriveFont(Font.BOLD, 13f));
            b.setMargin(new Insets(6, 14, 6, 14));
            grupo.add(b);
            b.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    actualizarVisibilidad();
                }
            });
        }
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        fila.setOpaque(false);
        fila.add(titulo);
        fila.add(consumidorFinal);
        fila.add(conDatos);
        add(fila, BorderLayout.NORTH);

        identificacion.putClientProperty("JTextField.placeholderText", "C\u00E9dula o RUC");
        nombre.putClientProperty("JTextField.placeholderText", "Nombre o raz\u00F3n social");
        correo.putClientProperty("JTextField.placeholderText", "Correo (opcional)");
        identificacion.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                alCambiarIdentificacion();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                alCambiarIdentificacion();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                alCambiarIdentificacion();
            }
        });

        datos.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 0, 2, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        datos.add(identificacion, c);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        datos.add(nombre, c);
        c.weightx = 0.8;
        datos.add(correo, c);
        c.gridy = 1;
        c.gridx = 0;
        c.gridwidth = 3;
        c.weightx = 1;
        estado.setFont(estado.getFont().deriveFont(12f));
        datos.add(estado, c);
        add(datos, BorderLayout.CENTER);
    }

    /**
     * Enter en cedula pasa a nombre, en nombre pasa a correo, y en correo
     * devuelve el control al monto a cobrar ({@code alTerminar}). Tener un
     * ActionListener tambien evita que Enter dispare el boton Aceptar del
     * dialogo por accidente mientras se escriben los datos.
     */
    public void setAlTerminarDatos(final Runnable alTerminar) {
        identificacion.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nombre.requestFocusInWindow();
            }
        });
        nombre.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                correo.requestFocusInWindow();
            }
        });
        correo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                alTerminar.run();
            }
        });
    }

    /** Prepara el panel para un cobro nuevo, con el cliente que ya tenga la venta (o ninguno). */
    public void preparar(CustomerInfoExt cliente) {
        autocompletando = true;
        idClienteExistente = cliente == null ? null : cliente.getId();
        identificacion.setText(cliente == null || cliente.getTaxid() == null ? "" : cliente.getTaxid());
        nombre.setText(cliente == null ? "" : cliente.getName());
        correo.setText(cliente == null || cliente.getEmail() == null ? "" : cliente.getEmail());
        autocompletando = false;
        (cliente == null ? consumidorFinal : conDatos).setSelected(true);
        actualizarVisibilidad();
        alCambiarIdentificacion();
    }

    public Eleccion getEleccion() {
        return conDatos.isSelected() ? Eleccion.CON_DATOS : Eleccion.CONSUMIDOR_FINAL;
    }

    /** Null si todo esta bien para cobrar; si no, el motivo para mostrarle al cajero. */
    public String validarParaCobrar() {
        if (getEleccion() == Eleccion.CONSUMIDOR_FINAL) {
            return null;
        }
        ValidadorIdentificacion.Resultado r = ValidadorIdentificacion.validar(identificacion.getText());
        if (!r.isValida()) {
            identificacion.requestFocusInWindow();
            return r.getMensaje();
        }
        if (nombre.getText().trim().isEmpty()) {
            nombre.requestFocusInWindow();
            return "Escribe el nombre o la raz\u00F3n social";
        }
        String email = correo.getText().trim();
        if (!email.isEmpty() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            correo.requestFocusInWindow();
            return "El correo no parece v\u00E1lido";
        }
        return null;
    }

    /**
     * Crea o actualiza el cliente en la base de datos con lo escrito y devuelve
     * su ID (para que la venta lo cargue). Solo para "Factura con datos".
     */
    public String guardarCliente() throws SQLException {
        String taxid = identificacion.getText().trim();
        String nombreCliente = nombre.getText().trim();
        String email = correo.getText().trim();
        Connection con = session.getConnection();
        if (idClienteExistente != null) {
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE CUSTOMERS SET NAME = ?, EMAIL = ?, TAXID = ? WHERE ID = ?")) {
                ps.setString(1, nombreCliente);
                ps.setString(2, email.isEmpty() ? null : email);
                ps.setString(3, taxid);
                ps.setString(4, idClienteExistente);
                ps.executeUpdate();
            }
            return idClienteExistente;
        }
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO CUSTOMERS (ID, SEARCHKEY, TAXID, NAME, EMAIL, MAXDEBT, VISIBLE) VALUES (?, ?, ?, ?, ?, 0, ?)")) {
            ps.setString(1, id);
            ps.setString(2, claveBusquedaLibre(con, taxid));
            ps.setString(3, taxid);
            ps.setString(4, nombreCliente);
            ps.setString(5, email.isEmpty() ? null : email);
            ps.setBoolean(6, true);
            ps.executeUpdate();
        }
        idClienteExistente = id;
        return id;
    }

    private static String claveBusquedaLibre(Connection con, String taxid) throws SQLException {
        String clave = taxid;
        int sufijo = 2;
        while (true) {
            try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM CUSTOMERS WHERE SEARCHKEY = ?")) {
                ps.setString(1, clave);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return clave;
                    }
                }
            }
            clave = taxid + "-" + sufijo++;
        }
    }

    private void actualizarVisibilidad() {
        datos.setVisible(conDatos.isSelected());
        revalidate();
        repaint();
    }

    private void alCambiarIdentificacion() {
        if (autocompletando) {
            return;
        }
        String texto = identificacion.getText().trim();
        if (texto.isEmpty()) {
            mostrarEstado(" ", null);
            return;
        }
        ValidadorIdentificacion.Resultado r = ValidadorIdentificacion.validar(texto);
        if (!r.isValida()) {
            mostrarEstado("\u2716  " + r.getMensaje(), ROJO);
            return;
        }
        String mensaje = r.getMensaje();
        Color color = mensaje.contains("at\u00EDpico") ? AMBAR : VERDE;
        String[] existente = buscarClientePorIdentificacion(texto);
        if (existente != null) {
            if (!existente[0].equals(idClienteExistente)) {
                idClienteExistente = existente[0];
                autocompletando = true;
                nombre.setText(existente[1]);
                correo.setText(existente[2] == null ? "" : existente[2]);
                autocompletando = false;
            }
            mensaje += " \u00B7 cliente registrado";
        } else {
            idClienteExistente = null;
            mensaje += " \u00B7 cliente nuevo (se guardar\u00E1 al cobrar)";
        }
        mostrarEstado("\u2714  " + mensaje, color);
    }

    /** {id, nombre, email} del cliente con esa cedula/RUC, o null. */
    private String[] buscarClientePorIdentificacion(String taxid) {
        try {
            Connection con = session.getConnection();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT ID, NAME, EMAIL FROM CUSTOMERS WHERE TAXID = ? OR SEARCHKEY = ? ORDER BY VISIBLE DESC")) {
                ps.setString(1, taxid);
                ps.setString(2, taxid);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? new String[]{rs.getString(1), rs.getString(2), rs.getString(3)} : null;
                }
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private void mostrarEstado(String texto, Color color) {
        estado.setText(texto);
        estado.setForeground(color == null ? UIManager.getColor("Label.foreground") : color);
    }
}
