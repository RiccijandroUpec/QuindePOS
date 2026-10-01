"""Resto de plantillas impresas y del visor de cliente en espanol, con el formato de Quinde POS."""
import os

T = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'src-pos', 'com', 'openbravo', 'pos', 'templates')
LINEA = '\t<line><text align="left" length="42">------------------------------------------</text></line>\n'


def licencia(nombre):
    s = open(os.path.join(T, nombre), encoding='utf-8', newline='').read()
    return s[:s.index('-->') + 3].replace('\r\n', '\n') if '-->' in s[:3000] else ''


def escribir(nombre, cuerpo):
    ruta = os.path.join(T, nombre)
    viejo = open(ruta, encoding='utf-8', newline='').read()
    nl = '\r\n' if '\r\n' in viejo else '\n'
    lic = licencia(nombre)
    open(ruta, 'w', encoding='utf-8', newline='').write(((lic + '\n\n') if lic else '') + cuerpo.replace('\n', nl))


def traducir(nombre, pares):
    ruta = os.path.join(T, nombre)
    s = open(ruta, encoding='utf-8', newline='').read()
    for a, b in pares:
        s = s.replace(a, b)
    open(ruta, 'w', encoding='utf-8', newline='').write(s)


NEGOCIO = '''	<image>Printer.Ticket.Logo</image>
#if ($negocio && $negocio.configurado)
	<line></line>
	<line></line>
	<line size="1"><text align="center" length="42" bold="true">${negocio.nombre}</text></line>
	#if ($negocio.ruc != "")
	<line><text align="center" length="42">RUC: ${negocio.ruc}</text></line>
	#end
	#if ($negocio.direccion != "")
	<line><text align="center" length="42">${negocio.direccion}</text></line>
	#end
#end
	<line></line>
	<line></line>
'''

PIE = '''	<line></line>
	<line><text align="center" length="42">Quinde POS - punto de venta libre</text></line>
'''

escribir('Printer.CustomerPaid.xml', '''<output>
<display>
	<line><text align="left" length="10">Abono</text><text align="right" length="10">${ticket.printTotalPaid()}</text></line>
	<line><text align="center" length="20">Gracias</text></line>
</display>
<ticket>
''' + NEGOCIO + '''	<line size="1"><text align="center" length="42" bold="true">ABONO A CUENTA</text></line>
	<line></line>
	<line><text align="left" length="12">Recibo:</text><text>${ticket.printId()}</text></line>
	<line><text align="left" length="12">Fecha:</text><text>${ticket.printDate()}</text></line>
	<line><text align="left" length="12">Cajero:</text><text>${ticket.printUser()}</text></line>
#if ($ticket.getCustomer())
	<line><text align="left" length="12">Cliente:</text><text>${ticket.getCustomer().printName()}</text></line>
	<line><text align="left" length="12">Ced./RUC:</text><text>${ticket.getCustomer().printTaxid()}</text></line>
#end
''' + LINEA + '''	<line></line>
	<line size="1"><text align="left" length="20" bold="true">ABONO</text><text align="right" length="22" bold="true">${ticket.printTotalPaid()}</text></line>
	<line></line>
	<line><text align="left" length="30" bold="true">Saldo pendiente</text><text align="right" length="12" bold="true">${customer.printCurDebt()}</text></line>
	<line></line>
#foreach ($paymentline in $ticket.payments)
	#if ($paymentline.name == "cash")
	<line><text align="left" length="22" bold="true">Efectivo</text><text align="right" length="20">${paymentline.printTotal()}</text></line>
	<line><text align="left" length="22">  Recibido:</text><text align="right" length="20">${paymentline.printPaid()}</text></line>
	<line><text align="left" length="22">  Cambio:</text><text align="right" length="20">${paymentline.printChange()}</text></line>
	#elseif ($paymentline.name == "magcard")
	<line><text align="left" length="22" bold="true">Tarjeta</text><text align="right" length="20">${paymentline.printTotal()}</text></line>
	<line><text>  ${paymentline.printCardNumber()}  Aut.: ${paymentline.printAuthorization()}</text></line>
	#elseif ($paymentline.name == "cheque")
	<line><text align="left" length="22" bold="true">Cheque</text><text align="right" length="20">${paymentline.printTotal()}</text></line>
	#elseif ($paymentline.name == "bank")
	<line><text align="left" length="22" bold="true">Transferencia</text><text align="right" length="20">${paymentline.printTotal()}</text></line>
	#elseif ($paymentline.name == "deuna")
	<line><text align="left" length="22" bold="true">DeUna</text><text align="right" length="20">${paymentline.printTotal()}</text></line>
	#elseif ($paymentline.name == "paperin")
	<line><text align="left" length="22" bold="true">Vale</text><text align="right" length="20">${paymentline.printTotal()}</text></line>
	#else
	<line><text align="left" length="22" bold="true">${paymentline.name}</text><text align="right" length="20">${paymentline.printTotal()}</text></line>
	#end
#end
	<line></line>
	<line><text align="center" length="42" bold="true">Gracias por su pago</text></line>
''' + PIE + '''</ticket>
#foreach ($paymentline in $ticket.payments)
	#if ($paymentline.name == "cash" || $paymentline.name == "cashrefund")
		<opendrawer/>
	#end
#end
<display>
	<line><text align="center" length="20">Siguiente cliente</text></line>
</display>
</output>
''')

