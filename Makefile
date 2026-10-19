# Cross-compile a static ARMv7 binary for the Nook Simple Touch (Android 2.1,
# kernel 2.6.29) with the musl.cc toolchain in ./toolchain, plus a host build
# for rendering previews on the PC.
CROSS   ?= toolchain/arm-linux-musleabi-cross/bin/arm-linux-musleabi-
PYTHON  ?= /usr/bin/python3
CFLAGS  ?= -O2 -Wall -Wextra -Wno-unused-parameter -std=c99
ARMFLAGS = -march=armv7-a -mtune=cortex-a8 -mfloat-abi=soft -static -no-pie -s

all: build/nookclock build/nookclock-host

src/assets.h: tools/genassets.py
	$(PYTHON) tools/genassets.py $@

build/nookclock: src/nookclock.c src/assets.h | build
	$(CROSS)gcc $(CFLAGS) $(ARMFLAGS) -o $@ src/nookclock.c

build/nookclock-host: src/nookclock.c src/assets.h | build
	gcc $(CFLAGS) -o $@ src/nookclock.c

# Offline previews from the saved sample response (landscape + portrait).
preview: build/nookclock-host
	printf 'rotate=90\n' > build/land.conf; printf 'rotate=0\n' > build/port.conf
	build/nookclock-host -c build/land.conf --json test/sample.json --preview build/landscape.pgm
	build/nookclock-host -c build/port.conf --json test/sample.json --preview build/portrait.pgm

build:
	mkdir -p build

clean:
	rm -rf build src/assets.h

.PHONY: all preview clean
