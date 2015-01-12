package com.fornsys.zigbee.xbee.api;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itaca.ztool.util.ByteUtils;
import com.itaca.ztool.util.DoubleByte;

public class XBeePacketStream {
    private final static Logger log = LoggerFactory.getLogger( XBeePacketStream.class );

    private boolean done;

    private int bytesRead;

    private int length;

    private int checksum = 0;

    private final InputStream in;

    public XBeePacketStream( InputStream in ) {
        this.in = in;
    }

    /**
     *
     * @param packet
     * @return
     * @since 0.6.0 - Revision 60
     */
    public static XBeePacket parsePacket( int[] packet ) {
        final XBeePacket response;
        int idx = 1;
        final int length = new DoubleByte(packet[idx], packet[idx + 1]).get16BitValue();
        idx += 2;
        //log.debug("data length is " + ByteUtils.formatByte(length.getLength()));
        final int[] payload = new int[length];
        final int commandType = packet[idx];
        idx++;
        for ( int i = 0; i < payload.length; i++ ) {
            payload[i] = packet[idx];
            idx++;
        }
        response = parsePayload(commandType, payload );
        int fcs = packet[idx];
        idx++;
        if ( fcs != response.getFCS() ) {
            log.debug( "Parsed packet was {}", packet );
            throw new XBeeParseException( "Packet checksum failed" );
        }
        if ( idx != packet.length ) {
            log.warn( "Packet buffer contains more data that has been ignored" );
        }
        return response;
    }

    public XBeePacket parsePacket()
        throws IOException {

        //ErrorType error = null;
        Exception exception = null;
        done = false;
        bytesRead = 0;
        try {
            final XBeePacket response;
            //int byteLength = this.read("Length");
            DoubleByte len = new DoubleByte(read("Length"), read("Length"));
            // Reset the checksum here, since it's incremented every read.
            checksum = 0;
            this.length = len.get16BitValue();
            log.debug("data length is " + length); //.getLength()));
            final int[] frameData = new int[length];

            for( int i=1; i<= length; i++) {
            	frameData[i-1] = read("data");
            }
//            frameData = this.readRemainingBytes();
            response = parsePayload(frameData[0], frameData );
        
            int fcs = this.read( "Checksum" );
            log.debug("Checksum={}; Response Checksum={}", fcs, response.getFCS()); 
            setDone(true);
            if ( fcs != response.getFCS() ) {
                //error = ErrorType.BAD_FCS;
                //log.debug("Checksum of packet failed: received =" + fcs + " expected = " + response.getFCS());
                throw new XBeeParseException( "Packet checksum failed" , frameData, response);
            }
            if ( !this.isDone() ) {
                // TODO this is not the answer!
                //error = ErrorType.PACKET_SHORTER_THEN_LENGTH;
                throw new XBeeParseException( "Packet stream is not finished yet we seem to think it is", frameData, response );
            }
            log.debug("Returning packet type: "+ByteUtils.toBase16(response.getFrameType()));
            return response;
        }
        catch ( Exception e ) {
            log.error("Failed due to exception", e);
            exception = e;
        }

        if ( exception != null ) {
            final ErrorPacket exceptionResponse = new ErrorPacket();
            //exceptionResponse.setError( error );
            exceptionResponse.setErrorMsg( exception.getMessage() );
            return exceptionResponse;
        }

        /*
         * We should never reach this point
         */
        throw new IllegalStateException("Reached an invalid code line");
    }

