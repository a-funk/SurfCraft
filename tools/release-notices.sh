#!/bin/sh
# Writes NOTICES.txt, the license asset for a GitHub release: SurfCraft's MIT license, its third-party notices and
# Valve's two Source SDK texts, each verbatim under a header. Usage: tools/release-notices.sh [out]
# (default build/NOTICES.txt; build/ is gitignored, so the generated file is never committed).
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:-$root/build/NOTICES.txt}
mkdir -p "$(dirname "$out")"
rule='=============================================================================='
{
	echo "SurfCraft $(sed -n 's/^version=//p' "$root/gradle.properties"): license and third-party notices."
	for entry in \
		"LICENSE|SurfCraft's license (MIT)" \
		"THIRD_PARTY_NOTICES.md|Third-party notices and acknowledgements" \
		"LICENSES/Valve-Source-SDK-2013.txt|Valve's Source 1 SDK License (acknowledged)" \
		"LICENSES/Valve-thirdpartylegalnotices.txt|Valve's third-party legal notices (companion to the SDK license)"; do
		file=${entry%%|*}
		printf '\n\n%s\n%s: %s\n%s\n\n' "$rule" "$file" "${entry#*|}" "$rule"
		cat "$root/$file"
	done
} > "$out"
echo "$out"
