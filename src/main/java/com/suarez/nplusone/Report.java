package com.suarez.nplusone;

import java.util.List;

/** Arma el reporte, en texto para leerlo o en JSON para que lo lea el CI. */
final class Report {

	/** Cuanto SQL se muestra antes de recortar por el medio. */
	private static final int HEAD = 140;
	private static final int TAIL = 90;

	record Summary(int statements, int shapes) {
	}

	private Report() {
	}

	static String text(Summary summary, List<Finding> findings) {
		StringBuilder out = new StringBuilder();
		out.append("nplusone ").append(Version.NUMBER).append("\n\n");
		out.append(summary.statements()).append(" sentencias, ")
				.append(summary.shapes()).append(" formas distintas.\n\n");

		if (findings.isEmpty()) {
			out.append("Sin rachas. Ninguna consulta se repitio lo suficiente como para\n");
			out.append("parecer un bucle.\n");
			return out.toString();
		}

		out.append(findings.size()).append(findings.size() == 1 ? " hallazgo.\n" : " hallazgos.\n");

		int number = 1;
		for (Finding finding : findings) {
			out.append('\n').append(render(number++, finding));
		}
		return out.toString();
	}

	private static String render(int number, Finding finding) {
		// switch sobre el tipo sellado: sin `default`. Si manana aparece otra
		// clase de hallazgo, esto deja de compilar en vez de imprimir de menos.
		String headline = switch (finding) {
			case Finding.RepeatedRead read -> read.count() + " lecturas repetidas";
			case Finding.RepeatedWrite write -> write.count() + " escrituras una por una";
		};
		String advice = switch (finding) {
			case Finding.RepeatedRead read -> """
					    Es el N+1: una consulta trajo la lista y despues se pidio cada
					    elemento por separado. Traelos de una, con un join o cargando
					    los ids juntos en un `in`.
					""";
			case Finding.RepeatedWrite write -> """
					    Cada fila viajo sola. Agrupalas en un lote para que sea un viaje
					    y no %d.
					""".formatted(write.count());
		};

		StringBuilder out = new StringBuilder();
		out.append('[').append(number).append("] ").append(headline)
				.append(", sentencias ").append(finding.from())
				.append(" a ").append(finding.to()).append("\n\n");
		finding.trigger().ifPresent(trigger -> out
				.append("    Despues de:\n      ").append(abbreviate(trigger.sql())).append('\n'));
		out.append("    Se repitio:\n      ").append(abbreviate(finding.sample())).append("\n\n");
		out.append(advice);
		return out.toString();
	}

	static String json(Summary summary, List<Finding> findings) {
		StringBuilder out = new StringBuilder();
		out.append("{\n");
		out.append("  \"version\": \"").append(Version.NUMBER).append("\",\n");
		out.append("  \"statements\": ").append(summary.statements()).append(",\n");
		out.append("  \"shapes\": ").append(summary.shapes()).append(",\n");
		out.append("  \"findings\": [");
		for (int i = 0; i < findings.size(); i++) {
			Finding finding = findings.get(i);
			String type = switch (finding) {
				case Finding.RepeatedRead read -> "repeated_read";
				case Finding.RepeatedWrite write -> "repeated_write";
			};
			out.append(i == 0 ? "\n" : ",\n");
			out.append("    {\n");
			out.append("      \"type\": \"").append(type).append("\",\n");
			out.append("      \"count\": ").append(finding.count()).append(",\n");
			out.append("      \"from\": ").append(finding.from()).append(",\n");
			out.append("      \"to\": ").append(finding.to()).append(",\n");
			out.append("      \"sql\": \"").append(escape(finding.sample())).append("\",\n");
			out.append("      \"trigger\": ")
					.append(finding.trigger().map(t -> "\"" + escape(t.sql()) + "\"").orElse("null"))
					.append('\n');
			out.append("    }");
		}
		out.append(findings.isEmpty() ? "]\n" : "\n  ]\n");
		out.append("}\n");
		return out.toString();
	}

	/**
	 * Recorta por el medio, no por el final: en un select de Hibernate la lista
	 * de columnas es larguisima y lo que uno necesita ver (el `where`) esta al
	 * final. Cortar por detras esconde justo la parte util.
	 */
	static String abbreviate(String sql) {
		if (sql.length() <= HEAD + TAIL + 5) {
			return sql;
		}
		return sql.substring(0, HEAD) + " ... " + sql.substring(sql.length() - TAIL);
	}

	private static String escape(String raw) {
		StringBuilder out = new StringBuilder(raw.length() + 16);
		for (char c : raw.toCharArray()) {
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					} else {
						out.append(c);
					}
				}
			}
		}
		return out.toString();
	}
}
