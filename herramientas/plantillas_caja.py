"""Plantillas impresas de Quinde POS: cierre de caja (Z), corte parcial (X) y comanda de cocina."""
import os

T = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'src-pos', 'com', 'openbravo', 'pos', 'templates')
LINEA = '\t<line><text align="left" length="42">------------------------------------------</text></line>\n'


def licencia(nombre):
    s = open(os.path.join(T, nombre), encoding='utf-8', newline='').read()
    return s[:s.index('-->') + 3].replace('\r\n', '\n')


def escribir(nombre, contenido):
    ruta = os.path.join(T, nombre)
    viejo = open(ruta, encoding='utf-8', newline='').read()
    nl = '\r\n' if '\r\n' in viejo else '\n'
    open(ruta, 'w', encoding='utf-8', newline='').write(contenido.replace('\n', nl))


NEGOCIO = '''	<image>Printer.Ticket.Logo</image>
#if ($negocio && $negocio.configurado)
	<line></line>
	<line></line>
	<line size="1"><text align="center" length="42" bold="true">${negocio.nombre}</text></line>
	#if ($negocio.ruc != "")
	<line><text align="center" length="42">RUC: ${negocio.ruc}</text></line>
	#end
#end
	<line></line>
'''


def titulo(texto):
    return '\t<line><text align="left" length="42">==========================================</text></line>\n' \
           '\t<line></line>\n' \
           '\t<line size="1"><text align="center" length="42" bold="true">%s</text></line>\n' \
           '\t<line><text align="left" length="42">==========================================</text></line>\n' % texto


def seccion(texto):
    return '\t<line></line>\n\t<line><text align="left" length="42" bold="true">%s</text></line>\n' % texto + LINEA


DATOS_CAJA = '''	<line><text align="left" length="14">Caja No.:</text><text>${payments.printSequence()}</text></line>
	<line><text align="left" length="14">Equipo:</text><text>${payments.printHost()}</text></line>
	<line><text align="left" length="14">Desde:</text><text>${payments.printDateStart()}</text></line>
	<line><text align="left" length="14">Hasta:</text><text>${payments.printDateEnd()}</text></line>
#if ($cajero)
	<line><text align="left" length="14">Cajero:</text><text>${cajero}</text></line>
#end
#if ($impreso)
	<line><text align="left" length="14">Impreso:</text><text>${impreso}</text></line>
#end
'''

RESUMEN = seccion('RESUMEN DE VENTAS') + '''	<line><text align="left" length="30">Tickets</text><text align="right" length="12">${payments.printSales()}</text></line>
	<line><text align="left" length="30">Subtotal sin impuestos</text><text align="right" length="12">${payments.printSalesBase()}</text></line>
	<line><text align="left" length="30">Impuestos</text><text align="right" length="12">${payments.printSalesTaxes()}</text></line>
	<line></line>
	<line size="1"><text align="left" length="20" bold="true">TOTAL VENDIDO</text><text align="right" length="22" bold="true">${payments.printSalesTotal()}</text></line>
'''

IMPUESTOS = seccion('IMPUESTOS POR TARIFA') + '''	<line><text align="left" length="12" bold="true">Tarifa</text><text align="right" length="10" bold="true">Base</text><text align="right" length="10" bold="true">IVA</text><text align="right" length="10" bold="true">Total</text></line>
#foreach ($line in $payments.getSaleLines())
	<line><text align="left" length="12">${line.printTaxName()}</text><text align="right" length="10">${line.printTaxNet()}</text><text align="right" length="10">${line.printTaxes()}</text><text align="right" length="10">${line.printTaxGross()}</text></line>
#end
'''

PAGOS = seccion('FORMAS DE PAGO') + '''#foreach ($line in $payments.getPaymentLines())
	<line><text align="left" length="22">${line.printType()}</text><text align="left" length="10">${line.printReason()}</text><text align="right" length="10">${line.printValue()}</text></line>
#end
''' + LINEA + '''	<line><text align="left" length="30" bold="true">Total cobrado</text><text align="right" length="12" bold="true">${payments.printPaymentsTotal()}</text></line>
	<line><text align="left" length="30">Movimientos</text><text align="right" length="12">${payments.printPayments()}</text></line>
#if ($nosales && $nosales != "0")
	<line><text align="left" length="30">Aperturas de cajon sin venta</text><text align="right" length="12">${nosales}</text></line>
#end
'''

CATEGORIAS = seccion('VENTAS POR CATEGORIA') + '''	<line><text align="left" length="24" bold="true">Categoria</text><text align="right" length="8" bold="true">Cant.</text><text align="right" length="10" bold="true">Total</text></line>
#foreach ($line in $payments.getCategorySalesLines())
	<line><text align="left" length="24">${line.printCategoryName()}</text><text align="right" length="8">${line.printCategoryUnits()}</text><text align="right" length="10">${line.printCategorySum()}</text></line>
#end
'''

PRODUCTOS = seccion('VENTAS POR PRODUCTO') + '''	<line><text align="left" length="22" bold="true">Producto</text><text align="right" length="8" bold="true">Cant.</text><text align="right" length="12" bold="true">Total</text></line>
#foreach ($line in $payments.getProductSalesLines())
	<line><text align="left" length="22">${line.printProductName()}</text><text align="right" length="8">${line.printProductUnits()}</text><text align="right" length="12">${line.printProductSubValue()}</text></line>
#end
''' + LINEA + '''	<line><text align="left" length="22" bold="true">Total</text><text align="right" length="8" bold="true">${payments.printProductSalesTotalUnits()}</text><text align="right" length="12" bold="true">${payments.printProductSalesTotal()}</text></line>
'''

