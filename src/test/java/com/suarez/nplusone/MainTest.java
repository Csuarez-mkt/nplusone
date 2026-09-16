package com.suarez.nplusone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas de la herramienta entera: argumentos, lectura, reporte y codigo de
 * salida. Es lo unico que garantiza que el CI de alguien mas se comporte como
 * dice el README.
 */
class MainTest {

	private static final String SICK_LOG = "select id, customer_id from orders where created_at > '2026-01-01'\n"
			+ IntStream.rangeClosed(1, 12)
					.mapToObj(i -> "select id, name from customers where id = " + i)
					.collect(Collectors.joining("\n")) + "\n";

	private static final String HEALTHY_LOG = """
			select id, customer_id from orders where created_at > '2026-01-01'
			select id, name from customers where id in (1, 2, 3, 4, 5)
			""";

	private record Run(int code, String out, String err) {
	}

	private static Run run(String stdin, String... args) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		InputStream in = new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8));
		int code = Main.run(args, in,
				new PrintStream(out, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8));
		return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("un log sano termina en 0")
	void healthy() {
		Run result = run(HEALTHY_LOG);

		assertEquals(Main.OK, result.code());
		assertTrue(result.out().contains("Sin rachas"), result.out());
	}

	@Test
	@DisplayName("un log con N+1 termina en 1, para que el CI lo frene")
	void findings() {
		Run result = run(SICK_LOG);

		assertEquals(Main.FINDINGS, result.code());
		assertTrue(result.out().contains("12 lecturas repetidas"), result.out());
		assertTrue(result.out().contains("Despues de:"), result.out());
	}

	@Test
	@DisplayName("con --no-fail reporta igual pero termina en 0")
	void noFail() {
		Run result = run(SICK_LOG, "--no-fail");

		assertEquals(Main.OK, result.code());
		assertTrue(result.out().contains("lecturas repetidas"), result.out());
	}

	@Test
	@DisplayName("con --json el reporte sale en JSON")
	void json() {
		Run result = run(SICK_LOG, "--json");

		assertEquals(Main.FINDINGS, result.code());
		assertTrue(result.out().startsWith("{"), result.out());
		assertTrue(result.out().contains("\"type\": \"repeated_read\""), result.out());
		assertTrue(result.out().contains("\"count\": 12"), result.out());
	}

	@Test
	@DisplayName("un umbral mas alto que la racha la deja pasar")
	void threshold() {
		assertEquals(Main.OK, run(SICK_LOG, "-t", "20").code());
		assertEquals(Main.FINDINGS, run(SICK_LOG, "-t", "12").code());
	}

	@Test
	@DisplayName("lee de un archivo cuando se lo pasan")
	void fromFile(@TempDir Path dir) throws IOException {
		Path log = dir.resolve("app.log");
		Files.writeString(log, SICK_LOG, StandardCharsets.UTF_8);

		Run result = run("", log.toString());

		assertEquals(Main.FINDINGS, result.code());
		assertTrue(result.out().contains("lecturas repetidas"), result.out());
	}

	@Test
	@DisplayName("un archivo que no existe es error de lectura, no una traza")
	void missingFile(@TempDir Path dir) {
		Run result = run("", dir.resolve("no-existe.log").toString());

		assertEquals(Main.ERROR, result.code());
		assertTrue(result.err().startsWith("nplusone: no se pudo leer"), result.err());
	}

	@Test
	@DisplayName("una opcion desconocida explica el uso y termina en 2")
	void unknownOption() {
		Run result = run("", "--recursivo");

		assertEquals(Main.ERROR, result.code());
		assertTrue(result.err().contains("opcion desconocida: --recursivo"), result.err());
		assertTrue(result.err().contains("Uso:"), result.err());
	}

	@Test
	@DisplayName("--threshold sin numero tambien es error de uso")
	void thresholdNeedsNumber() {
		Run result = run("", "-t", "muchas");

		assertEquals(Main.ERROR, result.code());
		assertTrue(result.err().contains("necesita un numero"), result.err());
	}

	@Test
	@DisplayName("la ayuda y la version terminan en 0")
	void helpAndVersion() {
		Run help = run("", "--help");
		assertEquals(Main.OK, help.code());
		assertTrue(help.out().contains("Codigos de salida"), help.out());

		Run version = run("", "--version");
		assertEquals(Main.OK, version.code());
		assertEquals("0.1.0", version.out().strip());
	}
}
