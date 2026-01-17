plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.objectweb.asm", "ru.hytalemodding.lineage.agent.asm")
    manifest {
        attributes(
            "Premain-Class" to "ru.hytalemodding.lineage.agent.LineageAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true"
        )
    }
}
