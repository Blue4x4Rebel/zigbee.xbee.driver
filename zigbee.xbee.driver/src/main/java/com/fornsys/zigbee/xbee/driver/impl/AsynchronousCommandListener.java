package com.fornsys.zigbee.xbee.driver.impl;

import com.fornsys.zigbee.xbee.api.XBeePacket;

public interface AsynchronousCommandListener {
	void receivedAsynchronousCommand(XBeePacket packet);

}
