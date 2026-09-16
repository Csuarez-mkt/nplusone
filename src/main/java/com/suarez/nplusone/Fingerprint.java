package com.suarez.nplusone;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reduce una sentencia a su forma: la misma consulta con otros valores da la
 * misma huella.
 *
 * Sin esto, `where id=1` y `where id=2` serian consultas distintas y el bucle
 * se veria como cincuenta consultas diferentes, que es justo lo que hay que
 * detectar.
 */
final class Fingerprint {

	/** Literales de texto, incluyendo el escape '' de SQL. */
	private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");

	private static final Pattern NUMBER = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");

	/** in (?, ?, ?) y in (?, ?) son la misma consulta con distinto numero de ids. */
	private static final Pattern IN_LIST = Pattern.compile("\\bin\\s*\\(\\s*\\?(?:\\s*,\\s*\\?)+\\s*\\)");

	/** values (?, ?), (?, ?) es un insert por lote: su forma no depende del tamano del lote. */
	private static final Pattern VALUES_LIST =
			Pattern.compile("\\bvalues\\s*\\([^()]*\\)(?:\\s*,\\s*\\([^()]*\\))+");

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private Fingerprint() {
	}

	static String of(String sql) {
		String s = sql.strip();
		while (s.endsWith(";")) {
			s = s.substring(0, s.length() - 1).strip();
		}
		// Los literales se van antes de bajar a minusculas: dentro de unas comillas
		// puede haber cualquier cosa, incluida la palabra `values`.
		s = STRING_LITERAL.matcher(s).replaceAll("?");
		s = s.toLowerCase(Locale.ROOT);
		s = WHITESPACE.matcher(s).replaceAll(" ");
		// Los alias que numera Hibernate (o1_0) sobreviven: el digito va pegado a
		// una letra, asi que no hay frontera de palabra donde empezar a contar.
		s = NUMBER.matcher(s).replaceAll("?");
		s = IN_LIST.matcher(s).replaceAll("in (?)");
		s = VALUES_LIST.matcher(s).replaceAll("values (?)");
		return s.strip();
	}
}
