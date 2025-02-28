/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.detector.uploader;

import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.detector.config.Config;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import lombok.NonNull;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;
import software.amazon.awssdk.services.greengrassv2data.model.UpdateConnectivityInfoRequest;
import software.amazon.awssdk.services.greengrassv2data.model.UpdateConnectivityInfoResponse;

import java.net.InetAddress;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class ConnectivityUpdater {

    private final DeviceConfiguration deviceConfiguration;
    private final GreengrassServiceClientFactory clientFactory;
    private List<String> ipAddresses;
    private final Logger logger = LogManager.getLogger(ConnectivityUpdater.class);

    /**
     * Constructor.
     *
     * @param deviceConfiguration client to get the device details
     * @param clientFactory factory to get data plane client
     */
    @Inject
    public ConnectivityUpdater(DeviceConfiguration deviceConfiguration, GreengrassServiceClientFactory clientFactory) {
        this.deviceConfiguration = deviceConfiguration;
        this.clientFactory = clientFactory;
    }

    /**
     * Send ip address updates.
     *
     * @param ipAddresses list of ipAddresses
     * @param config Configuration values
     */
    public void updateIpAddresses(List<InetAddress> ipAddresses, Config config) {
        if (ipAddresses == null || ipAddresses.isEmpty()) {
            return;
        }
        List<String> ips = ipAddresses.stream().filter(ip -> ip != null && ip.getHostAddress() != null)
                .map(InetAddress::getHostAddress).collect(Collectors.toList());
        uploadAddresses(ips, config);
    }

    //Default for JUnit Testing
    synchronized void uploadAddresses(List<String> ips, Config config) {
        if (!hasIpsChanged(ips)) {
            return;
        }
        List<ConnectivityInfo> connectivityInfoItems = ips.stream().map(ip -> ConnectivityInfo.builder()
                .hostAddress(ip).metadata("").id(ip).portNumber(config.getMqttPort()).build())
                .collect(Collectors.toList());
        try {
            UpdateConnectivityInfoResponse connectivityInfoResponse =
                    updateConnectivityInfo(connectivityInfoItems);
            if (connectivityInfoResponse != null && connectivityInfoResponse.version() != null) {
                this.ipAddresses = ips;
                logger.atInfo().kv("IPs", ips).log("Uploaded IP addresses");
            }
        } catch (SdkException e) {
            logger.atWarn()
                    .log("Failed to upload the IP addresses. Check that the core device's IoT policy grants the "
                            + "greengrass:UpdateConnectivityInfo permission.", e);
        }
    }

    //Default for JUnit Testing
    boolean hasIpsChanged(@NonNull List<String> ips) {
        if (this.ipAddresses == null) {
            return true;
        } else {
            return this.ipAddresses.size() != ips.size() || !this.ipAddresses.containsAll(ips);
        }
    }

    private UpdateConnectivityInfoResponse updateConnectivityInfo(List<ConnectivityInfo> connectivityInfoItems) {
        if (connectivityInfoItems == null || connectivityInfoItems.isEmpty()) {
            return null;
        }

        UpdateConnectivityInfoRequest updateConnectivityInfoRequest =
                UpdateConnectivityInfoRequest.builder().thingName(Coerce.toString(deviceConfiguration.getThingName()))
                        .connectivityInfo(connectivityInfoItems).build();

        return clientFactory.getGreengrassV2DataClient().updateConnectivityInfo(updateConnectivityInfoRequest);
    }

    //For Junit Testing
    void setIpAddresses(List<String> ipAddresses) {
        this.ipAddresses = ipAddresses;
    }
}
