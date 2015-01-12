package com.fornsys.zigbee.xbee.api.zdo;

import com.fornsys.zigbee.xbee.api.XBeeZdoPacket;
import com.itaca.ztool.api.ZToolAddress16;
import com.itaca.ztool.api.ZToolAddress64;

public class ZDO_END_DEVICE_ANNCE_IND extends XBeeZdoPacket {
    public int Capabilities;
    public ZToolAddress64 IEEEAddr;
    public ZToolAddress16 NwkAddr;
    public ZToolAddress16 SrcAddr;

    public ZDO_END_DEVICE_ANNCE_IND(int[] framedata)
    {
    	super(framedata);
    	int[] zdoFrame = this.getZdoPayload();
        this.SrcAddr = new ZToolAddress16(zdoFrame[1],zdoFrame[0]);
        this.NwkAddr = new ZToolAddress16(zdoFrame[3],zdoFrame[2]);
        byte[] bytes=new byte[8];
        for(int i=0;i<8;i++){
            bytes[i]=(byte) zdoFrame[11-i];
        }
        this.IEEEAddr = new ZToolAddress64(bytes);
        this.Capabilities = zdoFrame[12];
    }

}
