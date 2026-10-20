"""Genera las guías de la página (docs/guias/...) con el diseño del sitio.

Cada guía es una página propia (mejor para buscadores): docs/guias/<slug>/index.html, con datos
estructurados Article + BreadcrumbList, enlaces entre guías y una invitación a descargar Quinde POS.
Uso (desde la raíz del repo):  python herramientas/guias/generar_guias.py
Después: agregar las URL nuevas a docs/sitemap.xml si cambian los slugs.
"""
import html
import json
import os

SITIO = 'https://quindepos.cyrshop.app'
RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'docs')
FECHA = '2026-10-20'

GUIAS = [
    {
        'slug': 'facturacion-electronica-gratis-ecuador',
        'titulo': 'Cómo emitir facturas electrónicas gratis en Ecuador (paso a paso)',
        'corto': 'Facturar gratis, paso a paso',
        'descripcion': 'Guía paso a paso para emitir facturas electrónicas del SRI sin pagar mensualidades: '
                       'qué necesitas, las opciones gratuitas y cómo configurar Quinde POS.',
        'cuerpo': '''
<p class="entrada">Sí se puede facturar electrónicamente en Ecuador sin pagar mensualidades ni un valor por cada factura. Lo único que sí tiene costo es la <b>firma electrónica</b>, que necesitas con cualquier sistema. En esta guía te explicamos las dos opciones gratuitas y cómo empezar con Quinde POS.</p>

<h2>Lo que necesitas antes de empezar</h2>
<ul>
  <li><b>RUC activo</b>, con tu establecimiento registrado en el SRI.</li>
  <li><b>Clave de SRI en Línea</b>.</li>
  <li><b>Firma electrónica en archivo</b> (<code>.p12</code>) con su contraseña, emitida por una entidad de certificación autorizada.</li>
  <li><b>Una computadora con internet</b>: hoy la factura se envía al SRI en el mismo momento en que se emite.</li>
</ul>
<p>Si te falta algo, revisa la guía de <a href="/guias/requisitos-facturacion-electronica-sri/">requisitos para la facturación electrónica del SRI</a>.</p>

<h2>Las dos opciones gratuitas</h2>
<p><b>1. El Facturador del SRI.</b> Es la herramienta gratuita y en línea del propio SRI. Entras con tu usuario y clave de SRI en Línea y llenas cada factura en la página web. Sirve si emites pocas facturas al mes.</p>
<p><b>2. Un punto de venta gratuito como Quinde POS.</b> Es software libre: lo instalas en tu computadora y la factura sale sola cada vez que cobras, junto con la caja, el inventario y el ticket impreso. Conviene si vendes todos los días en mostrador.</p>
<p>¿No sabes cuál elegir? Mira la comparación <a href="/guias/facturador-sri-o-punto-de-venta/">Facturador del SRI o punto de venta gratis</a>.</p>

<h2>Paso a paso con Quinde POS</h2>
<ol class="lista-pasos">
  <li><b>Descarga el instalador.</b> Entra a <a href="/">quindepos.cyrshop.app</a> y toca <i>Descargar para Windows</i> (Windows 10 u 11). Trae Java y la base de datos incluidos: no instalas nada más.</li>
  <li><b>Instálalo.</b> Cuando pregunte por la base de datos, deja la opción recomendada (<i>la base de datos incluida</i>).</li>
  <li><b>Sigue el asistente.</b> La primera vez que lo abres te guía: datos y logo de tu negocio, tipo de negocio, IVA, usuarios, impresora de tickets y productos (también puedes cargarlos desde Excel).</li>
  <li><b>Configura la facturación electrónica.</b> En el paso <i>Facturación SRI</i> del asistente (o después, en <i>Sistema → Facturación electrónica</i>) escribe tu RUC, razón social y direcciones; el establecimiento y el punto de emisión (por ejemplo <code>001</code> y <code>001</code>, como están registrados en el SRI); elige el ambiente <b>Pruebas</b>; carga tu archivo <code>.p12</code> y su contraseña. Quinde POS te muestra de quién es la firma y hasta cuándo vale. Termina con <i>Probar conexión con el SRI</i>.</li>
  <li><b>Haz ventas de prueba.</b> Cobra una venta y, si quieres la factura a nombre de alguien, elige <i>Factura con datos</i> y escribe su cédula o RUC. En unos segundos verás el aviso <i>Factura autorizada</i>. En <i>Ventas → Comprobantes electrónicos</i> ves todas tus facturas, su RIDE en PDF y puedes reenviarlas por correo.</li>
  <li><b>Pasa a Producción.</b> Cuando todo funcione, cambia el ambiente a <b>Producción</b>. Antes, confirma en SRI en Línea que tu RUC está habilitado para emitir comprobantes electrónicos en producción; si tienes dudas, consúltalo con tu contador.</li>
</ol>

<h2>Errores comunes y cómo evitarlos</h2>
<ul>
  <li><b>Firma vencida o contraseña incorrecta:</b> la firma tiene fecha de vencimiento. Renuévala con tiempo; Quinde POS te avisa 30 días antes.</li>
  <li><b>Establecimiento o punto de emisión que no coinciden con el SRI:</b> usa exactamente los códigos registrados en tu RUC.</li>
  <li><b>Probar directamente en Producción:</b> en Pruebas las facturas no tienen validez tributaria, así que puedes equivocarte sin problema. Practica ahí primero.</li>
  <li><b>Sin internet:</b> las reglas actuales piden transmitir cada comprobante al SRI en el momento. Si se cae la conexión, Quinde POS reintenta solo, pero lo ideal es tener internet estable en la caja.</li>
</ul>
''',
    },
    {
        'slug': 'requisitos-facturacion-electronica-sri',
        'titulo': 'Requisitos para la facturación electrónica del SRI en Ecuador',
        'corto': 'Requisitos del SRI',
        'descripcion': 'Qué necesitas para emitir facturas electrónicas en Ecuador: RUC, clave de SRI en Línea, '
                       'firma electrónica .p12, un sistema de facturación e internet. Explicado en palabras simples.',
        'cuerpo': '''
<p class="entrada">La factura electrónica es hoy la forma normal de facturar en Ecuador. Estos son los requisitos, explicados en palabras simples, para que sepas qué tener listo antes de emitir tu primera factura.</p>

<h2>¿Quién debe emitir facturas electrónicas?</h2>
<p>La gran mayoría de contribuyentes con RUC que emiten facturas deben hacerlo de forma electrónica. Los contribuyentes del <b>RIMPE emprendedor</b> también emiten comprobantes electrónicos, con la leyenda <i>Contribuyente Régimen RIMPE</i>.</p>
<p>Los <b>negocios populares del RIMPE</b> tienen reglas especiales (por ejemplo, las notas de venta) que han cambiado en los últimos años. Si es tu caso, confirma en SRI en Línea o con tu contador qué comprobante te corresponde.</p>

<h2>Los 5 requisitos</h2>
<ol class="lista-pasos">
  <li><b>RUC activo</b>, con el establecimiento desde el que vas a facturar registrado. Cada factura lleva un código de establecimiento y de punto de emisión (por ejemplo <code>001-001</code>).</li>
  <li><b>Clave de SRI en Línea.</b> Con ella entras a los servicios del SRI, revisas tus comprobantes y usas el facturador gratuito.</li>
  <li><b>Firma electrónica en archivo (<code>.p12</code>).</b> Es lo que le da validez legal a cada factura. Se obtiene con una <b>entidad de certificación</b> calificada por ARCOTEL y registrada ante el SRI. Tiene costo y una fecha de vencimiento: guarda bien el archivo y su contraseña, y renuévala antes de que caduque.</li>
  <li><b>Un sistema de facturación.</b> Puede ser el Facturador gratuito del SRI o un programa propio que genere, firme y envíe las facturas, como <a href="/">Quinde POS</a>, que además es gratuito.</li>
  <li><b>Internet.</b> Desde 2026 el comprobante debe transmitirse al SRI en el mismo momento de su emisión. Ya no existe el plazo de varios días que había antes.</li>
</ol>

<h2>Ambiente de pruebas y producción</h2>
<p>El SRI tiene dos ambientes: <b>Pruebas</b>, donde las facturas no tienen validez tributaria y sirven para practicar, y <b>Producción</b>, donde las facturas son reales. Empieza siempre en Pruebas: así revisas tus datos, tu firma y tu impresora sin riesgo.</p>

<h2>¿Qué recibe tu cliente?</h2>
<p>Cuando el SRI autoriza la factura, tu cliente puede recibir el <b>RIDE</b> (la representación impresa de la factura, normalmente en PDF) y el archivo <b>XML</b>, que es la factura electrónica en sí. Con Quinde POS se envían solos a su correo, y el ticket impreso lleva el número de autorización.</p>

<h2>Lista rápida</h2>
<ul class="lista-check">
  <li>RUC activo y establecimiento registrado</li>
  <li>Clave de SRI en Línea</li>
  <li>Firma electrónica <code>.p12</code> vigente y su contraseña</li>
  <li>Sistema de facturación (Facturador del SRI o Quinde POS)</li>
  <li>Internet estable donde vas a facturar</li>
</ul>
<p>¿Ya tienes todo? Sigue la guía <a href="/guias/facturacion-electronica-gratis-ecuador/">Cómo emitir facturas electrónicas gratis, paso a paso</a>.</p>
<p class="nota">Esta guía es informativa y no reemplaza la normativa del SRI ni la asesoría de un contador. Las reglas tributarias cambian: confirma siempre en <a href="https://www.sri.gob.ec" rel="nofollow">sri.gob.ec</a>.</p>
''',
    },
    {
        'slug': 'facturador-sri-o-punto-de-venta',
        'titulo': 'Facturador del SRI o punto de venta gratis: ¿cuál te conviene?',
        'corto': 'Facturador del SRI o punto de venta',
        'descripcion': 'Comparación entre el Facturador gratuito del SRI y un punto de venta gratuito con facturación '
                       'electrónica como Quinde POS: qué hace cada uno y cuál conviene según tu negocio.',
        'cuerpo': '''
<p class="entrada">En Ecuador hay dos formas de facturar electrónicamente sin pagar mensualidades: el <b>Facturador del SRI</b> y un <b>punto de venta gratuito</b> como Quinde POS. Los dos emiten facturas válidas. La diferencia está en todo lo demás.</p>

<h2>Comparación rápida</h2>
<div class="tabla-envoltura">
<table>
  <thead><tr><th></th><th>Facturador del SRI</th><th>Quinde POS</th></tr></thead>
  <tbody>
    <tr><th>Costo</th><td>Gratis</td><td>Gratis (software libre)</td></tr>
    <tr><th>Firma electrónica</th><td>Necesaria (.p12)</td><td>Necesaria (.p12)</td></tr>
    <tr><th>Dónde funciona</th><td>En el navegador, con internet</td><td>Programa en tu computadora (Windows)</td></tr>
    <tr><th>Cómo se factura</th><td>Llenas cada factura en la página web</td><td>Cobras la venta y la factura sale sola</td></tr>
    <tr><th>Productos y precios</th><td>Se escriben en cada factura</td><td>Catálogo con fotos, búsqueda y código de barras</td></tr>
    <tr><th>Caja y cierre del día</th><td>No</td><td>Sí, con cuadre de caja</td></tr>
    <tr><th>Inventario</th><td>No</td><td>Sí, con aviso de productos por agotarse</td></tr>
    <tr><th>Ticket impreso</th><td>No</td><td>Sí, en impresora térmica</td></tr>
    <tr><th>Reportes</th><td>Consulta de comprobantes</td><td>Ventas del día, más vendidos e IVA del mes</td></tr>
  </tbody>
</table>
</div>

<h2>Cuándo conviene el Facturador del SRI</h2>
<ul>
  <li>Emites <b>pocas facturas al mes</b> (por ejemplo, servicios profesionales).</li>
  <li>No vendes productos en mostrador ni necesitas controlar inventario.</li>
  <li>Prefieres no instalar nada en tu computadora.</li>
</ul>

<h2>Cuándo conviene un punto de venta como Quinde POS</h2>
<ul>
  <li>Vendes <b>todos los días</b>: tienda, minimarket, ferretería, panadería, restaurante, farmacia.</li>
  <li>Quieres que la factura salga sola al cobrar, sin volver a escribir los productos.</li>
  <li>Necesitas cuadrar la caja, saber qué se vende más y qué se está acabando.</li>
  <li>Tienes cajeros y quieres que cada uno entre con su propio usuario.</li>
</ul>

<h2>Lo que cuestan los dos</h2>
<p>Ninguno cobra mensualidad ni un valor por factura. En ambos casos el único gasto es la <b>firma electrónica</b>, que se compra a una entidad de certificación y se renueva cuando vence. Con Quinde POS, además, puedes usar la impresora de tickets que ya tengas.</p>

<h2>¿Y si empiezo con uno y me cambio?</h2>
<p>Puedes hacerlo. La firma electrónica es la misma y las facturas que ya emitiste quedan registradas en el SRI. Solo cuida que la numeración de cada punto de emisión continúe sin repetirse.</p>
<p>Para empezar con Quinde POS, sigue la guía <a href="/guias/facturacion-electronica-gratis-ecuador/">Cómo emitir facturas electrónicas gratis, paso a paso</a>.</p>
''',
    },
]

