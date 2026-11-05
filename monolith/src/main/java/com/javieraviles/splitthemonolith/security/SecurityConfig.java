package com.javieraviles.splitthemonolith.security;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(RfqSecurityProperties.class)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

	private static final String OPERATOR = Role.OPERATOR.name();
	private static final String TRADER = Role.TRADER.name();

	private final RfqSecurityProperties properties;

	public SecurityConfig(final RfqSecurityProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void configure(final HttpSecurity http) throws Exception {
		http.csrf().disable()
				.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
				.and().httpBasic()
				.and().authorizeRequests()
				.antMatchers("/error").permitAll()
				.antMatchers(HttpMethod.GET, "/counterparties/**", "/bonds/**", "/rfqs/**").hasAnyRole(OPERATOR, TRADER)
				.antMatchers(HttpMethod.POST, "/rfqs").hasAnyRole(OPERATOR, TRADER)
				.antMatchers("/counterparties/**", "/bonds/**", "/rfqs/**").hasRole(OPERATOR)
				.anyRequest().authenticated();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	UserDetailsService rfqUserDetailsService(final PasswordEncoder encoder) {
		final Map<String, RfqUser> users = new HashMap<>();
		for (final RfqSecurityProperties.UserEntry entry : properties.getUsers()) {
			final RfqUser user = toUser(entry, encoder);
			if (users.putIfAbsent(user.getUsername(), user) != null) {
				throw new IllegalStateException("Duplicate rfq.security user: " + user.getUsername());
			}
		}
		return username -> {
			final RfqUser user = users.get(username);
			if (user == null) {
				throw new UsernameNotFoundException(username);
			}
			return new RfqUser(user.getUsername(), user.getPassword(), user.isEnabled(), user.getRole(),
					user.getCounterpartyLei());
		};
	}

	private static RfqUser toUser(final RfqSecurityProperties.UserEntry entry, final PasswordEncoder encoder) {
		if (!StringUtils.hasText(entry.getUsername()) || entry.getRole() == null) {
			throw new IllegalStateException("Every rfq.security user needs a username and a role");
		}
		if (entry.getRole() == Role.TRADER && !StringUtils.hasText(entry.getCounterpartyLei())) {
			throw new IllegalStateException("TRADER user " + entry.getUsername() + " needs a counterparty-lei");
		}
		final String raw = entry.getPassword();
		final boolean enabled = StringUtils.hasText(raw);
		final String encoded;
		if (!enabled) {
			LOG.warn("No password configured for rfq.security user '{}'; login is disabled for this user",
					entry.getUsername());
			encoded = encoder.encode(UUID.randomUUID().toString());
		} else if (raw.startsWith("{")) {
			encoded = raw;
		} else {
			encoded = encoder.encode(raw);
		}
		return new RfqUser(entry.getUsername(), encoded, enabled, entry.getRole(), entry.getCounterpartyLei());
	}
}
