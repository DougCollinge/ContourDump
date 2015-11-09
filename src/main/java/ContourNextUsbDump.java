/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Gary Rowe
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

import org.hid4java.*;
import org.hid4java.event.HidServicesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Demonstrate the USB HID interface using a Bayer Ascensia Contour Next USB glucometer.</p>
 *
 * @since 0.0.1
 *  
 */
public class ContourNextUsbDump implements HidServicesListener {
    private static final Logger LOG = LoggerFactory.getLogger(ContourNextUsbDump.class);

    private final int COMPANY_ASCENSIA = 0x1a79;
    private final int METER_ASCENSIA_NEXT_USB = 0x7410;

    private final int PACKET_LENGTH = 64;

    public static void main(String[] args) throws HidException {

        ContourNextUsbDump example = new ContourNextUsbDump();
        example.executeExample();
    }

    private void executeExample() throws HidException {

        BayerContourNextUSB meter = new BayerContourNextUSB();
        meter.initialize();
        meter.sync();
        System.exit(0);

        LOG.trace("Loading hidapi...");

        // Get HID services
        HidServices hidServices = HidManager.getHidServices();
        hidServices.addHidServicesListener(this);

        // Open the device by Vendor ID and Product ID with wildcard serial number
        HidDevice device = hidServices.getHidDevice(COMPANY_ASCENSIA, METER_ASCENSIA_NEXT_USB, null);
        if (device != null) {
            // Device is already attached so send message
            LOG.debug("Device attached:{}", device);
            readAllData(device);
        }
        else {
            LOG.error("Device not attached! Done.");
        }

        if (device != null && device.isOpen()) {
            device.close();
        }

        LOG.trace("Leaving hidapi...");
        System.exit(0);
    }

    public void hidDeviceAttached(HidServicesEvent event) {

    }


    public void hidDeviceDetached(HidServicesEvent event) {

    }

    public void hidFailure(HidServicesEvent event) {
        System.err.println("HID failure: " + event);
    }

    private void readAllData(HidDevice device) {
        LOG.trace("Entering readAllData.");
        // Read all the samples from the device 'device'.

        List<String> readings = new ArrayList<String>();

//        System.out.println("device:" + device.toString());

//        StringBuffer sbuf;
//        sbuf = suckInRecord(device);

        int retcode;

        StringBuilder msg = new StringBuilder(4 * PACKET_LENGTH); // Slightly too big.

        retcode = sendMessage(device,4);
//        System.out.println("sendMessage returned:"+retcode);

//        System.out.println("Fetching string data.");
        suckInString(msg, device);
//        System.out.println(msg.toString());
        readings.add( msg.toString() );

        do {
            retcode = sendMessage(device,6);
            LOG.trace("sendMessage returned:{}",retcode);
            retcode = suckInString(msg, device);
            LOG.trace("suckInString returned:{}",retcode);
            if(retcode != 4) {
                readings.add(msg.toString());
            }
//            if(retcode == 0) stringPrinter(msg.toString());
        } while( retcode != 4) ;


//        System.out.println("suckInRecord hit end-of-text.");

        for (String s : readings ) {
            stringPrinter(s);
        }
        LOG.trace("Leaving readAllData.");
    }

    private int sendMessage(HidDevice device, int command)
    {
        byte message[] = new byte[PACKET_LENGTH];
        message[0] = 0x00;  // Padding
        message[1] = 0x00;  // Padding
        message[2] = 0x00;  // Padding
        message[3] = 0x01;  // Length of data
        message[4] = (byte)command;

        LOG.trace("Sending command:{}",command);
        int val = device.write(message, PACKET_LENGTH, (byte) 0);
        if (val != -1) {
            LOG.debug("write returned:{}",val);
        } else {
            LOG.error("write returned -1, message:{}", device.getLastErrorMessage());
        }
        return val;
    }

    private int suckInString(StringBuilder outbuf, HidDevice device) {
        // Attempts to read a string from 'device'. If that succeeds the string is dropped in 'outbuf' and return 0.
        // Otherwise, we could get an EOT return, meaning there is no data to read. Return that == 0x04.
        byte pbuf[] = new byte[PACKET_LENGTH];
        outbuf.setLength(0);
        int retcode;

        do {
            retcode = device.read(pbuf, 500);
            if (pbuf[3] == 1) {
                // No data to read.
                LOG.trace("read returned:{}",pbuf[4]);
                return pbuf[4];
            }
            LOG.trace("read got string:{}",dataPrinter(pbuf));
            addToString(outbuf, pbuf);
        } while(!( outbuf.charAt(outbuf.length()-2) ==0x0d && outbuf.charAt((outbuf.length()-1))==0x0a ));
        LOG.debug("returning normally.");
        return 0;
    }

    private void addToString(StringBuilder out, byte[] in) {
        int datalen = in[3];
        for (int i = 0; i < datalen; i++) {
            out.append((char) in[i + 4]);
        }
    }

    private void stringPrinter(String sbuf) {
        char[] hexalph = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        for (int ichar = 0; ichar < sbuf.length(); ichar++) {
            char c = sbuf.charAt(ichar);
            if (Character.isISOControl(c)) {
                System.out.print("\\" + hexalph[c >> 4] + hexalph[c & 0x0f]);
            } else {
                System.out.print(c);
            }
        }
        System.out.println();
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
