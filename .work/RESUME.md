# Plate Character Rendering - Status

## Current State: BITMAP RENDERING WORKING (35/35 characters)
Characters are rendered as bitmap PNGs loaded from `assets/chars/`. Stroke widths normalized via 6px erosion for pages 57-58 characters. User confirmed "decent" result.

## What Was Done

### Sessions 1-2 (Previous)
- Built extraction pipeline (extract.py) from PDF regulation Dz.U. 2024/1709
- Created PlateTextView, PlateFont (vector paths), integrated into app
- Fixed SVG→Kotlin bugs (g-transform, relative commands)
- Fixed aspect ratio normalization

### Session 3 (Current)
1. User manually cleaned all 35 character images into `manually-cleaned-chars/`
2. **Normalization pipeline** (`normalize.py`):
   - Removes stray pixels (connected component filtering)
   - Applies 6px erosion to pages 57-58 characters (lower resolution, thicker strokes)
   - Characters from page 59 (digits 3, 5, 8) left untouched (correct stroke weight)
   - Crops to character bounding box, scales to 240px body height
   - Adds 10px uniform padding, saves as RGBA with transparent background
3. **Switched from vector to bitmap rendering**:
   - Removed `PlateFont.kt` (vector path approach)
   - Rewrote `PlateTextView.kt` to load PNGs from `assets/chars/`, draw as bitmaps
   - Strips 10px padding from PNGs, draws character body scaled to view height
   - Uses mm-based spacing (4mm gap, 12mm space, 80mm char height)

## Architecture

```
assets/chars/L_A.png ... L_Z.png, D_0.png ... D_9.png  (RGBA, transparent bg)
    ↓
PlateTextView.kt  (loads bitmaps, draws scaled to view height)
    ↓
item_plate.xml  (plate background + EU stripe + PlateTextView)
    ↓
MainActivity.kt  (fetches from wptr.pl, inflates plate items)
```

## Key Files
- `.work/normalize.py` - Normalization pipeline (erosion + scaling + padding)
- `.work/manually-cleaned-chars/` - User's manually cleaned source images
- `.work/normalized-chars/` - Pipeline output (= what's in assets/chars/)
- `app/src/main/assets/chars/` - Deployed character PNGs
- `app/src/main/java/pl/waw/oledzki/wptr/PlateTextView.kt` - Bitmap renderer
- `app/src/main/java/pl/waw/oledzki/wptr/MainActivity.kt` - Main activity
- `app/src/main/res/layout/item_plate.xml` - Plate item layout

## How To Re-run Normalization
```bash
cd /home/mccartney/robie/git/numery-wptr/.work
python3 -m venv /tmp/vecenv
/tmp/vecenv/bin/pip install Pillow numpy
/tmp/vecenv/bin/python normalize.py
cp normalized-chars/*.png ../app/src/main/assets/chars/
```

## Key Decisions
- 6px erosion chosen to match stroke ratio of page-59 chars (~0.133) vs page-57/58 chars (~0.179)
- Page 59 digits (3, 5, 8) at 3.19 px/pt → correct strokes, no erosion
- Pages 57-58 at 2.77-2.89 px/pt → thicker strokes, need erosion
- Bitmap approach chosen over vector paths for visual fidelity to regulation drawings
