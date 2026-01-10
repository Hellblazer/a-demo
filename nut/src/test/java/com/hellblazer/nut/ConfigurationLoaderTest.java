/*
 * Copyright (c) 2024 Hal Hildebrand. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.hellblazer.nut;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigurationLoader precedence handling
 *
 * @author hal.hildebrand
 */
public class ConfigurationLoaderTest {
    @Test
    public void defaultsOnly() throws IOException {
        // Load with no YAML and no environment variables
        SkyConfiguration config = ConfigurationLoader.load(null, new String[0]);

        assertNotNull(config);
        assertFalse(config.useServiceLayer, "useServiceLayer should default to false");
        assertNotNull(config.endpoints, "endpoints should be initialized with defaults");
    }

    @Test
    public void useServiceLayerFromCli() throws IOException {
        String[] cliArgs = { "--useServiceLayer=true" };
        SkyConfiguration config = ConfigurationLoader.load(null, cliArgs);

        assertTrue(config.useServiceLayer, "useServiceLayer should be true from CLI");
    }

    @Test
    public void endpointOverrideFromCli() throws IOException {
        String[] cliArgs = {
            "--endpoints.interfaceName=eth2",
            "--endpoints.apiPort=8888"
        };
        SkyConfiguration config = ConfigurationLoader.load(null, cliArgs);

        assertTrue(config.endpoints instanceof SkyConfiguration.InterfaceEndpoints,
                   "endpoints should be InterfaceEndpoints");
        SkyConfiguration.InterfaceEndpoints endpoints =
            (SkyConfiguration.InterfaceEndpoints) config.endpoints;
        assertEquals("eth2", endpoints.interfaceName, "interfaceName should be set from CLI");
        assertEquals(8888, endpoints.apiPort, "apiPort should be set from CLI");
    }


    @Test
    public void multipleCliArguments() throws IOException {
        String[] cliArgs = {
            "--useServiceLayer=true",
            "--endpoints.apiPort=7777",
            "--endpoints.approachPort=7778"
        };
        SkyConfiguration config = ConfigurationLoader.load(null, cliArgs);

        assertTrue(config.useServiceLayer, "useServiceLayer should be true");
        SkyConfiguration.InterfaceEndpoints endpoints =
            (SkyConfiguration.InterfaceEndpoints) config.endpoints;
        assertEquals(7777, endpoints.apiPort, "apiPort should be set");
        assertEquals(7778, endpoints.approachPort, "approachPort should be set");
    }

    @Test
    public void emptyCliArguments() throws IOException {
        String[] cliArgs = { };
        SkyConfiguration config = ConfigurationLoader.load(null, cliArgs);

        assertNotNull(config);
        assertFalse(config.useServiceLayer, "useServiceLayer should default to false");
    }

    @Test
    public void nullCliArguments() throws IOException {
        SkyConfiguration config = ConfigurationLoader.load(null, null);

        assertNotNull(config);
        assertFalse(config.useServiceLayer, "useServiceLayer should default to false");
    }

}
