package com.javieraviles.splitthemonolith.security;

import java.util.Collections;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class RfqUserDetails extends User {

	private static final long serialVersionUID = 1L;

	private final Role role;

	private final Long counterpartyId;

	public RfqUserDetails(final String username, final String password, final Role role, final Long counterpartyId) {
		super(username, password, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name())));
		this.role = role;
		this.counterpartyId = counterpartyId;
	}

	public Role getRole() {
		return role;
	}

	public Long getCounterpartyId() {
		return counterpartyId;
	}

	public boolean isOperator() {
		return role == Role.OPERATOR;
	}
}
