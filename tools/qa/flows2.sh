. /workspace/uae-tv/tools/qa/tv.sh
KD=4 k BACK BACK; KD=1.5 k DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT; sleep 6; KD=6 k DPAD_DOWN; KD=10 k DPAD_CENTER; shot 1080-poem; KD=6 k BACK
KD=4 k BACK; KD=1.2 k $(printf 'DPAD_LEFT %.0s' $(seq 8)); sleep 5; KD=5 k DPAD_DOWN; shot 1080-settings-focus; KD=8 k DPAD_CENTER; shot 1080-settings-city; KD=6 k BACK
KD=3 k DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN; KD=8 k DPAD_CENTER; shot 1080-settings-iptv; KD=6 k BACK
KD=4 k BACK; KD=1.2 k $(printf 'DPAD_LEFT %.0s' $(seq 7)); sleep 4; KD=4 k DPAD_DOWN; KD=2 k DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_LEFT; KD=25 k DPAD_CENTER; shot 1080-ask-answer
KD=4 k BACK; echo FLOWS2DONE
