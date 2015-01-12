package com.fornsys.zigbee.xbee.driver.impl;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fornsys.zigbee.xbee.api.XBeeATCommand;
import com.fornsys.zigbee.xbee.api.XBeeATResponse;
import com.fornsys.zigbee.xbee.api.XBeeCMD;
import com.fornsys.zigbee.xbee.api.XBeeException;
import com.fornsys.zigbee.xbee.api.XBeePacket;
import com.fornsys.zigbee.xbee.api.XBeeZdoPacket;
import com.fornsys.zigbee.xbee.api.zdo.XBee_ZDO_IEEE_ADDR_REQ;
import com.fornsys.zigbee.xbee.api.zdo.XBee_ZDO_IEEE_ADDR_REQ_SRSP;
import com.fornsys.zigbee.xbee.api.zdo.ZDO_END_DEVICE_ANNCE_IND;
import com.fornsys.zigbee.xbee.api.zdo.ZDO_STATE_CHANGE_IND;
import com.fornsys.zigbee.xbee.driver.zic.WaitForCommand;
import com.itaca.ztool.api.af.AF_DATA_CONFIRM;
import com.itaca.ztool.api.af.AF_DATA_REQUEST;
import com.itaca.ztool.api.af.AF_REGISTER;
import com.itaca.ztool.api.af.AF_REGISTER_SRSP;
import com.itaca.ztool.api.zdo.ZDO_ACTIVE_EP_REQ;
import com.itaca.ztool.api.zdo.ZDO_ACTIVE_EP_RSP;
import com.itaca.ztool.api.zdo.ZDO_BIND_REQ;
import com.itaca.ztool.api.zdo.ZDO_BIND_RSP;
import com.itaca.ztool.api.zdo.ZDO_IEEE_ADDR_REQ;
import com.itaca.ztool.api.zdo.ZDO_IEEE_ADDR_RSP;
import com.itaca.ztool.api.zdo.ZDO_MGMT_LQI_REQ;
import com.itaca.ztool.api.zdo.ZDO_MGMT_LQI_RSP;
import com.itaca.ztool.api.zdo.ZDO_NODE_DESC_REQ;
import com.itaca.ztool.api.zdo.ZDO_NODE_DESC_RSP;
import com.itaca.ztool.api.zdo.ZDO_SIMPLE_DESC_REQ;
import com.itaca.ztool.api.zdo.ZDO_SIMPLE_DESC_RSP;
import com.itaca.ztool.api.zdo.ZDO_UNBIND_REQ;
import com.itaca.ztool.api.zdo.ZDO_UNBIND_RSP;

import gnu.io.CommPortIdentifier;
import it.cnr.isti.zigbee.dongle.api.AFMessageListner;
import it.cnr.isti.zigbee.dongle.api.AnnounceListner;
import it.cnr.isti.zigbee.dongle.api.DriverStatus;
import it.cnr.isti.zigbee.dongle.api.NetworkMode;
import it.cnr.isti.zigbee.dongle.api.SimpleDriver;

public class DriverXBee implements Runnable, SimpleDriver {

    private final static Logger logger = LoggerFactory.getLogger(DriverXBee.class);

    private final int RESEND_TIMEOUT;
    private final int RESEND_MAX_RETRY;
    private final boolean RESEND_ONLY_EXCEPTION;

    public static final int RESEND_TIMEOUT_DEFAULT = 1000;
    public static final String RESEND_TIMEOUT_KEY = "zigbee.driver.xbee.resend.timeout";

    public static final int RESEND_MAX_RESEND_DEFAULT = 3;
    public static final String RESEND_MAX_RESEND_KEY = "zigbee.driver.xbee.resend.max";

    public static final boolean RESEND_ONLY_EXCEPTION_DEFAULT = true;
    public static final String RESEND_ONLY_EXCEPTION_KEY = "zigbee.driver.xbee.resend.exceptionally";

