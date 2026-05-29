package com.javieraviles.splitthemonolith;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.catalina.util.ServerInfo;
import org.junit.jupiter.api.Test;

/**
 * Regression test for SNYK-JAVA-ORGAPACHETOMCATEMBED-8523186 (CVE TOCTOU race
 * condition, CVSS 9.2). Ensures the embedded Tomcat version is at least 9.0.98,
 * the minimum patched release for CWE-367.
 */
public class TomcatVersionSecurityTest {

    private static final int REQUIRED_MAJOR = 9;
    private static final int REQUIRED_MINOR = 0;
    private static final int REQUIRED_PATCH = 98;

    @Test
    public void tomcatEmbedCore_isAtLeastPatchedVersion_SNYK_JAVA_ORGAPACHETOMCATEMBED_8523186() {
        String serverNumber = ServerInfo.getServerNumber();
        String[] parts = serverNumber.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);

        assertTrue(
                major > REQUIRED_MAJOR
                        || (major == REQUIRED_MAJOR && minor > REQUIRED_MINOR)
                        || (major == REQUIRED_MAJOR && minor == REQUIRED_MINOR && patch >= REQUIRED_PATCH),
                "tomcat-embed-core must be >= " + REQUIRED_MAJOR + "." + REQUIRED_MINOR + "."
                        + REQUIRED_PATCH + " to fix SNYK-JAVA-ORGAPACHETOMCATEMBED-8523186 "
                        + "(TOCTOU race condition, CWE-367). Found: " + serverNumber);
    }
}
