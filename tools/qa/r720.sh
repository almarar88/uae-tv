. /workspace/uae-tv/tools/qa/tv.sh
$A shell wm size 1280x720; $A shell wm density 213; $A shell am force-stop ae.majlis.tv; $A shell am start -n ae.majlis.tv/.MainActivity >/dev/null; sleep 35; shot 720-home; KD=4 k DPAD_DOWN; shot 720-home-row1; KD=4 k BACK; KD=8 k DPAD_LEFT; KD=4 k DPAD_DOWN; shot 720-news; KD=4 k BACK; KD=1.2 k DPAD_LEFT DPAD_LEFT DPAD_LEFT; sleep 6; shot 720-quran; KD=4 k BACK; KD=1.2 k DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT; sleep 6; shot 720-poetry; KD=4 k BACK; KD=1.2 k $(printf 'DPAD_LEFT %.0s' $(seq 8)); sleep 5; shot 720-settings; KD=4 k BACK
echo R720DONE
