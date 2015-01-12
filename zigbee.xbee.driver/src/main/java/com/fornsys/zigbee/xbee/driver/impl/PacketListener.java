package com.fornsys.zigbee.xbee.driver.impl;

import com.fornsys.zigbee.xbee.api.XBeePacket;

public interface PacketListener {
	void packetReceived(XBeePacket packet);

}
