package com.javieraviles.splitthemonolith.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(RfqSecurityProperties.class)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	private static final String OPERATOR = Role.OPERATOR.name();
	private static final String COUNTERPARTY = Role.COUNTERPARTY.name();

	@Bean
	UserDetailsService configuredUserDetailsService(final RfqSecurityProperties properties) {
		return new ConfiguredUserDetailsService(properties);
	}

	private static final String[] SHARED_READ_PATHS = { "/bonds", "/bonds/{id}", "/counterparties/{id}", "/rfqs",
			"/rfqs/{id}" };

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Override
	protected void configure(final HttpSecurity http) throws Exception {
		http.csrf().disable()
				.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
				.and()
				.authorizeRequests()
				.mvcMatchers("/error").permitAll()
				// Ownership of the counterparty / RFQ is enforced in the controllers (HEAD reuses the GET handlers).
				.mvcMatchers(HttpMethod.GET, SHARED_READ_PATHS).hasAnyRole(OPERATOR, COUNTERPARTY)
				.mvcMatchers(HttpMethod.HEAD, SHARED_READ_PATHS).hasAnyRole(OPERATOR, COUNTERPARTY)
				.mvcMatchers(HttpMethod.POST, "/rfqs").hasAnyRole(OPERATOR, COUNTERPARTY)
				.anyRequest().hasRole(OPERATOR)
				.and()
				.httpBasic();
	}
}
