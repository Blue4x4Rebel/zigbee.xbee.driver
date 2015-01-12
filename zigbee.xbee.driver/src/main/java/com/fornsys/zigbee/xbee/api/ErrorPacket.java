package com.fornsys.zigbee.xbee.api;

public class ErrorPacket extends XBeePacket {
	String errorMsg;
	
	public ErrorPacket() {
		super(XBeeCMD.SYS_ERROR, new int[0]);
	}
	
	public void setErrorMsg(String msg) {
		this.errorMsg = msg;
	}
}
