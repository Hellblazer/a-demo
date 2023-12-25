/*
 * Copyright (c) 2021, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */
package com.hellblazer.nut.service;

import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.Assert.assertNotNull;

/**
 * @author hal.hildebrand
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class SkyApiTest {
    public static DropwizardAppExtension<SkyConfiguration> EXT = new DropwizardAppExtension<>(SkyApplication.class,
                                                                                              ResourceHelpers.resourceFilePath(
                                                                                              "sky-test.yaml"));

    @Test
    void smokin() throws Exception {
        Client client = EXT.client();

        Thread.sleep(1_000);

        Response response = client.target(String.format("http://localhost:%d/login", EXT.getLocalPort()))
                                  .request()
                                  .post(Entity.json(testQuery()));
        assertNotNull(response);

    }

    private Object testQuery() {
        // TODO Auto-generated method stub
        return null;
    }

}