escribir('Printer.CustomerPaid2.xml', '''<output>
<display>
	<line><text align="left" length="10">Abono</text><text align="right" length="10">${ticket.printTotalPaid()}</text></line>
	<line><text align="center" length="20">Gracias</text></line>
</display>
</output>
''')

escribir('Printer.Inventory.xml', '''<output>
<ticket>
''' + NEGOCIO + '''#if ($inventoryrecord.isInput())
	<line size="1"><text align="center" length="42" bold="true">ENTRADA DE INVENTARIO</text></line>
#else
	<line size="1"><text align="center" length="42" bold="true">SALIDA DE INVENTARIO</text></line>
#end
	<line></line>
	<line><text align="left" length="12">Fecha:</text><text>${inventoryrecord.printDate()}</text></line>
	<line><text align="left" length="12">Motivo:</text><text>${inventoryrecord.printReason()}</text></line>
	<line><text align="left" length="12">Almacen:</text><text>${inventoryrecord.printLocation()}</text></line>
	<line></line>
	<line><text align="left" length="17" bold="true">Producto</text><text align="right" length="10" bold="true">Precio</text><text align="right" length="5" bold="true">Cant</text><text align="right" length="10" bold="true">Valor</text></line>
''' + LINEA + '''#foreach ($inventoryline in $inventoryrecord.getLines())
	<line><text align="left" length="17">${inventoryline.printName()}</text><text align="right" length="10">${inventoryline.printPrice()}</text><text align="right" length="5">x${inventoryline.printMultiply()}</text><text align="right" length="10">${inventoryline.printSubValue()}</text></line>
	#if ($inventoryline.productAttSetInstId)
	<line><text align="left" length="42">    ${inventoryline.productAttSetInstDesc}</text></line>
	#end
#end
''' + LINEA + '''	<line></line>
	<line></line>
	<line><text align="left" length="20">____________________</text><text align="left" length="2"></text><text align="left" length="20">____________________</text></line>
	<line><text align="center" length="20">Entrega</text><text align="left" length="2"></text><text align="center" length="20">Recibe</text></line>
''' + PIE + '''</ticket>
</output>
''')

escribir('Printer.Start.xml', '''<output>
<!-- Animacion del visor: flyer, scroll, blink, curtain o none -->
<display animation="flyer">
	<line><text align="center" length="20">Quinde POS</text></line>
	<line><text align="center" length="20">Bienvenido</text></line>
</display>
</output>
''')

