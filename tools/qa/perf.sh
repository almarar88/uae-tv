. /workspace/uae-tv/tools/qa/tv.sh
$A shell wm size reset; $A shell wm density reset; $A shell am force-stop ae.majlis.tv; $A shell am start -n ae.majlis.tv/.MainActivity >/dev/null; sleep 40
$A shell dumpsys gfxinfo ae.majlis.tv reset >/dev/null
KD=1.5 k DPAD_DOWN DPAD_LEFT DPAD_LEFT DPAD_RIGHT DPAD_RIGHT DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_UP DPAD_UP DPAD_UP DPAD_UP BACK
$A shell dumpsys gfxinfo ae.majlis.tv | grep -E "Total frames|Janky|percentile" > /workspace/uae-tv/shots/v2.0/gfxinfo-1080-home.txt
cat /workspace/uae-tv/shots/v2.0/gfxinfo-1080-home.txt
# soak: 30 min monkey restricted to the launcher package, memory sampled every 2 min
pid=$($A shell pidof ae.majlis.tv); echo "start pid $pid"
( $A shell monkey -p ae.majlis.tv --pct-touch 0 --pct-motion 0 --pct-trackball 0 --pct-syskeys 5 --pct-nav 70 --pct-majornav 20 --pct-appswitch 0 --pct-anyevent 5 --throttle 400 --ignore-crashes=false --monitor-native-crashes -s 42 -v 4500 > /tmp/monkey.log 2>&1 ) &
for i in $(seq 0 15); do echo "min $((i*2)) pid $($A shell pidof ae.majlis.tv) $($A shell dumpsys meminfo ae.majlis.tv | grep -E 'TOTAL PSS|TOTAL:' | head -1 | tr -s ' ')"; sleep 120; done
wait; tail -5 /tmp/monkey.log; $A logcat -d | grep -cE "ANR in ae.majlis|FATAL EXCEPTION"; echo PERFDONE
