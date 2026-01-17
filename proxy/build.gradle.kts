plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.shadow)
    application
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
    implementation(project(":api"))
    implementation(project(":shared"))
    implementation(libs.netty.all)
    implementation(libs.netty.quic)
    runtimeOnly("io.netty.incubator:netty-incubator-codec-native-quic:0.0.74.Final:linux-x86_64")
    implementation(libs.tomlj)
    implementation(libs.slf4j.api)
    implementation(libs.bc.prov)
    implementation(libs.bc.pkix)
    implementation(libs.asm)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("ru.hytalemodding.lineage.proxy.LineageProxyKt")
}

tasks {
    shadowJar {
        mergeServiceFiles()
    }
}
