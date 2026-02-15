plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
    maven {
        name = "hytale-release"
        url = uri("https://maven.hytale.com/release")
    }
    maven {
        name = "hytale-pre-release"
        url = uri("https://maven.hytale.com/pre-release")
    }
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.tomlj)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    val hytaleServerVersion = providers
        .gradleProperty("hytaleServerVersion")
        .orElse("2026.02.06-aa1b071c2")
        .get()
    compileOnly("com.hypixel.hytale:Server:$hytaleServerVersion")

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.logback.classic)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    shadowJar {
        archiveClassifier.set("")
    }
}
