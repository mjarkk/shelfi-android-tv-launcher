import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.mkopenga.tvhomescreen.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Shelfi"
            packageVersion = "1.0.0"
        }
    }
}
