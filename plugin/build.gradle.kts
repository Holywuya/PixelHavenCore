@file:Suppress("PropertyName")

import io.izzel.taboolib.gradle.*

taboolib {
    description {
        name("phcore")
        contributors {
            name("Esters")
        }
        dependencies {
            name("MythicMobs").with("bukkit").optional(true)
            name("CraftEngine").with("bukkit").optional(true)
            name("PacketEvents").with("bukkit").optional(true)
        }
    }

    relocate("com.zaxxer.hikari.", "com.zaxxer.hikari_7_1_0.")
    relocate("org.slf4j.", "org.slf4j_2_0_8.")
    relocate("org.slf4j.impl.", "org.slf4j_2_0_8.impl.")
    relocate("org.apache.commons.pool2.", "org.apache.commons.pool2_2_11_1.")
    relocate("top.maplex.arim.", "top.maplex.arim_phcore.")
}

repositories {
    mavenLocal()
}

dependencies {
    taboo("com.zaxxer:HikariCP:7.1.0")
    taboo("org.slf4j:slf4j-api:2.0.8")
    taboo("org.slf4j:slf4j-jdk14:2.0.8")
    taboo("top.maplex.arim:Arim:1.3.12")
    taboo("mysql:mysql-connector-java:8.0.33")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
}

tasks {
    jar {
        archiveBaseName.set(rootProject.name)
        // 打包所有非 plugin 子项目的源码和资源
        rootProject.subprojects.filter { it.name != "plugin" }.forEach {
            from(it.sourceSets["main"].output)
        }
    }
}
