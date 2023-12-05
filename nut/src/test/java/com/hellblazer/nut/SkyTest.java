package com.hellblazer.nut;

import com.salesforce.apollo.archipelago.LocalServer;
import com.salesforce.apollo.cryptography.DigestAlgorithm;
import com.salesforce.apollo.stereotomy.mem.MemKERL;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * @author hal.hildebrand
 **/
public class SkyTest {
    @Test
    public void smokin() throws Exception {
        var thothP = UUID.randomUUID().toString();
        var gorgonP = thothP + "-gorgon";
        var builder = Sky.Parameters.newBuilder()
                                    .setThothService(InProcessServerBuilder.forName(thothP))
                                    .setRouterFactory(member -> new LocalServer(gorgonP, member).router())
                                    .setKerl(new MemKERL(DigestAlgorithm.DEFAULT))
                                    .setContext(DigestAlgorithm.DEFAULT.getOrigin())
                                    .setEntropy(new SecureRandom());
        var nut = new Sky(builder.build());
        nut.start();
    }
}
