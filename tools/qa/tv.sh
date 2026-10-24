#!/bin/bash
# helpers: k <keys...> ; shot <name>
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}; A=$HOME/android-sdk/platform-tools/adb
k(){ for x in "$@"; do $A shell input keyevent $x; sleep ${KD:-1.2}; done; }
shot(){ $A exec-out screencap -p > /workspace/uae-tv/shots/v2.0/$1.png; echo /workspace/uae-tv/shots/v2.0/$1.png; }
inst(){ (cd /workspace/uae-tv && export JAVA_HOME=$HOME/jdk17 PATH=$HOME/jdk17/bin:$HOME/gradle-8.10.2/bin:$PATH ANDROID_HOME=$HOME/android-sdk; gradle -q assembleRelease 2>&1 | grep -E "^e:" && return 1; $A install -r app/build/outputs/apk/release/app-release.apk >/dev/null; $A shell cmd package set-home-activity ae.majlis.tv/.MainActivity >/dev/null; $A shell am start -n ae.majlis.tv/.MainActivity >/dev/null; sleep ${BOOTW:-25}); }
