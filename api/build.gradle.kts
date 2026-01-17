import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.plugins.signing.SigningExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
    id("com.vanniktech.maven.publish")
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
    api(libs.slf4j.api)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    pom {
        name.set("Lineage Proxy API")
        description.set("Public modding API for Lineage Proxy.")
        inceptionYear.set("2026")
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

extensions.configure<SigningExtension> {
    val signingKeyRaw = findProperty("signingKey") as String?
    val signingKey = signingKeyRaw?.replace("\\n", "\n")
    val signingPassword = findProperty("signingPassword") as String?
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
}
