#!/bin/sh
# Build the Nook clock APK with bare SDK tools (no Gradle): Android 2.1 / API 7.
# Toolchain lives in ../toolchain (JDK 17, build-tools 34, android-7 platform).
set -e
cd "$(dirname "$0")"
T=../toolchain
BT=$T/build-tools
JAR=$T/android-7/android.jar
export JAVA_HOME=$(cd $T/jdk && pwd) PATH=$(cd $T/jdk/bin && pwd):$PATH
KS=${KEYSTORE:-$HOME/.android/debug.keystore}
OUT=build

rm -rf $OUT && mkdir -p $OUT/classes $OUT/gen $OUT/dex
[ -f "$KS" ] || { mkdir -p "$(dirname "$KS")"; keytool -genkeypair -keystore "$KS" -storepass android \
	-keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
	-dname "CN=Android Debug,O=Android,C=US"; }

# Legacy aapt, not aapt2: Android 2.1 can only read UTF-16 resource string
# pools, and aapt2 always writes UTF-8 (install fails with "Bad string block").
$BT/aapt package -f --utf16 -M AndroidManifest.xml -S res -I $JAR -J $OUT/gen \
	--min-sdk-version 7 --target-sdk-version 7 -F $OUT/unsigned.apk
javac -nowarn -source 8 -target 8 -bootclasspath $JAR -d $OUT/classes \
	$(find src $OUT/gen -name '*.java') 2>&1 | grep -v "^warning: \[options\]" || true
[ -n "$(find $OUT/classes -name ClockActivity.class)" ] || { echo "javac failed"; exit 1; }
$BT/d8 --min-api 7 --lib $JAR --output $OUT/dex $(find $OUT/classes -name '*.class')
(cd $OUT/dex && zip -q ../unsigned.apk classes.dex)
$BT/zipalign -f 4 $OUT/unsigned.apk $OUT/aligned.apk
$BT/apksigner sign --ks "$KS" --ks-pass pass:android --min-sdk-version 7 \
	--out $OUT/nookclock.apk $OUT/aligned.apk
echo "built $OUT/nookclock.apk"
