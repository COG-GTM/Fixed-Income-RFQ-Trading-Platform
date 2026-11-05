package com.javieraviles.splitthemonolith.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

	private CurrentUser() {
	}

	public static RfqUserDetails get() {
		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof RfqUserDetails)) {
			throw new AccessDeniedException("Not authenticated");
		}
		return (RfqUserDetails) auth.getPrincipal();
	}

	public static void requireAccessToCounterparty(final long counterpartyId) {
		final RfqUserDetails user = get();
		if (!user.isOperator() && (user.getCounterpartyId() == null || user.getCounterpartyId() != counterpartyId)) {
			throw new AccessDeniedException("Access to counterparty denied");
		}
	}
}
