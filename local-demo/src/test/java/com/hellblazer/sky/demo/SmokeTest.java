package com.hellblazer.sky.demo;

import com.hellblazer.nut.comms.MtlsClient;
import com.hellblazer.nut.service.OracleAdapter;
import com.salesforce.apollo.cryptography.cert.CertificateWithPrivateKey;
import com.salesforce.apollo.cryptography.ssl.CertificateValidator;
import com.salesforce.apollo.delphinius.Oracle;
import com.salesforce.apollo.utils.Utils;
import io.netty.handler.ssl.ClientAuth;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @author hal.hildebrand
 **/
@Testcontainers
public class SmokeTest {

    @Container
    public static DockerComposeContainer boot;
    public static DockerComposeContainer kernel;

    static {
        var bootFile = new File("bootstrap/compose.yaml");
        boot = new DockerComposeContainer(bootFile).withTailChildContainers(true);
        var kernelFile = new File("kernel/compose.yaml");
        kernel = new DockerComposeContainer(kernelFile).withTailChildContainers(true);
    }

    @Test
    public void smokin() throws Exception {
        boot.start();
        //        new Socket().connect(new InetSocketAddress("172.18.0.2", 50004), 30_000);

        kernel.start();
        //        new Socket().connect(new InetSocketAddress("172.18.0.2", 50007), 30_000);
        Thread.sleep(30_000);
    }

    private MtlsClient apiClient(int i, InetSocketAddress serverAddress) {
        CertificateWithPrivateKey clientCert = Utils.getMember(i);

        return new MtlsClient(serverAddress, ClientAuth.REQUIRE, "foo", clientCert.getX509Certificate(),
                              clientCert.getPrivateKey(), CertificateValidator.NONE);
    }

    private Oracle of(SocketAddress endpoint) {
        var client = apiClient(0, (InetSocketAddress) endpoint);
        return new OracleAdapter(client.getChannel());
    }
}
