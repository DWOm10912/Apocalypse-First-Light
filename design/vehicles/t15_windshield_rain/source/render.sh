#!/bin/sh
# Renders concept.html (A-D) to ../sheets/*.png with headless Chrome, 1600 x 900.
D=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$D/../sheets"
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
URL="file:///$(cygpath -m "$D/concept.html")"
for s in A B C D; do
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --window-size=1600,900 \
    --virtual-time-budget=8000 --screenshot="$(cygpath -w "$D/../sheets/rain_$s.png")" "$URL?s=$s" 2>/dev/null
done
ls -la "$D/../sheets"
