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
 *  more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.hellblazer.nut;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author hal.hildebrand
 **/
public class SphinxTest {
    @Test
    public void development() throws Exception {
        new File("target/.id").delete();
        new File("target/.digest").delete();
        new File("target/kerl-state.mv.db").delete();
        new File("target/kerl-state.trace.db").delete();
        var devSecret = "Give me food or give me slack or kill me";
        Sphinx sphinx;
        try (var is = getClass().getResourceAsStream("/sky-test.yaml")) {
            sphinx = new Sphinx(is, devSecret);
        }
        assertNotNull(sphinx);
        sphinx.start();
        sphinx.shutdown();
    }
    @Test
    public void shamir() throws Exception {
        new File("target/.id").delete();
        new File("target/.digest").delete();
        new File("target/kerl-state.mv.db").delete();
        new File("target/kerl-state.trace.db").delete();
        var devSecret = "Give me food or give me slack or kill me";
        Sphinx sphinx;
        try (var is = getClass().getResourceAsStream("/sky-test.yaml")) {
            sphinx = new Sphinx(is);
        }
        assertNotNull(sphinx);
        sphinx.start();
        sphinx.shutdown();
    }
}
