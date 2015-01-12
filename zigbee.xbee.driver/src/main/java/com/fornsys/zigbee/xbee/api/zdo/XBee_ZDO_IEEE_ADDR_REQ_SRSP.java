package com.fornsys.zigbee.xbee.api.zdo;

import com.fornsys.zigbee.xbee.api.XBeeZdoPacket;

public class XBee_ZDO_IEEE_ADDR_REQ_SRSP extends XBeeZdoPacket {
	private int status;
	
	public XBee_ZDO_IEEE_ADDR_REQ_SRSP(int[] frameData) {
		super(frameData);
		status = super.getZdoPayload()[3];
	}
	
	public int getStatus() {
		return status;
	}
}
