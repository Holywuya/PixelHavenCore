import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit
import io.izzel.taboolib.gradle.BukkitHook
import io.izzel.taboolib.gradle.BukkitNMS
import io.izzel.taboolib.gradle.BukkitUtil
import io.izzel.taboolib.gradle.CommandHelper
import io.izzel.taboolib.gradle.Database
import io.izzel.taboolib.gradle.DatabasePlayer
import io.izzel.taboolib.gradle.Kether


plugins {
    java
    id("io.izzel.taboolib") version "2.0.27"
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
}

taboolib {
    env {
        install(Basic)
        install(CommandHelper)
        install(Bukkit)
        install(BukkitUtil)
        install(BukkitHook)
        install(BukkitNMS)
        install(Database)
        install(DatabasePlayer)
        install(Kether)
    }
    description {
        name = "phcore"
        contributors {
            name("Esters")
        }
    }
    relocate("com.zaxxer.hikari.", "com.zaxxer.hikari_4_0_3.")
    relocate("org.slf4j.", "org.slf4j_2_0_8.")
    relocate("org.slf4j.impl.", "org.slf4j_2_0_8.impl.")
    version { taboolib = "6.2.4-99fb800" }
}

repositories {
    mavenCentral()
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("ink.ptms.core:v12111:12111:mapped")
    compileOnly("ink.ptms.core:v12111:12111:universal")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
    taboo("com.zaxxer:HikariCP:4.0.3")
    taboo("org.slf4j:slf4j-api:2.0.8")
    taboo("org.slf4j:slf4j-jdk14:2.0.8")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
