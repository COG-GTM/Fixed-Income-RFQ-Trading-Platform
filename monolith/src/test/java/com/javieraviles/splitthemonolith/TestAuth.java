package com.javieraviles.splitthemonolith;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** HTTP Basic credentials for the users defined in application-test.properties. */
final class TestAuth {

	static final RequestPostProcessor OPERATOR = basic("test-operator", "test-operator-pw");
	static final RequestPostProcessor ACME_TRADER = basic("acme-trader", "acme-trader-pw");
	static final RequestPostProcessor UNBOUND_TRADER = basic("unbound-trader", "unbound-trader-pw");

	private TestAuth() {
	}

	static RequestPostProcessor basic(final String username, final String secret) {
		final String token = Base64.getEncoder()
				.encodeToString((username + ":" + secret).getBytes(StandardCharsets.UTF_8));
		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + token);
			return request;
		};
	}
}
