import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit
import io.izzel.taboolib.gradle.BukkitHook
import io.izzel.taboolib.gradle.BukkitUI
import io.izzel.taboolib.gradle.BukkitNMS
import io.izzel.taboolib.gradle.BukkitUtil
import io.izzel.taboolib.gradle.CommandHelper
import io.izzel.taboolib.gradle.Database
import io.izzel.taboolib.gradle.DatabasePlayer
import io.izzel.taboolib.gradle.AlkaidRedis


plugins {
    java
    id("io.izzel.taboolib") version "2.0.37"
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
}

taboolib {
    env {
        install(Basic)
        install(CommandHelper)
        install(Bukkit)
        install(BukkitUtil)
        install(BukkitUI)
        install(BukkitHook)
        install(BukkitNMSItemTag)
        install(Database)
        install(DatabasePlayer)
        install(AlkaidRedis)
    }
    description {
        name = "phcore"
        contributors {
            name("Esters")
        }
        dependencies {
            name("MythicMobs").with("bukkit").optional(true)
            name("Baikiruto").with("bukkit").optional(true)
            name("CraftEngine").with("bukkit").optional(true)
            name("PacketEvents").with("bukkit").optional(true)
        }
    }
    relocate("com.zaxxer.hikari.", "com.zaxxer.hikari_4_0_3.")
    relocate("org.slf4j.", "org.slf4j_2_0_8.")
    relocate("org.slf4j.impl.", "org.slf4j_2_0_8.impl.")
    relocate("redis.clients.jedis.", "redis.clients.jedis_4_2_30.")
    relocate("org.apache.commons.pool2.", "org.apache.commons.pool2_2_11_1.")
    relocate("top.maplex.arim.", "top.maplex.arim_phcore.")
    version { taboolib = "6.3.0-88720d8" }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.codemc.io/repository/creatorfromhell/")
    maven("https://nexus.maplex.top/repository/maven-public/")
    maven("https://repo.momirealms.net/releases/")
    maven("https://jitpack.io")
    maven("https://r.irepo.space/maven/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.16")
    compileOnly("net.momirealms:craft-engine-core:0.0.67")
    compileOnly("net.momirealms:craft-engine-bukkit:0.0.67")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
    taboo("com.zaxxer:HikariCP:4.0.3")
    taboo("org.slf4j:slf4j-api:2.0.8")
    taboo("org.slf4j:slf4j-jdk14:2.0.8")
    taboo("top.maplex.arim:Arim:1.3.12")
    taboo("redis.clients:jedis:4.2.3")
    taboo("org.apache.commons:commons-pool2:2.11.1")
    taboo("mysql:mysql-connector-java:8.0.33")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
