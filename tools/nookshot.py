#!/usr/bin/env python3
"""Screenshot of the Nook: pulls /dev/graphics/fb0 (RGB565, 600x800 double
buffered) over adb and saves the visible half as PNG.  usage: nookshot.py out.png"""
import subprocess, sys, tempfile
from PIL import Image

def adb(*a):
    return subprocess.run(["adb", *a], check=True, capture_output=True).stdout

pan = adb("shell", "cat /sys/class/graphics/fb0/pan").decode().strip()
yoff = int(pan.split(",")[1]) if "," in pan else 0
# old adbd has no exec-out and `adb shell` mangles binary output: copy + pull
adb("shell", "cat /dev/graphics/fb0 > /data/local/tmp/fb.raw")
with tempfile.TemporaryDirectory() as d:
    adb("pull", "/data/local/tmp/fb.raw", d + "/fb.raw")
    raw = open(d + "/fb.raw", "rb").read()
w, h = 600, 800
start = yoff * w * 2
img = Image.frombytes("RGB", (w, h), raw[start:start + w * h * 2], "raw", "BGR;16")
img.convert("L").save(sys.argv[1])
print("saved", sys.argv[1], "pan", pan)
