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

package com.hellblazer.sky.sanctum;

import com.salesforce.apollo.stereotomy.KeyCoordinates;
import com.salesforce.apollo.stereotomy.StereotomyKeyStore;

import java.security.KeyPair;
import java.util.Optional;

/**
 * @author hal.hildebrand
 **/
abstract public class StereoKeyStore implements StereotomyKeyStore {

    @Override
    public Optional<KeyPair> getKey(String alias) {
        return getDelegate().getKey(alias);
    }

    @Override
    public Optional<KeyPair> getKey(KeyCoordinates keyCoordinates) {
        return getDelegate().getKey(keyCoordinates);
    }

    @Override
    public Optional<KeyPair> getNextKey(KeyCoordinates keyCoordinates) {
        return getDelegate().getNextKey(keyCoordinates);
    }

    @Override
    public void removeKey(KeyCoordinates keyCoordinates) {
        getDelegate().removeKey(keyCoordinates);
    }

    @Override
    public void removeKey(String alias) {
        getDelegate().removeKey(alias);
    }

    @Override
    public void removeNextKey(KeyCoordinates keyCoordinates) {
        getDelegate().removeNextKey(keyCoordinates);
    }

    @Override
    public void storeKey(String alias, KeyPair keyPair) {
        getDelegate().storeKey(alias, keyPair);
    }

    @Override
    public void storeKey(KeyCoordinates keyCoordinates, KeyPair keyPair) {
        getDelegate().storeKey(keyCoordinates, keyPair);
    }

    @Override
    public void storeNextKey(KeyCoordinates keyCoordinates, KeyPair keyPair) {
        getDelegate().storeNextKey(keyCoordinates, keyPair);
    }

    abstract protected StereotomyKeyStore getDelegate();

}
