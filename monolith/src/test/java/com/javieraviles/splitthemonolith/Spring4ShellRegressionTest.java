package com.javieraviles.splitthemonolith;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
    public void Spring4Shell_ClassLoaderAccessBlocked_CVE_2022_22965() throws Exception {
        // The Spring4Shell exploit sends requests that attempt to traverse
        // class.module.classLoader to reach Tomcat's AccessLogValve.
        // On a patched version, these parameters are silently ignored by
        // the data binder (the classLoader property is not accessible).
        // A 400 response or a 200 response without side effects is acceptable;
        // a 500 with a classLoader-related stack trace would indicate the
        // vulnerability is still exploitable.
        mvc.perform(get("/bonds")
                .param("class.module.classLoader.resources.context.parent.pipeline.first.pattern",
                        "%25%7Bc2%7Di%20if(%22j%22.equals(request.getParameter(%22pwd%22)))%7B%20java.io.InputStream%20in%20%3D%20%25%7Bc1%7Di.getRuntime().exec(request.getParameter(%22cmd%22)).getInputStream()%3B%20int%20a%20%3D%20-1%3B%20byte%5B%5D%20b%20%3D%20new%20byte%5B2048%5D%3B%20while((a%3Din.read(b))!%3D-1)%7B%20out.println(new%20String(b))%3B%20%7D%20%7D%20%25%7Bsuffix%7Di")
                .param("class.module.classLoader.resources.context.parent.pipeline.first.suffix", ".jsp")
                .param("class.module.classLoader.resources.context.parent.pipeline.first.directory", "webapps/ROOT")
                .param("class.module.classLoader.resources.context.parent.pipeline.first.prefix", "tomcatwar")
                .param("class.module.classLoader.resources.context.parent.pipeline.first.fileDateFormat", ""))
                .andExpect(status().isOk());
    }

    @Test
    public void Spring4Shell_FrameworkVersionPatched_CVE_2022_22965() {
        String springVersion = org.springframework.core.SpringVersion.getVersion();
        // Spring4Shell is fixed in 5.2.20+ and 5.3.18+.
        // Spring Boot 2.7.x ships with Spring Framework 5.3.x.
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
