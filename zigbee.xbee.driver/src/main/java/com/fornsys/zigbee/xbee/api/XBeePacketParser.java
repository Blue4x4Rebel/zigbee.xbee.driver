package com.fornsys.zigbee.xbee.api;

import it.cnr.isti.io.MarkableInputStream;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itaca.ztool.util.ByteUtils;

public class XBeePacketParser implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(XBeePacketParser.class);

    private final int DEFAULT_TIMEOUT = 60000;

    private XBeePacketHandler handler;
    private Object newPacketNotification = null;
    private final InputStream in;
    private int timeout = DEFAULT_TIMEOUT;

    private Thread thread = null;

    private boolean done = false;

    public XBeePacketParser(InputStream in, XBeePacketHandler handler, Object lock) {
        logger.debug("Creating XBeePacketParser");
        if ( in.markSupported() ) {
            this.in = in;
        } else {
            logger.warn(
                    "Provided InputStream {} doesn't provide the mark()/reset() feature, " +
                    "wrapping it up as BufferedInputStream", in.getClass()
            );
            this.in = new MarkableInputStream(in);
        }
        this.handler = handler;
        this.newPacketNotification = lock;

        thread = new Thread(this,"XBeePacketParser");
        thread.setDaemon(true);
        thread.start();
    }

    public void run() {

        int val = -1;
//        final long MAX_READ_AGAIN_TIMEOUT = 500;
        int readAgainCount = 0;
//        long readAgainTimeout = 0;

        XBeePacket response = null;
        XBeePacketStream packetStream = null;
        logger.debug("Thread used by XBeePacketParser started");
        while (!done) {

            try {
                if (in.available() > 0) {
                    //THINK Why we loop on in.available() instead of wait on in.read ?
                    val = in.read();
                    if ( readAgainCount == 0 ) {
//                        readAgainTimeout = System.currentTimeMillis() + MAX_READ_AGAIN_TIMEOUT;
                    }
                    logger.trace("Read {} from input stream ", ByteUtils.formatByte(val));
                    if (val == XBeePacket.START_BYTE) {
                        in.mark(256);
                        packetStream = new XBeePacketStream(in);
                        response = packetStream.parsePacket();

                        logger.debug("Response is {} -> {}", response.getClass(), response);
                        
//                        if ( response.isError() ){
//                            logger.error("Received a BAD PACKET {}", response.getPacket() );
//                            if ( response.getError() == ErrorType.ZNP_ERROR_PACKET ) {
//                                /*
//                                 * Data was parsed correctly so we can move on and avoid to reset the stream
//                                 */
//                                readAgainCount = 0;
//                                continue;
//                            }
//                            if ( System.currentTimeMillis() > readAgainTimeout ) {
//                                /*
//                                 * We are merge data too old so we drop the stream even if we didn't read part of it correctly
//                                 */
//                                readAgainCount = 0;
//                                continue;
//                            }
//                            if ( readAgainCount > MAX_READ_AGAIN ) {
//                                /*
//                                 * We read the stream too many time better to drop it
//                                 */
//                                readAgainCount = 0;
//                                continue;
//                            }
//                            readAgainCount++;
//                            in.reset();
//                            continue;
//                        }
                        // wrap around entire parse routine
                        synchronized (this.newPacketNotification) {
                            // add to handler and newPacketNotification
                            handler.handlePacket(response);
                            //log.debug("Notifying API user that packets are ready");
                            newPacketNotification.notifyAll();
                        }
                    } else {
                        logger.warn("Discared stream: expected start byte but received this {}", ByteUtils.toBase16(val));
                    }
                } else {
                    logger.info("No data available, waiting for new data event or timeout");
                    long start = System.currentTimeMillis();

                    // we will wait here for RXTX to notify us of new data
                    synchronized (this) {
                        // There's a chance that we got notified after the first in.available check
                        if (in.available() > 0) {
                            continue;
                        }

                        // serial event will wake us up
                        this.wait(timeout);
                    }

                    //Looking for deadlock when packet is not received
                    synchronized (this.newPacketNotification) {
                        newPacketNotification.notifyAll();
                    }

                    final long waited = System.currentTimeMillis() - start;
                    if ( waited >= timeout) {
                        logger.debug("Timeout fired: checking for data");
                    } else {
                        logger.debug("Serial Event fired: Thread woken up");
                    }
                }
            } catch(InterruptedException ie) {
                logger.debug("Thread woken up by InterruptedException");
                // we've been told to stop
                //FIX replace break with continue, because
                //	the interrupt could be generated for other meanings
                break;
            } catch (Exception e) {
                /*
                 * handling exceptions in a thread is a bit dicey.
                 * the rest of the packet will be discarded
                 *
                 */
                logger.error("Exception in reader thread", e);
                handler.error(e);

                synchronized (this.newPacketNotification) {
                    newPacketNotification.notify();
                }
            }
        }
        logger.debug("Thread used by XBeePacketParser terminated");
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * This is hosw long we wait until we check for new data in the event RXTX fails to notify us.
     *
     * @param timeout
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public void interrupt() {
        thread.interrupt();
    }

    /**
     * @return the internal thread that is handling the {@link InputStream} for parsing packet
     * @since 0.6.0
     */
    public Thread getInternalThread() {
        return thread;
    }

}
