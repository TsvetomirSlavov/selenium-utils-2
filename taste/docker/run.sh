#!/usr/bin/env bash
# kill `pidof Xvfb`
/usr/bin/Xvfb :99 -screen 0 1024x768x24 +extension RANDR &
taste -d /mnt/target -c /mnt/cfg.taste /mnt/tests.taste