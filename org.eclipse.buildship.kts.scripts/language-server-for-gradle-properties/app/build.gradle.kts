/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java application project to get you started.
 * For more details take a look at the 'Building Java & JVM projects' chapter in the Gradle
 * User Manual available at https://docs.gradle.org/8.1.1/userguide/building_java_projects.html
 */

plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("com.github.johnrengelman:shadow:8.1.1")
    }
}
apply(plugin = "com.github.johnrengelman.shadow")

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.0.1")

    //    implementation
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.21.0")
    // This dependency is used by the application.
    implementation("com.google.guava:guava:31.1-jre")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

tasks.withType(Jar::class) {
    manifest {
        attributes["Main-Class"] = "server.GradlePropertiesLanguageServerLauncher";
    }
}

application {
    // Define the main class for the application.
    mainClass.set("server.GradlePropertiesLanguageServerLauncher")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
