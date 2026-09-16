package com.suarez.nplusone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DetectorTest {

	private static List<Statement> statements(String... sql) {
		List<Statement> out = new ArrayList<>();
		for (String each : sql) {
			out.add(Statement.of(out.size(), each));
		}
		return List.copyOf(out);
	}

	private static List<Statement> nPlusOne(int children) {
		List<String> sql = new ArrayList<>();
		sql.add("select id, customer_id from orders where created_at > '2026-01-01'");
		for (int i = 1; i <= children; i++) {
			sql.add("select id, name from customers where id = " + i);
		}
		return statements(sql.toArray(String[]::new));
	}

	@Test
	@DisplayName("el N+1 clasico sale, con la consulta que lo disparo")
	void findsClassicNPlusOne() {
		List<Finding> findings = new Detector(5, 2).scan(nPlusOne(10));

		assertEquals(1, findings.size());
		Finding finding = findings.getFirst();
		assertInstanceOf(Finding.RepeatedRead.class, finding);
		assertEquals(10, finding.count());
		assertEquals(1, finding.from());
		assertEquals(10, finding.to());
		assertTrue(finding.trigger().orElseThrow().sql().contains("from orders"));
	}

	@Test
	@DisplayName("justo debajo del umbral no se reporta, justo encima si")
	void thresholdBoundary() {
		assertTrue(new Detector(5, 2).scan(nPlusOne(4)).isEmpty());
		assertEquals(1, new Detector(5, 2).scan(nPlusOne(5)).size());
	}

	@Test
	@DisplayName("la misma consulta repartida a lo largo del log no es un bucle")
	void spreadOutIsNotALoop() {
		List<String> sql = new ArrayList<>();
		for (int i = 1; i <= 10; i++) {
			sql.add("select id from customers where id = " + i);
			sql.add("insert into audit (id, detail) values (" + i + ", 'a')");
			sql.add("update stock set units = units - 1 where sku = 'x" + i + "'");
			sql.add("select name from cities where id = " + i);
			sql.add("delete from cart_items where cart_id = " + i);
		}

		// Con tolerancia 0 cada aparicion queda aislada por las cuatro sentencias
		// ajenas que trae en medio, y ninguna racha llega al umbral.
		assertTrue(new Detector(5, 0).scan(statements(sql.toArray(String[]::new))).isEmpty());
	}

	@Test
	@DisplayName("un bucle que hace dos consultas por vuelta sigue siendo una racha")
	void toleratesInterleaving() {
		List<String> sql = new ArrayList<>();
		sql.add("select id from orders where created_at > '2026-01-01'");
		for (int i = 1; i <= 6; i++) {
			sql.add("select name from customers where id = " + i);
			sql.add("select line1 from addresses where customer_id = " + i);
		}

		List<Finding> findings = new Detector(5, 2).scan(statements(sql.toArray(String[]::new)));

		assertEquals(2, findings.size());
		assertTrue(findings.stream().allMatch(f -> f.count() == 6));
	}

	@Test
	@DisplayName("escribir fila por fila se reporta como lote que falta")
	void repeatedWrites() {
		List<String> sql = new ArrayList<>();
		for (int i = 1; i <= 8; i++) {
			sql.add("insert into tags (id, name) values (" + i + ", 'a')");
		}

		List<Finding> findings = new Detector(5, 2).scan(statements(sql.toArray(String[]::new)));

		assertEquals(1, findings.size());
		assertInstanceOf(Finding.RepeatedWrite.class, findings.getFirst());
		assertTrue(findings.getFirst().trigger().isEmpty(), "no habia nada antes de la racha");
	}

	@Test
	@DisplayName("el hallazgo mas repetido va de primero")
	void sortedByCount() {
		List<String> sql = new ArrayList<>();
		for (int i = 1; i <= 6; i++) {
			sql.add("select name from customers where id = " + i);
		}
		for (int i = 1; i <= 20; i++) {
			sql.add("select line1 from addresses where id = " + i);
		}

		List<Finding> findings = new Detector(5, 2).scan(statements(sql.toArray(String[]::new)));

		assertEquals(20, findings.getFirst().count());
		assertEquals(6, findings.getLast().count());
	}

	@Test
	@DisplayName("un umbral menor que 2 no tiene sentido y se rechaza")
	void rejectsSillyThreshold() {
		assertThrows(IllegalArgumentException.class, () -> new Detector(1, 2));
		assertThrows(IllegalArgumentException.class, () -> new Detector(5, -1));
	}
}
