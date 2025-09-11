plugins {
    id("fabric-loom") version "1.11-SNAPSHOT"
    java
}

fun getProp(name: String): String = findProperty(name) as? String ?: error("Missing Gradle property: $name")

group = getProp("maven_group")
version = getProp("mod_version")

base {
    archivesName.set(getProp("mod_id"))
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${getProp("mc_version")}")
    mappings("net.fabricmc:yarn:${getProp("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${getProp("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${getProp("fabric_version")}")
}

tasks {
    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(getProperties())
            expand(mutableMapOf("version" to project.version))
        }
    }
}

java {
    withSourcesJar()
}

