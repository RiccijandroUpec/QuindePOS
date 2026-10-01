"""Genera las imagenes del instalador (Inno Setup) con la marca de Quinde POS.

    python herramientas/instalador/generar_imagenes.py

Crea (PNG) en herramientas/instalador/imagenes/ la imagen grande de la izquierda (bienvenida y final) y
la pequena de arriba a la derecha, en los tamanos que Inno Setup elige segun la escala de Windows
(100 % a 250 %). Usa branding/quinde-icono-512.png y la fuente Outfit.
"""
import os

from PIL import Image, ImageDraw, ImageFont

RAIZ = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', '..'))
SALIDA = os.path.join(os.path.dirname(__file__), 'imagenes')
ICONO = Image.open(os.path.join(RAIZ, 'branding', 'quinde-icono-512.png')).convert('RGBA')
NEGRITA = os.path.join(RAIZ, 'branding', 'fuentes', 'Outfit-ExtraBold.ttf')
NORMAL = os.path.join(RAIZ, 'branding', 'fuentes', 'Outfit-Regular.ttf')

SELVA = (0x1B, 0x5E, 0x3F)
QUINDE = (0x2E, 0x9E, 0x6B)
BROTE = (0xA8, 0xE6, 0xC1)
BLANCO = (0xFF, 0xFF, 0xFF)

# Tamanos recomendados por Inno Setup para cada escala (100, 125, 150, 175, 200, 225, 250 %).
GRANDES = [(164, 314), (192, 386), (246, 459), (273, 523), (328, 628), (355, 682), (410, 797)]
PEQUENAS = [(55, 55), (64, 68), (83, 80), (92, 97), (110, 106), (119, 123), (138, 140)]


def degradado(ancho, alto):
    img = Image.new('RGB', (ancho, alto), SELVA)
    d = ImageDraw.Draw(img)
    for y in range(alto):
        t = y / max(1, alto - 1)
        c = tuple(round(SELVA[i] * (1 - t * 0.35) + 0x10 * t * 0.35) for i in range(3))
        d.line([(0, y), (ancho, y)], fill=c)
    return img


def tarjeta_icono(lado):
    """El icono ya trae su tarjeta blanca redondeada (el quinde verde no se ve directo sobre la selva)."""
    return ICONO.resize((lado, lado), Image.LANCZOS)


def grande(ancho, alto):
    img = degradado(ancho, alto).convert('RGBA')
    d = ImageDraw.Draw(img)
    s = ancho / 164.0
    # Hojas de selva al fondo, sutiles.
    for i, (cx, cy, r) in enumerate([(0.95, 0.80, 0.55), (0.05, 0.95, 0.42), (0.85, 0.05, 0.30)]):
        capa = Image.new('RGBA', img.size, (0, 0, 0, 0))
        ImageDraw.Draw(capa).ellipse([ancho * cx - ancho * r, alto * cy - ancho * r,
                                      ancho * cx + ancho * r, alto * cy + ancho * r], fill=QUINDE + (38,))
        img.alpha_composite(capa)
    lado = round(84 * s)
    x = (ancho - lado) // 2
    y = round(64 * s)
    img.alpha_composite(tarjeta_icono(lado), (x, y))
    f1 = ImageFont.truetype(NEGRITA, round(25 * s))
    f2 = ImageFont.truetype(NORMAL, round(11.5 * s))
    def centrado(texto, fuente, yy, color):
        w = d.textlength(texto, font=fuente)
        d.text(((ancho - w) / 2, yy), texto, font=fuente, fill=color)
    centrado('Quinde POS', f1, y + lado + round(16 * s), BLANCO)
    centrado('Punto de venta libre', f2, y + lado + round(52 * s), BROTE)
    centrado('para Ecuador', f2, y + lado + round(68 * s), BROTE)
    # Linea de acento abajo.
    d.rounded_rectangle([round(52 * s), alto - round(34 * s), ancho - round(52 * s), alto - round(30 * s)],
                        radius=round(2 * s), fill=QUINDE)
    return img.convert('RGB')


def pequena(ancho, alto):
    img = Image.new('RGBA', (ancho, alto), BLANCO + (255,))
    lado = min(ancho, alto)
    img.alpha_composite(ICONO.resize((lado, lado), Image.LANCZOS), ((ancho - lado) // 2, (alto - lado) // 2))
    return img.convert('RGB')


def main():
    os.makedirs(SALIDA, exist_ok=True)
    for i, (w, h) in enumerate(GRANDES):
        grande(w, h).save(os.path.join(SALIDA, 'lateral-%d.png' % i))
    for i, (w, h) in enumerate(PEQUENAS):
        pequena(w, h).save(os.path.join(SALIDA, 'icono-%d.png' % i))
    print('Imagenes en', SALIDA)


if __name__ == '__main__':
    main()
