"""Aplica el estilo de Quinde POS a los reportes JasperReports (.jrxml).

- Colores del celeste viejo (#33CCFF) a la paleta Selva; graficos con colores de la marca y planos (sin 3D).
- Hora "dd/MM/yyyy HH:mm" en vez de "h.mm a".
- Encabezado nuevo arriba: nombre y RUC del negocio ($P{QUINDE_NEGOCIO}) y su logo ($P{QUINDE_LOGO}).
- Pie: "Quinde POS" junto al codigo del reporte.
- Formas de pago traducidas (transpayment.*) en vez del codigo interno ("cash").
Es idempotente: si el reporte ya tiene QUINDE_NEGOCIO no lo vuelve a tocar.
"""
import glob
import os
import re
import sys

CARPETA = sys.argv[1]
DESPLAZAMIENTO = 34
PALETA = ['#2E9E6B', '#F2705E', '#1B5E3F', '#A8E6C1', '#10231A', '#5CC08A',
          '#F59D8F', '#3E7D5F', '#D8F3E3', '#C9573F', '#7FCBA3', '#26493A']

PARAMETROS = ('<parameter name="QUINDE_NEGOCIO" class="java.lang.String" isForPrompting="false"/>\n'
              '\t<parameter name="QUINDE_LOGO" class="java.awt.Image" isForPrompting="false"/>\n\t')


def encabezado(ancho):
    w_logo = 130
    return f'''
			<textField isBlankWhenNull="true">
				<reportElement key="quinde-negocio" mode="Transparent" x="0" y="0" width="{ancho - w_logo - 10}" height="30" forecolor="#10231A"/>
				<textElement verticalAlignment="Middle">
					<font fontName="SansSerif" size="10" isBold="true" pdfFontName="Helvetica-Bold"/>
				</textElement>
				<textFieldExpression><![CDATA[$P{{QUINDE_NEGOCIO}}]]></textFieldExpression>
			</textField>
			<image scaleImage="RetainShape" hAlign="Right" vAlign="Middle" isUsingCache="true" onErrorType="Blank">
				<reportElement key="quinde-logo" x="{ancho - w_logo}" y="0" width="{w_logo}" height="30"/>
				<imageExpression class="java.awt.Image"><![CDATA[$P{{QUINDE_LOGO}}]]></imageExpression>
			</image>'''


def desplazar_banda(s, nombre, ancho):
    """Agrega el encabezado arriba de la banda 'nombre' y baja lo demas. True si lo hizo."""
    m = re.search(r'(<%s>\s*<band)([^>]*?)(/?)>' % nombre, s)
    if not m:
        return s, False
    attrs = m.group(2)
    alto = re.search(r'height="(\d+)"', attrs)
    if m.group(3) == '/' or not alto or int(alto.group(1)) < 30:
        return s, False
    fin = s.index('</band>', m.end())
    cuerpo = s[m.end():fin]

    def bajar(mm):
        return '%sy="%d"' % (mm.group(1), int(mm.group(2)) + DESPLAZAMIENTO)
    cuerpo = re.sub(r'(<reportElement\b[^>]*?\s)y="(-?\d+)"', bajar, cuerpo)
    nuevo_attrs = re.sub(r'height="\d+"', 'height="%d"' % (int(alto.group(1)) + DESPLAZAMIENTO), attrs)
    s = s[:m.start()] + m.group(1) + nuevo_attrs + '>' + encabezado(ancho) + cuerpo + s[fin:]
    return s, True


def agregar_parametros(s):
    ultimo = None
    for mm in re.finditer(r'<parameter\b[^>]*(/>|>.*?</parameter>)', s, re.S):
        ultimo = mm
    if ultimo:
        pos = ultimo.end()
        return s[:pos] + '\n\t' + PARAMETROS.rstrip('\t').rstrip('\n') + s[pos:]
    m = re.search(r'<(queryString|field|sortField|variable|filterExpression|group|background|title|pageHeader)\b', s)
    return s[:m.start()] + PARAMETROS + s[m.start():]


def graficos(s):
    contador = [0]

    def color(mm):
        c = PALETA[contador[0] % len(PALETA)]
        contador[0] += 1
        return '%scolor="%s"' % (mm.group(1), c)
    s = re.sub(r'(<seriesColor\s+seriesOrder="\d+"\s+)color="#[0-9A-Fa-f]{6}"', color, s)
    # Graficos planos, mas legibles que en 3D.
    s = s.replace('<bar3DChart', '<barChart').replace('</bar3DChart>', '</barChart>')
    s = s.replace('<stackedBar3DChart', '<stackedBarChart').replace('</stackedBar3DChart>', '</stackedBarChart>')
    s = re.sub(r'<bar3DPlot\b([^>]*)>', lambda mm: '<barPlot' + re.sub(r'\s(xOffset|yOffset)="[^"]*"', '', mm.group(1)) + '>', s)
    s = s.replace('</bar3DPlot>', '</barPlot>')
    s = s.replace('<pie3DChart', '<pieChart').replace('</pie3DChart>', '</pieChart>')
    s = re.sub(r'<pie3DPlot\b([^>]*)>', lambda mm: '<piePlot' + re.sub(r'\sdepthFactor="[^"]*"', '', mm.group(1)) + '>', s)
    s = s.replace('</pie3DPlot>', '</piePlot>')
    return s


def transformar(s):
    if 'QUINDE_NEGOCIO' in s:
        return s, 'ya'
    s = s.replace('forecolor="#33CCFF"', 'forecolor="#1B5E3F"').replace('backcolor="#33CCFF"', 'backcolor="#2E9E6B"')
    s = s.replace('lineColor="#33CCFF"', 'lineColor="#2E9E6B"')
    s = s.replace('pattern="dd/MM/yyyy h.mm a"', 'pattern="dd/MM/yyyy HH:mm"')
    s = s.replace('<textFieldExpression><![CDATA[$F{PAYMENT}]]>',
                  '<textFieldExpression><![CDATA[com.openbravo.pos.forms.AppLocal.getIntString("transpayment." + $F{PAYMENT})]]>')
    s = re.sub(r'<text><!\[CDATA\[(rpt:[^\]]*)\]\]></text>', r'<text><![CDATA[Quinde POS  ·  \1]]></text>', s)
    s = graficos(s)
    ancho = int(re.search(r'columnWidth="(\d+)"', s).group(1))
    hecho = False
    for banda in ('title', 'pageHeader'):
        s, hecho = desplazar_banda(s, banda, ancho)
        if hecho:
            break
    if not hecho:
        return s, 'colores (sin encabezado)'
    return agregar_parametros(s), 'completo'


for ruta in sorted(glob.glob(os.path.join(CARPETA, '*.jrxml'))):
    original = open(ruta, encoding='utf-8', newline='').read()
    nl = '\r\n' if '\r\n' in original else '\n'
    s, estado = transformar(original.replace('\r\n', '\n'))
    if s.replace('\n', nl) != original:
        open(ruta, 'w', encoding='utf-8', newline='').write(s.replace('\n', nl))
    print('%-32s %s' % (os.path.basename(ruta), estado))
