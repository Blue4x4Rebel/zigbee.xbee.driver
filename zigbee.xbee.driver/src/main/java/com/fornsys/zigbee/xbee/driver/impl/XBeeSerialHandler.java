package com.fornsys.zigbee.xbee.driver.impl;

import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TooManyListenersException;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fornsys.zigbee.xbee.api.XBeeException;
import com.fornsys.zigbee.xbee.api.XBeePacket;
import com.fornsys.zigbee.xbee.api.XBeePacketHandler;
import com.fornsys.zigbee.xbee.api.XBeePacketParser;
import com.itaca.ztool.RxTxSerialComm;
import com.itaca.ztool.util.DoubleByte;

public class XBeeSerialHandler extends RxTxSerialComm implements XBeePacketHandler {

    private final static Logger logger = LoggerFactory.getLogger( XBeeSerialHandler.class );
    private String serialName;
    private int rate;
    private HashSet<PacketListener> pktListeners;
    private HashSet<AsynchronousCommandListener> asyncListeners;
    private XBeePacketParser parser;
    private Object parserLock = new Object();

    private static final boolean supportMultipleSynchronousCommand = false;
	private final Hashtable<Short, SynchronousCommandListener> pendingSREQ 
			= new Hashtable<Short, SynchronousCommandListener>();

	private final HashMap<SynchronousCommandListener, Long> timeouts = new HashMap<SynchronousCommandListener, Long>();

    
    public XBeeSerialHandler() {
    	pktListeners = new HashSet<>();
    }
    
	public void handlePacket(XBeePacket response) {
		// TODO Auto-generated method stub
		logger.debug(" DEFAULT PACKET HANDLER: "+response);
		// Maybe notify the other listeners?
        notifyPacketListeners( response );
	}

    protected void notifyPacketListeners( XBeePacket packet ) {
        //XXX Should we split to Multi-threaded notifier to speed up everything
        PacketListener[] copy;

        synchronized ( pktListeners ) {
            copy = pktListeners.toArray( new PacketListener[] {} );
        }

        for ( PacketListener listener : copy ) {
            try {
                listener.packetReceived( packet );
            }
            catch ( Throwable e ) {
                logger.error("Error genereated by notifyPacketListeners {}", e );
            }
        }
    }

	public void error(Throwable th) {
		// TODO Auto-generated method stub
		
	}
	
	public void open(String port, int baud) throws XBeeException {
        logger.debug( "Opening {} ", this.getClass() );
        serialName = port;
        rate = baud;
        /*
        milliSecondsPerPacket = Long.parseLong(
                System.getProperty( MILLISECONDS_PER_PACKET_KEY, MILLISECONDS_PER_PACKET_DEFAULT )
        );
        */

        logger.debug( "Opening port {}@{}", serialName, rate );
        try {
        	this.openSerialPort( serialName, rate );
        	parser = new XBeePacketParser( super.getInputStream(), this, parserLock );
        } catch( TooManyListenersException | IOException | PortInUseException | UnsupportedCommOperationException e ) {
        	logger.error("Failed to open Serial Port due Exception", e);
        	throw new XBeeException(e);
        }
        
        logger.debug( "Opened port {}@{}", serialName, rate );
        logger.debug( "Opened {}", this );
	}
	
	public void close() {
		if (parser != null) {
			parser.setDone(true);
			parser.interrupt();
			try {
				parser.getInternalThread().join();
			} catch (InterruptedException ie) {
			}
		}
		super.close();
	}

	@Override
	protected void handleSerialData() throws IOException {
        synchronized ( parser ) {
            parser.notify();
        }
	}

    public boolean addPacketListener( PacketListener listener ) {
        boolean result = false;
        synchronized ( pktListeners ) {
            result = pktListeners.add( listener );
        }
        return result;
    }

    public boolean removePacketListener( PacketListener listener ) {
        boolean result = false;
        synchronized ( pktListeners ) {
            result = pktListeners.remove( listener );
        }
        return result;
    }

