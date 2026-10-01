package com.openbravo.pos.asistente;

import com.openbravo.pos.util.Hashcypher;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * Usuarios: clave del administrador (sin ella cualquiera entra con todos los permisos),
 * cajeros nuevos con un PIN y que usuarios se muestran en la pantalla de inicio.
 */
final class PasoUsuarios extends Paso {

    private static final Logger LOG = Logger.getLogger(PasoUsuarios.class.getName());
    private static final String ROL_ADMIN = "0";
    private static final String ROL_EMPLEADO = "2";

    private final JPasswordField clave = new JPasswordField(16);
    private final JPasswordField repetir = new JPasswordField(16);
    private final JTextField nuevoNombre = new JTextField(16);
    private final JPasswordField nuevoPin = new JPasswordField(6);
    private final JPanel listaCajeros = Ui.columna();
    private final List<String[]> cajerosNuevos = new ArrayList<>();
    private final List<JCheckBox> visibles = new ArrayList<>();
    private String idAdmin;
    private String nombreAdmin = "Administrador";
    private boolean adminTieneClave;

    PasoUsuarios(ContextoAsistente ctx) {
        super(ctx);
    }

    @Override
    String id() {
        return "usuarios";
    }

    @Override
    String nombre() {
        return "Usuarios";
    }

    @Override
    String titulo() {
        return "Qui\u00E9n usa la caja";
    }

    @Override
    String descripcion() {
        return "Protege al administrador con una clave y crea un usuario para cada cajero.";
    }

    @Override
    protected JComponent crearPanel() {
        JPanel col = Ui.columna();
        JPanel visiblesPanel = Ui.columna();
        try {
            Connection con = ctx.session.getConnection();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT ID, NAME, ROLE, APPPASSWORD, VISIBLE FROM PEOPLE ORDER BY ROLE, NAME");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    String nombre = rs.getString(2);
                    if (ROL_ADMIN.equals(rs.getString(3)) && idAdmin == null) {
                        idAdmin = id;
                        nombreAdmin = nombre;
                        String pw = rs.getString(4);
                        adminTieneClave = pw != null && !pw.isEmpty() && !pw.startsWith("empty:");
                        continue;
                    }
                    JCheckBox cb = new JCheckBox(nombre, rs.getBoolean(5));
                    cb.putClientProperty("id", id);
                    cb.setOpaque(false);
                    cb.setFont(cb.getFont().deriveFont(15f));
                    cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                    visibles.add(cb);
                    visiblesPanel.add(cb);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudieron leer los usuarios", e);
        }

        col.add(Ui.subtitulo("Clave de \u201C" + nombreAdmin + "\u201D"));
        col.add(Ui.formulario("Clave nueva", clave, "Rep\u00EDtela", repetir));
        col.add(Ui.nota(adminTieneClave
                ? "Ya tiene una clave. D\u00E9jala en blanco para mantenerla."
                : "Hoy entra sin clave. Te recomendamos ponerle una (m\u00EDnimo 4 caracteres)."));

        col.add(Ui.subtitulo("Cajeros"));
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fila.setOpaque(false);
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);
        fila.add(new JLabel("Nombre"));
        fila.add(nuevoNombre);
        fila.add(new JLabel("PIN"));
        fila.add(nuevoPin);
        JButton agregar = new JButton("Agregar cajero");
        agregar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                agregarCajero();
            }
        });
        fila.add(agregar);
        col.add(fila);
        col.add(listaCajeros);
        col.add(Ui.nota("Cada cajero entra con su nombre y su PIN. Puede vender, pero no ver reportes ni cambiar la configuraci\u00F3n."));

        if (!visibles.isEmpty()) {
            col.add(Ui.subtitulo("Mostrar en la pantalla de inicio"));
            col.add(visiblesPanel);
            col.add(Ui.nota("Desmarca los usuarios de ejemplo que no vayas a usar."));
        }
        return col;
    }

    private void agregarCajero() {
        String nombre = nuevoNombre.getText().trim();
        String pin = new String(nuevoPin.getPassword()).trim();
        Component padre = Asistente.padreDialogos(nuevoNombre);
        if (nombre.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(padre, "Escribe el nombre del cajero.");
            return;
        }
        if (!pin.matches("\\d{4,8}")) {
            javax.swing.JOptionPane.showMessageDialog(padre, "El PIN debe tener de 4 a 8 n\u00FAmeros.");
            return;
        }
        if (existeNombre(nombre)) {
            javax.swing.JOptionPane.showMessageDialog(padre, "Ya existe un usuario llamado \u201C" + nombre + "\u201D.");
            return;
        }
        cajerosNuevos.add(new String[]{nombre, pin});
        JLabel l = Ui.texto("\u2713 " + nombre + " <span style='color:gray'>(cajero, se guarda al continuar)</span>");
        listaCajeros.add(l);
        listaCajeros.revalidate();
        nuevoNombre.setText("");
        nuevoPin.setText("");
        nuevoNombre.requestFocus();
    }

    private boolean existeNombre(String nombre) {
        for (String[] c : cajerosNuevos) {
            if (c[0].equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        try (PreparedStatement ps = ctx.session.getConnection().prepareStatement("SELECT COUNT(*) FROM PEOPLE WHERE NAME = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    String guardar() {
        String c1 = new String(clave.getPassword());
        String c2 = new String(repetir.getPassword());
        if (!c1.isEmpty() || !c2.isEmpty()) {
            if (!c1.equals(c2)) {
                return "Las dos claves no coinciden.";
            }
            if (c1.length() < 4) {
                return "La clave debe tener al menos 4 caracteres.";
            }
        }
        if (!nuevoNombre.getText().trim().isEmpty()) {
            return "Toca \u201CAgregar cajero\u201D para guardar a " + nuevoNombre.getText().trim() + ", o borra el nombre.";
        }
        try {
            Connection con = ctx.session.getConnection();
            if (!c1.isEmpty() && idAdmin != null) {
                try (PreparedStatement ps = con.prepareStatement("UPDATE PEOPLE SET APPPASSWORD = ? WHERE ID = ?")) {
                    ps.setString(1, Hashcypher.hashString(c1));
                    ps.setString(2, idAdmin);
                    ps.executeUpdate();
                }
                adminTieneClave = true;
            }
            for (String[] c : cajerosNuevos) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO PEOPLE (ID, NAME, APPPASSWORD, ROLE, VISIBLE) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, c[0]);
                    ps.setString(3, Hashcypher.hashString(c[1]));
                    ps.setString(4, ROL_EMPLEADO);
                    ps.setBoolean(5, true);
                    ps.executeUpdate();
                }
            }
            cajerosNuevos.clear();
            for (JCheckBox cb : visibles) {
                try (PreparedStatement ps = con.prepareStatement("UPDATE PEOPLE SET VISIBLE = ? WHERE ID = ?")) {
                    ps.setBoolean(1, cb.isSelected());
                    ps.setString(2, (String) cb.getClientProperty("id"));
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudieron guardar los usuarios", e);
            return "No se pudieron guardar los usuarios: " + e.getMessage();
        }
        if (!adminTieneClave) {
            int r = javax.swing.JOptionPane.showConfirmDialog(Asistente.padreDialogos(clave),
                    "El administrador sigue sin clave: cualquiera podr\u00EDa entrar con todos los permisos.\n\u00BFContinuar as\u00ED?",
                    "Usuarios", javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
            if (r != javax.swing.JOptionPane.YES_OPTION) {
                return "Escribe una clave para el administrador.";
            }
        }
        return null;
    }
}
