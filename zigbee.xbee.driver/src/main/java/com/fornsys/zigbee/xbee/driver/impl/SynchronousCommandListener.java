package com.fornsys.zigbee.xbee.driver.impl;

import com.fornsys.zigbee.xbee.api.XBeePacket;

public interface SynchronousCommandListener {
	public void receivedCommandResponse(XBeePacket packet);

}
