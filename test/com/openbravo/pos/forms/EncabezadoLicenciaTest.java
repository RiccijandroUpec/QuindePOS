package com.openbravo.pos.forms;

import org.junit.Test;

import static org.junit.Assert.*;

public class EncabezadoLicenciaTest {

    private static final String SCRIPT = "//\r\n"
            + "//    uniCenta oPOS - Touch Friendly Point Of Sale\r\n"
            + "//    Copyright (c) 2009-2014 uniCenta\r\n"
            + "//    http://sourceforge.net/projects/unicentaopos\r\n"
            + "//\r\n"
            + "//    This file is part of uniCenta oPOS.\r\n"
            + "//    You should have received a copy of the GNU General Public License\r\n"
            + "//    along with uniCenta oPOS.  If not, see <http://www.gnu.org/licenses/>.\r\n"
            + "// **************************************************************************\r\n"
            + "// Ticket.Close\r\n"
            + "com.openbravo.pos.sales.AvisoCambio.mostrar(ticket);\r\n";

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!-- \n"
            + "    uniCenta oPOS - Touch friendly Point Of Sale\n"
            + "    Copyright (c) 2009-2014 uniCenta.\n"
            + "    This file is part of uniCenta oPOS.\n"
            + "    along with uniCenta oPOS.  If not, see <http://www.gnu.org/licenses/>.\n"
            + " -->\n"
            + "<output><ticket/></output>\n";

    @Test
    public void scriptConservaCodigoYCopyright() {
        String r = EncabezadoLicencia.reemplazar(SCRIPT);
        assertFalse(r.contains("uniCenta oPOS"));
        assertTrue(r.startsWith("// Quinde POS - punto de venta libre para Ecuador\r\n"));
        assertTrue(r.contains("// Copyright (c) 2009-2014 uniCenta, 2026 Quinde POS\r\n"));
        assertTrue(r.contains("GNU (GPL)"));
        assertTrue(r.contains("// ****"));
        assertTrue(r.endsWith("// Ticket.Close\r\ncom.openbravo.pos.sales.AvisoCambio.mostrar(ticket);\r\n"));
        assertEquals(r, EncabezadoLicencia.reemplazar(r)); // aplicarlo de nuevo no cambia nada
    }

    @Test
    public void xmlConservaDeclaracionYContenido() {
        String r = EncabezadoLicencia.reemplazar(XML);
        assertTrue(r.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!--\n    Quinde POS"));
        assertFalse(r.contains("uniCenta oPOS"));
        assertTrue(r.contains("Copyright (c) 2009-2014 uniCenta, 2026 Quinde POS"));
        assertTrue(r.endsWith("-->\n<output><ticket/></output>\n"));
    }

    @Test
    public void sinAvisoViejoNoCambia() {
        String t = "// mi script\nsales.printTicket(\"Printer.Ticket\");\n";
        assertSame(t, EncabezadoLicencia.reemplazar(t));
    }
}
