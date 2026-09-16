package com.suarez.nplusone;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lee un log y devuelve las sentencias en el orden en que se ejecutaron.
 *
 * No trabaja linea por linea sino por bloques, porque con
 * `hibernate.format_sql` encendido una sola sentencia ocupa diez lineas y el
 * `select` ni siquiera va en la misma linea que el encabezado del log. Leyendo
 * linea por linea, esa sentencia se perderia entera, o peor: todas quedarian
 * reducidas a la palabra `select` y se verian iguales, que es un N+1 inventado.
 *
 * Un bloque empieza cuando la linea trae SQL por si sola, cuando trae marca de
 * log, o cuando no habia bloque abierto. Todo lo demas se pega al bloque
 * abierto. Al cerrarlo se busca el SQL en el bloque completo.
 */
final class LogReader {

	/**
	 * Marca de comienzo de linea de log: una fecha, un corchete de hilo, o un
	 * nivel. Los niveles se buscan en mayusculas a proposito, porque el SQL
	 * suele venir en minusculas y una columna llamada `error` no deberia
	 * cortar la sentencia por la mitad.
	 */
	private static final Pattern LOG_MARKER = Pattern.compile(
			"^\\s*(?:\\d{4}-\\d{2}-\\d{2}|\\[)|\\b(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\b");

	private LogReader() {
	}

	static List<Statement> read(BufferedReader input) throws IOException {
		List<Statement> statements = new ArrayList<>();
		StringBuilder block = null;

		String line;
		while ((line = input.readLine()) != null) {
			if (line.isBlank()) {
				close(block, statements);
				block = null;
				continue;
			}

			if (block == null || startsNewBlock(line)) {
				close(block, statements);
				block = new StringBuilder(line.strip());
			} else {
				block.append(' ').append(line.strip());
			}
		}
		close(block, statements);
		return List.copyOf(statements);
	}

	private static boolean startsNewBlock(String line) {
		return SqlExtractor.extract(line).isPresent() || LOG_MARKER.matcher(line).find();
	}

	private static void close(StringBuilder block, List<Statement> statements) {
		if (block == null) {
			return;
		}
		SqlExtractor.extract(block.toString())
				.ifPresent(sql -> statements.add(Statement.of(statements.size(), sql)));
	}
}
