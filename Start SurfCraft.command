#!/bin/bash
# Double-click (or run) to play SurfCraft in Minecraft's dev client: offline single player, game files in run/.
cd "$(dirname "$0")" || exit 1
if [ -z "$JAVA_HOME" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null) || JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
fi
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "SurfCraft needs Java 25. Install it with: brew install openjdk@25" >&2
  exit 1
fi
export JAVA_HOME
exec ./gradlew runClient --console=plain
