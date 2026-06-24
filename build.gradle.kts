@file:Suppress("PropertyName")

import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21

plugins {
    java
    id("io.izzel.taboolib") version "2.0.37" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.izzel.taboolib")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    configure<TabooLibExtension> {
        env {
            install(
                Basic,
                CommandHelper,
                Bukkit,
                BukkitUtil,
                BukkitUI,
                BukkitHook,
                BukkitNMSItemTag,
                Database,
                DatabasePlayer,
            )
        }
        version {
            taboolib = "6.3.0-8da9a20"
        }
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
        compileOnly(kotlin("stdlib"))
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JVM_21)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }
}
