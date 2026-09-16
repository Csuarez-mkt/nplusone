package com.suarez.nplusone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saca el SQL de una linea de log.
 *
 * No hay un formato por parsear: cada equipo loguea distinto (Hibernate, p6spy,
 * JdbcTemplate, o el SQL pelado). En vez de una gramatica por herramienta,
 * buscamos el verbo SQL de la linea y nos quedamos con lo que sigue.
 *
 * El verbo solo no basta: una linea que diga "delete failed for order 12" lo
 * tiene y no es SQL. Por eso cada verbo exige ademas su pieza obligatoria
 * (`from` para select, `into` para insert, `set` para update). Prefiero dejar
 * pasar una sentencia rara a inventarme sentencias que nadie ejecuto: un
 * hallazgo falso le quita al reporte toda la autoridad.
 */
final class SqlExtractor {

	private static final Pattern VERB = Pattern.compile("(?i)\\b(select|insert|update|delete|merge)\\b");

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	/** p6spy abre cada linea con la marca de tiempo y el tiempo de ejecucion. */
	private static final Pattern P6SPY = Pattern.compile("^\\d+\\|\\d+\\|");

	private SqlExtractor() {
	}

	static Optional<String> extract(String line) {
		if (line == null || line.isBlank()) {
			return Optional.empty();
		}

		List<String> candidates = new ArrayList<>();
		Matcher matcher = VERB.matcher(line);
		while (matcher.find()) {
			String candidate = line.substring(matcher.start()).strip();
			if (looksLikeSql(candidate)) {
				candidates.add(candidate);
			}
		}
		if (candidates.isEmpty()) {
			return Optional.empty();
		}

		// p6spy escribe la sentencia dos veces, primero con `?` y despues con los
		// valores puestos. Nos quedamos con la ultima, que es la que de verdad
		// viajo. En cualquier otro formato la primera es la buena: la segunda
		// seria una subconsulta, y cortar ahi partiria la sentencia por la mitad.
		return Optional.of(P6SPY.matcher(line).find() ? candidates.getLast() : candidates.getFirst());
	}

	private static boolean looksLikeSql(String candidate) {
		String flat = " " + WHITESPACE.matcher(candidate.toLowerCase(Locale.ROOT)).replaceAll(" ") + " ";
		return switch (Kind.of(candidate)) {
			case SELECT, DELETE -> flat.contains(" from ");
			case INSERT -> flat.contains(" into ");
			case UPDATE -> flat.contains(" set ");
			case MERGE -> flat.contains(" using ") || flat.contains(" into ");
			case OTHER -> false;
		};
	}
}
