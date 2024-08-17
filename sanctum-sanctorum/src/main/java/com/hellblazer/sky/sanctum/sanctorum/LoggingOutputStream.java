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

package com.hellblazer.sky.sanctum.sanctorum;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/**
 * @author hal.hildebrand
 */
public class LoggingOutputStream extends OutputStream {

    private final ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
    private final LogLevel              level;
    private final Logger                logger;

    public LoggingOutputStream(Logger logger, LogLevel level) {
        this.logger = logger;
        this.level = level;
    }

    @Override
    public void write(int b) {
        if (b == '\n') {
            String line = baos.toString();
            baos.reset();

            switch (level) {
            case TRACE:
                logger.trace(line);
                break;
            case DEBUG:
                logger.debug(line);
                break;
            case ERROR:
                logger.error(line);
                break;
            case INFO:
                logger.info(line);
                break;
            case WARN:
                logger.warn(line);
                break;
            }
        } else {
            baos.write(b);
        }
    }

    public enum LogLevel {
        DEBUG, ERROR, INFO, TRACE, WARN,
    }

}
