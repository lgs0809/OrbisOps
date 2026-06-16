#!/usr/bin/env python3
"""Build the README demo GIF from deterministic Playwright capture frames.

This is a documentation-only helper. Product build/test/runtime does not depend on Python or Pillow.
"""

from pathlib import Path
import sys

try:
    from PIL import Image
except ImportError:
    print("Pillow is required only to build docs/assets/orbisops-demo.gif. Install it with: python3 -m pip install Pillow", file=sys.stderr)
    sys.exit(2)

ASSETS = Path(__file__).resolve().parents[2] / "docs" / "assets"
FRAMES = [ASSETS / f"orbisops-demo-{index:02d}.png" for index in range(1, 5)]
OUTPUT = ASSETS / "orbisops-demo.gif"
TARGET_WIDTH = 960

missing = [str(path) for path in FRAMES if not path.exists()]
if missing:
    print("Missing README capture frames:\n" + "\n".join(missing), file=sys.stderr)
    sys.exit(3)

images = []
for path in FRAMES:
    image = Image.open(path).convert("RGB")
    if image.width != TARGET_WIDTH:
        height = round(image.height * TARGET_WIDTH / image.width)
        image = image.resize((TARGET_WIDTH, height), Image.Resampling.LANCZOS)
    images.append(image.convert("P", palette=Image.Palette.ADAPTIVE, colors=128))

images[0].save(
    OUTPUT,
    save_all=True,
    append_images=images[1:],
    duration=[1400, 1100, 1300, 1600],
    loop=0,
    optimize=True,
    disposal=2,
)

for path in FRAMES:
    path.unlink(missing_ok=True)

print(f"Generated {OUTPUT.relative_to(ASSETS.parents[1])} ({OUTPUT.stat().st_size} bytes)")
