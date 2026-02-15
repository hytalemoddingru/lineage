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
    implementation(libs.jline)
    implementation(libs.bc.prov)
    implementation(libs.bc.pkix)
    implementation(libs.asm)
    implementation(libs.logback.classic)

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
    val proxyVersion = version.toString()

    processResources {
        inputs.property("projectVersion", proxyVersion)
        filesMatching("lineage-version.properties") {
            expand(mapOf("projectVersion" to proxyVersion))
        }
    }

    shadowJar {
        mergeServiceFiles()
        manifest {
            attributes(
                "Implementation-Title" to "lineage-proxy",
                "Implementation-Version" to proxyVersion,
            )
        }
    }
}