ESTILO_EXTRA = '''
.articulo { max-width: 808px; margin: 0 auto; padding-top: 48px; padding-bottom: 72px; overflow-wrap: break-word; }
.migas { font-size: 15px; color: var(--texto-suave); margin-bottom: 18px; }
.migas a { color: inherit; }
.articulo h1 { font-size: clamp(32px, 4.6vw, 48px); }
.articulo h2 { font-size: 28px; margin: 40px 0 12px; letter-spacing: -0.01em; }
.articulo p, .articulo li { font-size: 18px; line-height: 1.65; }
.articulo .entrada { font-size: 20px; color: var(--texto-suave); }
.articulo ul, .articulo ol { padding-left: 1.3em; }
.articulo li { margin: 8px 0; }
.articulo a { color: var(--quinde); }
.articulo code { background: var(--fondo-suave); border: 1px solid var(--borde); border-radius: 6px; padding: 1px 6px; font-size: 0.92em; }
.lista-pasos li::marker { font-weight: 800; color: var(--quinde); }
.lista-check { list-style: none; padding-left: 0 !important; }
.lista-check li::before { content: "✓"; color: var(--quinde); font-weight: 800; margin-right: 10px; }
.nota { font-size: 15px !important; color: var(--texto-suave); border-top: 1px solid var(--borde); padding-top: 16px; margin-top: 32px; }
.tabla-envoltura { overflow-x: auto; border: 1px solid var(--borde); border-radius: var(--radio); }
.articulo table { border-collapse: collapse; width: 100%; min-width: 560px; font-size: 16px; }
.articulo th, .articulo td { text-align: left; padding: 12px 14px; border-bottom: 1px solid var(--borde); vertical-align: top; }
.articulo thead th { background: var(--fondo-suave); }
.articulo tbody tr:last-child th, .articulo tbody tr:last-child td { border-bottom: 0; }
.cta { margin-top: 44px; padding: 28px; border-radius: var(--radio); background: var(--fondo-suave); border: 1px solid var(--borde); }
.cta h2 { margin-top: 0; }
.relacionadas { margin-top: 36px; }
.relacionadas ul { list-style: none; padding: 0 !important; display: grid; gap: 10px; }
.relacionadas a { display: block; padding: 14px 18px; border: 1px solid var(--borde); border-radius: 12px; text-decoration: none; font-weight: 800; color: var(--texto); }
.relacionadas a:hover { border-color: var(--quinde); }
.tarjetas-guias { list-style: none; padding: 0; display: grid; gap: 16px; }
.tarjetas-guias a { display: block; padding: 22px 24px; border: 1px solid var(--borde); border-radius: var(--radio); background: var(--tarjeta); text-decoration: none; color: var(--texto); }
.tarjetas-guias a:hover { border-color: var(--quinde); }
.tarjetas-guias b { display: block; font-size: 22px; margin-bottom: 6px; }
.tarjetas-guias span { color: var(--texto-suave); font-size: 17px; line-height: 1.5; }
'''


