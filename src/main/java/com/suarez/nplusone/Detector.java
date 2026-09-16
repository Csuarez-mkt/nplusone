package com.suarez.nplusone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Encuentra las rachas.
 *
 * Contar repeticiones no basta, y esa es la diferencia con un `sort | uniq -c`:
 * una consulta puede correr mil veces al dia sin que eso sea un problema. Lo
 * que delata al N+1 es que las repeticiones vengan pegadas, justo despues de
 * una consulta distinta que trajo la lista.
 *
 * Por eso agrupamos las apariciones de cada forma en rachas, y toleramos hasta
 * `gap` sentencias ajenas entre dos apariciones: un bucle que por cada pedido
 * consulta el cliente y la direccion intercala dos consultas, y sigue siendo el
 * mismo bucle.
 */
public final class Detector {

	private final int threshold;
	private final int gap;

	public Detector(int threshold, int gap) {
		if (threshold < 2) {
			throw new IllegalArgumentException("el umbral tiene que ser 2 o mas");
		}
		if (gap < 0) {
			throw new IllegalArgumentException("la tolerancia no puede ser negativa");
		}
		this.threshold = threshold;
		this.gap = gap;
	}

	public List<Finding> scan(List<Statement> statements) {
		Map<String, List<Integer>> byShape = new LinkedHashMap<>();
		for (Statement statement : statements) {
			byShape.computeIfAbsent(statement.fingerprint(), key -> new ArrayList<>()).add(statement.index());
		}

		List<Finding> findings = new ArrayList<>();
		for (List<Integer> positions : byShape.values()) {
			for (List<Integer> run : splitIntoRuns(positions)) {
				if (run.size() >= threshold) {
					toFinding(statements, run).ifPresent(findings::add);
				}
			}
		}

		findings.sort(Comparator.comparingInt(Finding::count).reversed().thenComparingInt(Finding::from));
		return List.copyOf(findings);
	}

	/** Corta la lista de posiciones donde el hueco entre dos apariciones supera la tolerancia. */
	private List<List<Integer>> splitIntoRuns(List<Integer> positions) {
		List<List<Integer>> runs = new ArrayList<>();
		List<Integer> current = new ArrayList<>();
		for (int position : positions) {
			if (!current.isEmpty()) {
				int between = position - current.getLast() - 1;
				if (between > gap) {
					runs.add(current);
					current = new ArrayList<>();
				}
			}
			current.add(position);
		}
		if (!current.isEmpty()) {
			runs.add(current);
		}
		return runs;
	}

	private Optional<Finding> toFinding(List<Statement> statements, List<Integer> run) {
		int from = run.getFirst();
		int to = run.getLast();
		Statement sample = statements.get(from);
		Optional<Statement> trigger = triggerOf(statements, sample.fingerprint(), from);

		Finding finding = switch (sample.kind()) {
			case SELECT -> new Finding.RepeatedRead(
					sample.fingerprint(), sample.sql(), run.size(), from, to, trigger);
			case INSERT, UPDATE, DELETE, MERGE -> new Finding.RepeatedWrite(
					sample.fingerprint(), sample.sql(), run.size(), from, to, trigger);
			// Sentencias que no reconocemos (un `set`, un `call`) no dicen nada:
			// repetirlas no es en si un sintoma.
			case OTHER -> null;
		};
		return Optional.ofNullable(finding);
	}

	/** La ultima sentencia con forma distinta antes de que empezara la racha. */
	private Optional<Statement> triggerOf(List<Statement> statements, String fingerprint, int from) {
		for (int i = from - 1; i >= 0; i--) {
			Statement candidate = statements.get(i);
			if (!candidate.fingerprint().equals(fingerprint)) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}
}
