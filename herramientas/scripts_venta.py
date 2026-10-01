"""Scripts de la venta (recursos BeanShell) de Quinde POS.

Reescribe Ticket.Close y los scripts de descuento, notas, mesero, cocina y stock para que usen
los ayudantes de la app (com.openbravo.pos.sales.Dialogos, AvisoCambio, DescuentoManual,
ControlStock): ventanas sobre la app y con su tema, sin fuentes fijas, sin hilos sueltos y sin
abrir conexiones a la base en cada producto. Se conserva el aviso de licencia GPL del inicio.

Uso: python herramientas/scripts_venta.py
"""
import os

T = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                 'src-pos', 'com', 'openbravo', 'pos', 'templates')


def licencia(nombre):
    """Las lineas de comentario del inicio hasta la primera linea de asteriscos (el aviso GPL)."""
    lineas = open(os.path.join(T, nombre), encoding='utf-8', newline='').read().replace('\r\n', '\n').split('\n')
    salida = []
    for i, l in enumerate(lineas):
        salida.append(l)
        if i > 0 and l.startswith('// ****'):
            break
    return '\n'.join(salida)


def escribir(nombre, cuerpo):
    ruta = os.path.join(T, nombre)
    viejo = open(ruta, encoding='utf-8', newline='').read()
    nl = '\r\n' if '\r\n' in viejo else '\n'
    texto = licencia(nombre) + '\n' + cuerpo.strip('\n') + '\n'
    open(ruta, 'w', encoding='utf-8', newline='').write(texto.replace('\n', nl))


