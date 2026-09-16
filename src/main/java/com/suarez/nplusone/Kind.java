package com.suarez.nplusone;

import java.util.Locale;

/** Que clase de sentencia es. Determina como se reporta un hallazgo. */
public enum Kind {
	SELECT,
	INSERT,
	UPDATE,
	DELETE,
	MERGE,
	OTHER;

	static Kind of(String sql) {
		String trimmed = sql.stripLeading();
		int end = 0;
		while (end < trimmed.length() && Character.isLetter(trimmed.charAt(end))) {
			end++;
		}
		String verb = trimmed.substring(0, end).toUpperCase(Locale.ROOT);
		return switch (verb) {
			case "SELECT" -> SELECT;
			case "INSERT" -> INSERT;
			case "UPDATE" -> UPDATE;
			case "DELETE" -> DELETE;
			case "MERGE" -> MERGE;
			default -> OTHER;
		};
	}
}
