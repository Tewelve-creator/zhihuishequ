"""Extract tab icons from Hope Cloud reference — center-of-mass crop."""
from PIL import Image
import os

REF = os.path.join(os.path.dirname(__file__), 'ref_tabbar.png')
OUT = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', 'src', 'static'))
SIZE = 81
GRAY = (153, 153, 153, 255)
GREEN = (0, 179, 107, 255)


def is_bg(r, g, b, thresh=248):
    return r >= thresh and g >= thresh and b >= thresh


def centroid(im, x0, y0, x1, y1, thresh=248):
    px = im.load()
    sx = sy = n = 0
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b = px[x, y][:3]
            if not is_bg(r, g, b, thresh):
                sx += x
                sy += y
                n += 1
    if not n:
        return (x0 + x1) // 2, (y0 + y1) // 2
    return sx // n, sy // n


def to_rgba_transparent(rgb_im, thresh=248):
    im = rgb_im.convert('RGBA')
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if is_bg(r, g, b, thresh):
                px[x, y] = (0, 0, 0, 0)
            elif r > 200 and g > 200 and b > 200 and a:
                # near-white antialias on edges -> soften
                px[x, y] = (r, g, b, max(0, 255 - (r + g + b) // 3 + 40))
    return im


def fit_square(im, size=SIZE, pad_ratio=0.2):
    bbox = im.getbbox()
    if bbox:
        im = im.crop(bbox)
    w, h = im.size
    side = max(w, h)
    pad = max(2, int(side * pad_ratio))
    canvas = Image.new('RGBA', (side + pad * 2, side + pad * 2), (0, 0, 0, 0))
    canvas.paste(im, ((canvas.size[0] - w) // 2, (canvas.size[1] - h) // 2), im)
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


def recolor(im, rgba):
    im = im.convert('RGBA')
    px = im.load()
    tr, tg, tb, _ = rgba
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 16:
                px[x, y] = (0, 0, 0, 0)
            else:
                px[x, y] = (tr, tg, tb, a)
    return im


def save(im, name):
    path = os.path.join(OUT, name)
    im.save(path, 'PNG')
    print('saved', name)


def extract_icon(ref, index, half_w=20, half_h=18, cols=4):
    w, h = ref.size
    cw = w // cols
    # search band for centroid (icon only, above label)
    sx0 = index * cw + int(cw * 0.15)
    sx1 = (index + 1) * cw - int(cw * 0.15)
    sy0 = 2
    sy1 = int(h * 0.50)
    cx, cy = centroid(ref, sx0, sy0, sx1, sy1)
    # fixed window around centroid — same size for all tabs
    x0 = max(0, cx - half_w)
    x1 = min(w, cx + half_w)
    y0 = max(0, cy - half_h)
    y1 = min(h, cy + half_h)
    print('cell', index, 'center', (cx, cy), 'crop', (x0, y0, x1, y1))
    crop = ref.crop((x0, y0, x1, y1))
    return fit_square(to_rgba_transparent(crop))


def main():
    ref = Image.open(REF).convert('RGB')
    # mine is a bit larger filled icon
    house = extract_icon(ref, 0, half_w=18, half_h=17)
    mall = extract_icon(ref, 1, half_w=20, half_h=17)
    butler = extract_icon(ref, 2, half_w=20, half_h=17)
    mine = extract_icon(ref, 3, half_w=20, half_h=20)

    save(recolor(house, GRAY), 'tab-xiwang-v2.png')
    save(recolor(mall, GRAY), 'tab-mall-v2.png')
    save(recolor(butler, GRAY), 'tab-butler-v2.png')
    save(recolor(mine, GRAY), 'tab-wode-v2.png')

    save(recolor(house, GREEN), 'tab-xiwang-v2-active.png')
    save(recolor(mall, GREEN), 'tab-mall-v2-active.png')
    save(recolor(butler, GREEN), 'tab-butler-v2-active.png')
    save(mine, 'tab-wode-v2-active.png')

    pairs = [
        ('tab-xiwang-v2.png', 'tab-xiwang.png', 'tab-home.png'),
        ('tab-xiwang-v2-active.png', 'tab-xiwang-active.png', 'tab-home-active.png'),
        ('tab-mall-v2.png', 'tab-mall.png'),
        ('tab-mall-v2-active.png', 'tab-mall-active.png'),
        ('tab-butler-v2.png', 'tab-butler.png'),
        ('tab-butler-v2-active.png', 'tab-butler-active.png'),
        ('tab-wode-v2.png', 'tab-wode.png', 'tab-mine.png'),
        ('tab-wode-v2-active.png', 'tab-wode-active.png', 'tab-mine-active.png'),
    ]
    for src, *aliases in pairs:
        im = Image.open(os.path.join(OUT, src))
        for a in aliases:
            im.save(os.path.join(OUT, a))
    print('done')


if __name__ == '__main__':
    main()