    public void sendPacket( XBeePacket packet )
        throws IOException {
    	/*
        if ( milliSecondsPerPacket > 0 ) {
            forceMaxiumumPacketRate();
        }
*/
        //FIX Sending byte instead of int
        logger.debug( "Sending Packet {} {} ", packet.getClass(), packet.toString() );

        final int[] pck = packet.getPacket();
        final OutputStream out = this.getOutputStream();
        //Only a packet at the time can be sent, otherwise link communication will be mess up
        synchronized ( out ) {            
            for ( int i = 0; i < pck.length; i++ ) {
                out.write( pck[i] );
            }
            out.flush();
        }
    }
	
	public void sendSynchronousCommand(XBeePacket packet, SynchronousCommandListener listener, long timeout) throws IOException{
		if ( timeout == -1L ) {
			timeouts.put(listener, -1L);
		} else {
			final long expirationTime = System.currentTimeMillis() + timeout;
			timeouts.put(listener, expirationTime);
		}
		m_sendSynchrounsCommand(packet, listener);
	}
	
	private void m_sendSynchrounsCommand(XBeePacket packet, SynchronousCommandListener listener) throws IOException{
		// No need to check IDs...
		
		final DoubleByte cmdId = new DoubleByte(packet.getFID(), packet.getFrameType());
		final int value = (cmdId.getMsb() & 0xE0);
		if ( value != 0x20  ) {
			throw new IllegalArgumentException("You are trying to send a non SREQ packet. "
					+"Evaluated "+value+" instead of "+0x20+"\nPacket "+packet.getClass().getName()+"\n"+packet
			);
		}
//		profiler.info(" m_sendSynchrounsCommand(ZToolPacket packet, SynchrounsCommandListner listner): called");
		
		logger.debug("Preparing to send SynchronousCommand {} ", packet);
		cleanExpiredListener();
		if ( supportMultipleSynchronousCommand ) {
			synchronized (pendingSREQ) {
				final short id = (short) (cmdId.get16BitValue() & 0x1FFF);
				while(pendingSREQ.get(cmdId) != null) {
					try {
						logger.debug("Waiting for other request {} to complete", id);
						pendingSREQ.wait(500);
						cleanExpiredListener();
					} catch (InterruptedException ignored) {
					}
				}			
				//No listener register for this type of command, so no pending request. We can proceed
				logger.debug("Put pendingSREQ listener for {} command", id);
				pendingSREQ.put(id, listener);			
			}
		}else{
			synchronized (pendingSREQ) {
				final short id = (short) (cmdId.get16BitValue() & 0x1FFF);
				//while(pendingSREQ.isEmpty() == false || pendingSREQ.size() == 1 && pendingSREQ.get(id) == listner ) {
				while(pendingSREQ.isEmpty() == false ) {
					try {
						logger.debug("Waiting for other request to complete");
						pendingSREQ.wait(500);
						cleanExpiredListener();
					} catch (InterruptedException ignored) {
					}
				}			
				//No listener at all registered so this is the only command that we are waiting for a response
				logger.debug("Put pendingSREQ listener for {} command", id);
				pendingSREQ.put(id, listener);			
			}
		}
		logger.debug("Sending SynchrounsCommand {} ", packet);
//		profiler.info("m_sendSynchrounsCommand(ZToolPacket packet, SynchrounsCommandListner listner): acquired lock");
		sendPacket(packet);
	}

	private void cleanExpiredListener(){
		final long now = System.currentTimeMillis();
		final ArrayList<Short> expired = new ArrayList<Short>();
		synchronized (pendingSREQ) {
			Iterator<Entry<Short, SynchronousCommandListener>> i = pendingSREQ.entrySet().iterator();			
			while (i.hasNext()) {
				Entry<Short, SynchronousCommandListener> entry = i.next();
				
				final long expiration = timeouts.get(entry.getValue());
				if ( expiration != -1L && expiration < now ) {
					expired.add(entry.getKey());
				}
			}
			
			for (Short key : expired) {
				pendingSREQ.remove(key);
			}
			pendingSREQ.notifyAll();
		}
	}

	public boolean addAsynchronousCommandListener(
			AsynchronousCommandListener listener) {
    	boolean result = false;
    	synchronized (asyncListeners) {
        	result = asyncListeners.add(listener);
		}
    	return result;
	}

	public boolean removeAsynchronousCommandListener(
			AsynchronousCommandListener listener) {
    	boolean result = false;
    	synchronized (asyncListeners) {
    		result = asyncListeners.remove(listener);
		}
    	return result;
	}

}
