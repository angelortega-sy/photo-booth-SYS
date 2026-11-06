"""Writes a temp styles.json with the hardened Simpsons prompt (v2)."""

import json
import os

src = os.path.join(os.environ["LOCALAPPDATA"], "Temp", "opencode", "styles-simpson.json")
data = json.load(open(src, encoding="utf-8"))

PROMPT = (
    "Turn this exact photo into a still from a 1990s Matt Groening animated sitcom like "
    "The Simpsons. Keep the person recognizable: the same face shape, hairline and "
    "hairstyle, the same expression, the same head angle, the same pose, the same clothing "
    "colors and the same vertical framing as the original photo. Redraw everything as flat "
    "2D cel animation, a full redraw and not a filter over the photo. Skin is one single "
    "flat yellow with no shading, no blush, no stubble, no wrinkles and no photographic "
    "texture. The eyes are two large white ovals with small black dot pupils that both look "
    "at the camera, with no iris detail and no eyelids. No eyebrows of any kind. The mouth "
    "sits on a round protruding overbite muzzle like the show's characters, and the nose is "
    "a small rounded oval. Hair is a single flat color block with a simple silhouette and no "
    "individual strands. Clothing is flat color with only one darker fold per shape. Every "
    "shape has a bold black outline of the same uniform weight with rounded ends. Lighting is "
    "completely flat: no gradients, no soft shadows, no highlights, no rim light, no depth of "
    "field. Use the show's cartoon anatomy: a large head on a small body, tube-shaped limbs "
    "and four fingers. The background is a plain flat pastel color or a few simple flat "
    "shapes suggesting a room, always empty and uncluttered, with no other people. The person "
    "must stay recognizable and must not become an existing character. Do not add text, "
    "logos, signatures, frames or borders. Not 3D, not anime, not photoreal."
)

data = [s for s in data if s["id"] != "simpson"]
data.append({"id": "simpson", "name": "Simpsons", "prompt": PROMPT})

out = os.path.join(os.environ["LOCALAPPDATA"], "Temp", "opencode", "styles-simpson-v2.json")
with open(out, "w", encoding="utf-8") as handle:
    json.dump(data, handle, ensure_ascii=False, indent=2)
print("wrote", out)
