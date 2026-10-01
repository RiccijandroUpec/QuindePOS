"""Mensajes de los scripts de la venta en espanol (solo las frases visibles; la logica no cambia).

Los mismos pares los usa ActualizacionesEcoPos para traducir los recursos ya guardados en la base,
sin pisar lo que el negocio haya personalizado.
"""
import os

T = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'src-pos', 'com', 'openbravo', 'pos', 'templates')

PARES = {
    'script.AddLineNote.txt': [('showInputDialog("Line notes"', 'showInputDialog("Nota para esta línea (por ejemplo: sin sal)"')],
    'script.Event.Total.txt': [('"Before closing ticket: Please Send Order to Remote Printer", "Send Check"',
                                '"Antes de cobrar, envía el pedido a cocina.", "Pedido sin enviar"')],
    'script.SendOrder.txt': [('showMessageDialog(null, "Order sent to Kitchen")', 'showMessageDialog(null, "Pedido enviado a cocina")'),
                             ('"Nothing to Send", "Warning"', '"No hay nada nuevo para enviar a cocina", "Cocina"')],
    'script.SetPerson.txt': [('showInputDialog("Enter Waiter"', 'showInputDialog("Mesero"')],
    'script.StockCurrentAdd.txt': [('"This is a Service and Stock Level is not checked", "Stock Check"',
                                    '"Es un servicio: no se controla el stock.", "Stock"'),
                                   ('"Not enough stock at this Location " + loc + " - Use Stock Diary to Add Stock to Inventory", "Stock Check"',
                                    '"No hay stock suficiente en el almacén " + loc + ". Registra la entrada en Inventario.", "Stock"')],
    'script.StockCurrentSet.txt': [('showMessageDialog(null, "This is a Service and Stock Level is not checked")',
                                    'showMessageDialog(null, "Es un servicio: no se controla el stock.")'),
                                   ('"Not enough stock at this Location " + loc + " - Please use Stock Diary to Add Stock to Inventory", "Stock"',
                                    '"No hay stock suficiente en el almacén " + loc + ". Registra la entrada en Inventario.", "Stock"')],
    'script.linediscount.txt': [('"Line Discount " + sdiscount', '"Descuento " + sdiscount')],
    'script.ServiceCharge.txt': [('"Service @  " + scval + " of " + taxline.printSubTotal()',
                                  '"Servicio " + scval + " de " + taxline.printSubTotal()')],
}

if __name__ == '__main__':
    for nombre, pares in PARES.items():
        ruta = os.path.join(T, nombre)
        s = open(ruta, encoding='utf-8', newline='').read()
        for a, b in pares:
            assert a in s, (nombre, a[:50])
            s = s.replace(a, b)
        open(ruta, 'w', encoding='utf-8', newline='').write(s)
        print('ok', nombre)