SCRIPTS = {
    'Ticket.Close.xml': '''
// Quinde POS - Ticket.Close (se ejecuta al terminar cada venta)
// Muestra el cambio a entregar cuando se pago en efectivo. Suma todos los pagos en
// efectivo (tambien en un pago dividido), no aparece si no hay cambio, se cierra solo
// y no le quita el foco a la venta siguiente (teclado y lector de codigos siguen andando).
com.openbravo.pos.sales.AvisoCambio.mostrar(ticket);
''',

    'script.totaldiscount.txt': '''
// Quinde POS - Descuento a toda la venta, en %
// Escribe el porcentaje en el teclado antes de tocar el boton, o el sistema lo pregunta.
// 0% quita el descuento. Se calcula sobre el precio de lista (no se acumula), convive con
// las promociones y no cambia el nombre de los productos (que es el que va en la factura).
import com.openbravo.pos.sales.Dialogos;
import com.openbravo.pos.sales.DescuentoManual;

if (ticket.getLinesCount() == 0) {
    Dialogos.aviso("Descuento", "Agrega productos antes de aplicar un descuento.");
    return null;
}
pct = sales.getInputValue();
if (pct <= 0) {
    pct = Dialogos.pedirPorcentaje("Descuento a toda la venta", "Porcentaje de descuento (0 para quitarlo):");
    if (pct == null) {
        return null;
    }
}
if (pct > 100) {
    Dialogos.advertencia("Descuento", "El descuento no puede ser mayor a 100%.");
    return null;
}
DescuentoManual.aplicarATodo(ticket, pct);
''',

    'script.linediscount.txt': '''
// Quinde POS - Descuento a la linea seleccionada, en %
// Selecciona la linea, escribe el porcentaje en el teclado y toca el boton (o el sistema lo
// pregunta). 0% quita el descuento. Convive con las promociones y no cambia el nombre del producto.
import com.openbravo.pos.sales.Dialogos;
import com.openbravo.pos.sales.DescuentoManual;

index = sales.getSelectedIndex();
if (index < 0) {
    Dialogos.aviso("Descuento", "Selecciona la linea a la que quieres aplicar el descuento.");
    return null;
}
pct = sales.getInputValue();
if (pct <= 0) {
    pct = Dialogos.pedirPorcentaje("Descuento a la linea", "Porcentaje de descuento para \\"" + ticket.getLine(index).getProductName() + "\\" (0 para quitarlo):");
    if (pct == null) {
        return null;
    }
}
if (pct > 100) {
    Dialogos.advertencia("Descuento", "El descuento no puede ser mayor a 100%.");
    return null;
}
if (!DescuentoManual.aplicarALinea(ticket.getLine(index), pct)) {
    Dialogos.aviso("Descuento", "Esta linea no tiene precio al que aplicar descuento.");
}
sales.setSelectedIndex(index);
''',

    'script.AddLineNote.txt': '''
// Quinde POS - Nota para la linea seleccionada (sale en la comanda de cocina)
import com.openbravo.pos.sales.Dialogos;

index = sales.getSelectedIndex();
if (index < 0) {
    Dialogos.aviso("Nota", "Selecciona la linea a la que quieres agregar una nota.");
    return null;
}
line = ticket.getLine(index);
value = Dialogos.pedirTexto("Nota", "Nota para \\"" + line.getProductName() + "\\" (por ejemplo: sin sal):", line.getProperty("notes"));
if (value != null) {
    if (value.trim().length() == 0) {
        line.getProperties().remove("notes");
    } else {
        line.setProperty("notes", value.trim());
    }
}
''',

    'script.SetPerson.txt': '''
// Quinde POS - Mesero que atiende la venta
import com.openbravo.pos.sales.Dialogos;

value = Dialogos.pedirTexto("Mesero", "Nombre del mesero:", ticket.getProperty("person"));
if (value != null) {
    ticket.setProperty("person", value.trim());
}
''',

    'script.SendOrder.txt': '''
// Quinde POS - Enviar a cocina
// Imprime la comanda (Printer.TicketKitchen) con los productos de cocina que aun no se enviaron
// y los marca como enviados, para no repetirlos en el siguiente envio.
import com.openbravo.pos.sales.Dialogos;

boolean hayNuevos = false;
for (int i = 0; i < ticket.getLinesCount(); i++) {
    line = ticket.getLine(i);
    if (line.isProductKitchen()) {
        if (line.getProperty("sendstatus") == null) {
            line.setProperty("sendstatus", "No");
        }
        if ("No".equals(line.getProperty("sendstatus"))) {
            hayNuevos = true;
        }
    }
}
if (!hayNuevos) {
    Dialogos.aviso("Cocina", "No hay nada nuevo para enviar a cocina.");
    return null;
}
sales.printTicket("Printer.TicketKitchen");
for (int i = 0; i < ticket.getLinesCount(); i++) {
    line = ticket.getLine(i);
    if (line.isProductKitchen() && "No".equals(line.getProperty("sendstatus"))) {
        line.setProperty("sendstatus", "OK");
    }
}
Dialogos.aviso("Cocina", "Pedido enviado a cocina.");
''',

    'script.Event.Total.txt': '''
// Quinde POS - Antes de cobrar: avisa si hay productos de cocina sin enviar
// (activalo en Ticket.Buttons con <event key="ticket.total" code="script.Event.Total"/>)
import com.openbravo.pos.sales.Dialogos;

for (int i = 0; i < ticket.getLinesCount(); i++) {
    line = ticket.getLine(i);
    estado = line.getProperty("sendstatus");
    if (line.isProductKitchen() && (estado == null || "No".equals(estado))) {
        Dialogos.advertencia("Pedido sin enviar", "Hay productos que todavia no se enviaron a cocina.\\nEnvialos antes de cobrar.");
        return "Cancel";
    }
}
return null;
''',

    'script.StockCurrentAdd.txt': '''
// Quinde POS - Control de stock al agregar un producto
// (activalo en Ticket.Buttons con <event key="ticket.addline" code="script.StockCurrentAdd"/>)
// Si no alcanza el stock del almacen, avisa y no agrega la linea. Los servicios no se controlan.
return com.openbravo.pos.sales.ControlStock.revisar(sales.getApp(), ticket, line, -1);
''',

    'script.StockCurrentSet.txt': '''
// Quinde POS - Control de stock al cambiar la cantidad de una linea
// (activalo en Ticket.Buttons con <event key="ticket.setline" code="script.StockCurrentSet"/>)
return com.openbravo.pos.sales.ControlStock.revisar(sales.getApp(), ticket, line, sales.getSelectedIndex());
''',
}

if __name__ == '__main__':
    for nombre, cuerpo in SCRIPTS.items():
        escribir(nombre, cuerpo)
        print('ok', nombre)
