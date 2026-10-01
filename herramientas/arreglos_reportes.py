"""Arregla 4 reportes que ya venian rotos de la plantilla original (no abrian en la app)."""
import glob, os, re, sys
CARPETA = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'reports', 'com', 'openbravo', 'reports')
for ruta in sorted(glob.glob(os.path.join(CARPETA, '*.jrxml'))):
    original = open(ruta, encoding='utf-8', newline='').read()
    s = original
    # 1. "x == null ? Double.valueOf(0) : Formats.X.formatValue(x)" mezcla numero y texto: el compilador
    #    de expresiones de Jasper 4.5 no lo resuelve sobre Java 11. Ahora siempre da texto.
    s = re.sub(r'\$F\{(\w+)\}\s*==\s*null\s*\?\s*Double\.valueOf\(0\)\s*:\s*(com\.openbravo\.format\.Formats\.\w+\.formatValue)\(\$F\{\1\}\)',
               r'\2($F{\1} == null ? Double.valueOf(0) : $F{\1})', s)
    # 2. Expresion copiada del codigo generado (no es una expresion valida de reporte).
    s = s.replace('str("transpayment." + (String)field_PAYMENT.getValue())',
                  'com.openbravo.pos.forms.AppLocal.getIntString("transpayment." + $F{PAYMENT})')
    # 3. Atributos uuid de un iReport mas nuevo: este Jasper (4.5) no los acepta.
    s = re.sub(r'\s+uuid="[0-9a-fA-F-]+"', '', s)
    # 4. Usa $P{ARG} sin declararlo.
    if '$P{ARG}' in s and '<parameter name="ARG"' not in s:
        m = re.search(r'<parameter\b', s) or re.search(r'<(queryString|field)\b', s)
        s = s[:m.start()] + '<parameter name="ARG" class="java.lang.Object" isForPrompting="false"/>\n\t' + s[m.start():]
    # 5. Graficos en modo SVG necesitan Batik, que no viene con la app: se dibujan en modo normal.
    s = s.replace('renderType="svg"', 'renderType="draw"')
    # 6. Sin datos, el reporte mostraba una hoja en blanco (parecia un error): ahora muestra
    #    el encabezado y los totales vacios.
    if 'whenNoDataType=' not in s:
        s = s.replace('<jasperReport ', '<jasperReport whenNoDataType="AllSectionsNoDetail" ', 1)
    if s != original:
        open(ruta, 'w', encoding='utf-8', newline='').write(s)
        print('arreglado', os.path.basename(ruta))
