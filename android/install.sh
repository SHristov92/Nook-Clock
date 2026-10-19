#!/bin/sh
# Install the Clock app on the Nook and register it as the n-button home.
#
#   android/install.sh [nook-ip]          build if needed, install, start, open the chooser
#   android/install.sh [nook-ip] check    is Clock the saved default for the n button?
#   android/install.sh [nook-ip] remove   uninstall (n goes back to B&N Home / ReLaunch)
#
# The default home can't be set from the command line on Android 2.1, so after
# install the script fires the n-button intent: pick "Clock" in the chooser and
# tick "Use by default for this action".
set -e
cd "$(dirname "$0")"
NOOK=${1:-${NOOK:-192.168.100.6}}
CMD=${2:-install}
PKG=dev.nookclock
BN_HOME=com.bn.intent.action.home
APK=build/nookclock.apk

case "$NOOK" in *:*) ;; *) NOOK=$NOOK:5555 ;; esac
adb connect "$NOOK" >/dev/null
export ANDROID_SERIAL=$NOOK
adb get-state >/dev/null 2>&1 || { echo "cannot reach $NOOK - is wireless debugging on?"; exit 1; }

is_default() {
	adb shell "sed -n '/<preferred-activities/,/<\/preferred-activities>/p' /data/system/packages.xml" |
		grep "<item name=\"$PKG/" | grep -q .
}

case "$CMD" in
install)
	[ -f $APK ] && [ -z "$(find src res AndroidManifest.xml -newer $APK)" ] || ./build.sh
	adb install -r $APK | tail -1
	adb shell "am start -n $PKG/.ClockActivity" >/dev/null
	if is_default; then
		echo "Clock is already the default for the n button."
	else
		sleep 2
		adb shell "am start -a $BN_HOME" >/dev/null
		echo "On the Nook: choose 'Clock', tick 'Use by default for this action'."
		echo "Then run: $0 ${1:-} check"
	fi
	;;
check)
	if is_default; then
		echo "OK: n button -> Clock (press n on the clock for the Home/ReLaunch picker)"
	else
		echo "Clock is not the saved default yet: Android will ask on every n press."
		exit 1
	fi
	;;
remove)
	adb uninstall $PKG
	;;
*)
	echo "usage: $0 [nook-ip] install|check|remove"
	exit 2
	;;
esac
