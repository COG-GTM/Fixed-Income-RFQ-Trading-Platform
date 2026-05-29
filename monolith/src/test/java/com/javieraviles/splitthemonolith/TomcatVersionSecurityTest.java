package com.javieraviles.splitthemonolith;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.catalina.util.ServerInfo;
import org.junit.jupiter.api.Test;

/**
 * Regression test for SNYK-JAVA-ORGAPACHETOMCATEMBED-8383920 (CVE / CWE-248).
 * Ensures tomcat-embed-core is at least 9.0.96, the first version that
 * fixes the Uncaught Exception vulnerability (CVSS 9.2).
 */
public class TomcatVersionSecurityTest {

    private static final int MINIMUM_MINOR = 0;
    private static final int MINIMUM_PATCH = 96;

    @Test
    public void tomcatEmbedCore_IsAtLeast_9_0_96_SNYK_8383920() {
        String version = ServerInfo.getServerNumber();   // e.g. "9.0.96.0"
        String[] parts = version.split("\\.");

        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);

        assertTrue(major > 9 || (major == 9 && minor > MINIMUM_MINOR)
                        || (major == 9 && minor == MINIMUM_MINOR && patch >= MINIMUM_PATCH),
                "tomcat-embed-core must be >= 9.0.96 to fix SNYK-JAVA-ORGAPACHETOMCATEMBED-8383920, "
                        + "but found " + version);
    }
}
