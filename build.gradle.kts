plugins {
	application
}

group = "com.suarez"
version = "0.1.0"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

// Sin dependencias en tiempo de ejecucion, a proposito: el reporte JSON se
// escribe a mano. Una herramienta que se mete en el CI de otros no deberia
// arrastrarles un arbol de librerias para leer un log de texto.
dependencies {
}

application {
	mainClass = "com.suarez.nplusone.Main"
	applicationName = "nplusone"
}

testing {
	suites {
		val test by getting(JvmTestSuite::class) {
			useJUnitJupiter("6.0.3")
		}
	}
}

tasks.test {
	testLogging {
		events("passed", "failed", "skipped")
	}
}

// Manifiesto con la clase principal: sin dependencias, el jar suelto ya es
// ejecutable con `java -jar`, que es la forma mas corta de probarla.
tasks.jar {
	manifest {
		attributes["Main-Class"] = "com.suarez.nplusone.Main"
	}
}
