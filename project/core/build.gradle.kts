import io.izzel.taboolib.gradle.*

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.20")
    compileOnly("net.momirealms:craft-engine-core:26.6")
    compileOnly("net.momirealms:craft-engine-bukkit:26.6")
    compileOnly("com.zaxxer:HikariCP:7.1.0")
    compileOnly("top.maplex.arim:Arim:1.3.12")
    compileOnly(project(":project:bridge"))
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree(rootProject.file("libs")))
}

taboolib { subproject = true }
