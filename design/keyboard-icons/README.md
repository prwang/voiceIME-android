# Keyboard icon assets

Generated with the built-in image generation tool, then split into 16 icons.
The final generation prompt is in `prompt.txt`; the source sheet is `atlas.png`.
The earlier transparent draft was rejected for visual artifacts.

Run `python3 tools/split_keyboard_icons.py` from the repository root (Pillow
required) to reproduce the PNGs in `app/src/main/res/drawable-nodpi/`.
Splitting trims each cell, converts the black drawing to a white alpha mask,
and normalizes it to a 96px canvas. No icons are redrawn by the script.

All 16 assets are used across the keyboard and Hold control. Symbols and ET
use single-line native text.
