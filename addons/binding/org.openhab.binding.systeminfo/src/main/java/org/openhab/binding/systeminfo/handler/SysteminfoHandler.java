/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.systeminfo.handler;

import static org.openhab.binding.systeminfo.SysteminfoBindingConstants.*;

import java.math.BigDecimal;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.bidning.systeminfo.model.SysteminfoImpl;
import org.openhab.bidning.systeminfo.model.SysteminfoInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oshi.SystemInfo;

/**
 * The {@link SysteminfoHandler} is responsible for providing real time information about the system
 * (CPU, Memory, Storage, Display and others).
 *
 * @author Svilen Valkanov - Initial contribution
 */

public class SysteminfoHandler extends BaseThingHandler {
    /**
     * Refresh interval for {@link #highPriorityChannels} in seconds.
     */
    private BigDecimal refreshIntervalHighPriority;

    /**
     * Refresh interval for {@link #mediumPriorityChannels} in seconds.
     */
    private BigDecimal refreshIntervalMediumPriority;

    /**
     * Channels with priority configuration parameter set to High. They usually need frequent update of the state like
     * CPU load, or information about the free and used memory.
     * They are updated periodically at {@link #refreshIntervalHighPriority}.
     */
    private Set<ChannelUID> highPriorityChannels = new HashSet<ChannelUID>();

    /**
     * Channels with priority configuration parameter set to Medium. These channels usually need update of the
     * state not so oft like battery capacity, storage used and etc.
     * They are updated periodically at {@link #refreshIntervalMediumPriority}.
     */
    private Set<ChannelUID> mediumPriorityChannels = new HashSet<ChannelUID>();

    /**
     * Channels with priority configuration parameter set to Low. They represent static information or information
     * that is updated rare- e.g. CPU name, storage name and etc.
     * They are updated only at {@link #initialize()}.
     */
    private Set<ChannelUID> lowPriorityChannels = new HashSet<ChannelUID>();

    /**
     * Wait time for the creation of Item-Channel links in seconds. This delay is needed, because the Item-Channel
     * links have to be created before the thing state is updated, otherwise item state will not be updated.
     */
    private static final int WAIT_TIME_CHANNEL_ITEM_LINK_INIT = 1;

    private SysteminfoInterface systeminfo;

    ScheduledFuture<?> highPriorityTasks;
    ScheduledFuture<?> mediumPriorityTasks;

    private Logger logger = LoggerFactory.getLogger(SysteminfoHandler.class);

    public SysteminfoHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Start initializing!");

        try {
            this.systeminfo = new SysteminfoImpl(new SystemInfo());
        } catch (SocketException e) {
            logger.error("Network information is not availble ! I/O error occured {e}", e);
        }