    private final int TIMEOUT;
    public static final int DEFAULT_TIMEOUT = 5000;
    public static final String TIMEOUT_KEY = "zigbee.driver.xbee.timeout";

    private volatile DriverStatus state;
    private String port;
    private int rate;
    
    private Thread driver;
    private XBeeSerialHandler hardWare;
    
    private HashSet<AnnounceListner> announceListeners;
    private final AnnounceListenerFilter announceListener = new AnnounceListenerFilter(
            announceListeners);
    private final HashMap<Class<?>, Thread> conversation3Way = new HashMap<Class<?>, Thread>();
    
    private class AnnounceListenerFilter implements AsynchronousCommandListener {

        private final Collection<AnnounceListner> listners;

        private AnnounceListenerFilter(Collection<AnnounceListner> list) {
            listners = list;
        }

        public void receivedAsynchronousCommand(XBeePacket packet) {
            if (packet.isError())
                return;
            if( !(packet instanceof XBeeZdoPacket) ) {
            	return;
            }
            XBeeZdoPacket zPacket = (XBeeZdoPacket)packet;
            if (zPacket.getCMD().get16BitValue() == XBeeCMD.ZDO_END_DEVICE_ANNCE_IND) {
                logger.debug("Recieved announce message {} value is {}",
                        packet.getClass(), packet);
                ZDO_END_DEVICE_ANNCE_IND annunce = (ZDO_END_DEVICE_ANNCE_IND) zPacket;
                for (AnnounceListner l : listners) {
                    l.notify(annunce.SrcAddr, annunce.IEEEAddr,
                            annunce.NwkAddr, annunce.Capabilities);
                }
            } else if (zPacket.getCMD().get16BitValue() == XBeeCMD.ZDO_STATE_CHANGE_IND) {
                try {
                    ZDO_STATE_CHANGE_IND p = ((ZDO_STATE_CHANGE_IND) zPacket);
                    /*
                     * DEV_HOLD=0x00, // Initialized - not started automatically
                     * DEV_INIT=0x01, // Initialized - not connected to anything
                     * DEV_NWK_DISC=0x02, // Discovering PAN's to join
                     * DEV_NWK_JOINING=0x03, // Joining a PAN DEV_NWK_=0x04, //
                     * ReJoining a PAN, only for end-devices
                     * DEV_END_DEVICE_UNAUTH=0x05, // Joined but not yet
                     * authenticated by trust center DEV_END_DEVICE=0x06, //
                     * Started as device after authentication DEV_ROUTER=0x07,
                     * // Device joined, authenticated and is a router
                     * DEV_COORD_STARTING=0x08, // Started as Zigbee Coordinator
                     * DEV_ZB_COORD=0x09, // Started as Zigbee Coordinator
                     * DEV_NWK_ORPHAN=0x0A // Device has lost information about
                     * its parent
                     */
                    switch (p.State) {
                        case 0 :
                            logger.debug("Initialized - not started automatically");
                            break;
                        case 1 :
                            logger.debug("Initialized - not connected to anything");
                            break;
                        case 2 :
                            logger.debug("Discovering PANs to join or waiting for permit join");
                            break;
                        case 3 :
                            logger.debug("Joining a PAN");
                            break;
                        case 4 :
                            logger.debug("Rejoining a PAN, only for end-devices");
                            break;
                        case 5 :
                            logger.debug("Joined but not yet authenticated by trust center");
                            break;
                        case 6 :
                            logger.debug("Started as device after authentication");
                            break;
                        case 7 :
                            logger.debug("Device joined, authenticated and is a router");
                            break;
                        case 8 :
                            logger.debug("Starting as Zigbee Coordinator");
                            break;
                        case 9 :
                            logger.debug("Started as Zigbee Coordinator");
                            break;
                        case 10 :
                            logger.debug("Device has lost information about its parent");
                            break;
                        default :
                            break;
                    }
                } catch (Exception ex) {
                    // ignored
                }
            }
        }
    }

