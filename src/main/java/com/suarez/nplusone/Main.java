package com.suarez.nplusone;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * nplusone: busca consultas repetidas en bucle dentro de un log de SQL.
 */
public final class Main {

	/** Sin hallazgos. */
	static final int OK = 0;
	/** Hubo hallazgos: asi el CI puede frenar el merge. */
	static final int FINDINGS = 1;
	/** El usuario se equivoco al invocar, o el archivo no se pudo leer. */
	static final int ERROR = 2;

	private static final String USAGE = """
			nplusone %s - encuentra consultas repetidas en bucle (el N+1)

			Uso:
			  nplusone [opciones] [archivo]
			  ... | nplusone [opciones]

			Lee un log con SQL (Hibernate, p6spy, JdbcTemplate o SQL pelado) y
			reporta las consultas que se repitieron muchas veces seguidas.

			Opciones:
			  -t, --threshold N   repeticiones seguidas para reportar (por omision %d)
			  -g, --gap N         sentencias ajenas toleradas dentro de una racha (por omision %d)
			  -j, --json          reporte en JSON
			      --no-fail       termina en 0 aunque haya hallazgos
			  -h, --help          esta ayuda
			  -v, --version       la version

			Codigos de salida:
			  0  sin hallazgos
			  1  hubo hallazgos
			  2  error de uso o de lectura
			""".formatted(Version.NUMBER, Options.DEFAULT_THRESHOLD, Options.DEFAULT_GAP);

	private Main() {
	}

	public static void main(String[] args) {
		System.exit(run(args, System.in, System.out, System.err));
	}

	/**
	 * El trabajo de verdad, con las tres corrientes por parametro.
	 *
	 * main() solo traduce a codigo de salida. Asi la prueba puede correr la
	 * herramienta entera (argumentos, lectura, reporte y codigo de salida) sin
	 * tumbar la JVM del test con un System.exit().
	 */
	static int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
		Options options;
		try {
			options = Options.parse(args);
		} catch (Options.UsageException e) {
			err.println("nplusone: " + e.getMessage());
			err.println();
			err.print(USAGE);
			return ERROR;
		}

		if (options.help()) {
			out.print(USAGE);
			return OK;
		}
		if (options.version()) {
			out.println(Version.NUMBER);
			return OK;
		}

		List<Statement> statements;
		try {
			statements = readStatements(options, in);
		} catch (IOException e) {
			err.println("nplusone: no se pudo leer: " + e.getMessage());
			return ERROR;
		}

		List<Finding> findings = new Detector(options.threshold(), options.gap()).scan(statements);
		Report.Summary summary = new Report.Summary(statements.size(), shapeCount(statements));

		out.print(options.json() ? Report.json(summary, findings) : Report.text(summary, findings));

		return findings.isEmpty() || !options.failOnFindings() ? OK : FINDINGS;
	}

	private static List<Statement> readStatements(Options options, InputStream in) throws IOException {
		if (options.file().isPresent()) {
			Path path = options.file().get();
			try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				return LogReader.read(reader);
			}
		}
		return LogReader.read(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
	}

	private static int shapeCount(List<Statement> statements) {
		Set<String> shapes = new HashSet<>();
		for (Statement statement : statements) {
			shapes.add(statement.fingerprint());
		}
		return shapes.size();
	}
}
