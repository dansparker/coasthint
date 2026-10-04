import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// File format of the offline road database, shared by the app and the builder tool. Pure Kotlin/JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    api(libs.androidx.sqlite)

    testImplementation(libs.androidx.sqlite.bundled)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
