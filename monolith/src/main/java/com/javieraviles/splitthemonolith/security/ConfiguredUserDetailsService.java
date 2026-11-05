package com.javieraviles.splitthemonolith.security;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;

/**
 * Users are supplied via configuration (rfq.security.users[n].*), typically from
 * environment variables. No users are configured by default, so every request is
 * rejected until credentials are provisioned.
 */
public class ConfiguredUserDetailsService implements UserDetailsService {

	private static final Logger LOG = LoggerFactory.getLogger(ConfiguredUserDetailsService.class);

	private final Map<String, RfqUserDetails> users = new HashMap<>();

	public ConfiguredUserDetailsService(final RfqSecurityProperties properties) {
		for (final RfqSecurityProperties.UserEntry entry : properties.getUsers()) {
			if (!StringUtils.hasText(entry.getUsername()) || !StringUtils.hasText(entry.getPassword())
					|| entry.getRole() == null) {
				throw new IllegalStateException("rfq.security.users entries require username, password and role");
			}
			if (entry.getRole() == Role.COUNTERPARTY && entry.getCounterpartyId() == null) {
				throw new IllegalStateException(
						"COUNTERPARTY user '" + entry.getUsername() + "' requires a counterpartyId");
			}
			if (users.containsKey(entry.getUsername())) {
				throw new IllegalStateException("Duplicate rfq.security user '" + entry.getUsername() + "'");
			}
			final Long counterpartyId = entry.getRole() == Role.COUNTERPARTY ? entry.getCounterpartyId() : null;
			users.put(entry.getUsername(),
					new RfqUserDetails(entry.getUsername(), entry.getPassword(), entry.getRole(), counterpartyId));
		}
		if (users.isEmpty()) {
			LOG.warn("No rfq.security.users configured: all API requests will be rejected with 401");
		}
	}

	@Override
	public UserDetails loadUserByUsername(final String username) {
		final RfqUserDetails user = users.get(username);
		if (user == null) {
			throw new UsernameNotFoundException("Unknown user");
		}
		return new RfqUserDetails(user.getUsername(), user.getPassword(), user.getRole(), user.getCounterpartyId());
	}
}
