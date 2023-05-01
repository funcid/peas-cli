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
}

tasks {
	withType<JavaCompile>().configureEach {
		options.run {
			encoding = Charsets.UTF_8.name()
			compilerArgs.addAll(listOf(
				"--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
				"--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
			))
		}
	}
	withType<AbstractArchiveTask>().configureEach {
		isReproducibleFileOrder = true
		isPreserveFileTimestamps = false
	}
}
