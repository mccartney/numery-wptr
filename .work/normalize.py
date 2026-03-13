#!/usr/bin/env python3
"""Normalize manually-cleaned character images to uniform height, padding, and stroke width."""

import os
import numpy as np
from PIL import Image, ImageFilter
from collections import deque

SRC_DIR = "manually-cleaned-chars"
DST_DIR = "normalized-chars"
TARGET_HEIGHT = 240   # pixels for the character body
PADDING = 10          # uniform padding in pixels on all sides
MIN_COMPONENT_PIXELS = 20

# Characters from page 59 (higher resolution, correct stroke width) - no erosion needed
GOOD_CHARS = {'D_3', 'D_5', 'D_8'}

# Fixed erosion for all other characters (from pages 57-58, lower resolution)
# Stroke is ~43px at ~240px body height (ratio 0.179).
# Target ~32px (ratio 0.133, matching the good chars).
# Erosion = (43-32)/2 ≈ 6px per side.
ERODE_PX = 6


def remove_stray_pixels(arr):
    """Remove small connected components from binary image (0=char, 1=bg)."""
    char_mask = (arr == 0)
    h, w = arr.shape
    labeled = np.zeros((h, w), dtype=np.int32)
    component_id = 0
    components = {}

    for y in range(h):
        for x in range(w):
            if char_mask[y, x] and labeled[y, x] == 0:
                component_id += 1
                queue = deque([(y, x)])
                pixels = []
                labeled[y, x] = component_id
                while queue:
                    cy, cx = queue.popleft()
                    pixels.append((cy, cx))
                    for dy, dx in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                        ny, nx = cy + dy, cx + dx
                        if 0 <= ny < h and 0 <= nx < w and char_mask[ny, nx] and labeled[ny, nx] == 0:
                            labeled[ny, nx] = component_id
                            queue.append((ny, nx))
                components[component_id] = pixels

    if not components:
        return arr

    largest_id = max(components, key=lambda k: len(components[k]))
    largest_size = len(components[largest_id])

    result = np.ones_like(arr)
    for cid, pixels in components.items():
        if len(pixels) >= MIN_COMPONENT_PIXELS or len(pixels) >= largest_size * 0.01:
            for y, x in pixels:
                result[y, x] = 0
    return result


def erode_char(arr, px):
    """Erode the character by px pixels. arr: 0=char, 1=bg."""
    # Convert to char=255, bg=0 for PIL MinFilter (erodes white)
    char_img = Image.fromarray(((arr == 0) * 255).astype(np.uint8), mode='L')
    for _ in range(px):
        char_img = char_img.filter(ImageFilter.MinFilter(3))
    eroded = np.array(char_img)
    # Convert back: 0=char, 1=bg
    return np.where(eroded >= 128, 0, 1).astype(np.uint8)


def process_image(src_path, dst_path, char_key):
    """Load, clean, optionally erode, crop, scale, pad, save as RGBA."""
    im = Image.open(src_path)
    arr = np.array(im)

    # Remove stray pixels
    arr = remove_stray_pixels(arr)

    # Erode if not a "good" character
    erode_applied = 0
    if char_key not in GOOD_CHARS:
        arr = erode_char(arr, ERODE_PX)
        erode_applied = ERODE_PX

    # Find bounding box
    ys, xs = np.where(arr == 0)
    if len(ys) == 0:
        print(f"  WARNING: {os.path.basename(src_path)} empty after processing!")
        return None

    y_min, y_max = ys.min(), ys.max()
    x_min, x_max = xs.min(), xs.max()
    char_h = y_max - y_min + 1
    char_w = x_max - x_min + 1

    # Crop to bounding box
    cropped = arr[y_min:y_max + 1, x_min:x_max + 1]

    # Scale to target height
    scale = TARGET_HEIGHT / char_h
    new_w = max(1, round(char_w * scale))
    new_h = TARGET_HEIGHT

    crop_img = Image.fromarray((cropped * 255).astype(np.uint8), mode='L')
    resized = crop_img.resize((new_w, new_h), Image.LANCZOS)
    resized_arr = np.array(resized)

    # Add padding
    padded_h = new_h + 2 * PADDING
    padded_w = new_w + 2 * PADDING
    result = np.ones((padded_h, padded_w), dtype=np.uint8) * 255
    result[PADDING:PADDING + new_h, PADDING:PADDING + new_w] = resized_arr

    # Save as RGBA with transparent background
    alpha = 255 - result
    rgba = np.zeros((*result.shape, 4), dtype=np.uint8)
    rgba[:, :, 3] = alpha
    Image.fromarray(rgba, 'RGBA').save(dst_path)

    return (new_w, new_h, padded_w, padded_h, erode_applied)


def main():
    os.makedirs(DST_DIR, exist_ok=True)

    files = sorted([f for f in os.listdir(SRC_DIR) if f.endswith('.png')])
    print(f"Processing {len(files)} images -> {DST_DIR}/")
    print(f"Target height: {TARGET_HEIGHT}px, padding: {PADDING}px, erosion: {ERODE_PX}px (except {GOOD_CHARS})")
    print()

    for f in files:
        src = os.path.join(SRC_DIR, f)
        parts = f.replace('.png', '').split('_')
        char_key = f"{parts[1]}_{parts[2]}"
        out_name = f"{char_key}.png"
        dst = os.path.join(DST_DIR, out_name)

        result = process_image(src, dst, char_key)
        if result:
            cw, ch, pw, ph, erode = result
            tag = "" if erode == 0 else f" (eroded {erode}px)"
            print(f"  {out_name}: char={cw}x{ch} output={pw}x{ph}{tag}")

    print(f"\nDone.")


if __name__ == '__main__':
    main()
