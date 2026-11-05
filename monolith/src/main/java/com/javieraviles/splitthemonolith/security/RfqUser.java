package com.javieraviles.splitthemonolith.security;

import java.util.Collections;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class RfqUser extends User {

	private static final long serialVersionUID = 1L;

	private final Role role;

	private final String counterpartyLei;

	public RfqUser(final String username, final String encodedPassword, final boolean enabled, final Role role,
			final String counterpartyLei) {
		super(username, encodedPassword, enabled, true, true, true,
				Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name())));
		this.role = role;
		this.counterpartyLei = counterpartyLei;
	}

	public Role getRole() {
		return role;
	}

	public String getCounterpartyLei() {
		return counterpartyLei;
	}
}
