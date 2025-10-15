#!/bin/bash
# Clean any NCS environment variables
unset NCS_ROOT
unset NRF_SDK_INSTALL_DIR

# Set standalone Zephyr paths
export ZEPHYR_BASE=$HOME/Zephyr-Toolset/zephyr
export ZEPHYR_SDK_INSTALL_DIR=$HOME/Zephyr-Toolset/zephyr-sdk-0.16.5-1
export ZEPHYR_TOOLCHAIN_VARIANT=zephyr

# Source Zephyr environment
source $ZEPHYR_BASE/zephyr-env.sh

echo "✅ Standalone Zephyr 3.5.0 Environment Set"
echo "   ZEPHYR_BASE: $ZEPHYR_BASE"
echo "   SDK: $ZEPHYR_SDK_INSTALL_DIR"
