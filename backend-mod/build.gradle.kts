plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
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
    
    compileOnly(files("../libs/HytaleServer.jar"))

    testImplementation(libs.junit.jupiter.api)
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
