"""Пиксельный логотип Woody. Превью: python -I pixel_logo.py (пишет pixel.html рядом). Ресурсы: python -I pixel_logo.py android <путь к app/src/main/res>. Используется карта "h"."""
import sys
from pathlib import Path

MAPS = {
    "g": """
...R....R...............
...RR...RR..R...........
...RRR..RRRRR...........
....RRRRRRRRR...........
...RRRRRRRRRRRR.........
..RRRRRRRRRRRRRR........
..RRRRRRWWWWWRRRR.......
.RRRRRRWWWWBBBWRRR......
.RRRRRRWWWWBWBWRRR......
.RRRRRRWWWWBBBWRRYYYYY..
.RRRRRRRWWWWWWRRYYYYYYYY
.RRRRRRRRRRRRRRYYYYYYY..
..RRRRRRRRRRRRWWW.......
..RRRRRRRRRRWWWWW.......
...RRRRRRRRWWWWW........
....RRRRRRWWWWW.........
...BBBBRRRWWWW..........
..BBBBBBBWWWWB..........
.BBBBBBBBBWWBB..........
BBBBBBBBBBBBBB..........
""",
    "h": """
..R.....R...............
..RR....RR...R..........
..RRR...RRR.RR..........
...RRRRRRRRRRR..........
...RRRRRRRRRRRRR........
..RRRRRRRRRRRRRRR.......
..RRRRRRWWWWWWRRRR......
.RRRRRRWWWWWBBBWRRR.....
.RRRRRRWWWWWBWBWRRR.....
.RRRRRRWWWWWBBBWRRRYYYY.
.RRRRRRRWWWWWWWRRYYYYYYY
.RRRRRRRRRRRRRRRYYYYYY..
..RRRRRRRRRRRRRWWW......
..RRRRRRRRRRRWWWWW......
...RRRRRRRRRWWWWW.......
....RRRRRRRWWWWW........
...BBBBRRRRWWWW.........
..BBBBBBBBWWWWB.........
.BBBBBBBBBBWWBB.........
BBBBBBBBBBBBBBB.........
""",
}

COLORS = {"R": "#E63946", "B": "#2F5DA8", "W": "#FFF6E5", "Y": "#FFB703"}
ORDER = "RBWY"


def parse(text):
    rows = [r for r in text.strip("\n").splitlines()]
    n = max(len(rows), max(len(r) for r in rows))
    return rows, n


def fmt(v):
    return ("%.3f" % v).rstrip("0").rstrip(".")


def paths(text, size=62.0, ox=None, oy=None, bleed=True):
    rows, n = parse(text)
    cell = size / n
    # Центр головы (ячейка ~11.5, ~11) — в центр иконки.
    ox = 54 - 12.0 * cell if ox is None else ox
    oy = 54 - 10.0 * cell if oy is None else oy
    out = {}
    for j, row in enumerate(rows):
        row = row.ljust(n, ".")
        i = 0
        while i < n:
            c = row[i]
            if c not in COLORS:
                i += 1
                continue
            k = i
            while k < n and row[k] == c:
                k += 1
            h = cell + 0.08  # лёгкое перекрытие рядов — без щелей при сглаживании
            # Последняя строка продолжается до низа иконки — «бюст» уходит за край.
            if bleed and j == len(rows) - 1:
                h = 108 - (oy + j * cell)
            out.setdefault(c, []).append(
                f"M{fmt(ox + i * cell)},{fmt(oy + j * cell)}h{fmt((k - i) * cell)}v{fmt(h)}h-{fmt((k - i) * cell)}z"
            )
            i = k
    return [(c, "".join(out[c])) for c in ORDER if c in out]


if __name__ == "__main__" and len(sys.argv) == 1:
    here = Path(__file__).parent
    cells = []
    for name, text in MAPS.items():
        body = "".join(f'<path fill="{COLORS[c]}" d="{d}"/>' for c, d in paths(text))
        svg = f'<svg viewBox="18 18 72 72" width="100%" height="100%" shape-rendering="crispEdges"><rect width="108" height="108" fill="#FFF6E5"/>{body}</svg>'
        svg_dark = svg.replace('fill="#FFF6E5"/>', 'fill="#BFD7FF"/>', 1)
        cells.append(
            f'<div class="c"><div class="mask">{svg}</div><div class="sq">{svg_dark}</div><div class="small">{svg}</div>'
            f'<div class="dark"><div class="small">{svg}</div></div>{name}</div>'
        )
    html = """<!doctype html><html><head><meta charset="utf-8"><title>Pixel logo</title><style>
body{margin:0;padding:16px;background:#ddd;font-family:sans-serif;display:flex;gap:18px;flex-wrap:wrap}
.c{display:flex;flex-direction:column;align-items:center;gap:8px}
.mask{width:200px;height:200px;border-radius:50%;overflow:hidden}
.sq{width:120px;height:120px;border-radius:28px;overflow:hidden}
.small{width:48px;height:48px;border-radius:50%;overflow:hidden}
.dark{background:#0d1220;padding:10px;border-radius:12px}
</style></head><body>""" + "".join(cells) + "</body></html>"
    (here / "pixel.html").write_text(html, encoding="utf-8")


def write_android(res_dir, name="h"):
    res = Path(res_dir)
    ns = 'xmlns:android="http://schemas.android.com/apk/res/android"'
    text = MAPS[name]

    def vec(w, vw, vh, body, extra=""):
        return (f'<?xml version="1.0" encoding="utf-8"?>\n<vector {ns}\n    android:width="{w}dp"\n    android:height="{w}dp"\n'
                f'    android:viewportWidth="{vw}"\n    android:viewportHeight="{vh}">\n{body}</vector>\n')

    def path(color, d, indent="    "):
        return f'{indent}<path android:fillColor="{color}" android:pathData="{d}" />\n'

    full = paths(text)
    solid = [(c, d) for c, d in full if c != "W"]
    color = "".join(path("#FF" + COLORS[c][1:], d) for c, d in full)
    mono = "".join(path("#FF000000", d) for c, d in solid)
    (res / "drawable/ic_launcher_foreground.xml").write_text(vec(108, 108, 108, color), encoding="utf-8")
    (res / "drawable/ic_launcher_monochrome.xml").write_text(vec(108, 108, 108, mono), encoding="utf-8")

    circle = "M18,54 a36,36 0 1,0 72,0 a36,36 0 1,0 -72,0 Z"
    mark = (path("#FFFFF6E5", circle) + f'    <group>\n        <clip-path android:pathData="{circle}" />\n'
            + "".join(path("#FF" + COLORS[c][1:], d, "        ") for c, d in full) + "    </group>\n")
    mark = f'    <group android:translateX="-18" android:translateY="-18">\n' + "".join("    " + l for l in mark.splitlines(True)) + "    </group>\n"
    (res / "drawable/woody_mark.xml").write_text(vec(108, 72, 72, mark), encoding="utf-8")

    stat_paths = [(c, d) for c, d in paths(text, bleed=False) if c != "W"]
    stat = "".join(path("#FFFFFFFF", d, "        ") for c, d in stat_paths)
    stat = f'    <group android:translateX="-20" android:translateY="-20">\n{stat}    </group>\n'
    (res / "drawable/ic_stat_woody.xml").write_text(vec(24, 68, 68, stat), encoding="utf-8")


if __name__ == "__main__" and len(sys.argv) > 1 and sys.argv[1] == "android":
    write_android(sys.argv[2])
    print("written")
