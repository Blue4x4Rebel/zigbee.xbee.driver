package com.fornsys.zigbee.xbee.api;

import com.itaca.ztool.util.ByteUtils;
import com.itaca.ztool.util.DoubleByte;

public class XBeePacket {
    public final static int START_BYTE = 0x7E;
    private static short counter = 1;

    protected int[] packet, DATA;
//    private final static int PAYLOAD_START_INDEX = 6;
    private DoubleByte LEN; //, CMD;
    private int FRAME_TYPE, FID, FCS, ERROR;
    private String ERROR_MSG;

    /*
    public XBeePacket(int commandType, DoubleByte ApiId, int[] frameData) {
    	ERROR = 0;
    	this.buildPacket(commandType, ApiId, frameData);
    }
    */
    
    public XBeePacket(int frameType, int[] frameData) {
    	ERROR = 0;
    	this.buildPacket(frameType, frameData);
    }
    
    public void buildPacket(int frameType, int[] frameData) {
    	FRAME_TYPE = frameType;
    	FID = frameData[1]; // Not always, but if it exists, it's here....
    	packet = new int[frameData.length+4];
    	packet[0] = START_BYTE;
    	
    	//Checksum checksum = new Checksum();
    	int checksum = 0;
    	this.LEN = new DoubleByte(frameData.length);
    	packet[1] = this.LEN.getMsb();
    	packet[2] = this.LEN.getLsb();
    	for( int i=0; i < frameData.length; i++) {
    		packet[3+i] = frameData[i];
    		//checksum.addByte(frameData[i]);
    		checksum += frameData[i];
    	}
    	//checksum.compute();
    	this.FCS = 0xFF - (checksum & 0xFF); //.getChecksum();
    	packet[packet.length-1] = this.FCS;
    }
/*
    public void buildPacket(int commandType, DoubleByte ApiId, int[] frameData) {
        // packet size is start byte + len byte + 2 cmd bytes + data + checksum byte
        packet = new int[frameData.length + 8];
        packet[0] = START_BYTE;

        // note: if checksum is not correct, XBee won't send out packet or return error.  ask me how I know.
        // checksum is always computed on pre-escaped packet
        int checksum = 0;
        // Packet length does not include escape bytes
        this.LEN = new DoubleByte(frameData.length);
        packet[1] = this.LEN.getMsb();
        packet[2] = this.LEN.getLsb();

        packet[3] = commandType;
        checksum += packet[3];
        this.FRAME_TYPE = commandType;
        
        packet[4] = ApiId.getMsb();
        checksum += (packet[4]);
        packet[5] = ApiId.getLsb();
        checksum += (packet[5]);
        this.CMD = ApiId;
        
        //data
        for (int i = 0; i < frameData.length; i++) {
            if ( ! ByteUtils.isByteValue( frameData[i] ) ) {
                throw new RuntimeException("Value is greater than one byte: " + frameData[i] +" ("+ Integer.toHexString( frameData[i] ) + ")");
            }
            packet[PAYLOAD_START_INDEX + i] = frameData[i];
            checksum += (packet[PAYLOAD_START_INDEX + i]);
        }
        this.DATA = Arrays.copyOf(frameData, frameData.length);
        
        // set last byte as checksum
        this.FCS = 0xFF - (checksum & 0xFF);
        packet[packet.length - 1] = this.FCS;
    }
    */
    
    public int[] getPacket() {
    	return packet;
    }

	public short getFrameType() {
		return (short)FRAME_TYPE;
	}
	
	public boolean isError() {
		// TODO: Is this an error?
		return false;
	}
	
	public String getErrorMsg() {
		// TODO: See above....
		return "There are no errors. Only Zool!";
	}

	public int getFCS() {
		return this.FCS;
	}
	
	public int getFID() {
		return this.FID;
	}

    public String toString() {
        return "Packet: length = " + this.LEN +
//                ", apiId = " + ByteUtils.toBase16(this.CMD.getMsb()) + " " + ByteUtils.toBase16(this.CMD.getLsb()) +
                ", full data = " + ByteUtils.toBase16(this.packet) +
                ", checksum = " + ByteUtils.toBase16(this.FCS) +
                ", error = " + this.ERROR +
                ", errorMessage = " + this.ERROR_MSG;
    }
    
    public static int nextCounter() {
    	return ++counter;
    }
}
