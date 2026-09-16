package com.suarez.nplusone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FingerprintTest {

	@Test
	@DisplayName("la misma consulta con otro valor da la misma huella")
	void sameShapeDifferentValues() {
		assertEquals(
				Fingerprint.of("select * from orders where id = 1"),
				Fingerprint.of("select * from orders where id = 42"));
	}

	@Test
	@DisplayName("los literales de texto tampoco cambian la huella")
	void stringLiterals() {
		assertEquals(
				Fingerprint.of("select * from users where email = 'ana@correo.com'"),
				Fingerprint.of("select * from users where email = 'luis@correo.com'"));
	}

	@Test
	@DisplayName("una comilla escapada dentro del literal no rompe la huella")
	void escapedQuote() {
		assertEquals(
				Fingerprint.of("select * from cities where name = 'Bogota'"),
				Fingerprint.of("select * from cities where name = 'D''Alembert'"));
	}

	@Test
	@DisplayName("un in de tres ids y uno de diez son la misma consulta")
	void inListCollapses() {
		assertEquals(
				Fingerprint.of("select * from orders where id in (1, 2, 3)"),
				Fingerprint.of("select * from orders where id in (7, 8, 9, 10, 11)"));
	}

	@Test
	@DisplayName("un insert por lote no cambia de forma segun el tamano del lote")
	void valuesListCollapses() {
		assertEquals(
				Fingerprint.of("insert into tags (id, name) values (1, 'a'), (2, 'b')"),
				Fingerprint.of("insert into tags (id, name) values (3, 'c'), (4, 'd'), (5, 'e')"));
	}

	@Test
	@DisplayName("mayusculas, espacios de sobra y el punto y coma final dan igual")
	void caseAndWhitespace() {
		assertEquals(
				Fingerprint.of("select * from orders where id = ?"),
				Fingerprint.of("SELECT   *\n  FROM orders\n WHERE id = ? ;"));
	}

	@Test
	@DisplayName("los alias que Hibernate numera se conservan, no son literales")
	void hibernateAliasesSurvive() {
		String shape = Fingerprint.of("select o1_0.id from orders o1_0 where o1_0.id=?");
		assertEquals("select o1_0.id from orders o1_0 where o1_0.id=?", shape);
	}

	@Test
	@DisplayName("dos tablas distintas siguen siendo consultas distintas")
	void differentTables() {
		assertNotEquals(
				Fingerprint.of("select * from orders where id = 1"),
				Fingerprint.of("select * from customers where id = 1"));
	}
}
