# Gera as camadas do ícone adaptativo (fundo, porquinho e monocromática) a partir do ícone do iOS.
# Uso: python3 app/gerar-icone.py ../frontend/ios/App/App/Assets.xcassets/AppIcon.appiconset/icone.png app/src/main/res
# (precisa do Pillow: pip install pillow)
import sys
from PIL import Image, ImageDraw, ImageFilter
src, out = sys.argv[1], sys.argv[2]
img = Image.open(src).convert('RGB')
W = img.width
lum = img.convert('L')
contorno = lum.point(lambda v: 255 if v < 110 else 0)
mask = contorno.copy()
for c in [(0, 0), (W - 1, 0), (0, W - 1), (W - 1, W - 1)]:
    ImageDraw.floodfill(mask, c, 128, thresh=0)
# fundo = 128; o resto e o porquinho. Engorda 2px para nao perder a borda suavizada.
pig = mask.point(lambda v: 0 if v == 128 else 255).filter(ImageFilter.MaxFilter(5))
fg = img.convert('RGBA'); fg.putalpha(pig)
mono = Image.new('RGBA', img.size, (255, 255, 255, 0)); mono.putalpha(contorno.filter(ImageFilter.MaxFilter(3)))
# degradê do fundo: mesmas cores dos cantos do icone, na diagonal
a, b = img.getpixel((4, 4)), img.getpixel((W - 5, W - 5))
dens = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
import os
for d, f in dens.items():
    n = int(108 * f); inner = int(72 * f); off = (n - inner) // 2
    os.makedirs(f'{out}/mipmap-{d}', exist_ok=True)
    for nome, camada in [('foreground', fg), ('monochrome', mono)]:
        tela = Image.new('RGBA', (n, n), (0, 0, 0, 0))
        tela.paste(camada.resize((inner, inner), Image.LANCZOS), (off, off))
        tela.save(f'{out}/mipmap-{d}/ic_launcher_{nome}.png', optimize=True)
    bg = Image.new('RGB', (n, n))
    px = bg.load()
    for y in range(n):
        for x in range(n):
            t = (x + y) / (2 * (n - 1))
            px[x, y] = tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))
    bg.save(f'{out}/mipmap-{d}/ic_launcher_background.png', optimize=True)