    private static XBeePacket parsePayload( final int commandType, final int[] payload ) {
    	log.debug("Looking for commandType: "+ByteUtils.toBase16(commandType));
        switch ( commandType ) {
            case XBeeCMD.SYS_AT_RESPONSE:
            	log.debug("Found SYS_AT_RESPONSE");
                return XBeeATCommand.parseResponse( payload );
                /*
            case ZToolCMD.SYS_ADC_READ_SRSP:
                return new SYS_ADC_READ_SRSP( payload );
            case ZToolCMD.SYS_RESET_RESPONSE:
                return new SYS_RESET_RESPONSE( payload );
            case ZToolCMD.SYS_VERSION_RESPONSE:
                return new SYS_VERSION_RESPONSE( payload );
            case ZToolCMD.SYS_PING_RESPONSE:
                return new SYS_PING_RESPONSE( payload );
            case ZToolCMD.SYS_OSAL_NV_READ_SRSP:
                return new SYS_OSAL_NV_READ_SRSP( payload );
            case ZToolCMD.SYS_OSAL_NV_WRITE_SRSP:
                return new SYS_OSAL_NV_WRITE_SRSP( payload );
            case ZToolCMD.SYS_OSAL_START_TIMER_SRSP:
                return new SYS_OSAL_START_TIMER_SRSP( payload );
            case ZToolCMD.SYS_OSAL_STOP_TIMER_SRSP:
                return new SYS_OSAL_STOP_TIMER_SRSP( payload );
            case ZToolCMD.SYS_OSAL_TIMER_EXPIRED_IND:
                return new SYS_OSAL_TIMER_EXPIRED_IND( payload );
            case ZToolCMD.SYS_RANDOM_SRSP:
                return new SYS_RANDOM_SRSP( payload );
            case ZToolCMD.SYS_GPIO_SRSP:
                return new SYS_GPIO_SRSP( payload );
            case ZToolCMD.SYS_TEST_LOOPBACK_SRSP:
                return new SYS_TEST_LOOPBACK_SRSP( payload );
            case ZToolCMD.AF_DATA_CONFIRM:
                return new AF_DATA_CONFIRM( payload );
            case ZToolCMD.AF_DATA_SRSP:
                return new AF_DATA_SRSP( payload );
            case ZToolCMD.AF_INCOMING_MSG:
                return new AF_INCOMING_MSG( payload );
            case ZToolCMD.AF_REGISTER_SRSP:
                return new AF_REGISTER_SRSP( payload );
            case ZToolCMD.ZB_ALLOW_BIND_CONFIRM:
                return new ZB_ALLOW_BIND_CONFIRM();
            case ZToolCMD.ZB_ALLOW_BIND_RSP:
                return new ZB_ALLOW_BIND_RSP( payload );
            case ZToolCMD.ZB_APP_REGISTER_RSP:
                return new ZB_APP_REGISTER_RSP( payload );
            case ZToolCMD.ZB_BIND_CONFIRM:
                return new ZB_BIND_CONFIRM( payload );
            case ZToolCMD.ZB_BIND_DEVICE_RSP:
                return new ZB_BIND_DEVICE_RSP( payload );
            case ZToolCMD.ZB_FIND_DEVICE_CONFIRM:
                return new ZB_FIND_DEVICE_CONFIRM( payload );
            case ZToolCMD.ZB_FIND_DEVICE_REQUEST_RSP:
                return new ZB_FIND_DEVICE_REQUEST_RSP();
            case ZToolCMD.ZB_GET_DEVICE_INFO_RSP:
                return new ZB_GET_DEVICE_INFO_RSP( payload );
            case ZToolCMD.ZB_PERMIT_JOINING_REQUEST_RSP:
                return new ZB_PERMIT_JOINING_REQUEST_RSP( payload );
            case ZToolCMD.ZB_READ_CONFIGURATION_RSP:
                return new ZB_READ_CONFIGURATION_RSP( payload );
            case ZToolCMD.ZB_RECEIVE_DATA_INDICATION:
                return new ZB_RECEIVE_DATA_INDICATION( payload );
            case ZToolCMD.ZB_SEND_DATA_CONFIRM:
                return new ZB_SEND_DATA_CONFIRM( payload );
            case ZToolCMD.ZB_SEND_DATA_REQUEST_RSP:
                return new ZB_SEND_DATA_REQUEST_RSP( payload );
            case ZToolCMD.ZB_START_CONFIRM:
                return new ZB_START_CONFIRM( payload );
            case ZToolCMD.ZB_START_REQUEST_RSP:
                return new ZB_START_REQUEST_RSP( payload );
            case ZToolCMD.ZB_WRITE_CONFIGURATION_RSP:
                return new ZB_WRITE_CONFIGURATION_RSP( payload );
            case ZToolCMD.ZDO_ACTIVE_EP_REQ_SRSP:
                return new ZDO_ACTIVE_EP_REQ_SRSP( payload );
            case ZToolCMD.ZDO_ACTIVE_EP_RSP:
                return new ZDO_ACTIVE_EP_RSP( payload );
            case ZToolCMD.ZDO_BIND_REQ_SRSP:
                return new ZDO_BIND_REQ_SRSP( payload );
            case ZToolCMD.ZDO_BIND_RSP:
                return new ZDO_BIND_RSP( payload );
            case ZToolCMD.ZDO_END_DEVICE_ANNCE_IND:
                return new ZDO_END_DEVICE_ANNCE_IND( payload );
            case ZToolCMD.ZDO_END_DEVICE_ANNCE_SRSP:
                return new ZDO_END_DEVICE_ANNCE_SRSP( payload );
            case ZToolCMD.ZDO_END_DEVICE_BIND_REQ_SRSP:
                return new ZDO_END_DEVICE_BIND_REQ_SRSP( payload );
            case ZToolCMD.ZDO_END_DEVICE_BIND_RSP:
                return new ZDO_END_DEVICE_BIND_RSP( payload );
            case ZToolCMD.ZDO_IEEE_ADDR_REQ_SRSP:
                return new ZDO_IEEE_ADDR_REQ_SRSP( payload );
            case ZToolCMD.ZDO_IEEE_ADDR_RSP:
                return new ZDO_IEEE_ADDR_RSP( payload );
            case ZToolCMD.ZDO_MATCH_DESC_REQ_SRSP:
                return new ZDO_MATCH_DESC_REQ_SRSP( payload );
            case ZToolCMD.ZDO_MATCH_DESC_RSP:
                return new ZDO_MATCH_DESC_RSP( payload );
            case ZToolCMD.ZDO_MGMT_LEAVE_REQ_SRSP:
                return new ZDO_MGMT_LEAVE_REQ_SRSP( payload );
            case ZToolCMD.ZDO_MGMT_LEAVE_RSP:
                return new ZDO_MGMT_LEAVE_RSP( payload );
            case ZToolCMD.ZDO_MGMT_LQI_REQ_SRSP:
                return new ZDO_MGMT_LQI_REQ_SRSP( payload );
            case ZToolCMD.ZDO_MGMT_LQI_RSP:
                return new ZDO_MGMT_LQI_RSP( payload );
            case ZToolCMD.ZDO_MGMT_PERMIT_JOIN_REQ_SRSP:
                return new ZDO_MGMT_PERMIT_JOIN_REQ_SRSP( payload );
            case ZToolCMD.ZDO_MGMT_PERMIT_JOIN_RSP:
                return new ZDO_MGMT_PERMIT_JOIN_RSP( payload );
            case ZToolCMD.ZDO_NODE_DESC_REQ_SRSP:
                return new ZDO_NODE_DESC_REQ_SRSP( payload );
            case ZToolCMD.ZDO_NODE_DESC_RSP:
                return new ZDO_NODE_DESC_RSP( payload );
            case ZToolCMD.ZDO_NWK_ADDR_REQ_SRSP:
                return new ZDO_NWK_ADDR_REQ_SRSP( payload );
            case ZToolCMD.ZDO_NWK_ADDR_RSP:
                return new ZDO_NWK_ADDR_RSP( payload );
            case ZToolCMD.ZDO_SIMPLE_DESC_REQ_SRSP:
                return new ZDO_SIMPLE_DESC_REQ_SRSP( payload );
            case ZToolCMD.ZDO_SIMPLE_DESC_RSP:
                return new ZDO_SIMPLE_DESC_RSP( payload );
            case ZToolCMD.ZDO_STATE_CHANGE_IND:
                return new ZDO_STATE_CHANGE_IND( payload );
            case ZToolCMD.ZDO_UNBIND_REQ_SRSP:
                return new ZDO_UNBIND_REQ_SRSP( payload );
            case ZToolCMD.ZDO_UNBIND_RSP:
                return new ZDO_UNBIND_RSP( payload );
            case ZToolCMD.ZDO_USER_DESC_REQ_SRSP:
                return new ZDO_USER_DESC_REQ_SRSP( payload );
            case ZToolCMD.ZDO_USER_DESC_RSP:
                return new ZDO_USER_DESC_RSP( payload );
            case ZToolCMD.ZDO_USER_DESC_CONF:
                return new ZDO_USER_DESC_CONF( payload );
            case ZToolCMD.ZDO_USER_DESC_SET_SRSP:
                return new ZDO_USER_DESC_SET_SRSP( payload );
            case ZToolCMD.ZDO_STARTUP_FROM_APP_SRSP:
                return new ZDO_STARTUP_FROM_APP_SRSP( payload );
            case ZToolCMD.UTIL_SET_PANID_RESPONSE:
                return new UTIL_SET_PANID_RESPONSE( payload );
            case ZToolCMD.UTIL_SET_CHANNELS_RESPONSE:
                return new UTIL_SET_CHANNELS_RESPONSE( payload );
            case ZToolCMD.UTIL_GET_DEVICE_INFO_RESPONSE:
                return new UTIL_GET_DEVICE_INFO_RESPONSE( payload);
                */
            default:
                return new XBeePacket(commandType, payload );
        }
    }