ELIMINADAS = '''#if ($payments.getRemovedProductLines() && $payments.getRemovedProductLines().size() > 0)
''' + seccion('LINEAS ELIMINADAS') + '''	<line><text align="left" length="16" bold="true">Usuario</text><text align="left" length="20" bold="true">Producto</text><text align="right" length="6" bold="true">Cant.</text></line>
#foreach ($line in $payments.getRemovedProductLines())
	<line><text align="left" length="16">${line.printWorkerName()}</text><text align="left" length="20">${line.printProductName()}</text><text align="right" length="6">${line.printTotalUnits()}</text></line>
#end
#end
'''

ARQUEO = '''#if ($arqueo)
''' + seccion('ARQUEO DE EFECTIVO') + '''#foreach ($d in $arqueo.detalle)
	<line><text align="left" length="42">  ${d}</text></line>
#end
''' + LINEA + '''	<line><text align="left" length="30">Fondo de caja</text><text align="right" length="12">${arqueo.fondo}</text></line>
	<line><text align="left" length="30">+ Efectivo esperado</text><text align="right" length="12">${arqueo.esperado}</text></line>
	<line><text align="left" length="30">= Deberia haber</text><text align="right" length="12">${arqueo.deberiaHaber}</text></line>
	<line><text align="left" length="30" bold="true">Contado</text><text align="right" length="12" bold="true">${arqueo.contado}</text></line>
	<line></line>
	#if ($arqueo.cuadra)
	<line size="1"><text align="center" length="42" bold="true">LA CAJA CUADRA</text></line>
	#else
	<line size="1"><text align="left" length="20" bold="true">${arqueo.estado}</text><text align="right" length="22" bold="true">${arqueo.diferencia}</text></line>
	#end
#end
'''

FIRMAS = '''	<line></line>
	<line></line>
	<line></line>
	<line><text align="left" length="20">____________________</text><text align="left" length="2"></text><text align="left" length="20">____________________</text></line>
	<line><text align="center" length="20">Cajero</text><text align="left" length="2"></text><text align="center" length="20">Supervisor</text></line>
	<line></line>
	<line><text align="center" length="42">Quinde POS - punto de venta libre</text></line>
'''

CIERRE = '<output>\n<ticket>\n' + NEGOCIO + titulo('CIERRE DE CAJA (Z)') + DATOS_CAJA + RESUMEN + IMPUESTOS + PAGOS \
    + ARQUEO + CATEGORIAS + ELIMINADAS + FIRMAS + '</ticket>\n</output>\n'

PARCIAL = '<output>\n<ticket>\n' + NEGOCIO + titulo('CORTE PARCIAL (X)') + DATOS_CAJA \
    + '\t<line><text align="center" length="42">La caja sigue abierta</text></line>\n' \
    + RESUMEN + IMPUESTOS + PAGOS + CATEGORIAS + PRODUCTOS + ELIMINADAS \
    + '\t<line></line>\n\t<line><text align="center" length="42">Quinde POS - punto de venta libre</text></line>\n' \
    + '</ticket>\n</output>\n'

COCINA = '''<output>
<display>
	<line><text align="left" length="10">Cocina</text><text align="right" length="10">${ticket.printTotal()}</text></line>
	<line><text align="center" length="20">Pedido enviado</text></line>
</display>

<ticket printer="2">
	<line></line>
	<line></line>
	<line size="1"><text align="center" length="42" bold="true">COMANDA DE COCINA</text></line>
	<line></line>
#if ($place && ${tickettext.place} != ${place})
	<line size="1"><text align="left" length="10" bold="true">MESA</text><text align="left" length="32" bold="true">${place}</text></line>
	<line></line>
#end
	<line><text align="left" length="10">Pedido:</text><text>${ticket.printId()}</text></line>
	<line><text align="left" length="10">Hora:</text><text>${ticket.printDate()}</text></line>
	<line><text align="left" length="10">Mesero:</text><text>${ticket.printUser()}</text></line>
#if ($ticket.getCustomer())
	<line><text align="left" length="10">Cliente:</text><text>${ticket.getCustomer().printName()}</text></line>
#end
''' + LINEA + '''#foreach ($ticketline in $ticket.getLines())
	#if (($ticketline.isProductKitchen()) && ($ticketline.getProperty("sendstatus").equals("No")))
	<line size="1">
		<text align="left" length="6" bold="true">${ticketline.printMultiply()}x</text>
		#if ($ticketline.isProductCom())
		<text align="left" length="36" bold="true">+ ${ticketline.printName()}</text>
		#else
		<text align="left" length="36" bold="true">${ticketline.printName()}</text>
		#end
	</line>
		#if ($ticketline.productAttSetInstId)
	<line><text align="left" length="42">      ${ticketline.productAttSetInstDesc}</text></line>
		#end
		#if ($ticketline.getProperty("notes") && $ticketline.getProperty("notes") != "")
	<line><text align="left" length="42" bold="true">      >> #if ($xml)${xml.esc($ticketline.getProperty("notes"))}#else${ticketline.getProperty("notes")}#end</text></line>
		#end
	<line></line>
	#end
#end
''' + LINEA + '''	<line><text align="center" length="42">Quinde POS</text></line>
</ticket>
</output>
'''

escribir('Printer.CloseCash.xml', licencia('Printer.CloseCash.xml') + '\n\n' + CIERRE)
escribir('Printer.PartialCash.xml', licencia('Printer.PartialCash.xml') + '\n\n' + PARCIAL)
escribir('Printer.TicketKitchen.xml', licencia('Printer.TicketKitchen.xml') + '\n\n' + COCINA)
print('ok')
