#!/usr/bin/env python3
"""Split the generated 4x4 sheet into normalized, white alpha-masked icons.
Requires Pillow. The drawings come entirely from design/keyboard-icons/atlas.png.
"""
from pathlib import Path
from PIL import Image, ImageOps

root = Path(__file__).resolve().parents[1]
atlas = Image.open(root / "design/keyboard-icons/atlas.png").convert("L")
output = root / "app/src/main/res/drawable-nodpi"
output.mkdir(parents=True, exist_ok=True)
names = ["enter", "space", "backspace", "close", "left", "up", "down", "right",
         "home", "end", "page_up", "page_down", "hide", "mic", "ctrl_j", "ctrl_k"]
for index, name in enumerate(names):
    x, y = index % 4, index // 4
    tile = atlas.crop((round(x * atlas.width / 4), round(y * atlas.height / 4),
                       round((x + 1) * atlas.width / 4), round((y + 1) * atlas.height / 4)))
    alpha = ImageOps.invert(tile).point(lambda p: 0 if p < 40 else min(255, (p - 40) * 255 // 200))
    box = alpha.point(lambda p: 255 if p >= 128 else 0).getbbox()
    assert box, name
    alpha = alpha.crop(box)
    alpha.thumbnail((88, 88), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (96, 96), (255, 255, 255, 0))
    glyph = Image.new("RGBA", alpha.size, "white")
    glyph.putalpha(alpha)
    canvas.paste(glyph, ((96 - alpha.width) // 2, (96 - alpha.height) // 2))
    canvas.save(output / f"key_{name}.png", optimize=True)
print(f"Split {len(names)} icons into {output}")