    public void run() {
        logger.info("Initializing");
        setState(DriverStatus.HARDWARE_INITIALIZING);
        if (initializeHardware() == true) {
            setState(DriverStatus.HARDWARE_READY);
        } else {
            close();
            return;
        }

        setState(DriverStatus.NETWORK_INITIALIZING);
        setState(DriverStatus.NETWORK_READY);
	}

	/**
	 * Empty method stub. This is configured using X-CTU.
	 */
	public void setZigBeeNodeMode(NetworkMode m) {
		logger.info("ZigBeeNodeMode ignored. Configure using X-CTU.");
	}

	/**
	 * Empty method stub. This is configured using X-CTU.
	 */
	public void setZigBeeNetwork(byte ch, short panId) {
		logger.info("ZigBeeNetwork ignored. Configure using X-CTU.");
	}

	public void setSerialPort(String serialName, int bitRate) {
        if (state != DriverStatus.CLOSED) {
            throw new IllegalStateException("Serial port can be changed only "
                    + "if driver is CLOSED while it is:" + state);
        }
        port = serialName;
        rate = bitRate;
	}

	public void open(boolean cleanCache) {
        open();
	}

	public void open() {
        if (state == DriverStatus.CLOSED) {
            state = DriverStatus.CREATED;
            driver = new Thread(this);
            driver.setName(buildDriverThreadName(port, rate));
            driver.start();
        } else {
            throw new IllegalStateException(
                    "Driver already opened, current status is:" + state);
        }
	}

	public void close() {
        if (state == DriverStatus.CLOSED) {
            logger.debug("Already CLOSED");
            return;
        }
        logger.info("Closing");
        if (Thread.currentThread() != driver) {
            logger.debug("Waiting for initialization operation to complete before closing.");
            try {
                driver.join();
            } catch (InterruptedException ignored) {
            }
        } else {
            logger.debug("Self closing");
        }
        if (state == DriverStatus.NETWORK_READY) {
            logger.debug("Closing NETWORK");
            setState(DriverStatus.HARDWARE_READY);
        }
        if (state == DriverStatus.HARDWARE_READY || state == DriverStatus.NETWORK_INITIALIZING ) {
            logger.debug("Closing HARDWARE");
            hardWare.close();
            setState(DriverStatus.CREATED);
        }
        if (state == DriverStatus.CREATED) {
            setState(DriverStatus.CLOSED);
        }
        logger.info("Closed");
	}

	public ZDO_IEEE_ADDR_RSP sendZDOIEEEAddressRequest(ZDO_IEEE_ADDR_REQ request) {
        if (waitForNetwork() == false)
            return null;
        ZDO_IEEE_ADDR_RSP result = null;
        XBee_ZDO_IEEE_ADDR_REQ xRequest = new XBee_ZDO_IEEE_ADDR_REQ(request);

        waitAndLock3WayConversation(xRequest);
        final WaitForCommand waiter = new WaitForCommand(
                XBeeCMD.ZDO_IEEE_ADDR_RSP, hardWare);

        logger.debug("Sending ZDO_IEEE_ADDR_REQ {}", request);
        XBee_ZDO_IEEE_ADDR_REQ_SRSP response = (XBee_ZDO_IEEE_ADDR_REQ_SRSP) sendSynchronous(
                hardWare, xRequest);
        if (response == null || response.getStatus() != 0) {
            logger.debug("ZDO_IEEE_ADDR_REQ failed, received {}", response);
            waiter.cleanup();
        } else {
            result = new ZDO_IEEE_ADDR_RSP(
            		((XBeeZdoPacket) waiter.getCommand(TIMEOUT)).getZdoPayload());
        }
        unLock3WayConversation(xRequest);
        return result;
	}

	public ZDO_NODE_DESC_RSP sendZDONodeDescriptionRequest(
			ZDO_NODE_DESC_REQ request) {
		// TODO Auto-generated method stub
		return null;
	}

