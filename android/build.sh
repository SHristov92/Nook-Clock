#!/bin/sh
# Build the Nook clock APK with bare SDK tools (no Gradle): Android 2.1 / API 7.
# Toolchain defaults to ../toolchain (JDK 17, build-tools 34, android-7 platform);
# CI overrides it with BUILD_TOOLS / ANDROID_JAR and its own JAVA_HOME.
#
#   KEYSTORE / KEYSTORE_PASS / KEY_ALIAS   signing key (default: Android debug key)
#   VERSION_CODE / VERSION_NAME            override the manifest version
set -e
cd "$(dirname "$0")"
T=../toolchain
BT=${BUILD_TOOLS:-$T/build-tools}
JAR=${ANDROID_JAR:-$T/android-7/android.jar}
if [ -d $T/jdk ]; then
	export JAVA_HOME=$(cd $T/jdk && pwd) PATH=$(cd $T/jdk/bin && pwd):$PATH
fi
KS=${KEYSTORE:-$HOME/.android/debug.keystore}
KS_PASS=${KEYSTORE_PASS:-android}
OUT=build
# Version from git: the commit count always grows (Android refuses to
# downgrade), the name is the nearest tag, e.g. "v1.1" or "v1.1-3-gabc1234".
VERSION_CODE=${VERSION_CODE:-$(git rev-list --count HEAD 2>/dev/null || echo 1)}
VERSION_NAME=${VERSION_NAME:-$(git describe --tags --always --dirty 2>/dev/null || echo dev)}
VERSION="--version-code $VERSION_CODE --version-name $VERSION_NAME"

rm -rf $OUT && mkdir -p $OUT/classes $OUT/gen $OUT/dex
[ -f "$KS" ] || { mkdir -p "$(dirname "$KS")"; keytool -genkeypair -keystore "$KS" -storepass android \
	-keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
	-dname "CN=Android Debug,O=Android,C=US"; }

# Legacy aapt, not aapt2: Android 2.1 can only read UTF-16 resource string
# pools, and aapt2 always writes UTF-8 (install fails with "Bad string block").
$BT/aapt package -f --utf16 -M AndroidManifest.xml -S res -I $JAR -J $OUT/gen \
	--min-sdk-version 7 --target-sdk-version 7 $VERSION -F $OUT/unsigned.apk
javac -nowarn -source 8 -target 8 -bootclasspath $JAR -d $OUT/classes \
	$(find src $OUT/gen -name '*.java') 2>&1 | grep -v "^warning: \[options\]" || true
[ -n "$(find $OUT/classes -name ClockActivity.class)" ] || { echo "javac failed"; exit 1; }
$BT/d8 --min-api 7 --lib $JAR --output $OUT/dex $(find $OUT/classes -name '*.class')
(cd $OUT/dex && zip -q ../unsigned.apk classes.dex)
$BT/zipalign -f 4 $OUT/unsigned.apk $OUT/aligned.apk
$BT/apksigner sign --ks "$KS" --ks-pass "pass:$KS_PASS" ${KEY_ALIAS:+--ks-key-alias $KEY_ALIAS} --min-sdk-version 7 \
	--out $OUT/nookclock.apk $OUT/aligned.apk
echo "built $OUT/nookclock.apk ($VERSION_NAME, code $VERSION_CODE)"
