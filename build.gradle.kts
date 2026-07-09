plugins {
    id("fabric-loom") version "1.14-SNAPSHOT"
    `maven-publish`
    java
}

version = property("mod_version").toString()
group   = property("maven_group").toString()
base.archivesName.set(property("archives_base_name").toString())

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.meteordev.org/releases") }
    maven { url = uri("https://maven.meteordev.org/snapshots") }
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.11")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.19.2")
    modCompileOnly("meteordevelopment:meteor-client:1.21.11-SNAPSHOT")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 21
}
