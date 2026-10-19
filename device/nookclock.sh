#!/system/bin/sh
# nookclock control script for the Nook Simple Touch.
# Written for Android 2.1's minimal /system/bin/sh: no [ / test, no tail,
# only toolbox commands - process control lives in the nookclock binary.
#
#   sh nookclock.sh start     stop the B&N UI and start the clock
#   sh nookclock.sh stop      stop the clock and bring the B&N UI back
#   sh nookclock.sh restart
#   sh nookclock.sh status    show whether it runs + the log
#   sh nookclock.sh probe     dump device info (useful when something fails)
#
# KEEP_UI=1 sh nookclock.sh start   draws without stopping Android (debug only:
#                                   the stock UI will paint over the clock).

DIR=/data/local/nookclock
BIN=$DIR/nookclock
CONF=$DIR/nookclock.conf
PID=$DIR/nookclock.pid
LOG=$DIR/nookclock.log

# Comma separated DNS servers handed out by DHCP (the binary does its own DNS).
dns_servers() {
	for p in net.dns1 net.dns2 dhcp.tiwlan0.dns1 dhcp.wlan0.dns1 net.tiwlan0.dns1; do
		v=`getprop $p`
		case "$v" in
		""|0.0.0.0) ;;
		*) echo -n "$v," ;;
		esac
	done
}

do_start() {
	if $BIN --status -p $PID >/dev/null; then
		echo "already running"
		return 0
	fi
	mv $LOG $LOG.1 2>/dev/null
	# keep the CPU (and therefore Wi-Fi) awake while the framework is stopped
	echo nookclock > /sys/power/wake_lock 2>/dev/null
	case "$KEEP_UI" in
	1) ;;
	*)
		echo "stopping Android UI (zygote)"
		stop
		sleep 3
		;;
	esac
	NOOKCLOCK_DNS=`dns_servers` $BIN -d -c $CONF -p $PID >>$LOG 2>&1 </dev/null
	sleep 2
	$BIN --status -p $PID
}

do_stop() {
	$BIN --stop -p $PID
	echo nookclock > /sys/power/wake_unlock 2>/dev/null
	echo "restarting Android UI (zygote)"
	start
}

case "$1" in
start) do_start ;;
stop) do_stop ;;
restart) $BIN --stop -p $PID; do_start ;;
status)
	$BIN --status -p $PID
	cat $LOG 2>/dev/null
	;;
probe)
	echo "== id / kernel";     id; cat /proc/version
	echo "== build";           getprop ro.build.display.id; getprop ro.build.version.release
	echo "== dns";             getprop | grep -i dns
	echo "== net";             netcfg 2>/dev/null
	echo "== fb0 sysfs";       ls /sys/class/graphics/fb0/
	echo "== fb0 mode";        cat /sys/class/graphics/fb0/virtual_size /sys/class/graphics/fb0/bits_per_pixel
	echo "== power_supply";    ls /sys/class/power_supply/
	echo "== wakelocks";       ls /sys/power/
	echo "== input";           ls /dev/input/
	echo "== storage";         df /data
	;;
*)
	echo "usage: $0 start|stop|restart|status|probe"
	exit 1
	;;
esac
