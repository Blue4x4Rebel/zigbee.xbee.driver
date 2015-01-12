package com.fornsys.zigbee.xbee.api;

public interface XBeePacketHandler {
	/**
	 * A callback used by {@link XBeePacketParser} for notifying that a new packet is arrived
	 * <b>NOTE</b>: Bad packet would not be notified
	 * 
	 * @param response the new {@link XBeePacket} parsed by {@link XBeePacketParser}
	 */
	public void handlePacket(XBeePacket response);
	
	/**
	 * A callback used by {@link XBeePacketParser} for notifying that an {@link Exception} has<br>
	 * been thrown
	 * 
	 * @param th
	 */
	public void error(Throwable th);
}
