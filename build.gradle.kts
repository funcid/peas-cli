@file:Suppress("UNUSED_VARIABLE")

plugins {
	java
	id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("com.esotericsoftware:kryo:5.4.0")
	implementation("net.openhft:zero-allocation-hashing:0.16")

	implementation("me.tongfei:progressbar:0.9.4")
	implementation("info.picocli:picocli:4.7.3")
	annotationProcessor("info.picocli:picocli-codegen:4.7.3")

	compileOnly("org.checkerframework:checker-qual:3.33.0")
	annotationProcessor("org.checkerframework:checker-qual:3.33.0")

	implementation(libs.netty.buffer)
	implementation(libs.netty.codec)
	implementation(libs.netty.handler)
	implementation(libs.netty.transport)

	compileOnly("org.graalvm.sdk:graal-sdk:22.3.2") // SVM Substitutions
}

tasks {
	assemble { dependsOn(shadowJar) }
	shadowJar {
		minimize()

		manifest {
			attributes(
				"Main-Class" to "me.func.peas.cli.Main",
				"Multi-Release" to "true",
				"Automatic-Module-Name" to "me.func.peas"
			)
		}
	}
	withType<JavaCompile>().configureEach {
		options.run {
			encoding = Charsets.UTF_8.name()
			compilerArgs.addAll(listOf(
				"--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
				"--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
				"--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
			))
		}
	}
	withType<AbstractArchiveTask>().configureEach {
		isReproducibleFileOrder = true
		isPreserveFileTimestamps = false
	}
}

testing {
	suites {
		val test by getting(JvmTestSuite::class) {
			useJUnitJupiter("5.9.3")
		}
	}
}
