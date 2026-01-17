import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    id("org.jetbrains.dokka") version "2.1.0"
    id("org.jetbrains.dokka-javadoc") version "2.1.0" apply false
    id("com.diffplug.spotless") version "6.25.0"
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
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
    dokka(project(":agent"))
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

    val publishModules = setOf("api", "shared")
    plugins.withId("java") {
        if (name !in publishModules) {
            return@withId
        }
        apply(plugin = "maven-publish")
        apply(plugin = "signing")
        apply(plugin = "org.jetbrains.dokka-javadoc")

        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
        }

        val dokkaJavadoc = tasks.named("dokkaGeneratePublicationJavadoc")
        val javadocJar = tasks.register<Jar>("javadocJar") {
            archiveClassifier.set("javadoc")
            from(dokkaJavadoc.map { it.outputs.files.singleFile })
        }

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    artifact(javadocJar)
                    pom {
                        name.set(if (project.name == "api") "Lineage Proxy API" else "Lineage Proxy Shared")
                        description.set(
                            if (project.name == "api") {
                                "Public modding API for Lineage Proxy."
                            } else {
                                "Shared protocol and token utilities for Lineage Proxy."
                            }
                        )
                        url.set("https://github.com/hytalemoddingru/lineage")
                        licenses {
                            license {
                                name.set("GNU Affero General Public License v3.0")
                                url.set("https://www.gnu.org/licenses/agpl-3.0.html")
                            }
                        }
                        scm {
                            url.set("https://github.com/hytalemoddingru/lineage")
                            connection.set("scm:git:https://github.com/hytalemoddingru/lineage.git")
                            developerConnection.set("scm:git:ssh://git@github.com/hytalemoddingru/lineage.git")
                        }
                        developers {
                            developer {
                                id.set("amanomasato")
                                name.set("@amanomasato")
                                url.set("https://github.com/amanomasato")
                            }
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "OSSRH"
                    url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = findProperty("ossrhUsername") as String?
                        password = findProperty("ossrhPassword") as String?
                    }
                }
            }
        }

        extensions.configure<SigningExtension> {
            val signingKeyRaw = findProperty("signingKey") as String?
            val signingKey = signingKeyRaw?.replace("\\n", "\n")
            val signingPassword = findProperty("signingPassword") as String?
            if (!signingKey.isNullOrBlank()) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                val publication = extensions.getByType<PublishingExtension>()
                    .publications
                    .getByName("mavenJava")
                sign(publication)
            }
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(findProperty("ossrhUsername") as String?)
            password.set(findProperty("ossrhPassword") as String?)
        }
    }
}
