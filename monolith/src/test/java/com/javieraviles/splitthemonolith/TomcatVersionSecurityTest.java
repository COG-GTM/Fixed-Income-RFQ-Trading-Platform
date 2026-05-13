package com.javieraviles.splitthemonolith;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.catalina.util.ServerInfo;
import org.junit.jupiter.api.Test;

/**
 * Regression test for COG-427 / SNYK-JAVA-ORGAPACHETOMCATEMBED-8547999.
 * Ensures the embedded Tomcat version is at least 9.0.98, which contains
 * the fix for the CWE-367 TOCTOU race condition (CVSS 9.2).
 */
public class TomcatVersionSecurityTest {

	private static final int REQUIRED_MAJOR = 9;
	private static final int REQUIRED_MINOR = 0;
	private static final int REQUIRED_PATCH = 98;

	@Test
	public void embeddedTomcat_IsAtLeast_9_0_98_CVE_TOCTOU_RaceCondition() {
		String serverNumber = ServerInfo.getServerNumber(); // e.g. "9.0.98"
		String[] parts = serverNumber.split("\\.");

		int major = Integer.parseInt(parts[0]);
		int minor = Integer.parseInt(parts[1]);
		int patch = Integer.parseInt(parts[2]);

		boolean atLeastRequired =
				(major > REQUIRED_MAJOR) ||
				(major == REQUIRED_MAJOR && minor > REQUIRED_MINOR) ||
				(major == REQUIRED_MAJOR && minor == REQUIRED_MINOR && patch >= REQUIRED_PATCH);

		assertTrue(atLeastRequired,
				"Tomcat version " + serverNumber + " is below the minimum required "
						+ REQUIRED_MAJOR + "." + REQUIRED_MINOR + "." + REQUIRED_PATCH
						+ " (fix for TOCTOU race condition CVE, SNYK-JAVA-ORGAPACHETOMCATEMBED-8547999)");
	}
}
