plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "pers.neige"
version = "1.0.0-bridge"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly(files("../../libs/Baikiruto-1.1.10.jar"))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(mapOf("version" to project.version))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveFileName.set("NeigeItems-Bridge-${project.version}.jar")
    // 不 relocate Baikiruto；由服务器上的 Baikiruto 本体提供
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
