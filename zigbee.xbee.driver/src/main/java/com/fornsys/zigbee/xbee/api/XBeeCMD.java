package com.fornsys.zigbee.xbee.api;

/**
*
* @author <a href="mailto:josh@fornwall.com">Josh Fornwall</a>
*/
public class XBeeCMD {
	/// <name>Firmware Version</name>
    /// <summary>The firmware version returns 4 hexadecimal values (2 bytes) "ABCD". Digits ABC are	the main release number and D is the revision number from the main release. "B" is a variant designator.</summary>
	
	// Packet Types
	public static final short SYS_AT_REQUEST = 0x08;
	public static final short SYS_AT_RESPONSE = 0x88;
	public static final short SYS_ERROR = -1;
	public static final short EXPLICIT_TRANSMIT = 0x11;

	// System Parameters
	public static final int SYS_PARAM_FIRMWARE_VER = 0x5652;	// VR
	public static final int SYS_PARAM_OP_PAN_ID = 0x4f49;		// OI
	public static final int SYS_PARAM_OP_PAN_ID_EXT = 0x4f50;	// OP
	public static final int SYS_PARAM_OP_CHANNEL = 0x4348;		// CH
	public static final int SYS_PARAM_SD_HIGH = 0x5348;			// SH
	public static final int SYS_PARAM_SD_LOW = 0x534c;			// SL
	
	// ZDO Commands
	public static final int ZDO_END_DEVICE_ANNCE_IND = 0xc145;
	public static final int ZDO_STATE_CHANGE_IND = 0xc045;
	public static final int ZDO_IEEE_ADDR_RSP = 0x8045;
}
