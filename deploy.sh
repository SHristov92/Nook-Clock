#!/bin/sh
# Push nookclock to the Nook over ADB (Wi-Fi) and (re)start it.
#
#   ./deploy.sh 192.168.1.50          push + restart
#   ./deploy.sh 192.168.1.50 probe    any nookclock.sh command instead of restart
#
# Set NOOK_KEEP_CONF=1 to keep the config already on the device.
set -e
NOOK=${1:?usage: $0 <nook-ip> [start|stop|restart|status|probe]}
CMD=${2:-restart}
DIR=/data/local/nookclock
cd "$(dirname "$0")"

command -v adb >/dev/null || { echo "adb not found: sudo apt install adb"; exit 1; }
case "$NOOK" in *:*) ;; *) NOOK=$NOOK:5555 ;; esac
adb connect "$NOOK"
ADB="adb -s $NOOK"

if [ "$CMD" = restart ]; then
	make build/nookclock
	$ADB shell "mkdir $DIR 2>/dev/null"
	$ADB push build/nookclock $DIR/nookclock
	$ADB push device/nookclock.sh $DIR/nookclock.sh
	[ -n "$NOOK_KEEP_CONF" ] || $ADB push device/nookclock.conf $DIR/nookclock.conf
	$ADB shell "chmod 755 $DIR/nookclock $DIR/nookclock.sh"
fi
$ADB shell "sh $DIR/nookclock.sh $CMD"
