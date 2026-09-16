package com.suarez.nplusone;

/**
 * Una sentencia SQL encontrada en el log, con su posicion en el orden de ejecucion.
 *
 * El orden importa: sin el no se distingue una consulta que corrio 50 veces
 * seguidas dentro de un bucle de una que corrio 50 veces repartidas a lo largo
 * del dia. La primera es un N+1, la segunda es trafico normal.
 */
public record Statement(int index, String sql, String fingerprint, Kind kind) {

	public static Statement of(int index, String sql) {
		return new Statement(index, sql, Fingerprint.of(sql), Kind.of(sql));
	}
}
