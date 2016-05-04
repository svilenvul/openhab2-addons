/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.systeminfo.model;

import java.math.BigDecimal;
import java.net.SocketException;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.StringType;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.Display;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.PowerSource;
import oshi.hardware.Sensors;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.EdidUtil;

/**
 * This implementation of {@link SysteminfoInterface} is using the open source library Oshi to provide system
 * information. Only the network information is provided from JDK.
 *
 * @author Svilen Valkanov
 *
 */
public class OshiSysteminfo implements SysteminfoInterface {
    private OperatingSystem operatingSystem;
    private NetworkIF[] networks;
    private Display[] displays;
    private OSFileStore[] fileStores;
    private GlobalMemory memory;
    private PowerSource[] powerSources;
    private CentralProcessor cpu;
    private HWDiskStore[] drives;
    private Sensors sensors;

    public final static int PRECISION_AFTER_DECIMAl_SIGN = 2;

    /**
     * Some of the methods used in this constructor execute native code and require execute permissions
     *
     * @throws SocketException when it is not able to access the network information.
     */
    public OshiSysteminfo() throws SocketException {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        operatingSystem = systemInfo.getOperatingSystem();
        displays = hal.getDisplays();
        fileStores = hal.getFileStores();
        memory = hal.getMemory();
        powerSources = hal.getPowerSources();
        cpu = hal.getProcessor();
        sensors = hal.getSensors();
        networks = hal.getNetworkIFs();
        drives = hal.getDiskStores();
    }

    @SuppressWarnings("null")
    private Object getDevice(Object[] devices, int index) throws DeviceNotFoundException {
        if ((devices != null) && (devices.length <= index)) {
            throw new DeviceNotFoundException("Device with index: " + index + " can not be found!");
        }
        return devices[index];
    }

    @Override
    public StringType getOsFamily() {
        String osFamily = operatingSystem.getFamily();
        return new StringType(osFamily);
    }

    @Override
    public StringType getOsManufacturer() {
        String osManufacturer = operatingSystem.getManufacturer();
        return new StringType(osManufacturer);
    }

    @Override
    public StringType getOsVersion() {
        String osVersion = operatingSystem.getVersion().toString();
        return new StringType(osVersion);
    }

    @Override
    public StringType getCpuName() {
        String name = cpu.getName();
        return new StringType(name);
    }

    @Override
    public StringType getCpuDescription() {
        String model = cpu.getModel();
        String family = cpu.getFamily();
        String serialNumber = cpu.getSystemSerialNumber();
        String identifier = cpu.getIdentifier();
        String vendor = cpu.getVendor();
        String architecture = cpu.isCpu64bit() ? "64 bit" : "32 bit";
        String descriptionFormatString = "Model: %s %s,family: %s, vendor: %s, sn: %s, identifier: %s ";
        String description = String.format(descriptionFormatString, model, architecture, family, vendor, serialNumber,
                identifier);

        return new StringType(description);
    }

    @Override
    public DecimalType getCpuLogicalCores() {
        int logicalProcessorCount = cpu.getLogicalProcessorCount();
        return new DecimalType(logicalProcessorCount);
    }

    @Override
    public DecimalType getCpuPhysicalCores() {
        int physicalProcessorCount = cpu.getPhysicalProcessorCount();
        return new DecimalType(physicalProcessorCount);
    }

    @Override
    public DecimalType getCpuLoad() {
        double processorLoad = cpu.getSystemCpuLoad();
        BigDecimal processorLoadPercent = getPercentsValue(processorLoad);
        return new DecimalType(processorLoadPercent);
    }

    @Override
    public DecimalType getMemoryTotal() {
        long totalMemory = memory.getTotal();
        totalMemory = getSizeInMB(totalMemory);
        return new DecimalType(totalMemory);
    }

    @Override
    public DecimalType getMemoryAvailable() {
        long availableMemory = memory.getAvailable();
        availableMemory = getSizeInMB(availableMemory);
        return new DecimalType(availableMemory);
    }

