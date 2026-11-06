import io
import os
import urllib.request

from PIL import Image

OUT = os.path.join("design", "style-previews")
os.makedirs(OUT, exist_ok=True)

CANDIDATES = {
    "a": "photo-1507003211169-0a1dd7228f2d",
    "b": "photo-1506794778202-cad84cf45f1d",
    "c": "photo-1531427186611-ecfd6d936c79",
    "d": "photo-1519345182560-3f2917c472ef",
    "e": "photo-1568602471122-7832951cc4c5",
    "f": "photo-1500648767791-00dcc994a43e",
}

BASE = "https://images.unsplash.com/"

for key, pid in CANDIDATES.items():
    url = f"{BASE}{pid}?w=540&h=960&fit=crop&crop=faces&q=80"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = resp.read()
        img = Image.open(io.BytesIO(data)).convert("RGB")
        img.save(os.path.join(OUT, f"candidate-{key}.jpg"), quality=90)
        print(key, pid, img.size, len(data) // 1024, "KB")
    except Exception as exc:  # noqa: BLE001
        print(key, pid, "FAILED", exc)
