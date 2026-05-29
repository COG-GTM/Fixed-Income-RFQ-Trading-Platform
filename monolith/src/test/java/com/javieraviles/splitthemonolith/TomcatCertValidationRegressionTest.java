package com.javieraviles.splitthemonolith;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Regression test for CVE in tomcat-embed-core — Improper Certificate
 * Validation (CWE-295, CVSS 9.1). Verifies the runtime Tomcat version
 * is at or above the patched release (9.0.113).
 *
 * @see <a href="https://security.snyk.io/vuln/SNYK-JAVA-ORGAPACHETOMCATEMBED-15307781">Snyk advisory</a>
 */
@SpringBootTest
public class TomcatCertValidationRegressionTest {

    private static final int PATCHED_MAJOR = 9;
    private static final int PATCHED_MINOR = 0;
    private static final int PATCHED_PATCH = 113;

    @Autowired
    private ApplicationContext context;

    @Test
    public void tomcatVersion_isAtOrAbovePatched_CVE_CWE295() {
        String version = org.apache.catalina.util.ServerInfo.getServerNumber();
        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);

        assertTrue(
            major > PATCHED_MAJOR
                || (major == PATCHED_MAJOR && minor > PATCHED_MINOR)
                || (major == PATCHED_MAJOR && minor == PATCHED_MINOR && patch >= PATCHED_PATCH),
            "tomcat-embed-core must be >= " + PATCHED_MAJOR + "." + PATCHED_MINOR + "." + PATCHED_PATCH
                + " to fix CWE-295 certificate validation bypass, but found " + version
        );
    }

    @Test
    public void embeddedTomcat_bootsSuccessfully_withPatchedVersion_CVE_CWE295() {
        assertNotNull(context, "Spring context with patched Tomcat must start");
        String version = org.apache.catalina.util.ServerInfo.getServerNumber();
        assertNotNull(version, "Tomcat ServerInfo.getServerNumber() must not be null");
        assertTrue(version.startsWith("9.0."),
            "Expected Tomcat 9.0.x but got " + version);
    }
}