    @Override
    public DecimalType getMemoryUsed() {
        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();
        long usedMemory = totalMemory - availableMemory;
        usedMemory = getSizeInMB(usedMemory);
        return new DecimalType(usedMemory);
    }

    @Override
    public DecimalType getStorageTotal(int index) throws DeviceNotFoundException {
        OSFileStore fileStore = (OSFileStore) getDevice(fileStores, index);
        long totalSpace = fileStore.getTotalSpace();
        totalSpace = getSizeInMB(totalSpace);
        return new DecimalType(totalSpace);
    }

    @Override
    public DecimalType getStorageAvailable(int index) throws DeviceNotFoundException {
        OSFileStore fileStore = (OSFileStore) getDevice(fileStores, index);
        long freeSpace = fileStore.getUsableSpace();
        freeSpace = getSizeInMB(freeSpace);
        return new DecimalType(freeSpace);
    }

    @Override
    public DecimalType getStorageUsed(int index) throws DeviceNotFoundException {
        OSFileStore fileStore = (OSFileStore) getDevice(fileStores, index);
        long totalSpace = fileStore.getTotalSpace();
        long freeSpace = fileStore.getUsableSpace();
        long usedSpace = totalSpace - freeSpace;
        usedSpace = getSizeInMB(usedSpace);
        return new DecimalType(usedSpace);
    }

    @Override
    public DecimalType getStorageAvailablePercent(int deviceIndex) throws DeviceNotFoundException {
        OSFileStore fileStore = (OSFileStore) getDevice(fileStores, deviceIndex);
        long freeStorage = fileStore.getUsableSpace();
        long totalStorage = fileStore.getTotalSpace();
        double freePercentDecimal = (double) freeStorage / (double) totalStorage;
        BigDecimal freePercent = getPercentsValue(freePercentDecimal);
        return new DecimalType(freePercent);
    }

    @Override
    public StringType getStorageName(int index) throws DeviceNotFoundException {
        OSFileStore fileStore = (OSFileStore) getDevice(fileStores, index);
        String name = fileStore.getName();
        return new StringType(name);
    }

    @Override
    public StringType getStorageType(int deviceIndex) throws DeviceNotFoundException {
        OSFileStore fileStore = (OSFileStore) getDevice(fileStores, deviceIndex);
        String type = fileStore.getType();
        return new StringType(type);
    }

    @Override
    public StringType getStorageDescription(int index) throws DeviceNotFoundException {
        OSFileStore fileStore = (OSFileStore) getDevice(fileStores, index);
        String description = fileStore.getDescription();
        return new StringType(description);
    }

    @Override
    public StringType getNetworkIp(int index) throws DeviceNotFoundException {
        NetworkIF netInterface = (NetworkIF) getDevice(networks, index);
        String[] ipAddresses = netInterface.getIPv4addr();
        String ipv4 = (String) getDevice(ipAddresses, 0);
        return new StringType(ipv4);
    }

    @Override
    public StringType getNetworkName(int index) throws DeviceNotFoundException {
        NetworkIF netInterface = (NetworkIF) getDevice(networks, index);
        String name = netInterface.getName();
        return new StringType(name);
    }

    @Override
    public StringType getNetworkDisplayName(int index) throws DeviceNotFoundException {
        NetworkIF netInterface = (NetworkIF) getDevice(networks, index);
        String adapterName = netInterface.getDisplayName();
        return new StringType(adapterName);
    }

    @Override
    public StringType getDisplayInformation(int index) throws DeviceNotFoundException {
        Display display = (Display) getDevice(displays, index);

        byte[] edid = display.getEdid();
        String manufacturer = EdidUtil.getManufacturerID(edid);
        String product = EdidUtil.getProductID(edid);
        String serialNumber = EdidUtil.getSerialNo(edid);
        int width = EdidUtil.getHcm(edid);
        int height = EdidUtil.getVcm(edid);

        String edidFormatString = "Product %s, manufacturer %s, SN: %s, Width: %d, Height: %d";
        String edidInfo = String.format(edidFormatString, product, manufacturer, serialNumber, width, height);
        return new StringType(edidInfo);
    }