    public int read( final String context )
        throws IOException {
        int b = read();
        log.trace( "Read {}  byte, val is {}", context, ByteUtils.formatByte( b ) );
        return b;
    }

    /**
     * TODO implement as class that extends inputstream?
     *
     * This method reads bytes from the underlying input stream and performs the following tasks: keeps track of how
     * many bytes we've read, un-escapes bytes if necessary and verifies the checksum.
     */
    public int read()
        throws IOException {

        int b = in.read();

        if ( b == -1 ) {
            throw new XBeeParseException( "Read -1 from input stream while reading packet!" );
        }

        bytesRead++;

        // when verifying checksum you must add the checksum that we are verifying
        // when computing checksum, do not include start byte; when verifying, include checksum
        checksum += ( b );
        //log.debug("Read byte " + ByteUtils.formatByte(b) + " at position " + bytesRead + ", data length is " + this.length.getLength() + ", #escapeBytes is " + escapeBytes + ", remaining bytes is " + this.getRemainingBytes());

        if ( this.getFrameDataBytesRead() >= ( length + 1 ) ) {
            // this is checksum and final byte of packet
            done = true;

            //log.debug("Checksum byte is " + b);
            /*
             * if (!checksum.verify()) {/////////////Maybe expected in ZTool is 0x00, not FF//////////////////// throw
             * new ZToolParseException("Checksum is incorrect.  Expected 0xff, but got " + checksum.getChecksum()); }
             */
        }

        return b;
    }

    /**
     * Returns number of bytes remaining, relative to the stated packet length (not including checksum).
     *
     * @return
     */
    public int getFrameDataBytesRead() {
        // subtract out the 1 length bytes and API ID 2 bytes
        return this.getBytesRead() - 3;
    }

    /**
     * Number of bytes remaining to be read, including the checksum
     *
     * @return
     */
    public int getRemainingBytes() {
        // add one for checksum byte (not included) in packet length
        return length - this.getFrameDataBytesRead() ;
    }

    // get unescaped packet length
    // get escaped packet length
    /**
     * Does not include any escape bytes
     *
     * @return
     */
    public int getBytesRead() {
        return bytesRead;
    }

    public void setBytesRead( int bytesRead ) {
        this.bytesRead = bytesRead;
    }

    private boolean isDone() {
        return done;
    }
    
    private void setDone(boolean done) {
    	this.done = done;
    }

    public int getChecksum() {
        return 0xFF - (checksum & 0xFF);
    }
}
