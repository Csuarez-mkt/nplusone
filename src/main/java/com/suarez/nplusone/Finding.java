package com.suarez.nplusone;

import java.util.Optional;

/**
 * Una racha de la misma consulta, seguidas, que huele a bucle.
 *
 * Es un tipo sellado: los desenlaces posibles estan cerrados y el compilador lo
 * sabe. Cuando agregues una clase de hallazgo, todo `switch` que no la
 * contemple deja de compilar, y el reporte no puede quedarse callado sobre algo
 * que si detectamos.
 */
public sealed interface Finding {

	String fingerprint();

	String sample();

	int count();

	int from();

	int to();

	/** La sentencia distinta anterior a la racha: casi siempre, la que trajo la lista. */
	Optional<Statement> trigger();

	/** Leer lo mismo N veces: el N+1 clasico. */
	record RepeatedRead(
			String fingerprint,
			String sample,
			int count,
			int from,
			int to,
			Optional<Statement> trigger) implements Finding {
	}

	/** Escribir N veces una por una: un lote que no se agrupo. */
	record RepeatedWrite(
			String fingerprint,
			String sample,
			int count,
			int from,
			int to,
			Optional<Statement> trigger) implements Finding {
	}
}
