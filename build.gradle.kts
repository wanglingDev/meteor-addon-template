plugins {
    alias(libs.plugins.loom)
    `maven-publish`
    java
}

version = property("mod_version").toString()
group   = property("maven_group").toString()
base.archivesName.set(property("archives_base_name").toString())

repositories {
    maven { url = uri("https://maven.meteordev.org/releases") }
    maven { url = uri("https://maven.meteordev.org/snapshots") }
}

dependencies {
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabricLoader)
    modCompileOnly(libs.meteorClient)
}

tasks.processResources {
    val props = mapOf("version" to version, "mc_version" to libs.versions.minecraft.get())
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 21
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
