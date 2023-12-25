/*
 * Copyright (c) 2023 Hal Hildebrand. All rights reserved.
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
package com.hellblazer.nut.service;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Message;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * @author hal.hildebrand
 *
 */
@Provider
@Consumes("application/x-protobuf")
@Produces("application/x-protobuf")

public class ProtobufMimeProvider implements MessageBodyWriter<Message>, MessageBodyReader<Message> {
    // MessageBodyWriter Implementation
    @Override
    public long getSize(Message message, Class<?> arg1, Type arg2, Annotation[] arg3, MediaType arg4) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            message.writeTo(baos);
        } catch (IOException e) {
            return -1;
        }
        return baos.size();
    }

    // MessageBodyReader Implementation
    @Override
    public boolean isReadable(Class<?> arg0, Type arg1, Annotation[] arg2, MediaType arg3) {
        return Message.class.isAssignableFrom(arg0);
    }

    @Override
    public boolean isWriteable(Class<?> arg0, Type arg1, Annotation[] arg2, MediaType arg3) {
        return Message.class.isAssignableFrom(arg0);
    }

    @Override
    public Message readFrom(Class<Message> arg0, Type arg1, Annotation[] arg2, MediaType arg3,
                            MultivaluedMap<String, String> arg4,
                            InputStream istream) throws IOException, WebApplicationException {
        try {
            Method builderMethod = arg0.getMethod("newBuilder");
            GeneratedMessage.Builder<?> builder = (GeneratedMessage.Builder<?>) builderMethod.invoke(arg0);
            return builder.mergeFrom(istream).build();
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Override
    public void writeTo(Message message, Class<?> arg1, Type arg2, Annotation[] arg3, MediaType arg4,
                        MultivaluedMap<String, Object> arg5,
                        OutputStream ostream) throws IOException, WebApplicationException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        message.writeTo(baos);
        ostream.write(baos.toByteArray());
    }
}
