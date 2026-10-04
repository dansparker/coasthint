import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Desktop tool: converts an OpenStreetMap extract (.osm.pbf) into the offline road database.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

application {
    mainClass = "io.github.dansparker.coasthint.roaddb.builder.MainKt"
    // Enough for most countries; override with ROADDB_BUILDER_OPTS=-Xmx8g for very large ones.
    applicationDefaultJvmArgs = listOf("-Xmx4g")
}

dependencies {
    implementation(project(":roaddb"))
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.osmpbf)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