def cabeza(titulo, descripcion, url, ld):
    t, d = html.escape(titulo), html.escape(descripcion)
    return f'''<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{t} · Quinde POS</title>
  <meta name="description" content="{d}">
  <meta name="theme-color" content="#1B5E3F">
  <link rel="canonical" href="{url}">
  <meta property="og:type" content="article">
  <meta property="og:locale" content="es_EC">
  <meta property="og:site_name" content="Quinde POS">
  <meta property="og:title" content="{t}">
  <meta property="og:description" content="{d}">
  <meta property="og:url" content="{url}">
  <meta property="og:image" content="{SITIO}/assets/compartir.png">
  <meta property="og:image:width" content="1200">
  <meta property="og:image:height" content="630">
  <meta name="twitter:card" content="summary_large_image">
  <link rel="icon" href="/favicon.ico" sizes="any">
  <link rel="icon" type="image/png" sizes="48x48" href="/assets/favicon-48.png">
  <link rel="icon" type="image/svg+xml" href="/assets/icono.svg">
  <link rel="apple-touch-icon" href="/assets/icono.png">
  <link rel="stylesheet" href="/assets/estilo.css">
  <link rel="stylesheet" href="/assets/guias.css">
  <script type="application/ld+json">
{json.dumps(ld, ensure_ascii=False, indent=2)}
  </script>
</head>
<body>
<header class="barra">
  <div class="contenedor">
    <a class="marca" href="/" aria-label="Quinde POS, inicio">
      <img src="/assets/icono.png" alt="" width="34" height="34">
      <span>Quinde<span class="pos">POS</span></span>
    </a>
    <nav class="menu" aria-label="Secciones">
      <a href="/#funciones">Funciones</a>
      <a href="/#sri">Facturación SRI</a>
      <a href="/guias/">Guías</a>
      <a class="boton chico" href="/#empezar">Descargar gratis</a>
    </nav>
  </div>
</header>
<main>
'''