    @Override
    public DecimalType getSensorsCpuTemperature() {
        double cpuTemp = sensors.getCpuTemperature();
        return new DecimalType(cpuTemp);
    }

    @Override
    public DecimalType getSensorsCpuVoltage() {
        double cpuVoltage = sensors.getCpuVoltage();
        return new DecimalType(cpuVoltage);
    }

    @Override
    public DecimalType getSensorsFanSpeed(int index) throws DeviceNotFoundException {
        int[] fanSpeeds = sensors.getFanSpeeds();
        int speed = (int) getDevice(ArrayUtils.toObject(fanSpeeds), index);
        return new DecimalType(speed);
    }

    @Override
    public DecimalType getBatteryRemainingTime(int index) throws DeviceNotFoundException {
        PowerSource powerSource = (PowerSource) getDevice(powerSources, index);
        double remainingTime = powerSource.getTimeRemaining();
        // The getTimeRemaining() method returns (-1.0) if is calculating or (-2.0) if the time is unlimited
        if (remainingTime > 0) {
            remainingTime = getTimeInMinutes(remainingTime);
        } else if (remainingTime == -2 || remainingTime == -1) {
            remainingTime = 999;
        }

        return new DecimalType(remainingTime);
    }

    @Override
    public DecimalType getBatteryRemainingCapacity(int index) throws DeviceNotFoundException {
        PowerSource powerSource = (PowerSource) getDevice(powerSources, index);
        double remainingCapacity = powerSource.getRemainingCapacity();
        BigDecimal remainingCapacityPercents = getPercentsValue(remainingCapacity);
        return new DecimalType(remainingCapacityPercents);
    }

    @Override
    public StringType getBatteryName(int index) throws DeviceNotFoundException {
        PowerSource powerSource = (PowerSource) getDevice(powerSources, index);
        String name = powerSource.getName();
        return new StringType(name);
    }

    @Override
    public DecimalType getMemoryAvailablePercent() {
        long availableMemory = memory.getAvailable();
        long totalMemory = memory.getTotal();
        BigDecimal freePercent;
        if (totalMemory > 0) {
            double freePercentDecimal = (double) availableMemory / (double) totalMemory;
            freePercent = getPercentsValue(freePercentDecimal);
        } else {
            freePercent = new BigDecimal(0);
        }
        return new DecimalType(freePercent);
    }

    @Override
    public StringType getDriveName(int deviceIndex) throws DeviceNotFoundException {
        HWDiskStore drive = (HWDiskStore) getDevice(drives, deviceIndex);
        String name = drive.getName();
        return new StringType(name);
    }

    @Override
    public StringType getDriveModel(int deviceIndex) throws DeviceNotFoundException {
        HWDiskStore drive = (HWDiskStore) getDevice(drives, deviceIndex);
        String model = drive.getModel();
        return new StringType(model);
    }

    @Override
    public StringType getDriveSerialNumber(int deviceIndex) throws DeviceNotFoundException {
        HWDiskStore drive = (HWDiskStore) getDevice(drives, deviceIndex);
        String serialNumber = drive.getSerial();
        return new StringType(serialNumber);
    }

    @Override
    public DecimalType getSwapTotal() {
        long swapTotal = memory.getSwapTotal();
        swapTotal = getSizeInMB(swapTotal);
        return new DecimalType(swapTotal);
    }

    @Override
    public DecimalType getSwapAvailable() {
        long swapTotal = memory.getSwapTotal();
        long swapUsed = memory.getSwapUsed();
        long swapAvaialble = swapTotal - swapUsed;
        swapAvaialble = getSizeInMB(swapAvaialble);
        return new DecimalType(swapAvaialble);
    }

