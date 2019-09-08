/*
 * Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.ftp.client.actions;

import org.ballerinalang.ftp.util.BallerinaFTPException;
import org.ballerinalang.ftp.util.FTPUtil;
import org.ballerinalang.ftp.util.FtpConstants;
import org.ballerinalang.jvm.BRuntime;
import org.ballerinalang.jvm.values.ObjectValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.remotefilesystem.RemoteFileSystemConnectorFactory;
import org.wso2.transport.remotefilesystem.client.connector.contract.FtpAction;
import org.wso2.transport.remotefilesystem.client.connector.contract.VFSClientConnector;
import org.wso2.transport.remotefilesystem.exception.RemoteFileSystemConnectorException;
import org.wso2.transport.remotefilesystem.impl.RemoteFileSystemConnectorFactoryImpl;
import org.wso2.transport.remotefilesystem.message.RemoteFileSystemBaseMessage;
import org.wso2.transport.remotefilesystem.message.RemoteFileSystemMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * FTP file size operation.
 */
public class Size extends AbstractFtpAction {

    private static final Logger log = LoggerFactory.getLogger("ballerina");

    public static int size(ObjectValue clientConnector, String path) throws BallerinaFTPException {

        String username = (String) clientConnector.getNativeData(FtpConstants.ENDPOINT_CONFIG_USERNAME);
        String password = (String) clientConnector.getNativeData(FtpConstants.ENDPOINT_CONFIG_PASSWORD);
        String host = (String) clientConnector.getNativeData(FtpConstants.ENDPOINT_CONFIG_HOST);
        int port = (int) clientConnector.getNativeData(FtpConstants.ENDPOINT_CONFIG_PORT);
        String protocol = (String) clientConnector.getNativeData(FtpConstants.ENDPOINT_CONFIG_PROTOCOL);
        String url = FTPUtil.createUrl(protocol, host, port, username, password, path);
        Map<String, String> prop = (Map<String, String>) clientConnector.getNativeData(FtpConstants.PROPERTY_MAP);
        Map<String, String> propertyMap = new HashMap<>(prop);
        propertyMap.put(FtpConstants.PROPERTY_URI, url);
        propertyMap.put(FtpConstants.FTP_PASSIVE_MODE, Boolean.TRUE.toString());

        CompletableFuture<Object> future = BRuntime.markAsync();
        FTPFileSizeListener connectorListener = new FTPFileSizeListener(future);
        RemoteFileSystemConnectorFactory fileSystemConnectorFactory = new RemoteFileSystemConnectorFactoryImpl();
        VFSClientConnector connector;
        try {
            connector = fileSystemConnectorFactory.createVFSClientConnector(propertyMap, connectorListener);
        } catch (RemoteFileSystemConnectorException e) {
            log.error(e.getMessage(), e);
            throw new BallerinaFTPException(e.getMessage());
        }
        connector.send(null, FtpAction.SIZE);
        return 0;
    }

    private static class FTPFileSizeListener extends FTPClientConnectorListener {

        private static final Logger log = LoggerFactory.getLogger("ballerina");
        private CompletableFuture<Object> future;

        FTPFileSizeListener(CompletableFuture<Object> future) {

            super(future);
            this.future = future;
        }

        @Override
        public boolean onMessage(RemoteFileSystemBaseMessage remoteFileSystemBaseMessage) {

            if (remoteFileSystemBaseMessage instanceof RemoteFileSystemMessage) {
                RemoteFileSystemMessage message = (RemoteFileSystemMessage) remoteFileSystemBaseMessage;
                future.complete((int) message.getSize());
            }
            return true;
        }

        @Override
        public void onError(Throwable throwable) {

            log.error(throwable.getMessage(), throwable);
            future.complete(FTPUtil.createError(throwable.getMessage()));
        }
    }
}