PIE = '''</main>
<footer>
  <div class="contenedor">
    <span>© 2026 Quinde POS · Software libre bajo la <a href="https://www.gnu.org/licenses/gpl-3.0.html">GPL v3</a></span>
    <span>Hecho en Ecuador</span>
  </div>
</footer>
</body>
</html>
'''

CTA = '''
<section class="cta">
  <h2>Prueba Quinde POS gratis</h2>
  <p>Punto de venta con facturación electrónica del SRI incluida, caja, inventario y reportes. Sin mensualidades ni costo por factura.</p>
  <div class="acciones">
    <a class="boton" href="https://github.com/RiccijandroUpec/QuindePOS/releases/latest">Descargar para Windows</a>
    <a class="boton secundario" href="https://wa.me/593983185069?text=Hola%20Richard%2C%20tengo%20una%20pregunta%20sobre%20Quinde%20POS.">Preguntar por WhatsApp</a>
  </div>
</section>
'''


def migas(items):
    return {'@type': 'BreadcrumbList', 'itemListElement': [
        {'@type': 'ListItem', 'position': i + 1, 'name': n, 'item': u} for i, (n, u) in enumerate(items)]}


def escribir(ruta, contenido):
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    with open(ruta, 'w', encoding='utf-8', newline='\n') as f:
        f.write(contenido)