escribir('Printer.TicketTotal.xml', '''<output>
<display>
	<line><text align="left" length="10">Total:</text><text align="right" length="10">${ticket.printTotal()}</text></line>
	<line><text align="center" length="20">Gracias</text></line>
</display>
</output>
''')

escribir('Printer.TicketClose.xml', '''<output>
<display>
#foreach ($paymentline in $ticket.payments)
	<line><text align="left" length="10">Recibido:</text><text align="right" length="10">${paymentline.printPaid()}</text></line>
	<line><text align="left" length="10">Cambio:</text><text align="right" length="10">${paymentline.printChange()}</text></line>
#end
</display>
</output>
''')

traducir('Printer.Product.xml', [
    ('<text align="center" length="42">N.I.F. 00.000.000 X</text>', '<text align="center" length="42">${product.printCode()}</text>'),
    ('<text>Eur.</text>', '<text>$</text>'),
    ('''        <line size="2">
            <text>Pts.</text>
            <text align ="right" length="6">${product.printPricePts()}</text>
        </line>
''', ''),
])

PARES_TICKET = [
    ('>Account #:<', '>Cuenta:<'), ('>Authorisation:<', '>Autorizacion:<'), ('>Card Number:<', '>Tarjeta No.:<'),
    ('>Card Refund<', '>Devolucion tarjeta<'), ('>Card<', '>Tarjeta<'), ('>Cash<', '>Efectivo<'),
    ('>Change:<', '>Cambio:<'), ('>Cheque Refund<', '>Devolucion cheque<'), ('>Cheque refund<', '>Devolucion cheque<'),
    ('>Cheque<', '>Cheque<'), ('>Current Debt:<', '>Saldo:<'), ('>Customer:<', '>Cliente:<'), ('>Date:<', '>Fecha:<'),
    ('>EcoPos<', '>Quinde POS<'), ('>Expiration Date:<', '>Vence:<'), ('>Free<', '>Cortesia<'), ('>Item<', '>Producto<'),
    ('>Items count: <', '>Articulos: <'), ('Items count: ', 'Articulos: '), ('>Nett of Tax:<', '>Subtotal:<'),
    ('>Note Refund<', '>Devolucion vale<'), ('>Note<', '>Vale<'), ('>On Account<', '>A credito<'),
    ('>Operation:<', '>Operacion:<'), ('>Please Call Again<', '>Vuelva pronto<'), ('>Price<', '>Precio<'),
    ('>Qty<', '>Cant.<'), ('>Receipt:<', '>Ticket:<'), ('>Refund:<', '>Devolucion:<'), ('>Refund<', '>Devolucion<'),
    ('>Served by:<', '>Atendido:<'), ('>Table:<', '>Mesa:<'), ('>Taxes:<', '>Impuestos:<'), ('>Tendered:<', '>Recibido:<'),
    ('>Thank You<', '>Gracias por su compra<'), ('>Total<', '>Total<'), ('>Value<', '>Valor<'),
    ('>Mag card refund<', '>Devolucion tarjeta<'), ('>Mag card<', '>Tarjeta<'), ('>Touch Friendly Point Of Sale<', '>Punto de venta<'),
]
traducir('Printer.TicketNew.xml', PARES_TICKET + [('>Thank you for your custom<', '>Gracias por su compra<')])
traducir('Printer.FiscalTicket.xml', PARES_TICKET)

# Etiqueta de producto: sin el precio en pesetas (el espaciado del original varia).
import re
ruta = os.path.join(T, 'Printer.Product.xml')
s = open(ruta, encoding='utf-8', newline='').read()
s = re.sub(r'[ \t]*<line size="2">\s*<text>Pts\.</text>\s*<text[^>]*>\$\{product\.printPricePts\(\)\}</text>\s*</line>\r?\n', '', s)
open(ruta, 'w', encoding='utf-8', newline='').write(s)
print('ok')
