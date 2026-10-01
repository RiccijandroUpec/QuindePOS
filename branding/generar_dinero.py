"""Genera las imagenes de billetes y monedas de dolar del cobro en efectivo (temas clasicos).

Uso: python branding/generar_dinero.py
Los nombres siguen los que usa el recurso payment.cash (note.50 = $50 ... coin.20 = 25 centavos).
"""
import os
from PIL import Image, ImageDraw, ImageFont

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESTINO = os.path.join(RAIZ, 'src-pos', 'com', 'openbravo', 'pos', 'templates')
FUENTE = os.path.join(RAIZ, 'branding', 'fuentes', 'Outfit-ExtraBold.ttf')
FUENTE_REG = os.path.join(RAIZ, 'branding', 'fuentes', 'Outfit-Regular.ttf')
K = 4  # se dibuja 4 veces mas grande y se reduce: bordes suaves

SELVA = (27, 94, 63)
TINTA = (16, 35, 26)
BROTE = (216, 243, 227)

BILLETES = {'note.50': '50', 'note.20': '20', 'note.10': '10', 'note.5': '5'}
MONEDAS = {
    'coin.1': ('$1', (212, 175, 55), (120, 92, 18)),
    'coin.50': ('50¢', (201, 206, 211), (90, 98, 106)),
    'coin.20': ('25¢', (201, 206, 211), (90, 98, 106)),
    'coin.10': ('10¢', (201, 206, 211), (90, 98, 106)),
    'coin.05': ('5¢', (201, 206, 211), (90, 98, 106)),
    'coin.01': ('1¢', (199, 123, 69), (110, 58, 24)),
}


def centrado(d, caja, texto, fuente, color):
    x0, y0, x1, y1 = caja
    b = d.textbbox((0, 0), texto, font=fuente)
    d.text((x0 + (x1 - x0 - (b[2] - b[0])) / 2 - b[0], y0 + (y1 - y0 - (b[3] - b[1])) / 2 - b[1]), texto, font=fuente, fill=color)


def billete(valor):
    w, h = 128 * K, 68 * K
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((2 * K, 2 * K, w - 2 * K, h - 2 * K), radius=8 * K, fill=BROTE, outline=SELVA, width=3 * K)
    d.rounded_rectangle((8 * K, 8 * K, w - 8 * K, h - 8 * K), radius=5 * K, outline=(46, 158, 107), width=1 * K)
    centrado(d, (0, 4 * K, w, h - 16 * K), '$' + valor, ImageFont.truetype(FUENTE, 34 * K), TINTA)
    centrado(d, (0, h - 22 * K, w, h - 8 * K), 'DÓLARES', ImageFont.truetype(FUENTE_REG, 9 * K), SELVA)
    return im.resize((128, 68), Image.LANCZOS)


def moneda(texto, color, borde):
    t = 64 * K
    im = Image.new('RGBA', (t, t), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((2 * K, 2 * K, t - 2 * K, t - 2 * K), fill=color, outline=borde, width=3 * K)
    d.ellipse((8 * K, 8 * K, t - 8 * K, t - 8 * K), outline=borde, width=1 * K)
    centrado(d, (0, 0, t, t), texto, ImageFont.truetype(FUENTE, (22 if len(texto) > 2 else 26) * K), borde)
    return im.resize((64, 64), Image.LANCZOS)


for nombre, valor in BILLETES.items():
    billete(valor).save(os.path.join(DESTINO, nombre + '.png'))
for nombre, (texto, color, borde) in MONEDAS.items():
    moneda(texto, color, borde).save(os.path.join(DESTINO, nombre + '.png'))
print('ok')
