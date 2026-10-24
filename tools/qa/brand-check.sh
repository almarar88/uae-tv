#!/bin/sh
# QA gate (v2 native): logo only — no wordmark/tagline, and no «شاهد الآن» anywhere user-visible.
# Scans res/, the manifest, and Kotlin string literals (comments excluded).
cd "$(dirname "$0")/../.."
PAT='الكود TV|Alcode TV|Majlis TV|تلفزيونك|الإماراتي الذكي|التلفزيون الذكي|واجهة رئيسية عربية|slogan|tagline|شاهد الآن|شاهد الان|Watch now'
hits=$(grep -rnE "$PAT" app/src/main/res app/src/main/AndroidManifest.xml --include=*.xml)
kt=$(grep -rnE "\"[^\"]*($PAT)[^\"]*\"" app/src/main/java --include=*.kt | grep -vE ':[0-9]+:\s*(//|\*|/\*)')
if [ -n "$hits$kt" ]; then echo "FAIL: forbidden strings found:"; echo "$hits"; echo "$kt"; exit 1; fi
echo "PASS: no wordmark, tagline or «شاهد الآن» in res/, manifest or Kotlin strings"
