. /workspace/uae-tv/tools/qa/tv.sh
inst || { echo BUILDFAIL; exit; }
KD=2 k DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN; KD=3 k BACK; shot 1080-back-to-top
# Quran reader
KD=2 k BACK; KD=0.8 k DPAD_LEFT DPAD_LEFT DPAD_LEFT; sleep 5; KD=3 k DPAD_DOWN DPAD_RIGHT DPAD_CENTER; sleep 5; shot 1080-quran-reader; KD=3 k DPAD_DOWN DPAD_CENTER; sleep 6; shot 1080-quran-tafsir; KD=3 k BACK; shot 1080-quran-reader-back; KD=3 k BACK; shot 1080-quran-restore
# Poetry poem
KD=2 k BACK; KD=0.8 k DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT; sleep 5; KD=3 k DPAD_DOWN DPAD_CENTER; sleep 4; shot 1080-poem; KD=3 k BACK
# Settings city dialog
KD=2 k BACK; KD=0.8 k $(printf 'DPAD_LEFT %.0s' $(seq 8)); sleep 4; KD=3 k DPAD_DOWN; shot 1080-settings-focus; KD=3 k DPAD_CENTER; shot 1080-settings-city; KD=3 k BACK
KD=3 k DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_CENTER; shot 1080-settings-iptv; KD=3 k BACK
# Ask: local chip
KD=2 k BACK; KD=0.8 k $(printf 'DPAD_LEFT %.0s' $(seq 7)); sleep 3; KD=3 k DPAD_DOWN; KD=1 k DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT; KD=12 k DPAD_CENTER; shot 1080-ask-answer
KD=2 k BACK HOME; sleep 3; shot 1080-home-after
echo FLOWSDONE
