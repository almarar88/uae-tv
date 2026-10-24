. /workspace/uae-tv/tools/qa/tv.sh
names=(x x prayer quran poetry weather league ask settings)
for n in 2 3 4 5 6 7 8; do KD=2 k BACK; KD=0.8 k $(printf 'DPAD_LEFT %.0s' $(seq $n)); sleep 8; shot 1080-${names[$n]}; KD=3 k DPAD_DOWN; shot 1080-${names[$n]}-down; done
echo TABSDONE
