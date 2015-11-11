#!/bin/sh
# TODO Replace this with something a little more delicate and sensitive.
UDEVRULEPATH=/etc/udev/rules.d/50-BayerContourNextUSB.rules
echo "# Added to handle the Bayer/Ascenscia Contour Next USB glucometer, a HID device." >$UDEVRULEPATH
echo "SUBSYSTEM==\"usb\", ATTR{idVendor}==\"1a79\", ATTR{idProduct}==\"7410\", MODE:=\"0666\"" >S>$UDEVRULEPATH
