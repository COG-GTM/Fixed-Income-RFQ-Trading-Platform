package com.javieraviles.splitthemonolith;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.WebDataBinder;

import com.javieraviles.splitthemonolith.entity.Bond;

/**
 * Regression test for CVE-2022-22965 (Spring4Shell).
 *
 * The vulnerability allowed RCE via class loader manipulation through
 * Spring MVC data binding on JDK 9+. The fix in Spring Framework 5.3.18+
 * restricts property access so that {@code class.module.classLoader} paths
 * are no longer reachable through data binding.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class Spring4ShellRegressionTest {

	@Autowired
	private MockMvc mvc;

	@Test
	public void Spring4Shell_ClassLoaderNotBindable_CVE_2022_22965() {
		// CVE-2022-22965 exploits Spring's data binder to traverse
		// class.module.classLoader on JDK 9+. Patched Spring (>= 5.3.18)
		// blocks access to "class" in CachedIntrospectionResults, making
		// the classLoader property unreachable via data binding.
		Bond target = new Bond();
		WebDataBinder binder = new WebDataBinder(target, "target");

		BeanWrapperImpl wrapper = new BeanWrapperImpl(target);
		boolean classLoaderReachable = wrapper.isReadableProperty("class.module.classLoader");

		assertTrue(!classLoaderReachable,
				"class.module.classLoader must NOT be reachable via property access. "
						+ "This indicates CVE-2022-22965 (Spring4Shell) is still exploitable.");
	}

	@Test
	public void Spring4Shell_ClassPropertyValueIsNull_CVE_2022_22965() {
		// Even if "class" is technically a readable property on Object,
		// the patched BeanWrapperImpl should prevent traversal past "class"
		// to reach "module.classLoader".
		Bond target = new Bond();
		BeanWrapperImpl wrapper = new BeanWrapperImpl(target);

		Object value = null;
		try {
			value = wrapper.getPropertyValue("class.module.classLoader");
		} catch (Exception e) {
			// Expected on patched versions — access is blocked
		}
		assertNull(value,
				"class.module.classLoader returned a non-null value, "
						+ "indicating CVE-2022-22965 (Spring4Shell) may be exploitable.");
	}

	@Test
	public void Spring4Shell_FrameworkVersionPatched_CVE_2022_22965() {
		String springVersion = org.springframework.core.SpringVersion.getVersion();
		String[] parts = springVersion.split("\\.");
		int major = Integer.parseInt(parts[0]);
		int minor = Integer.parseInt(parts[1]);
		int patch = Integer.parseInt(parts[2]);

		boolean patched;
		if (major > 5) {
			patched = true;
		} else if (major == 5 && minor > 3) {
			patched = true;
		} else if (major == 5 && minor == 3) {
			patched = patch >= 18;
		} else if (major == 5 && minor == 2) {
			patched = patch >= 20;
		} else {
			patched = false;
		}

		assertTrue(patched,
				"Spring Framework version " + springVersion
						+ " is vulnerable to CVE-2022-22965 (Spring4Shell). "
						+ "Minimum safe versions: 5.2.20 or 5.3.18.");
	}
}
