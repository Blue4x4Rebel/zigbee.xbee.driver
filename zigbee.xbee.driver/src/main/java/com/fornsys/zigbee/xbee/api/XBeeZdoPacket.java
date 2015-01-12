package com.fornsys.zigbee.xbee.api;

import java.util.Arrays;

import com.itaca.ztool.api.ZToolAddress16;
import com.itaca.ztool.api.ZToolAddress64;
import com.itaca.ztool.util.DoubleByte;

public class XBeeZdoPacket extends XBeePacket {
	/**
	 * The makeup of a ZDO packet is as follows:
	 	0x00 - API ID (transmit request)
		0x00 – frame ID (set to 0 to disable transmit status)
		0x00000000 0000FFFF – 64-bit address for a broadcast transmission 0xFFFE – 16-bit address for a broadcast transmission
		0x00 – source endpoint (ZDO endpoint)
		0x00 – destination endpoint (ZDO endpoint)
		0x0001 - Cluster ID (IEEE Address Request)
		0x0000 – Profile ID (ZigBee Device Profile ID)
		0x00 – Broadcast radius
		0x00 – Transmit options
		
	   And then the ZDO payload	
	
	 */
	
	private DoubleByte CMD;
	
	private ZToolAddress64 extendedAddress;
	private ZToolAddress16 address;
	private static final int sourceEndpoint = 0x00;
	private static final int destinationEndpoint = 0x00;
	private DoubleByte clusterId;
	private DoubleByte profileId;
	private int broadcastRadius;
	private int transmitOptions;
	private int[] zdoPayload;
	
	private static short transSeq = 0;
	
	public XBeeZdoPacket(int[] frameData) {
		super(XBeeCMD.EXPLICIT_TRANSMIT, frameData);
		this.extendedAddress = new ZToolAddress64((byte)frameData[9], (byte)frameData[8], (byte)frameData[7], 
				(byte)frameData[6], (byte)frameData[5], (byte)frameData[4], (byte)frameData[3], 
				(byte)frameData[2]);
		this.address = new ZToolAddress16(Arrays.copyOfRange(frameData, 11, 10));
		this.clusterId = new DoubleByte(frameData[13], frameData[12]);
		this.profileId = new DoubleByte(frameData[15], frameData[14]);
	}
	
	//public XBeeZdoPacket()
	
	public XBeeZdoPacket(short counter, ZToolAddress64 extendedAddress, ZToolAddress16 address, DoubleByte clusterId, 
			DoubleByte profileId, int broadcastRadius, int transmitOptions, int[] zdoPayload) {
		super(XBeeCMD.EXPLICIT_TRANSMIT, buildPacket(counter,
				extendedAddress, address, clusterId, profileId,
				broadcastRadius, transmitOptions, zdoPayload));
		this.extendedAddress = extendedAddress;
		this.address = address;
		this.clusterId = clusterId;
		this.profileId = profileId;
		this.broadcastRadius = broadcastRadius;
		this.transmitOptions = transmitOptions;
		this.zdoPayload = zdoPayload;
	}
	
	public ZToolAddress64 getExtendedAddress() {
		return extendedAddress;
	}

	public ZToolAddress16 getAddress() {
		return address;
	}

	public static int getSourceendpoint() {
		return sourceEndpoint;
	}

	public static int getDestinationendpoint() {
		return destinationEndpoint;
	}

	public DoubleByte getClusterId() {
		return clusterId;
	}

	public DoubleByte getProfileId() {
		return profileId;
	}

	public int getBroadcastRadius() {
		return broadcastRadius;
	}

	public int getTransmitOptions() {
		return transmitOptions;
	}

	public int[] getZdoPayload() {
		return zdoPayload;
	}
	
	public static short getTransactionSequence() {
		return ++transSeq;
	}

	private static int[] buildPacket(short counter, ZToolAddress64 extendedAddress, ZToolAddress16 address, DoubleByte clusterId, 
			DoubleByte profileId, int broadcastRadius, int transmitOptions, int[] zdoPayload) {
		int[] payload = new int[20+zdoPayload.length];
		payload[0] = XBeeCMD.EXPLICIT_TRANSMIT;
		payload[1] = counter < 0 ? XBeePacket.nextCounter() : counter;
		for(int i=0; i<8; i++) {
			payload[2+i] = extendedAddress.getAddress()[i];
		}
		payload[10] = address.getLsb();
		payload[11] = address.getMsb();
		payload[12] = XBeeZdoPacket.sourceEndpoint;
		payload[13] = XBeeZdoPacket.destinationEndpoint;
		payload[14] = clusterId.getLsb();
		payload[15] = clusterId.getMsb();
		payload[16] = profileId.getLsb();
		payload[17] = profileId.getMsb();
		payload[18] = broadcastRadius;
		payload[19] = transmitOptions;
		for(int i=0; i < zdoPayload.length; i++) {
			payload[20+i] = zdoPayload[i];
		}
		
		return payload;
	}
	
	public DoubleByte getCMD() {
		return this.CMD;
	}
}
