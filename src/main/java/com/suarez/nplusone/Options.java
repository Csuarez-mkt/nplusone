package com.suarez.nplusone;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Lo que pidio quien invoco la herramienta.
 *
 * `file` vacio significa leer de la entrada estandar, que es como se usa en
 * una tuberia: `mvn test | nplusone`.
 */
record Options(
		Optional<Path> file,
		int threshold,
		int gap,
		boolean json,
		boolean failOnFindings,
		boolean help,
		boolean version) {

	static final int DEFAULT_THRESHOLD = 5;
	static final int DEFAULT_GAP = 2;

	/** Error de uso: el mensaje va al usuario tal cual, sin traza. */
	static final class UsageException extends RuntimeException {
		UsageException(String message) {
			super(message);
		}
	}

	static Options parse(String[] args) {
		Optional<Path> file = Optional.empty();
		int threshold = DEFAULT_THRESHOLD;
		int gap = DEFAULT_GAP;
		boolean json = false;
		boolean failOnFindings = true;

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			switch (arg) {
				case "-h", "--help" -> {
					return new Options(Optional.empty(), threshold, gap, json, failOnFindings, true, false);
				}
				case "-v", "--version" -> {
					return new Options(Optional.empty(), threshold, gap, json, failOnFindings, false, true);
				}
				case "-t", "--threshold" -> threshold = intArg(args, ++i, arg);
				case "-g", "--gap" -> gap = intArg(args, ++i, arg);
				case "-j", "--json" -> json = true;
				case "--no-fail" -> failOnFindings = false;
				case "-" -> file = Optional.empty();
				default -> {
					if (arg.startsWith("-")) {
						throw new Options.UsageException("opcion desconocida: " + arg);
					}
					if (file.isPresent()) {
						throw new Options.UsageException("solo se lee un archivo a la vez");
					}
					file = Optional.of(Path.of(arg));
				}
			}
		}

		if (threshold < 2) {
			throw new Options.UsageException("el umbral tiene que ser 2 o mas");
		}
		if (gap < 0) {
			throw new Options.UsageException("la tolerancia no puede ser negativa");
		}
		return new Options(file, threshold, gap, json, failOnFindings, false, false);
	}

	private static int intArg(String[] args, int index, String flag) {
		if (index >= args.length) {
			throw new Options.UsageException(flag + " necesita un numero");
		}
		try {
			return Integer.parseInt(args[index]);
		} catch (NumberFormatException e) {
			throw new Options.UsageException(flag + " necesita un numero, no '" + args[index] + "'");
		}
	}
}
