# Plate Character Extraction - Status

## Current State: AWAITING USER TEST (35/35 characters)
PlateFont.kt has been regenerated with aspect ratio fixes and deployed to the app. User needs to rebuild and test.

## What To Do Next

### If user says characters look good
- Done! Commit the changes.

### If user reports sizing/shape issues still
The aspect ratio normalization and `--tight` removal should have fixed the "different sizes, different shapes" problem. If it persists:
1. Check the clean bitmaps in `.work/clean-chars/` - are they the right shapes?
2. Check the SVG viewBox ratios (`grep viewBox /tmp/plate-chars/*.svg`) - they should all be ~0.5875 for letters, ~0.5375 for digits
3. Check the generated Kotlin paths - are the coordinates in a reasonable 0..47 / 0..80 range?

### If user reports specific characters look wrong
- Check that character's clean bitmap in `.work/clean-chars/clean_L_X.png` or `clean_D_X.png`
- If the bitmap looks wrong, the flood-fill step failed - may need to add that character to the programmatic drawing list (like G, W, A)
- If the bitmap looks right but the rendered character doesn't, there's a bug in the SVG→Kotlin pipeline

### If user reports annotation artifacts (lines in holes of B, O, S, K, etc.)
Known issue from previous runs. Options:
1. Add these characters to the programmatic drawing list in `draw_char_programmatic()`
2. Add morphological cleanup specifically targeting thin lines in holes
3. Accept as cosmetic (they're small at render scale)

### If K's lower diagonal is still outline-only
K has a secondary filled region that isn't captured by the largest-component algorithm. Fix: modify `fill_outlines()` to merge the two largest components for K, or draw K programmatically.

## What Was Done (Chronological)

### Session 1
1. Built extraction pipeline (`extract.py`) - PDF image extraction, flood-fill, potrace vectorization, SVG→Kotlin conversion
2. Created `PlateTextView.kt`, updated `MainActivity.kt`, `item_plate.xml`
3. Drew G, W, A programmatically (annotation-heavy characters that defeat flood-fill)

### Session 2 - User reported "none of the letters is legible"
Found TWO critical bugs in SVG→Kotlin pipeline:
1. **`<g transform>` from potrace SVG was ignored** - potrace wraps paths in `<g transform="translate(0,H) scale(0.1,-0.1)">`. Raw coordinates (10x too large, Y-inverted) were applied directly. Fixed by parsing g-transform in `parse_svg()` and incorporating into combined transform in `svg_to_result()`.
2. **Relative SVG commands treated as absolute** - potrace outputs relative `l`, `c` commands. `l -23 -11` was converted to `lineTo(-23, -11)` instead of accumulating offsets. Fixed by adding `to_absolute()` function.

### Session 2 continued - User reported "different sizes, different shapes"
After SVG bugs were fixed, characters were readable but had inconsistent sizing because:
- Source bitmaps have varying amounts of annotation extending the bounding box
- Potrace `--tight` flag recomputed viewBox, undoing any pre-potrace normalization
Fixed by:
1. Adding aspect ratio normalization in `process_char()` - center-crop to expected 47:80 or 43:80 ratio (±5% tolerance) before potrace
2. Removing `--tight` flag from potrace so viewBox matches the normalized bitmap

## Pipeline Overview

```
PDF (D20241709.pdf)
  → get_page_images_sorted()     # PyMuPDF, xref-based bbox mapping
  → fill_outlines()              # MinFilter(3), flood fill from (0,0), largest component
    OR draw_char_programmatic()  # For G, W, A only
  → potrace (bitmap → SVG)      # NO --tight flag
  → parse_svg()                  # Extract viewBox + g-transform
  → to_absolute()               # Convert relative SVG commands to absolute
  → svg_to_result()             # Combined transform: g-transform × viewBox→target scaling
  → cmds_to_kotlin()            # Generate Path().apply { moveTo(); lineTo(); cubicTo(); close() }
  → PlateFont.kt                # All 35 characters assembled
```

### Key functions in extract.py

- `get_page_images_sorted(page)` - Extracts images using `get_image_info(xrefs=True)` for correct bbox↔xref mapping. Y-clusters with gap=40 for reading order.
- `fill_outlines(img)` - Standard algorithm: MinFilter(3), flood fill from (0,0), largest white region = body, small regions = artifacts (filled), larger non-largest = holes (preserved). Post: `keep_largest_component` + `clean_hole_lines`.
- `draw_char_programmatic(ch, scale)` - Draws G, W, A using cv2 at 10px/mm (470×800px). Returns PIL Image.
- `process_char(img, target_w, target_h, ch)` - Orchestrates: fill → aspect ratio crop → potrace → parse SVG → transform → Kotlin.
- `parse_svg(path)` - Extracts viewBox (vw, vh) AND `<g transform="translate(tx,ty) scale(sx,sy)">` from potrace SVG.
- `to_absolute(cmds)` - Converts relative SVG commands (m,l,c,h,v) to absolute (M,L,C) tracking current position.
- `svg_to_result(svg_path, target_w, target_h)` - Combined transform: `sx = g_sx * target_w / vw`, applies to all coordinates.
- `cmds_to_kotlin(cmds)` - Generates Kotlin Path code from absolute M,L,C,Z commands.

### Critical lessons learned (DO NOT repeat these mistakes)
1. **Always parse `<g transform>` from potrace SVG** - potrace uses internal coords that are 10x scaled and Y-inverted
2. **Always convert relative SVG commands to absolute** before applying coordinate transforms
3. **NO margin cropping** in flood-fill - it breaks characters whose strokes touch the image edge
4. **MinFilter(3)** not MinFilter(5) - larger filter erodes thin features
5. **Largest region** selection, not center-weighted scoring
6. **NO `--tight` flag** on potrace - it recomputes viewBox and defeats pre-potrace normalization
7. **`get_image_info(xrefs=True)`** for correct PDF image→position mapping, NOT sequential matching of `get_images()` with text blocks

## File Inventory
- `.work/extract.py` - Extraction pipeline (working version)
- `.work/D20241709.pdf` - Source regulation PDF (Dz.U. 2024 poz. 1709, pp. 57-59)
- `.work/raw-chars/` - Raw character images extracted from PDF
- `.work/clean-chars/` - Cleaned/filled character bitmaps (current run)
- `/tmp/plate-chars/PlateFont.kt` - Generated Kotlin (also copied to app)
- `/tmp/plate-chars/*.svg` - Generated SVGs (intermediate)
- `app/src/main/java/pl/waw/oledzki/wptr/PlateFont.kt` - DEPLOYED to app
- `app/src/main/java/pl/waw/oledzki/wptr/PlateTextView.kt` - Custom view (DONE)
- `app/src/main/java/pl/waw/oledzki/wptr/MainActivity.kt` - Updated (DONE)
- `app/src/main/res/layout/item_plate.xml` - Updated (DONE)

## How To Re-run The Pipeline
```bash
# Set up venv (if /tmp/vecenv doesn't exist)
python3 -m venv /tmp/vecenv
/tmp/vecenv/bin/pip install PyMuPDF Pillow numpy opencv-python-headless

# System dependency
sudo apt install potrace

# Run from .work/ directory
cd /home/mccartney/robie/git/numery-wptr/.work
/tmp/vecenv/bin/python extract.py

# Deploy to app
cp /tmp/plate-chars/PlateFont.kt ../app/src/main/java/pl/waw/oledzki/wptr/PlateFont.kt
```

## Character Dimensions (from regulation)
- Letters (ABCDEFGHIJKLMNOPRSTUVWXYZ - 25 chars, no Q): 47×80mm, stroke 9mm
- Digits (0-9): 43×80mm, stroke 9mm
- These are "samochodowe" (car) plate dimensions
