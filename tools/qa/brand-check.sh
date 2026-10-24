#!/bin/sh
# QA gate: the brand is the logo alone — no wordmark next to it, no tagline/slogan anywhere user-visible.
# Exit 1 if any user-visible string contains a wordmark or a tagline.
cd "$(dirname "$0")/../.."
PAT='الكود TV|Alcode TV|Majlis TV|تلفزيونك|الإماراتي الذكي|التلفزيون الذكي|واجهة رئيسية عربية|شعار|slogan|tagline'
hits=$(grep -rnE "$PAT" app/src/main/res app/src/main/assets/web app/src/main/AndroidManifest.xml \
  --include=*.xml --include=*.html --include=*.js --include=*.css \
  | grep -v '^\S*:\s*[0-9]*:\s*//' | grep -vE '/\*.*\*/' )
if [ -n "$hits" ]; then echo "FAIL: wordmark/tagline strings found:"; echo "$hits"; exit 1; fi
echo "PASS: no wordmark or tagline strings in res/ or assets/web"
