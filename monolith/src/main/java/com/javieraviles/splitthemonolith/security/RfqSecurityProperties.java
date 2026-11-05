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
		 * Either an encoded value with an id prefix (e.g. {bcrypt}...) or a raw
		 * value, which is bcrypt-encoded at startup. Blank disables the user.
		 */
		private String password;

		private Role role;

		/** LEI of the counterparty a TRADER acts for. */
		private String counterpartyLei;

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

		public String getCounterpartyLei() {
			return counterpartyLei;
		}

		public void setCounterpartyLei(final String counterpartyLei) {
			this.counterpartyLei = counterpartyLei;
		}
	}
}
