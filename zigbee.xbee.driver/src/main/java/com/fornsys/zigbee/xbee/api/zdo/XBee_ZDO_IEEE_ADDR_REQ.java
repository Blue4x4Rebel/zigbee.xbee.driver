package com.fornsys.zigbee.xbee.api.zdo;

import com.fornsys.zigbee.xbee.api.XBeeZdoPacket;
import com.itaca.ztool.api.ZToolAddress16;
import com.itaca.ztool.api.zdo.ZDO_IEEE_ADDR_REQ;

public class XBee_ZDO_IEEE_ADDR_REQ extends XBeeZdoPacket {

	private short shortAddress;
	private int reqType, startIdx;
	
	public XBee_ZDO_IEEE_ADDR_REQ(int[] frameData) {
		super(frameData);
		int[] zFrame = super.getZdoPayload();
		shortAddress = new ZToolAddress16(zFrame[2], zFrame[1]).get16BitValue();
		reqType = zFrame[3];
		startIdx = zFrame[4];
		// TODO Auto-generated constructor stub
	}
	
	public XBee_ZDO_IEEE_ADDR_REQ(ZDO_IEEE_ADDR_REQ zToolRequest) {
		// TODO Convert the sucker!
		super(zToolRequest.getPacket());
		shortAddress = zToolRequest.getShortAddress();
		reqType = zToolRequest.getRequestType().getValue();
		startIdx = zToolRequest.getStartIndex();
	}
	
	public short getShortAddress() {
		return shortAddress;
	}
	
	public int getRequestType() {
		return reqType;
	}
	
	public int getStartIndex() {
		return startIdx;
	}

	public enum RequestTypes {
		Single((byte)0x00), 
		Extended((byte)0x01);
		
		private byte value;
		private RequestTypes(byte val) {
			this.value = val;
		}
		
		byte getValue() {
			return this.value;
		}
	}
}
