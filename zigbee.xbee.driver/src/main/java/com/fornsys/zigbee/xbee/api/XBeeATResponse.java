package com.fornsys.zigbee.xbee.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itaca.ztool.util.ByteUtils;
import com.itaca.ztool.util.DoubleByte;

public class XBeeATResponse extends XBeePacket {

    private final static Logger log = LoggerFactory.getLogger( XBeePacketStream.class );

	private int CMD;
	
	public XBeeATResponse(int[] payload) {
		super(XBeeCMD.SYS_AT_RESPONSE, payload);
		log.debug("Creating AT Response packet... type {}, length {}", ByteUtils.toBase16(payload[0]), payload.length);
		this.CMD = new DoubleByte(payload[2], payload[3]).get16BitValue();
		this.DATA = new int[payload.length - 5];
		for(int i=0; i < this.DATA.length; i++) {
			this.DATA[i] = payload[5+i];
		}
		log.debug("Data: "+ByteUtils.toBase16(this.DATA));
	}

	public String getData() {
		return ByteUtils.toChar(this.DATA);
	}
	
	public int[] getDataBytes() {
		return this.DATA;
	}
	
	public int getCMD() {
		return this.CMD;
	}
}
