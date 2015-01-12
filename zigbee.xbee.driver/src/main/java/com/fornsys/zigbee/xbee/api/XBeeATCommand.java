package com.fornsys.zigbee.xbee.api;

import com.itaca.ztool.util.DoubleByte;

public class XBeeATCommand {
	private static short counter = 1;
	
	public static XBeePacket getParameterReq(int parameter) {
		int[] frame = new int[4];
		frame[0] = XBeeCMD.SYS_AT_REQUEST;
		DoubleByte cmd = new DoubleByte(parameter);
		frame[1] = counter++;
		frame[2] = cmd.getMsb();
		frame[3] = cmd.getLsb();
		
		return new XBeePacket(XBeeCMD.SYS_AT_REQUEST, frame);
	}
	
	public static XBeePacket parseResponse(int[] payload) {
		return new XBeeATResponse(payload);
	}
}