	public ZDO_ACTIVE_EP_RSP sendZDOActiveEndPointRequest(
			ZDO_ACTIVE_EP_REQ request) {
		// TODO Auto-generated method stub
		return null;
	}

	public ZDO_SIMPLE_DESC_RSP sendZDOSimpleDescriptionRequest(
			ZDO_SIMPLE_DESC_REQ request) {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean addAnnounceListener(AnnounceListner listner) {
        if (announceListeners.isEmpty() && isHardwareReady()) {
            hardWare.addAsynchronousCommandListener(announceListener);
        }
        return announceListeners.add(listner);
	}

	public boolean removeAnnounceListener(AnnounceListner listener) {
        boolean result = announceListeners.remove(listener);
        if (announceListeners.isEmpty() && isHardwareReady()) {
            hardWare.removeAsynchronousCommandListener(announceListener);
        }
        return result;
	}

	public AF_REGISTER_SRSP sendAFRegister(AF_REGISTER request) {
		// TODO Auto-generated method stub
		return null;
	}

	public AF_DATA_CONFIRM sendAFDataRequest(AF_DATA_REQUEST request) {
		// TODO Auto-generated method stub
		return null;
	}

	public ZDO_BIND_RSP sendZDOBind(ZDO_BIND_REQ request) {
		// TODO Auto-generated method stub
		return null;
	}

	public ZDO_UNBIND_RSP sendZDOUnbind(ZDO_UNBIND_REQ request) {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean removeAFMessageListener(AFMessageListner listner) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean addAFMessageListner(AFMessageListner listner) {
		// TODO Auto-generated method stub
		return false;
	}

	public ZDO_MGMT_LQI_RSP sendLQIRequest(ZDO_MGMT_LQI_REQ request) {
		// TODO Auto-generated method stub
		return null;
	}

	public void addCustomDevice(String endpointNumber, String profileID,
			String deviceID, String version, String inputClusters,
			String outputCluster) {
		// TODO Auto-generated method stub
		
	}

	public long getExtendedPanId() {
        if (waitForNetwork() == false) {
            logger.info(
                    "Failed to reach the {} level: getCurrentChannel() failed",
                    DriverStatus.NETWORK_READY);
            return -1;
        }

        int[] result = getDeviceInfo(XBeeCMD.SYS_PARAM_OP_PAN_ID_EXT);

        if (result == null) {
            return -1;
        } else {
            return result[0];
        }
	}

	public long getIEEEAddress() {
        if (waitForNetwork() == false) {
            logger.info(
                    "Failed to reach the {} level: getCurrentChannel() failed",
                    DriverStatus.NETWORK_READY);
            return -1;
        }

        int[] result = getDeviceInfo(XBeeCMD.SYS_PARAM_SD_HIGH);

        if (result == null || result.length < 4) {
            return -1;
        } else {
        	Long address = 0x0L;
        	for(int i=result.length-4; i < result.length; i++) {
        		address = (address << 4) | result[i];
        	}
        	
        	result = getDeviceInfo(XBeeCMD.SYS_PARAM_SD_LOW);
        	
        	if( result == null || result.length < 4) {
        		return -1;
        	}
        	for(int i=result.length-4; i < result.length; i++) {
        		address = (address << 4) | result[i];
        	}
        	
            return address;
        }
	}

	public int getCurrentPanId() {
        if (waitForNetwork() == false) {
            logger.info(
                    "Failed to reach the {} level: getCurrentChannel() failed",
                    DriverStatus.NETWORK_READY);
            return -1;
        }

        int[] result = getDeviceInfo(XBeeCMD.SYS_PARAM_OP_PAN_ID);

        if (result == null) {
            return -1;
        } else {
            return result[0];
        }
	}

	public int getCurrentChannel() {
        if (waitForNetwork() == false) {
            logger.info(
                    "Failed to reach the {} level: getCurrentChannel() failed",
                    DriverStatus.NETWORK_READY);
            return -1;
        }

        int[] result = getDeviceInfo(XBeeCMD.SYS_PARAM_OP_CHANNEL);

        if (result == null) {
            return -1;
        } else {
            return result[0];
        }
	}

	public int getCurrentState() {
		// TODO Not sure what to do with these, as they seem ZTool specific...
		return 0;
	}

	public int getZigBeeNodeMode() {
		// TODO Can probably map to ZTool expected values... why not use NetworkMode enum?
		return 0; // NetworkMode.COORDINATOR ????
	}

	public DriverStatus getDriverStatus() {
		return state;
	}

    private void setState(DriverStatus value) {
        logger.info("State changed from {} to {}", this.state, value);
        synchronized (this) {
            state = value;
            notifyAll();
        }/*
        // TODO: Do I need to do this?
        if (state == DriverStatus.HARDWARE_READY) {
            postHardwareEnabled();
        }*/
    }

    private String buildDriverThreadName(String serialPort, int bitrate) {
        return "SimpleDriver[" + serialPort + "," + bitrate + "]";
    }
    
    @SuppressWarnings("unchecked")
	private boolean initializeHardware() {
        String portToOpen = null;
        if ("auto".equalsIgnoreCase(port)) {
            logger.info("Automatic discovery the dongle port by inspecting all the serial ports...");
            Enumeration<CommPortIdentifier> ports = CommPortIdentifier
                    .getPortIdentifiers();
            while (ports.hasMoreElements()) {
                CommPortIdentifier com = ports.nextElement();
                if (initializeHardware(com.getName(), rate)) {
                    portToOpen = com.getName();
                    Thread.currentThread().setName(
                            buildDriverThreadName(portToOpen, rate));
                    break;
                }
            }
            if (portToOpen == null) {
                logger.error("Automatic discovery FAILED! the dongle couldn't be find on any port: it may be frozen");
                return false;
            }
        } else {
            if (initializeHardware(port, rate) == true) {
                portToOpen = port;
            } else {
                logger.error(
                        "Failed to intialize the dongle on port {} at rate {}",
                        port, rate);
                return false;
            }
        }

        hardWare = new XBeeSerialHandler();
        try {
            hardWare.open(portToOpen, rate);
        } catch (XBeeException e) {
            logger.error(
                    "The port was already open in advance but we can't open it now",
                    e);
            hardWare.close();
            return false;
        }

        return true;
    }

    private boolean initializeHardware(String portName, int baudRate) {
		boolean result = false;
		final int received[] = new int[1];
		final XBeeSerialHandler probingDriver = new XBeeSerialHandler();
		final PacketListener monitor = new PacketListener() {
			public void packetReceived(XBeePacket packet) {
				logger.debug("Received initializing SYS VERSION candidate");
				if (packet.getFrameType() == XBeeCMD.SYS_AT_RESPONSE) {
					logger.debug("Initializing Hardware: Received correctly SYS_VERSION_RESPONSE");
					synchronized (received) {
						received[0] = 3;
					}
				} else if (packet.isError()) {
					logger.debug(
							"Initializing Hardware: Received erroneous packet: {}",
							packet.getErrorMsg());
					synchronized (received) {
						received[0] += 1;
					}
				} else {
					logger.debug("Initializing Hardware: Received {}", packet
							.getClass().getName());
					synchronized (received) {
						received[0] += 1;
					}
				}
			}
		};
		probingDriver.addPacketListener(monitor);
		try {
			probingDriver.open(portName, baudRate);
			probingDriver.sendPacket(XBeeATCommand.getParameterReq(XBeeCMD.SYS_PARAM_FIRMWARE_VER));
			final long ready = System.currentTimeMillis() + TIMEOUT; // manlio
																		// 5000;
			while (ready > System.currentTimeMillis()) {
				synchronized (received) {
					if (received[0] == 3) {
						logger.debug("Received initializing SYS VERSION");
						break;
					}
				}

				try {
					Thread.sleep(500);
				} catch (InterruptedException ignored) {
					logger.debug("Exception SYS VERSION");
				}
			}
			logger.debug("End of waiting for SYS VERSION");
			synchronized (received) {
				if (received[0] == 3) {
					logger.debug("Succeeded initializing SYS VERSION");
					result = true;
				}
			}
		} catch (XBeeException e) {
			logger.info("Unable to open serial port: {}", portName);
			logger.error("Unable to open serial port, due to:", e);
		} catch (IOException e) {
			logger.error("Hardware initialization failed", e);
		} finally {
		    probingDriver.close();
		    probingDriver.removePacketListener(monitor);
		}
		return result;
	}

    /**
     * @param request
     */
    private void waitAndLock3WayConversation(XBeePacket request) {
        synchronized (conversation3Way) {
            Class<?> clz = request.getClass();
            Thread requestor = null;
            while ((requestor = conversation3Way.get(clz)) != null) {
                if (requestor.isAlive() == false) {
                    logger.error("Thread {} whom requested {} DIED before unlocking the conversation");
                    logger.debug("The thread {} who was waiting for {} to complete DIED, so we have to remove the lock");
                    conversation3Way.put(clz, null);
                    break;
                }
                logger.debug(
                        "{} is waiting for {} to complete which was issued by {} to complete",
                        new Object[]{Thread.currentThread(), clz, requestor});
                try {
                    conversation3Way.wait();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                } catch (IllegalMonitorStateException ex) {
                    ex.printStackTrace();
                }
            }
            conversation3Way.put(clz, Thread.currentThread());
        }
    }

    /**
     * Release the lock held for the 3-way communication
     *
     * @param request
     */
    private void unLock3WayConversation(XBeePacket request) {
        Class<?> clz = request.getClass();
        Thread requestor = null;
        synchronized (conversation3Way) {
            requestor = conversation3Way.get(clz);
            conversation3Way.put(clz, null);
            conversation3Way.notifyAll();
        }
        if (requestor == null) {
            logger.error(
                    "LOCKING BROKEN - SOMEONE RELEASE THE LOCK WITHOUT LOCKING IN ADVANCE for {}",
                    clz);
        } else if (requestor != Thread.currentThread()) {
            logger.error("Thread {} stolen the answer of {} waited by {}",
                    new Object[]{Thread.currentThread(), clz, requestor});
        }
    }

    public DriverXBee(String serialPort, int bitRate, long timeout) {
        int aux = RESEND_TIMEOUT_DEFAULT;
        try {
            aux = Integer.parseInt(System.getProperty(RESEND_TIMEOUT_KEY));
            logger.debug("Using RESEND_TIMEOUT set from enviroment {}", aux);
        } catch (NumberFormatException ex) {
            logger.debug("Using RESEND_TIMEOUT set as DEFAULT {}", aux);
        }
        RESEND_TIMEOUT = aux;

        aux = (int) Math.max(DEFAULT_TIMEOUT, timeout);
        try {
            aux = Integer.parseInt(System.getProperty(TIMEOUT_KEY));
            logger.debug("Using TIMEOUT set from enviroment {}", aux);
        } catch (NumberFormatException ex) {
            logger.debug("Using TIMEOUT set as DEFAULT {}ms", aux);
        }
        TIMEOUT = aux;

        aux = RESEND_MAX_RESEND_DEFAULT;
        try {
            aux = Integer.parseInt(System.getProperty(RESEND_MAX_RESEND_KEY));
            logger.debug("Using RESEND_MAX_RETRY set from enviroment {}", aux);
        } catch (NumberFormatException ex) {
            logger.debug("Using RESEND_MAX_RETRY set as DEFAULT {}", aux);
        }
        RESEND_MAX_RETRY = aux;

        String p = System.getProperty(RESEND_ONLY_EXCEPTION_KEY);
        if (p != null ) {
            RESEND_ONLY_EXCEPTION = Boolean.parseBoolean(p);
            logger.debug("Using RESEND_ONLY_EXCEPTION set from environment {}", RESEND_ONLY_EXCEPTION);
        } else {
            RESEND_ONLY_EXCEPTION = RESEND_ONLY_EXCEPTION_DEFAULT;
            logger.debug("Using RESEND_ONLY_EXCEPTION set as DEFAULT {}", RESEND_ONLY_EXCEPTION);
        }

        state = DriverStatus.CLOSED;
        setSerialPort(serialPort, bitRate);
    }

    private int[] getDeviceInfo(int type) {
        XBeeATResponse response = (XBeeATResponse) sendSynchronous(
                hardWare, XBeeATCommand.getParameterReq(type));

        if (response == null) {
            logger.debug("Failed getDeviceInfo for {} due to null value", type);
            return null;
        } else if (response.getCMD() != type) {
            logger.debug(
                    "Failed getDeviceInfo for {} non matching response returned {}",
                    type, response.getCMD());
            return null;
        } else {
            logger.debug("Successed getDeviceInfo for {}", type);
            return response.getDataBytes();
        }
    }

    private boolean waitForNetwork() {
        synchronized (this) {
            while (state != DriverStatus.NETWORK_READY
                    && state != DriverStatus.CLOSED) {
                logger.debug("Waiting for NETWORK to become ready");
                try {
                    wait();
                } catch (InterruptedException ignored) {
                }
            }
            return isNetworkReady();
        }
    }

    private boolean isNetworkReady() {
        synchronized (this) {
            return state.ordinal() >= DriverStatus.NETWORK_READY.ordinal()
                    && state.ordinal() < DriverStatus.CLOSED.ordinal();
        }
    }
    
    private boolean isHardwareReady() {
    	synchronized(this) {
    		return state.ordinal() >= DriverStatus.HARDWARE_READY.ordinal()
    				&& state.ordinal() < DriverStatus.CLOSED.ordinal();
    	}
    }

    private XBeePacket sendSynchronous(final XBeeSerialHandler hwDriver,
            final XBeePacket request) {
        final XBeePacket[] response = new XBeePacket[]{null};
        // final int TIMEOUT = 1000, MAX_SEND = 3;
        int sending = 1;

        logger.info("Sending Synchrouns {}", request.getClass().getName());

        SynchronousCommandListener listener = new SynchronousCommandListener() {

            public void receivedCommandResponse(XBeePacket packet) {
                logger.info("Received Synchrouns Response {}", packet
                        .getClass().getName());
                synchronized (response) {
                    response[0] = packet;
                    response.notify();
                }
            }
        };

        while (sending <= RESEND_MAX_RETRY) {
            try {
                try {
                    hwDriver.sendSynchronousCommand(request, listener,
                            RESEND_TIMEOUT);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                logger.info("Sent {} during the {}-th tentative", request
                        .getClass().getName(), sending);
                synchronized (response) {
                    long wakeUpTime = System.currentTimeMillis()
                            + RESEND_TIMEOUT;
                    while (response[0] == null
                            && wakeUpTime > System.currentTimeMillis()) {
                        final long sleeping = wakeUpTime
                                - System.currentTimeMillis();
                        logger.debug(
                                "Waiting for synchronous command up to {}ms till {} Unixtime",
                                sleeping, wakeUpTime);
                        if (sleeping <= 0) {
                            break;
                        }
                        try {
                            response.wait(sleeping);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
                if (response[0] != null) {
                    logger.debug(
                            "Received synchronous command {} before timeout",
                            response[0]);
                } else {
                    logger.debug(
                            "Timeout fired and no synchronous command received",
                            response[0]);
                }
                if (RESEND_ONLY_EXCEPTION) {
                    break;
                } else {
                    logger.info("Failed to send {} during the {}-th tentative",
                            request.getClass().getName(), sending);
                    sending++;
                }
            } catch (Exception ignored) {
                logger.info("Failed to send {} during the {}-th tentative",
                        request.getClass().getName(), sending);
                logger.debug("Sending operation failed due to ", ignored);
                sending++;
            }
        }

        return response[0];
    }

}
