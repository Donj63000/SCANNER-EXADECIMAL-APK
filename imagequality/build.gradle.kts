import org.gradle.api.plugins.JavaBasePlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

tasks.register("testDebugUnitTest") {
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Runs JVM unit tests for the debug variant compatibility"
    dependsOn(tasks.named("test"))
}
