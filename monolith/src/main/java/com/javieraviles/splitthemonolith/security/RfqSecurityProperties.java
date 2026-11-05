package com.javieraviles.splitthemonolith.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rfq.security")
public class RfqSecurityProperties {

	private List<UserEntry> users = new ArrayList<>();

	public List<UserEntry> getUsers() {
		return users;
	}

	public void setUsers(final List<UserEntry> users) {
		this.users = users;
	}

	public static class UserEntry {

		private String username;

		/**
		 * Password in Spring Security's delegating format, e.g. {bcrypt}$2a$10$...
		 */
		private String password;

		private Role role;

		private Long counterpartyId;

		public String getUsername() {
			return username;
		}

		public void setUsername(final String username) {
			this.username = username;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(final String password) {
			this.password = password;
		}

		public Role getRole() {
			return role;
		}

		public void setRole(final Role role) {
			this.role = role;
		}

		public Long getCounterpartyId() {
			return counterpartyId;
		}

		public void setCounterpartyId(final Long counterpartyId) {
			this.counterpartyId = counterpartyId;
		}
	}
}