        if (isConfigurationValid()) {
            groupChannelsByPriority();
            scheduleUpdates();
            logger.debug("Thing is successfully initialized!");
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Configuration is invalid !");
        }

    }

    private boolean isConfigurationValid() {
        logger.debug("Start reading Thing configuration.");
        Configuration config = getConfig();
        try {
            refreshIntervalMediumPriority = (BigDecimal) config.get(MEDIUM_PRIORITY_REFRESH_TIME);
            refreshIntervalHighPriority = (BigDecimal) config.get(HIGH_PRIORITY_REFRESH_TIME);

            if (refreshIntervalHighPriority.intValue() <= 0 || refreshIntervalMediumPriority.intValue() <= 0) {
                throw new IllegalArgumentException("Refresh time must be positive value !");
            }
            logger.debug("Refresh time for medium priority channels set to {} s", refreshIntervalMediumPriority);
            logger.debug("Refresh time for high priority channels set to {} s", refreshIntervalHighPriority);
            return true;
        } catch (Exception e) {
            logger.error("Refresh time value is invalid!. Please change the thing configuration!", e);
            return false;
        }
    }

    private void groupChannelsByPriority() {
        for (Channel channel : getThing().getChannels()) {
            String priority = (String) channel.getConfiguration().get("priority");
            switch (priority) {
                case "High":
                    highPriorityChannels.add(channel.getUID());
                    break;
                case "Medium":
                    mediumPriorityChannels.add(channel.getUID());
                    break;
                case "Low":
                    lowPriorityChannels.add(channel.getUID());
                    break;
            }
        }
    }

    private void scheduleUpdates() {
        logger.debug("Schedule high priority tasks at fixed rate {} s.", refreshIntervalHighPriority);

        highPriorityTasks = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                publishData(highPriorityChannels);
            }
        }, WAIT_TIME_CHANNEL_ITEM_LINK_INIT, refreshIntervalHighPriority.intValue(), TimeUnit.SECONDS);

        logger.debug("Schedule medium priority tasks at fixed rate {} s.", refreshIntervalMediumPriority);
        mediumPriorityTasks = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                publishData(mediumPriorityChannels);
            }
        }, WAIT_TIME_CHANNEL_ITEM_LINK_INIT, refreshIntervalMediumPriority.intValue(), TimeUnit.SECONDS);

        logger.debug("Schedule one time update for low priority tasks.");
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                publishData(lowPriorityChannels);
            }
        }, WAIT_TIME_CHANNEL_ITEM_LINK_INIT, TimeUnit.SECONDS);

    }

    private void publishData(Set<ChannelUID> channels) {
        Iterator<ChannelUID> iter = channels.iterator();
        while (iter.hasNext()) {
            ChannelUID channeUID = iter.next();
            if (isLinked(channeUID.getId())) {
                publishDataForChannel(channeUID);
            }
        }
    }

    private void publishDataForChannel(ChannelUID channelUID) {
        State state = getInfoForChannel(channelUID);
        String channelID = channelUID.getId();
        if (state != null) {
            updateState(channelID, state);
        } else {
            logger.warn("Channel with ID {} can not be updated! No information available for the selected device.",
                    channelID);
        }
    }

    /**
     * This method gets the information for specific channel through the {@link SysteminfoInterface}. It uses the
     * channel ID to call the correct method from the {@link SysteminfoInterface} with deviceIndex parameter (in case of
     * multiple devices, for reference see {@link #getDeviceIndex(String)}})
     *
     * @param channelUID - the UID of the channel
     * @return State object or null, if there is no information for the device with this index
     */
    private State getInfoForChannel(ChannelUID channelUID) {
        State state = null;
        String channelID = channelUID.getId();
        int deviceIndex = getDeviceIndex(channelID);
        if (deviceIndex > 0) {
            // The channelID contains deviceIndex. It must be deleted from the channelID, because otherwise the method
            // will not find the correct method below.
            // All digits are deleted from the ID
            channelID = channelID.replaceAll("\\d+", "");
        }

        switch (channelID) {
            case CHANNEL_OS_FAMILY:
                state = systeminfo.getOsFamily();
                break;
            case CHANNEL_OS_MANUFACTURER:
                state = systeminfo.getOsManufacturer();
                break;
            case CHANNEL_OS_VERSION:
                state = systeminfo.getOsVersion();
                break;
            case CHANNEL_DISPLAY_INFORMATION:
                state = systeminfo.getDisplayInformation(deviceIndex);
                break;
            case CHANNEL_BATTERY_NAME:
                state = systeminfo.getBatteryName(deviceIndex);
                break;
            case CHANNEL_BATTERY_REMAINING_CAPACITY:
                state = systeminfo.getBatteryRemainingCapacity(deviceIndex);
                break;
            case CHANNEL_BATTERY_REMAINING_TIME:
                state = systeminfo.getBatteryRemainingTime(deviceIndex);
                break;
            case CHANNEL_SENSORS_CPU_TEMPERATURE:
                state = systeminfo.getSensorsCpuTemperature();
                break;
            case CHANNEL_SENOSRS_CPU_VOLTAGE:
                state = systeminfo.getSensorsCpuVoltage();
                break;
            case CHANNEL_SENSORS_FAN_SPEED:
                state = systeminfo.getSensorsFanSpeed(deviceIndex);
                break;
            case CHANNEL_CPU_LOAD:
                state = systeminfo.getCpuLoad();
                break;
            case CHANNEL_CPU_PHYSICAL_CORES:
                state = systeminfo.getCpuPhysicalCores();
                break;
            case CHANNEL_CPU_LOGICAL_CORES:
                state = systeminfo.getCpuLogicalCores();
                break;
            case CHANNEL_CPU_DESCRIPTION:
                state = systeminfo.getCpuDescription();
                break;
            case CHANNEL_CPU_NAME:
                state = systeminfo.getCpuName();
                break;
            case CHANNEL_MEMORY_AVAILABLE:
                state = systeminfo.getMemoryAvailable();
                break;
            case CHANNEL_MEMORY_USED:
                state = systeminfo.getMemoryUsed();
                break;
            case CHANNEL_MEMORY_TOTAL:
                state = systeminfo.getMemoryTotal();
                break;
            case CHANNEL_MEMORY_AVAILABLE_PERCENT:
                state = systeminfo.getMemoryAvailablePercent();
                break;
            case CHANNEL_STORAGE_NAME:
                state = systeminfo.getStorageName(deviceIndex);
                break;
            case CHANNEL_STORAGE_DESCRIPTION:
                state = systeminfo.getStorageDescription(deviceIndex);
                break;
            case CHANNEL_STORAGE_AVAILABLE:
                state = systeminfo.getStorageAvailable(deviceIndex);
                break;
            case CHANNEL_STORAGE_USED:
                state = systeminfo.getStorageUsed(deviceIndex);
                break;
            case CHANNEL_STORAGE_TOTAL:
                state = systeminfo.getStorageTotal(deviceIndex);
                break;
            case CHANNEL_STORAGE_AVAILABLE_PERCENT:
                state = systeminfo.getStorageAvailablePercent(deviceIndex);
                break;
            case CHANNEL_NETWORK_IP:
                state = systeminfo.getNetworkIp(deviceIndex);
                break;
            case CHANNEL_NETWORK_ADAPTER_NAME:
                state = systeminfo.getNetworkAdapterName(deviceIndex);
                break;
            case CHANNEL_NETWORK_NAME:
                state = systeminfo.getNetworkName(deviceIndex);
                break;
        }
        return state;
    }

    /**
     * The device index is an optional part of the channelID - the last characters. It is used to identify unique
     * device, when more than one devices are available (e.g. local disks with names C:\, D:\, E"\ - the first will have
     * deviceIndex=0, the second deviceIndex=1 ant etc).
     * When no device index is specified, default value of 0 (first device in the list) is returned.
     *
     * @param channelID - the ID of the channel
     * @return index >= 0
     */
    private int getDeviceIndex(String channelID) {
        int deviceIndex = 0;
        char lastChar = channelID.charAt(channelID.length() - 1);
        if (Character.isDigit(lastChar)) {
            // All non-digits are deleted from the ID
            String deviceIndexPart = channelID.replaceAll("\\D+", "");
            deviceIndex = Integer.parseInt(deviceIndexPart);
        }
        return deviceIndex;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            logger.debug("Refresh command received for channel {}!", channelUID);
            publishDataForChannel(channelUID);
        } else {
            logger.debug("Unsuported command {}! Supported commands: REFRESH", command);
        }
    }

    @Override
    public void dispose() {
        if (highPriorityTasks != null) {
            highPriorityTasks.cancel(true);
        }
        if (mediumPriorityTasks != null) {
            mediumPriorityTasks.cancel(true);
        }
    }

}