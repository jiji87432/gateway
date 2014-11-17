/**
 * Copyright (c) 2007-2014 Kaazing Corporation. All rights reserved.
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kaazing.gateway.transport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import junit.framework.Assert;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kaazing.gateway.server.config.parse.GatewayConfigParser;
import org.kaazing.gateway.server.config.sep2014.GatewayConfigDocument;
import org.kaazing.gateway.server.context.GatewayContext;
import org.kaazing.gateway.server.context.resolve.GatewayContextResolver;
import org.kaazing.gateway.service.ServiceContext;

public class GatewayBindTest {
    private static GatewayConfigParser parser;
    private GatewayContext ctx = null;

    @BeforeClass
    public static void init() {
        parser = new GatewayConfigParser();
    }
    
    @After
    public void shutdownGateway() {
        // Make sure we shut down resources, e.g. I/O workers
        if (ctx != null) {
            ctx.dispose();
        }
    }

    private File createTempFileFromResource(String resourceName) throws IOException {
        File file = File.createTempFile("gateway-config", "xml");
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream is = classLoader.getResource(resourceName).openStream();
        FileOutputStream fos = new FileOutputStream(file);
        int datum;
        while ((datum = is.read()) != -1) {
            fos.write(datum);
        }
        fos.flush();
        fos.close();
        return file;
    }

    private GatewayConfigDocument createGatewayConfig() throws Exception {
        File configFile = null;
        try {
            configFile = createTempFileFromResource("org/kaazing/gateway/server/config/parse/data/gateway-config-bind.xml");
            return parser.parse(configFile);
        } finally {
            if (configFile != null) {
                configFile.delete();
            }
        }
    }

    private GatewayConfigDocument createBalancerConfig() throws Exception {
        File configFile = null;
        try {
            configFile = createTempFileFromResource("org/kaazing/gateway/server/config/parse/data/gateway-config-bind-balancer.xml");
            return parser.parse(configFile);
        } finally {
            if (configFile != null) {
                configFile.delete();
            }
        }
    }

    private GatewayConfigDocument createSecureConfig() throws Exception {
        File configFile = null;
        try {
            configFile = createTempFileFromResource("org/kaazing/gateway/server/config/parse/data/gateway-config-bind-missingcert.xml");
            return parser.parse(configFile);
        } finally {
            if (configFile != null) {
                configFile.delete();
            }
        }
    }

    @Test
    public void testServiceBind() throws Exception {
        File homeDir = null;
        File configDir = null;
        File webRootDir = null;
        File tempDir = null;

        try {
            GatewayConfigDocument config = createGatewayConfig();

            // create the directories for config, web, and temp
            homeDir = new File("kaazing-home/");
            if (!homeDir.mkdir()) {
                throw new RuntimeException("Failed to create directory kaazing-home/");
            }

            configDir = new File(homeDir, "config/");
            if (!configDir.mkdir()) {
                throw new RuntimeException("Failed to create directory kaazing-home/config/");
            }

            webRootDir = new File(homeDir, "web/");
            if (!webRootDir.mkdir()) {
                throw new RuntimeException("Failed to create directory kaazing-home/web/");
            }

            tempDir = new File(homeDir, "temp/");
            if (!tempDir.mkdir()) {
                throw new RuntimeException("Failed to create directory kaazing-home/temp/");
            }

            Set<String> allowedServices = new HashSet<String>();
            allowedServices.add("echo");

            GatewayContextResolver resolver = new GatewayContextResolver(configDir, webRootDir, tempDir);
            ctx = resolver.resolve(config);
        } finally {
            if (tempDir != null) {
                tempDir.delete();
            }
            if (webRootDir != null) {
                webRootDir.delete();
            }
            if (configDir != null) {
                configDir.delete();
            }
            if (homeDir != null) {
                homeDir.delete();
            }
        }
    }

    @Test
    public void testSecureServiceBindMissingCert() throws Exception {
        File homeDir = null;
        File configDir = null;
        File webRootDir = null;
        File tempDir = null;

        try {
            GatewayConfigDocument config = createSecureConfig();

            // create the directories for config, web, and temp
            homeDir = new File("kaazing-home/");
            if (!homeDir.mkdir()) {
                throw new RuntimeException("Failed to create directory kaazing-home/");
            }

            configDir = new File(homeDir, "config/");
            if (!configDir.mkdir()) {
                throw new RuntimeException("Failed to create directory kaazing-home/config/");
            }

            webRootDir = new File(homeDir, "web/");
            if (!webRootDir.mkdir()) {
                throw new RuntimeException("Failed to create directory kaazing-home/web/");
            }

            tempDir = new File(homeDir, "temp/");
            if (!tempDir.mkdir()) {
                throw new RuntimeException("Failed to create directory kaazing-home/temp/");
            }

            Set<String> allowedServices = new HashSet<String>();
            allowedServices.add("echo");

            GatewayContextResolver resolver = new GatewayContextResolver(configDir, webRootDir, tempDir);
            ctx = resolver.resolve(config);

            Collection<? extends ServiceContext> serviceContexts = ctx.getServices();
            Assert.assertTrue(String.format("Expected 1 service context, got %d", serviceContexts.size()), serviceContexts.size() == 1);

            boolean haveAcceptableException = false;

            for (ServiceContext serviceContext : serviceContexts) {
                serviceContext.getService().init(serviceContext);

                try {
                    serviceContext.start();

                } catch (RuntimeException re) {
                    System.err.println(String.format("Caught exception: %s", re));
                    re.printStackTrace();

                    if (re.getMessage().contains("otherhost")) {
                        haveAcceptableException = true;
                    }
                }
            }

            Assert.assertTrue("Expected RuntimeException whose message mentions 'otherhost', did not see it", haveAcceptableException);

        } finally {
            if (tempDir != null) {
                tempDir.delete();
            }
            if (webRootDir != null) {
                webRootDir.delete();
            }
            if (configDir != null) {
                configDir.delete();
            }
            if (homeDir != null) {
                homeDir.delete();
            }
        }
    }

}
