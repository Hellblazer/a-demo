package com.hellblazer.nut.aws;

/**
 * @author hal.hildebrand
 **/
public class AwsProvisionerTest {
    public static String TEST_DOC = """
        {
            "instanceId" : "i-c495bb93",
            "billingProducts" : [ "bp-xxx" ],
            "accountId" : "xxx",
            "imageId" : "ami-e80xxxx",
            "instanceType" : "c3.xlarge",
            "kernelId" : "aki-825ea7eb",
            "ramdiskId" : null,
            "pendingTime" : "2015-02-24T14:38:43Z",
            "architecture" : "x86_64",
            "region" : "us-east-1",
            "version" : "2010-08-31",
            "availabilityZone" : "us-east-1c",
            "privateIp" : "w.x.y.z",
            "devpayProductCodes" : null
        }
    """;
}
