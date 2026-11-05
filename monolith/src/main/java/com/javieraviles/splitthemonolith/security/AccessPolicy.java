package com.javieraviles.splitthemonolith.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;

/**
 * Resolves the authenticated caller and the counterparty a TRADER is allowed to
 * act for. OPERATORs may act on any counterparty.
 */
@Component
public class AccessPolicy {

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	public boolean isOperator() {
		return currentUser().getRole() == Role.OPERATOR;
	}

	/** The counterparty bound to the current TRADER. */
	public Counterparty ownCounterparty() {
		final List<Counterparty> matches = counterpartyRepository.findByLei(currentUser().getCounterpartyLei());
		if (matches.size() != 1) {
			throw new AccessDeniedException("No unique counterparty is bound to this user");
		}
		return matches.get(0);
	}

	public void checkCounterpartyAccess(final long counterpartyId) {
		if (!isOperator() && ownCounterparty().getId() != counterpartyId) {
			throw new AccessDeniedException("Not allowed to act for counterparty " + counterpartyId);
		}
	}

	private static RfqUser currentUser() {
		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof RfqUser)) {
			throw new AccessDeniedException("Unauthenticated");
		}
		return (RfqUser) auth.getPrincipal();
	}
}
