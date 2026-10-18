#!/bin/sh
# Render every SVG sheet in the out dir to PNG with headless Chrome. Usage: render.sh <outdir> [name]
cd "$(dirname "$0")"
OUT="$1"
for svg in "$OUT"/*.svg; do
  name=$(basename "$svg" .svg)
  [ -n "$2" ] && [ "$2" != "$name" ] && continue
  W=$(grep -o 'width="[0-9]*"' "$svg" | head -1 | grep -o '[0-9]*')
  H=$(grep -o 'height="[0-9]*"' "$svg" | head -1 | grep -o '[0-9]*')
  WIN=$(cygpath -w "$(pwd)/$OUT/$name.png")
  URL="file:///$(cygpath -m "$(pwd)/$svg")"
  "/c/Program Files/Google/Chrome/Application/chrome.exe" --headless=new --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=1 --window-size=$W,$H --screenshot="$WIN" "$URL" 2>/dev/null
  echo "rendered $name ${W}x${H}"
done