def generar():
    escribir(os.path.join(RAIZ, 'assets', 'guias.css'), '/* Guías de Quinde POS (docs/guias). */' + ESTILO_EXTRA)
    for g in GUIAS:
        url = f'{SITIO}/guias/{g["slug"]}/'
        ld = {'@context': 'https://schema.org', '@graph': [
            {'@type': 'Article', 'headline': g['titulo'], 'description': g['descripcion'], 'inLanguage': 'es-EC',
             'datePublished': FECHA, 'dateModified': FECHA, 'mainEntityOfPage': url,
             'image': f'{SITIO}/assets/compartir.png',
             'author': {'@type': 'Organization', 'name': 'Quinde POS', 'url': SITIO + '/'},
             'publisher': {'@type': 'Organization', 'name': 'Quinde POS', 'logo': {'@type': 'ImageObject', 'url': f'{SITIO}/assets/icono.png'}}},
            migas([('Inicio', SITIO + '/'), ('Guías', SITIO + '/guias/'), (g['corto'], url)]),
        ]}
        otras = ''.join(f'<li><a href="/guias/{o["slug"]}/">{html.escape(o["titulo"])}</a></li>' for o in GUIAS if o is not g)
        cuerpo = f'''<article class="contenedor articulo">
<nav class="migas" aria-label="Ruta"><a href="/">Inicio</a> › <a href="/guias/">Guías</a> › {html.escape(g["corto"])}</nav>
<h1>{html.escape(g["titulo"])}</h1>
{g["cuerpo"]}
{CTA}
<section class="relacionadas"><h2>Otras guías</h2><ul>{otras}</ul></section>
</article>
'''
        escribir(os.path.join(RAIZ, 'guias', g['slug'], 'index.html'), cabeza(g['titulo'], g['descripcion'], url, ld) + cuerpo + PIE)

    # Índice de guías
    url = SITIO + '/guias/'
    titulo = 'Guías de facturación electrónica en Ecuador'
    desc = 'Guías gratuitas y en palabras simples para facturar electrónicamente con el SRI en Ecuador.'
    ld = {'@context': 'https://schema.org', '@graph': [
        {'@type': 'CollectionPage', 'name': titulo, 'description': desc, 'url': url, 'inLanguage': 'es-EC'},
        migas([('Inicio', SITIO + '/'), ('Guías', url)])]}
    tarjetas = ''.join(f'<li><a href="/guias/{g["slug"]}/"><b>{html.escape(g["titulo"])}</b><span>{html.escape(g["descripcion"])}</span></a></li>' for g in GUIAS)
    cuerpo = f'''<div class="contenedor articulo">
<nav class="migas" aria-label="Ruta"><a href="/">Inicio</a> › Guías</nav>
<h1>{titulo}</h1>
<p class="entrada">Todo lo que necesitas para emitir facturas electrónicas del SRI, explicado en palabras simples.</p>
<ul class="tarjetas-guias">{tarjetas}</ul>
{CTA}
</div>
'''
    escribir(os.path.join(RAIZ, 'guias', 'index.html'), cabeza(titulo, desc, url, ld) + cuerpo + PIE)
    print('ok:', len(GUIAS), 'guías + índice')


if __name__ == '__main__':
    generar()
