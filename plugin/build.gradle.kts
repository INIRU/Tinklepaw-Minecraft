import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "dev.nyaru"
version = (project.findProperty("pluginVersion") as String?) ?: "1.0.12"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.fancyinnovations.com/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("de.oliver:FancyNpcs:2.9.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("nyaru-plugin")
    archiveVersion.set("")
    archiveClassifier.set("")
    relocate("okhttp3", "dev.nyaru.shade.okhttp3")
    relocate("okio", "dev.nyaru.shade.okio")
    relocate("kotlinx.coroutines", "dev.nyaru.shade.kotlinx.coroutines")
    relocate("com.google.gson", "dev.nyaru.shade.gson")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("pluginVersion" to project.version.toString())
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
