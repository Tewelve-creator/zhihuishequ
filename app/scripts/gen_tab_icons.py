"""Generate Hope Cloud style tab bar icons (transparent bg)."""
from PIL import Image, ImageDraw
import math
import os

OUT = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', 'src', 'static'))
SIZE = 162
GRAY = (153, 153, 153, 255)   # #999999
GREEN = (0, 179, 107, 255)    # #00b36b


def blank():
    return Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))


def save(img, name):
    img = img.resize((81, 81), Image.Resampling.LANCZOS)
    path = os.path.join(OUT, name)
    img.save(path, 'PNG')
    print('saved', path)


def thick_line(draw, a, b, color, w):
    draw.line([a, b], fill=color, width=w)


def draw_house(draw, color, fill=False, w=9):
    """House with inner horizontal dash — Hope Cloud style."""
    # Roof peak + body
    roof = [(81, 30), (130, 72), (118, 72), (118, 134), (44, 134), (44, 72), (32, 72)]
    if fill:
        draw.polygon(roof, fill=color)
        # white dash inside
        draw.rounded_rectangle([58, 90, 104, 102], radius=4, fill=(255, 255, 255, 255))
    else:
        # roof
        thick_line(draw, (81, 34), (128, 74), color, w)
        thick_line(draw, (81, 34), (34, 74), color, w)
        # body
        thick_line(draw, (44, 70), (44, 132), color, w)
        thick_line(draw, (118, 70), (118, 132), color, w)
        thick_line(draw, (44, 132), (118, 132), color, w)
        thick_line(draw, (44, 70), (118, 70), color, w)
        # inner dash
        draw.rounded_rectangle([58, 90, 104, 102], radius=4, fill=color)


def draw_mall(draw, color, fill=False, w=9):
    """Shop with scalloped awning."""
    if fill:
        draw.rounded_rectangle([34, 74, 128, 136], radius=6, fill=color)
        draw.rectangle([48, 94, 70, 122], fill=(255, 255, 255, 255))
        draw.rectangle([92, 94, 114, 122], fill=(255, 255, 255, 255))
        awn = [(34, 74)]
        for x in range(34, 129):
            t = (x - 34) / 94 * 4 * math.pi
            y = 48 + abs(math.sin(t)) * 18
            awn.append((x, int(y)))
        awn.append((128, 74))
        draw.polygon(awn, fill=color)
    else:
        draw.rounded_rectangle([34, 76, 128, 136], radius=4, outline=color, width=w)
        draw.rectangle([50, 96, 72, 124], outline=color, width=max(w - 2, 5))
        draw.rectangle([90, 96, 112, 124], outline=color, width=max(w - 2, 5))
        pts = []
        for x in range(34, 129):
            t = (x - 34) / 94.0 * 4 * math.pi
            y = 46 + abs(math.sin(t)) * 18
            pts.append((x, int(y)))
        for i in range(len(pts) - 1):
            draw.line([pts[i], pts[i + 1]], fill=color, width=w)
        thick_line(draw, (34, 46), (34, 76), color, w)
        thick_line(draw, (128, 46), (128, 76), color, w)
        thick_line(draw, (34, 76), (128, 76), color, max(w - 1, 5))


def draw_butler(draw, color, fill=False, w=9):
    """Tuxedo + bowtie."""
    if fill:
        draw.polygon([(32, 148), (40, 76), (81, 96), (122, 76), (130, 148)], fill=color)
        draw.polygon([(81, 88), (68, 148), (94, 148)], fill=(255, 255, 255, 255))
        draw.polygon([(46, 78), (72, 88), (46, 98)], fill=(255, 255, 255, 255))
        draw.polygon([(116, 78), (90, 88), (116, 98)], fill=(255, 255, 255, 255))
        draw.ellipse([72, 80, 90, 96], fill=(255, 255, 255, 255))
        for by in (112, 126, 140):
            draw.ellipse([76, by, 86, by + 10], fill=(255, 255, 255, 255))
    else:
        thick_line(draw, (40, 78), (32, 146), color, w)
        thick_line(draw, (122, 78), (130, 146), color, w)
        thick_line(draw, (32, 146), (130, 146), color, w)
        thick_line(draw, (40, 78), (81, 94), color, w)
        thick_line(draw, (122, 78), (81, 94), color, w)
        thick_line(draw, (81, 94), (58, 142), color, w)
        thick_line(draw, (81, 94), (104, 142), color, w)
        # bowtie
        draw.polygon([(48, 76), (72, 88), (48, 100)], outline=color)
        thick_line(draw, (48, 76), (72, 88), color, w - 1)
        thick_line(draw, (72, 88), (48, 100), color, w - 1)
        thick_line(draw, (48, 76), (48, 100), color, w - 1)
        thick_line(draw, (114, 76), (90, 88), color, w - 1)
        thick_line(draw, (90, 88), (114, 100), color, w - 1)
        thick_line(draw, (114, 76), (114, 100), color, w - 1)
        draw.ellipse([72, 80, 90, 96], outline=color, width=w - 1)
        for by in (112, 126, 140):
            draw.ellipse([76, by, 86, by + 10], fill=color)


def draw_mine(draw, color, fill=False, w=9):
    """Person silhouette; active has smile curve on head."""
    if fill:
        draw.ellipse([50, 20, 112, 82], fill=color)
        # shoulders blob
        draw.ellipse([20, 98, 142, 172], fill=color)
        # smile (white)
        draw.arc([64, 44, 98, 74], 15, 165, fill=(255, 255, 255, 255), width=6)
    else:
        draw.ellipse([50, 22, 112, 84], outline=color, width=w)
        # open shoulder arc
        draw.arc([24, 92, 138, 168], 200, 340, fill=color, width=w)


def main():
    os.makedirs(OUT, exist_ok=True)
    # New filenames to bust H5/static cache
    jobs = [
        ('tab-xiwang.png', draw_house, False, GRAY),
        ('tab-xiwang-active.png', draw_house, True, GREEN),
        ('tab-mall.png', draw_mall, False, GRAY),
        ('tab-mall-active.png', draw_mall, True, GREEN),
        ('tab-butler.png', draw_butler, False, GRAY),
        ('tab-butler-active.png', draw_butler, True, GREEN),
        ('tab-wode.png', draw_mine, False, GRAY),
        ('tab-wode-active.png', draw_mine, True, GREEN),
    ]
    for name, drawer, fill, color in jobs:
        img = blank()
        drawer(ImageDraw.Draw(img), color, fill=fill)
        save(img, name)
    print('done ->', OUT)


if __name__ == '__main__':
    main()
