package com.fornsys.zigbee.xbee.driver;

import it.cnr.isti.osgi.util.OSGiProperties;
import it.cnr.isti.zigbee.dongle.api.ConfigurationProperties;
import it.cnr.isti.zigbee.dongle.api.SimpleDriver;

import java.util.Properties;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import com.fornsys.zigbee.xbee.driver.impl.DriverXBee;

public class Activator implements BundleActivator {

    private DriverXBee driver;
    private ServiceRegistration service;

    public void start(BundleContext bc) throws Exception {
        driver = new DriverXBee(
                OSGiProperties.getString(bc, ConfigurationProperties.COM_NAME_KEY, ConfigurationProperties.COM_NAME),
                OSGiProperties.getInt(bc, ConfigurationProperties.COM_BAUDRATE_KEY, 57600),
                OSGiProperties.getLong(bc, ConfigurationProperties.APPLICATION_MSG_TIMEOUT_KEY, ConfigurationProperties.APPLICATION_MSG_TIMEOUT)
        );
        Properties properties = new Properties();
        properties.put("zigbee.driver.id", DriverXBee.class.getName());
        properties.put("zigbee.supported.devices", new String[]{"xbee"});
        properties.put("zigbee.driver.type", "hardware");
        properties.put("zigbee.driver.mode", "real");
        service = bc.registerService(SimpleDriver.class.getName(), driver, properties);	
    
        driver.open();
        Thread.sleep(10000);
        driver.close();
    }

    public void stop(BundleContext bc) throws Exception {
        service.unregister();
        driver.close();
    }


}
