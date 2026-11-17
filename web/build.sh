#!/usr/bin/env bash
# Builds the browser version into web/:
#   web/dnd_engine.jar     the app, compiled for Java 11 (run in the browser by CheerpJ)
#   web/weapons.json       bundled rules data the app reads at startup
#   web/spells.json
#   web/search-index.json  rules/spells/feats/conditions/items for the site's search bar
#
# Usage (from anywhere):  bash web/build.sh
# Preview locally:        cd web && python3 -m http.server 8000   then open http://localhost:8000/
# (CheerpJ needs a web server; opening index.html as a file:// URL won't work.)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLASSES="$ROOT/build/web-classes"
WEB="$ROOT/web"

rm -rf "$CLASSES"
mkdir -p "$CLASSES"

echo "Compiling src/ for Java 11..."
javac --release 11 -encoding UTF-8 -d "$CLASSES" "$ROOT"/src/*.java

echo "Packaging web/dnd_engine.jar..."
jar --create --file "$WEB/dnd_engine.jar" --main-class App -C "$CLASSES" .

echo "Copying data files..."
cp "$ROOT/weapons.json" "$ROOT/spells.json" "$WEB/"
if [ -d "$ROOT/art" ]; then
  rm -rf "$WEB/art"
  cp -R "$ROOT/art" "$WEB/art"
fi

echo "Exporting search index..."
(cd "$ROOT" && java -Djava.awt.headless=true -cp "$CLASSES" SearchIndexExport "$WEB/search-index.json")

echo "Done. Output is in web/"
