plugins {
	java
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("com.esotericsoftware:kryo:5.4.0")
	implementation("net.openhft:zero-allocation-hashing:0.16")

	implementation("info.picocli:picocli:4.7.3")
	annotationProcessor("info.picocli:picocli-codegen:4.7.3")

	compileOnly("org.checkerframework:checker-qual:3.33.0")
	annotationProcessor("org.checkerframework:checker-qual:3.33.0")
}
