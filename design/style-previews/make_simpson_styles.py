import json
import os

src = os.path.join(os.environ["LOCALAPPDATA"], "Temp", "opencode", "styles-pixar-v2.json")
data = json.load(open(src, encoding="utf-8"))

prompt = (
    "Redraw this exact photo-booth portrait in the flat 2D cartoon style of a 1990s "
    "Matt Groening TV show like The Simpsons, not 3D and not anime. Keep the same people, "
    "recognizable face shape, hairstyle, pose, clothing colors and vertical framing. "
    "Give the character bright flat yellow skin, big round white eyes with tiny black dot "
    "pupils, no eyebrows, a pronounced overbite muzzle, a small rounded nose tip, and thick "
    "rounded tube-like limbs. Use bold uniform black outlines with constant weight and flat "
    "unshaded color fills, with no gradients, no soft shading and no photo texture. "
    "The background, only where no other backdrop was requested, is a flat pastel suburban "
    "scene made of simple shapes. The person must stay recognizable and must not be redrawn "
    "as an existing character. No new people. Do not add text, logos, watermarks or frames."
)

data = [s for s in data if s["id"] != "simpson"]
data.append({"id": "simpson", "name": "Simpsons", "prompt": prompt})

out = os.path.join(os.environ["LOCALAPPDATA"], "Temp", "opencode", "styles-simpson.json")
with open(out, "w", encoding="utf-8") as handle:
    json.dump(data, handle, ensure_ascii=False, indent=2)
print("wrote", out)
