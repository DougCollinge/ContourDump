import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;

/**
 * Created by djc on 08/11/15.
 */

import org.hid4java.*;
import org.hid4java.event.HidServicesEvent;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hid4java.HidManager.getHidServices;

public class BayerContourNextUSB implements HidServicesListener {
    private static final Logger LOG = LoggerFactory.getLogger(BayerContourNextUSB.class);

    private final int COMPANY_ASCENSIA = 0x1a79;
    private final int METER_ASCENSIA_NEXT_USB = 0x7410;
    private final int PACKET_LENGTH = 64;

    private enum State {
        ESTABLISH,
        DATA
    }
    State pstate;

    HidDevice device;
    byte[] readBuffer;

    public BayerContourNextUSB() {

    }

    public void initialize()
    {
        readBuffer = new byte[PACKET_LENGTH];
        LOG.trace("initialize()");
        HidServices hidServices = getHidServices();
        hidServices.addHidServicesListener(this);

        // Open the device by Vendor ID and Product ID with wildcard serial number
        device = hidServices.getHidDevice(COMPANY_ASCENSIA, METER_ASCENSIA_NEXT_USB, null);
        if (device != null) {
            // Device is already attached so send message
            LOG.debug("Device attached:{}", device);
            pstate = State.ESTABLISH;
//            readAllData(device);
        }
        else {
            LOG.error("Device not attached! Done.");
        }

        if (device != null && device.isOpen()) {
            device.close();
        }

    }

    public void sync()
    {
        int rc;
        LOG.trace("sync() device:{}",device);
        while(pstate == State.ESTABLISH) {
            rc = device.read(readBuffer,1000);
            LOG.trace("sync() rc:{} readBuffer:{}",rc,dataPrinter(readBuffer));
        }

    }

    public void hidDeviceAttached(HidServicesEvent event) {
        LOG.info("HID device attached event: " + event);
    }


    public void hidDeviceDetached(HidServicesEvent event) {
        LOG.info("HID device detached: " + event);
    }


    public void hidFailure(HidServicesEvent event) {
        LOG.error("HID failure: " + event);
    }

    private String dataPrinter(byte data[]) {
        char[] hexalph = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        StringBuilder output = new StringBuilder(2*data.length);

        for (byte b : data) {
            if (Character.isISOControl((char) b)) {
//                System.out.print("\\x" + (b >> 4) + (b & 0x0f));
                output.append(".");
            } else {
                output.append((char) b);
            }
        }
        output.append("  ");
        int bytecounter = 0;
        for (byte b : data) {
            output.append(hexalph[b >> 4]);
            output.append(hexalph[b & 0x0f]);
            if ((bytecounter & 0x03) == 3)
                output.append(" ");
            bytecounter++;
        }
        output.append("\n");
        return output.toString();
    }
}
