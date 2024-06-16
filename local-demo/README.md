# Sky Application Local Demo

___
This is the docker compose configurations for running a local cluster of the Sky Application.
___
This demo uses the local bridge network to connect the cluster of Sky nodes. To bootstrap the cluster,
the bootstrap node's host address and seed/approach ports need to be well-known. Currently, the bootstrap 
host is hard-wired in this demo to be **172.17.0.2**
.
This will be configurable, discoverable.  The shared secret for the Sky cluster
is likewise hard-wired until convenient client demos for Shamir bootstrapping can be built for the demo.
### Minimal Quorum Kernel Bootstrap

The Sky cluster must first bootstrap a minimal quorum of members—i.e., four kernel members. This is achieved by first starting the bootstrap member of the cluster using
[bootstrap/compose.yaml](bootstrap/compose.yaml). The remaining three members of the
kernel minimal quorum are started using
[kernel/compose.yaml](kernel/compose.yaml). It's important that you allow the kernel membership
to finish generating the **Genesis** block of the cluster before starting any other nodes.  (I will figure out how to make this dependency work automagically, but
for now...).
### Add Moar Nodes
Once you have the minimal quorum up, you can add nodes as desired using [nodes/compose.yaml](nodes/compose.yaml).
Scale the number of nodes up and down.

