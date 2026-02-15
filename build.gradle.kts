import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    id("org.jetbrains.dokka") version "2.1.0"
    id("com.diffplug.spotless") version "6.25.0"
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
}

val dokkaLogo = rootProject.layout.projectDirectory
    .file("docs/assets/lineage-proxy-logo.svg")
    .asFile

repositories {
    mavenCentral()
}

allprojects {
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            licenseHeaderFile(rootProject.file("license_header"))
        }
        java {
            target("src/**/*.java")
            licenseHeaderFile(rootProject.file("license_header"))
        }
    }
}

dependencies {
    dokka(project(":api"))
    dokka(project(":shared"))
    dokka(project(":proxy"))
    dokka(project(":backend-mod"))
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
        moduleName.set("Lineage")
        includes.from(
            fileTree("docs/modding") {
                include("**/*.md")
            }
        )
    }
    pluginsConfiguration.html {
        if (dokkaLogo.exists()) {
            customAssets.from(dokkaLogo)
        }
        val logoCss = rootProject.layout.projectDirectory.file("docs/dokka/logo.css").asFile
        if (logoCss.exists()) {
            customStyleSheets.from(logoCss)
        }
    }
}

subprojects {
    repositories {
        mavenCentral()
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "org.jetbrains.dokka")
    }
    plugins.withId("java") {
        if (!plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
            apply(plugin = "org.jetbrains.dokka")
        }
    }

    plugins.withId("org.jetbrains.dokka") {
        dokka {
            dokkaSourceSets.configureEach {
                val docSourceRoots = listOf("src/main/kotlin", "src/main/java")
                    .map { project.layout.projectDirectory.dir(it).asFile }
                    .filter { it.exists() }
                if (docSourceRoots.isNotEmpty()) {
                    sourceRoots.setFrom(docSourceRoots)
                }
                configurations.findByName("compileClasspath")?.let { classpath.from(it) }
            }
            pluginsConfiguration.html {
                if (dokkaLogo.exists()) {
                    customAssets.from(dokkaLogo)
                }
                val logoCss = rootProject.layout.projectDirectory.file("docs/dokka/logo.css").asFile
                if (logoCss.exists()) {
                    customStyleSheets.from(logoCss)
                }
            }
        }
    }

    val shadowPluginIds = listOf(
        "com.gradleup.shadow",
        "com.github.johnrengelman.shadow",
    )
    shadowPluginIds.forEach { pluginId ->
        plugins.withId(pluginId) {
            tasks.named<Jar>("shadowJar") {
                archiveBaseName.set("lineage-${project.name}")
                archiveClassifier.set("")
            }
            tasks.named("assemble") {
                dependsOn(tasks.named("shadowJar"))
            }
            tasks.named<Jar>("jar") {
                enabled = false
            }
        }
    }

}
