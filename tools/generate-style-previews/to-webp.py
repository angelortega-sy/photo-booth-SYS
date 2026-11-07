from pathlib import Path
import sys
from PIL import Image

src = Path("out/style-previews-new")
dst = Path("app/src/main/assets/style_previews")
ids = sys.argv[1:] or [p.stem for p in sorted(src.glob("*.png"))]

for style_id in ids:
    source = src / f"{style_id}.png"
    if not source.exists():
        print(f"skip {style_id}: missing png")
        continue
    image = Image.open(source).convert("RGB")
    w, h = image.size
    target_ratio = 270 / 480
    ratio = w / h
    if ratio > target_ratio:
        new_w = int(h * target_ratio)
        left = (w - new_w) // 2
        image = image.crop((left, 0, left + new_w, h))
    elif ratio < target_ratio:
        new_h = int(w / target_ratio)
        top = (h - new_h) // 2
        image = image.crop((0, top, w, top + new_h))
    image = image.resize((270, 480), Image.LANCZOS)
    out = dst / f"{style_id}.webp"
    image.save(out, "WEBP", quality=82, method=6)
    print(f"{out.name}: {out.stat().st_size // 1024} KB")
