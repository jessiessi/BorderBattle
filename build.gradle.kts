plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.4.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    implementation("de.exlll:configlib-paper:4.8.1")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    runServer {
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        filteringCharset = "UTF-8"
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
