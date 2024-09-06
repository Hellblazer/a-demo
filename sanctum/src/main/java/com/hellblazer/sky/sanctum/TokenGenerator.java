package com.hellblazer.sky.sanctum;

import com.google.protobuf.ByteString;
import com.macasaet.fernet.Token;

public interface TokenGenerator {
    Token apply(byte[] bytes);

    ByteString validate(Sanctum.HashedToken hashed);
}
