package com.fornsys.zigbee.xbee.api;

public class XBeeException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public XBeeException() {
		super();
	}
	
	public XBeeException(Exception e) {
		super(e);
	}
	
	public XBeeException(String msg) {
		super(msg);
	}
	
	public XBeeException(String msg, Exception e) {
		super(msg, e);
	}
}
