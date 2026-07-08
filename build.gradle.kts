plugins {
    alias(libs.plugins.loom)
    `maven-publish`
    java
}

version = property("mod_version").toString()
group   = property("maven_group").toString()
base.archivesName.set(property("archives_base_name").toString())

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

loom {
    runConfigs["client"].ideConfigGenerated(true)
    mixin.defaultRefmapName.set("addon.refmap.json")
}

// force Loom to search mavenLocal
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "meteordevelopment" && requested.name == "meteor-client") {
            useVersion("26.1.2-local")
        }
    }
}

repositories {
    maven(url = uri("${rootProject.projectDir}/repo"))
    mavenCentral()
    maven("https://maven.meteordev.org/releases")
    maven("https://maven.meteordev.org/snapshots")
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modCompileOnly("meteordevelopment:meteor-client:26.1.2-local")
    compileOnly(files("libs/orbit-0.2.4.jar"))
}

afterEvaluate {
    tasks.named<JavaCompile>("compileJava") {
        classpath = classpath + files(
            "${projectDir}/libs/meteor-client.jar",
            "${projectDir}/libs/orbit-0.2.4.jar"
        )
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "mc_version" to libs.versions.minecraft.get(),
        "loader_version" to libs.versions.fabric.loader.get()
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}
