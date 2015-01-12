package com.fornsys.zigbee.xbee.api;

import java.util.Arrays;

public class XBeeParseException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int[] frame;
    private XBeePacket packet;

    public XBeeParseException() {
		super();
	}
	
	public XBeeParseException(Exception e) {
		super(e);
	}
	
	public XBeeParseException(String msg) {
		super(msg);
	}
	
	public XBeeParseException(String msg, Exception e) {
		super(msg, e);
	}

	public XBeeParseException(String string, int[] frameData,
            XBeePacket response) {
        this.frame = Arrays.copyOf(frameData, frameData.length);
        this.packet = response;
    }

    public int[] getRawFrame() {
        return frame;
    }

    public XBeePacket getPacket() {
        return packet;
    }
}
