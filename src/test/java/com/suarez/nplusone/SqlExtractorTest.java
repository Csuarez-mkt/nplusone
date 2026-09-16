package com.suarez.nplusone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SqlExtractorTest {

	@Test
	@DisplayName("saca el SQL de una linea de Hibernate")
	void hibernate() {
		String line = "2026-09-16 10:00:01.123 DEBUG 9120 --- [main] org.hibernate.SQL"
				+ " : select c1_0.id from customers c1_0 where c1_0.id=?";
		assertEquals("select c1_0.id from customers c1_0 where c1_0.id=?",
				SqlExtractor.extract(line).orElseThrow());
	}

	@Test
	@DisplayName("saca el SQL de una linea de p6spy, que lo deja al final entre barras")
	void p6spy() {
		String line = "1726480801|3|statement|connection 4|url jdbc:postgresql://localhost/app"
				+ "|select * from orders where id = ?|select * from orders where id = 12";
		assertEquals("select * from orders where id = 12",
				SqlExtractor.extract(line).orElseThrow());
	}

	@Test
	@DisplayName("acepta el SQL pelado, sin prefijo de log")
	void plain() {
		assertEquals("insert into tags (id) values (?)",
				SqlExtractor.extract("insert into tags (id) values (?)").orElseThrow());
	}

	@Test
	@DisplayName("una frase en prosa con la palabra delete no es SQL")
	void proseIsNotSql() {
		assertTrue(SqlExtractor.extract("WARN  delete failed for order 12").isEmpty());
	}

	@Test
	@DisplayName("un update sin set no es SQL")
	void updateNeedsSet() {
		assertTrue(SqlExtractor.extract("INFO  update available for the agent").isEmpty());
	}

	@Test
	@DisplayName("una linea en blanco no aporta nada")
	void blank() {
		assertTrue(SqlExtractor.extract("   ").isEmpty());
		assertTrue(SqlExtractor.extract(null).isEmpty());
	}

	@Test
	@DisplayName("el concatenador || de Postgres no parte la sentencia")
	void postgresConcat() {
		String sql = "select first_name || ' ' || last_name from users where id = ?";
		assertEquals(sql, SqlExtractor.extract(sql).orElseThrow());
	}
}
