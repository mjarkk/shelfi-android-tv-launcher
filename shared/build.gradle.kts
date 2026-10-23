import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    android {
        namespace = "com.mkopenga.tvhomescreen.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            // Skiko's native renderer for the headless scene tests and snapshots, and the Main dispatcher that
            // collectAsStateWithLifecycle needs, as in desktopApp.
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.resources {
    packageOfResClass = "com.mkopenga.tvhomescreen.resources"
}

val snapshotDir = rootProject.layout.buildDirectory.dir("snapshots")
val snapshotRenderer = "com.mkopenga.tvhomescreen.ui.SnapshotRenderer"

tasks.named<Test>("jvmTest") {
    filter { excludeTestsMatching(snapshotRenderer) }
}

tasks.register<Test>("snapshot") {
    description = "Renders 1920x1080 PNGs of the home screen with fake data into build/snapshots"
    group = "verification"
    val compilation = kotlin.jvm().compilations.getByName("test")
    testClassesDirs = compilation.output.classesDirs
    classpath = files(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    useJUnit()
    filter { includeTestsMatching(snapshotRenderer) }
    val dir = snapshotDir.map { it.asFile.absolutePath }
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dshelfi.snapshotDir=${dir.get()}", "-Djava.awt.headless=true") })
    outputs.dir(snapshotDir)
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    testLogging { showStandardStreams = true }
}
