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
				.mvcMatchers(HttpMethod.GET, "/bonds", "/bonds/{id}").hasAnyRole(OPERATOR, COUNTERPARTY)
				// Ownership of the counterparty / RFQ is enforced in the controllers.
				.mvcMatchers(HttpMethod.GET, "/counterparties/{id}").hasAnyRole(OPERATOR, COUNTERPARTY)
				.mvcMatchers(HttpMethod.GET, "/rfqs", "/rfqs/{id}").hasAnyRole(OPERATOR, COUNTERPARTY)
				.mvcMatchers(HttpMethod.POST, "/rfqs").hasAnyRole(OPERATOR, COUNTERPARTY)
				.anyRequest().hasRole(OPERATOR)
				.and()
				.httpBasic();
	}
}