    @Override
    public DecimalType getSwapUsed() {
        long swapTotal = memory.getSwapUsed();
        swapTotal = getSizeInMB(swapTotal);
        return new DecimalType(swapTotal);
    }

    @Override
    public DecimalType getSwapAvailablePercent() {
        long usedSwap = memory.getSwapUsed();
        long totalSwap = memory.getSwapTotal();
        long freeSwap = totalSwap - usedSwap;
        BigDecimal freePercent;
        if (totalSwap > 0) {
            double freePercentDecimal = (double) freeSwap / (double) totalSwap;
            freePercent = getPercentsValue(freePercentDecimal);
        } else {
            freePercent = new BigDecimal(0);
        }

        return new DecimalType(freePercent);
    }

    private long getSizeInMB(long sizeInBytes) {
        return sizeInBytes /= 1024 * 1024;
    }

    private BigDecimal getPercentsValue(double decimalFraction) {
        BigDecimal result = new BigDecimal(decimalFraction * 100);
        result = result.setScale(PRECISION_AFTER_DECIMAl_SIGN, BigDecimal.ROUND_HALF_UP);
        return result;
    }

    private double getTimeInMinutes(double timeInSeconds) {
        return timeInSeconds / 100;
    }

    @Override
    public DecimalType getCpuLoad1() {
        return new DecimalType(getAvarageCpuLoad(1));
    }

    @Override
    public DecimalType getCpuLoad5() {
        return new DecimalType(getAvarageCpuLoad(5));
    }

    @Override
    public DecimalType getCpuLoad15() {
        return new DecimalType(getAvarageCpuLoad(15));
    }

    private BigDecimal getAvarageCpuLoad(int timeInMunutes) {
        // This paramater is specified in OSHI Javadoc
        int index;
        switch (timeInMunutes) {
            case 1:
                index = 0;
                break;
            case 5:
                index = 1;
                break;
            case 15:
                index = 2;
                break;
            default:
                index = -1;
        }
        double processorLoads[] = cpu.getSystemLoadAverage(index);
        BigDecimal result = new BigDecimal(processorLoads[index]);
        result.setScale(PRECISION_AFTER_DECIMAl_SIGN);
        return result;
    }

    @Override
    public DecimalType getCpuUptime() {
        long seconds = cpu.getSystemUptime();
        return new DecimalType(getTimeInMinutes(seconds));
    }

    @Override
    public DecimalType getCpuThreads() {
        int threadCount = cpu.getThreadCount();
        return new DecimalType(threadCount);
    }

    @Override
    public StringType getNetworkMac(int networkIndex) throws DeviceNotFoundException {
        NetworkIF network = (NetworkIF) getDevice(networks, networkIndex);
        String mac = network.getMacaddr();
        return new StringType(mac);
    }

    @Override
    public DecimalType getNetworkPackageReceived(int networkIndex) throws DeviceNotFoundException {
        NetworkIF network = (NetworkIF) getDevice(networks, networkIndex);
        long packRecv = network.getPacketsRecv();
        return new DecimalType(packRecv);
    }

    @Override
    public DecimalType getNetworkPackageSent(int networkIndex) throws DeviceNotFoundException {
        NetworkIF network = (NetworkIF) getDevice(networks, networkIndex);
        long packSent = network.getPacketsSent();
        return new DecimalType(packSent);
    }

    @Override
    public DecimalType getNetworkDataSent(int networkIndex) throws DeviceNotFoundException {
        NetworkIF network = (NetworkIF) getDevice(networks, networkIndex);
        long bytesSent = network.getBytesSent();
        return new DecimalType(getSizeInMB(bytesSent));
    }

    @Override
    public DecimalType getNetworkDataReceived(int networkIndex) throws DeviceNotFoundException {
        NetworkIF network = (NetworkIF) getDevice(networks, networkIndex);
        long bytesRecv = network.getBytesRecv();
        return new DecimalType(getSizeInMB(bytesRecv));
    }

}
