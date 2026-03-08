import java.util.zip.ZipFile

plugins {
    id("fabric-loom") version "1.15.4"
}

version = project.property("mod_version") as String
group = "dev.nyaru"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Workaround: Loom 1.8 remapJar produces empty jar with MC 1.21.4 Yarn mappings.
// Use TinyRemapper CLI directly instead.
val tinyRemapperFat by configurations.creating

dependencies {
    tinyRemapperFat("net.fabricmc:tiny-remapper:0.10.4:fat")
}

tasks.remapJar {
    doLast {
        val devJar = tasks.jar.get().archiveFile.get().asFile
        val outputJar = archiveFile.get().asFile

        // Yarn mappings artifact is a jar; extract mappings/mappings.tiny from it
        val mappingsJar = configurations["mappings"].resolvedConfiguration.resolvedArtifacts
            .first().file
        val mappingsFile = File(layout.buildDirectory.get().asFile, "tmp/remapper/mappings.tiny")
        mappingsFile.parentFile.mkdirs()
        ZipFile(mappingsJar).use { zip: ZipFile ->
            val entry = zip.getEntry("mappings/mappings.tiny")
            zip.getInputStream(entry).use { input: java.io.InputStream ->
                mappingsFile.outputStream().use { out: java.io.OutputStream ->
                    input.copyTo(out)
                }
            }
        }

        // compileClasspath includes the named MC jar + all mod dependencies
        val cp = configurations["compileClasspath"].resolvedConfiguration.resolvedArtifacts
            .map { it.file.absolutePath }

        val javaExec = "${System.getProperty("java.home")}/bin/java"
        val cmd = buildList {
            add(javaExec)
            add("-cp")
            add(tinyRemapperFat.asPath)
            add("net.fabricmc.tinyremapper.Main")
            add(devJar.absolutePath)
            add(outputJar.absolutePath)
            add(mappingsFile.absolutePath)
            add("named")
            add("intermediary")
            addAll(cp)
        }
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        proc.inputStream.bufferedReader().forEachLine { println(it) }
        val exit = proc.waitFor()
        if (exit != 0) error("TinyRemapper failed with exit code $exit")
    }
}
