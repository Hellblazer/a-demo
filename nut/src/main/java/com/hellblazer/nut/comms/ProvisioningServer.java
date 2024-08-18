package com.hellblazer.nut.comms;

import com.hellblazer.nut.Provisioner;
import com.hellblazer.nut.proto.InitialProvisioning;
import com.hellblazer.nut.proto.ProvisioningGrpc;
import com.hellblazer.sanctorum.proto.FernetToken;
import io.grpc.stub.StreamObserver;

/**
 * @author hal.hildebrand
 **/
public class ProvisioningServer extends ProvisioningGrpc.ProvisioningImplBase {
    private final Provisioner provisioner;

    public ProvisioningServer(Provisioner provisioner) {
        this.provisioner = provisioner;
    }

    @Override
    public void provision(InitialProvisioning request, StreamObserver<FernetToken> responseObserver) {
        var token = provisioner.initialProvisioning(request);
        responseObserver.onNext(token);
    }
}
