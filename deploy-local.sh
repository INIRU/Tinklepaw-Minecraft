#!/bin/bash
# Usage: ./deploy-local.sh /path/to/server/plugins
# Or set SERVER_PLUGINS_DIR in environment

SERVER_PLUGINS_DIR="${1:-${SERVER_PLUGINS_DIR:-}}"

if [ -z "$SERVER_PLUGINS_DIR" ]; then
  echo "Usage: ./deploy-local.sh /path/to/server/plugins"
  exit 1
fi

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Building plugin..."
cd "$SCRIPT_DIR/plugin"
./gradlew shadowJar --quiet

echo "Copying plugin jar..."
cp build/libs/nyaru-plugin-*.jar "$SERVER_PLUGINS_DIR/"

echo "Copying etc/ jars..."
if [ -d "etc" ]; then
  for jar in etc/*.jar; do
    [ -f "$jar" ] && cp "$jar" "$SERVER_PLUGINS_DIR/" && echo "  Copied: $(basename $jar)"
  done
fi

echo "Done. Restart your server to apply changes."
