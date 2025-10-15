#!/bin/bash

# Source Zephyr environment
source $ZEPHYR_BASE/zephyr-env.sh

# Clean build
rm -rf build

# Build using west (properly handles overlays)
west build -b nrf52840dk_nrf52840 -p

# Flash using nrfjprog
sudo nrfjprog --program build/zephyr/zephyr.hex --chiperase --reset


# rm -rf build
# cmake -B build -GNinja -DBOARD=nrf52840dk_nrf52840
# ninja -C build

# sudo nrfjprog --program build/zephyr/zephyr.hex --chiperase --reset
