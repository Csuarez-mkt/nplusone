package com.suarez.nplusone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogReaderTest {

	private static List<Statement> read(String log) throws IOException {
		return LogReader.read(new BufferedReader(new StringReader(log)));
	}

	@Test
	@DisplayName("numera las sentencias en el orden en que se ejecutaron")
	void keepsOrder() throws IOException {
		List<Statement> statements = read("""
				select * from orders where id = 1
				select * from customers where id = 7
				""");

		assertEquals(2, statements.size());
		assertEquals(0, statements.getFirst().index());
		assertEquals(1, statements.getLast().index());
		assertEquals(Kind.SELECT, statements.getFirst().kind());
	}

	@Test
	@DisplayName("junta el SQL que Hibernate reparte en varias lineas")
	void joinsFormattedSql() throws IOException {
		List<Statement> statements = read("""
				2026-09-16 10:00:01.123 DEBUG 1 --- [main] org.hibernate.SQL :
				    select
				        c1_0.id,
				        c1_0.name
				    from
				        customers c1_0
				    where
				        c1_0.id=?
				2026-09-16 10:00:01.130 DEBUG 1 --- [main] com.acme.OrderService : listo
				""");

		assertEquals(1, statements.size());
		String sql = statements.getFirst().sql();
		assertTrue(sql.contains("from customers c1_0"), sql);
		assertTrue(sql.endsWith("c1_0.id=?"), sql);
	}

	@Test
	@DisplayName("una linea de log posterior cierra la sentencia, no se pega a ella")
	void logLineClosesStatement() throws IOException {
		List<Statement> statements = read("""
				select * from orders where id = 1
				2026-09-16 10:00:01.130 INFO  1 --- [main] com.acme.App : termino
				select * from orders where id = 2
				""");

		assertEquals(2, statements.size());
		assertEquals(statements.getFirst().fingerprint(), statements.getLast().fingerprint());
	}

	@Test
	@DisplayName("un log sin nada de SQL no produce sentencias")
	void noSql() throws IOException {
		assertTrue(read("arrancando la app\nlisto\n").isEmpty());
	}
}
