package com.javieraviles.splitthemonolith;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Regression tests for CVE-2021-42392 (SNYK-JAVA-COMH2DATABASE-2348247).
 *
 * RCE via JDBC URL manipulation was fixed in H2 >= 2.1.210.
 */
@SpringBootTest
@TestPropertySource(properties = {
	"spring.datasource.url=jdbc:h2:mem:securitytest;MODE=LEGACY;DB_CLOSE_ON_EXIT=FALSE"
})
public class H2SecurityTest {

	@Autowired
	private DataSource dataSource;

	@Value("${spring.h2.console.enabled:true}")
	private boolean h2ConsoleEnabled;

	@Test
	public void h2Version_isAtLeast_2_1_210_CVE2021_42392() throws Exception {
		Connection conn = dataSource.getConnection();
		try {
			final String version = conn.getMetaData().getDatabaseProductVersion();
			final String[] parts = version.split("\\.");
			final int major = Integer.parseInt(parts[0]);
			final int minor = Integer.parseInt(parts[1]);
			final int patch = Integer.parseInt(parts[2].split("[^0-9]")[0]);

			assertThat(major).as("H2 major version").isGreaterThanOrEqualTo(2);
			if (major == 2) {
				assertThat(minor).as("H2 minor version").isGreaterThanOrEqualTo(1);
				if (minor == 1) {
					assertThat(patch).as("H2 patch version").isGreaterThanOrEqualTo(210);
				}
			}
		} finally {
			conn.close();
		}
	}

	@Test
	public void h2Console_isDisabled_CVE2021_42392() {
		assertThat(h2ConsoleEnabled)
				.as("H2 console must be disabled to mitigate RCE attack surface")
				.isFalse();
	}
}
