import org.hid4java.HidDevice;
import org.hid4java.HidServices;

/**
 * Created by djc on 08/11/15.
 */

import org.hid4java.*;
import org.hid4java.event.HidServicesEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hid4java.HidManager.getHidServices;

public class BayerContourNextUSB implements HidServicesListener {
    private static final Logger LOG = LoggerFactory.getLogger(BayerContourNextUSB.class);

    private final int COMPANY_ASCENSIA = 0x1a79;
    private final int METER_ASCENSIA_NEXT_USB = 0x7410;
    private final int PACKET_LENGTH = 64;
    private final int READ_TIMEOUT_MS = 1000;

    private final byte AsciiETX = 0x03;
    private final byte AsciiEOT = 0x04;
    private final byte AsciiENQ = 0x05;
    private final byte AsciiACK = 0x06;
    private final byte AsciiCR  = 0x0d;
    private final byte AsciiLF  = 0x0a;
    private final byte AsciiNAK = 0x15;
    private final byte AsciiETB = 0x17;

    private enum State {
        TERMINATE,
        ESTABLISH,
        DATA
    }
    State pstate;

    HidDevice device;
    byte[] readBuffer;
    byte[] sendBuffer;

    public BayerContourNextUSB() {

    }

    public boolean initialize()
    {
        readBuffer = new byte[PACKET_LENGTH];
        sendBuffer = new byte[PACKET_LENGTH];

        LOG.trace("initialize()");
        HidServices hidServices = getHidServices();
        hidServices.addHidServicesListener(this);

        // Open the device by Vendor ID and Product ID with wildcard serial number
        device = hidServices.getHidDevice(COMPANY_ASCENSIA, METER_ASCENSIA_NEXT_USB, null);
        if (device == null) {
                LOG.error("Device not attached! Done.");
            return false;
        }
        LOG.debug("Device attached:{}", device);

//        if (device != null && device.isOpen()) {
//            device.close();
//        }
        return true;

    }

    public void sync()
    {
        // Device should be selected and open at this point.
        int rc;
        StringBuilder readResult = new StringBuilder(5*PACKET_LENGTH); // Just a guess on capacity.

        LOG.trace("sync() device:{}",device);
        pstate = State.ESTABLISH;


        while(pstate != State.TERMINATE) {
//            rc = device.read(readBuffer, READ_TIMEOUT_MS);
//            LOG.trace("sync readAllFragments rc:{} data:{}",rc,dataPrinter(readBuffer));
//            sleeperms(1000);

            switch(pstate) {
                case ESTABLISH:
                    LOG.trace("sync ESTABLISH.");
                    rc = sendMessage(AsciiEOT);
                    LOG.trace("sync sent EOT: rc:{}",rc);
                    pstate = State.DATA;
                    break;
                case DATA:
                    LOG.trace("sync DATA.");
                    rc = readAllFragments(readResult);
                    LOG.trace("sync readAllFragments rc:{} data:{}",rc,stringPrinter(readResult.toString()));
                    rc = sendMessage(AsciiACK);

                    if(readResult.length() == 1) {
                        LOG.trace("sync() single char result:{}",readResult.charAt(0));
                    }
                    else {
                        LOG.trace("sync() multi char result.");
                        int reslen = readResult.length();
                        int etbetx = readResult.charAt(reslen - 5);
                        int checksum = readResult.charAt(reslen - 4) * 256 + readResult.charAt(reslen - 3);

                        if (etbetx == AsciiETX) {
                            // End of transmission, end of data, leave data mode.
                            LOG.trace("sync ETX block.");
                            pstate = State.TERMINATE;
                        }
                    }

//                    if( rc == AsciiNAK ) {
//                        pstate = State.TERMINATE;
//                    }
//                    else if( rc==AsciiENQ ) {
//                        rc = sendMessage(AsciiACK);
//
//                    }
//                    break;
////                case (DATA):
////                        rc = readAllFragments(readResult) ;
////                    LOG.trace("sync() rc:{} readBuffer:{}", rc, dataPrinter(readBuffer));
            }
        }

        LOG.debug("sync() finished with:\n{}", stringPrinter(readResult.toString()) );
    }

    private void sleeperms(int ms)
    {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            //Handle exception
        }
    }

    private int sendMessage(int command)
    {
        sendBuffer[0] = 0x00;  // Padding
        sendBuffer[1] = 0x00;  // Padding
        sendBuffer[2] = 0x00;  // Padding
        sendBuffer[3] = 0x01;  // Length of data
        sendBuffer[4] = (byte)command;
        // The rest of it is irrelevant.

        LOG.trace("Sending message:{}",command);
        int val = device.write( sendBuffer, PACKET_LENGTH, (byte) 0 );
        if (val != -1) {
            LOG.debug("write returned:{}",val);
        } else {
            LOG.error("write returned -1, message:{}", device.getLastErrorMessage());
        }
        return val;
    }

    private int readAllFragments(StringBuilder outbuf) {
        // Attempts to read a string from 'device'. If that succeeds the string is dropped in 'outbuf' and return 0.
        // Otherwise, we could get an EOT return, meaning there is no data to read. Return that == 0x04.
        outbuf.setLength(0);
        int fraglen;

        int rc;

        do {
            rc = device.read(readBuffer, READ_TIMEOUT_MS);
            LOG.trace( "read rc:{} buf:{}", rc, dataPrinter(readBuffer) );

            if(readBuffer[3] == 1) {
                // Only a single byte in the packet. Return that byte.
                LOG.trace("readAllFragments() got single char:{}",readBuffer[4]);
                outbuf.append(readBuffer[4]);
                return 0;
            }
            if( readBuffer[0] != 'A' || readBuffer[1] != 'B' || readBuffer[2] != 'C') {
                // There's a problem. NAK the packet here. TODO
            }
            fraglen = readBuffer[3];

            appendFragment(outbuf, readBuffer);
        } while(!( outbuf.charAt(outbuf.length()-2) == AsciiCR && outbuf.charAt((outbuf.length()-1)) == AsciiLF ));
        LOG.debug("returning normally.");
        return 0;
    }

    private void appendFragment(StringBuilder out, byte[] in) {
        int datalen = in[3];
        for (int i = 0; i < datalen; i++) {
            out.append((char) in[i + 4]);
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

    private String stringPrinter(String sbuf) {
        StringBuilder result = new StringBuilder(sbuf.length()*2);
        char[] hexalph = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        for (int ichar = 0; ichar < sbuf.length(); ichar++) {
            char c = sbuf.charAt(ichar);
            if (Character.isISOControl(c)) {
                result.append("\\");
                result.append(hexalph[c >> 4] + hexalph[c & 0x0f]);
            } else {
                result.append(c);
            }
        }
        return result.toString();
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
