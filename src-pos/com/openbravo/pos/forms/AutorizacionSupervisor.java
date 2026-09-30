package com.openbravo.pos.forms;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;

/**
 * Autorizacion de supervisor para acciones sensibles de la venta (eliminar una
 * linea o una venta, descuentos, abrir el cajon). Quien tenga el permiso
 * {@value #PERMISO} (Administrador y Gerente por defecto) las hace sin que se
 * le pida nada; a los demas se les pide la clave de un supervisor, y cada
 * autorizacion queda registrada en la tabla ecopos_auditoria.
 *
 * Para no dejar a nadie bloqueado, solo se exige cuando al menos un usuario
 * con ese permiso tiene clave: en una instalacion donde nadie tiene clave,
 * todo sigue funcionando como antes.
 */
public final class AutorizacionSupervisor {

    public static final String PERMISO = "sales.SinAutorizacion";

    private static final Logger LOG = Logger.getLogger(AutorizacionSupervisor.class.getName());

    private AutorizacionSupervisor() {
    }

    /** true si la accion puede seguir (el usuario tiene el permiso o un supervisor la autorizo). */
    public static boolean autorizar(Component padre, AppView app, String accion, String detalle) {
        AppUser usuario = app.getAppUserView().getUser();
        if (usuario.hasPermission(PERMISO)) {
            return true;
        }
        List<AppUser> supervisores = supervisoresConClave(app);
        if (supervisores.isEmpty()) {
            return true; // nadie puede autorizar todavia: no se bloquea la caja
        }

        final JPasswordField clave = new JPasswordField(16);
        clave.putClientProperty("JTextField.placeholderText", "Clave del supervisor");
        JLabel mensaje = new JLabel("<html><div style='width:300px'><b>" + accion + "</b> necesita la autorizaci\u00F3n de un supervisor."
                + (detalle == null || detalle.isEmpty() ? "" : "<br><span style='color:#78909C'>" + detalle + "</span>") + "</html>");
        mensaje.setFont(mensaje.getFont().deriveFont(Font.PLAIN, 13f));
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        panel.add(mensaje, BorderLayout.NORTH);
        panel.add(clave, BorderLayout.CENTER);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                clave.requestFocusInWindow();
            }
        });

        while (true) {
            int opcion = JOptionPane.showConfirmDialog(padre, panel, "Autorizaci\u00F3n de supervisor",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (opcion != JOptionPane.OK_OPTION) {
                return false;
            }
            String texto = new String(clave.getPassword());
            for (AppUser supervisor : supervisores) {
                if (supervisor.authenticate(texto)) {
                    registrar(app, usuario.getName(), supervisor.getName(), accion, detalle);
                    return true;
                }
            }
            clave.setText("");
            JOptionPane.showMessageDialog(padre, "Clave incorrecta", "Autorizaci\u00F3n de supervisor", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static List<AppUser> supervisoresConClave(AppView app) {
        List<AppUser> resultado = new ArrayList<AppUser>();
        try {
            DataLogicSystem dlSystem = (DataLogicSystem) app.getBean("com.openbravo.pos.forms.DataLogicSystem");
            for (Object o : dlSystem.listPeopleVisible()) {
                AppUser persona = (AppUser) o;
                if (!persona.authenticate()) { // tiene clave
                    persona.fillPermissions(dlSystem);
                    if (persona.hasPermission(PERMISO)) {
                        resultado.add(persona);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo leer la lista de supervisores", e);
        }
        return resultado;
    }

    private static void registrar(AppView app, String usuario, String supervisor, String accion, String detalle) {
        try {
            Connection con = app.getSession().getConnection();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO ecopos_auditoria (id, fecha, usuario, supervisor, accion, detalle) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                ps.setString(3, usuario);
                ps.setString(4, supervisor);
                ps.setString(5, accion);
                ps.setString(6, detalle == null ? null : (detalle.length() > 500 ? detalle.substring(0, 500) : detalle));
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo registrar la autorizacion en ecopos_auditoria", e);
        }
    }
}
