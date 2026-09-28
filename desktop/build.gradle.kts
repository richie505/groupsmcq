import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// The Windows app reuses the Android app's code (data, screens, techniques) and its question bank.
// Only the entry point and two tiny platform files differ (src/main/kotlin/.../platform, Main.kt).
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets["main"].kotlin.srcDir("../app/src/main/java")
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
sourceSets["main"].resources.srcDir("../app/src/main/assets")
// Android-only files
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    exclude("**/MainActivity.kt", "**/platform/AndroidPlatform.kt")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "3g"
    testLogging { events("passed", "skipped", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

compose.desktop {
    application {
        mainClass = "com.appsc.mcq.MainKt"
        jvmArgs += listOf("-Xmx1g")
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "APPSC MCQ 90"
            packageVersion = "1.4.0"
            description = "APPSC Group 1 & 2 MCQ practice on the 90-day plan"
            vendor = "APPSC MCQ 90"
            modules("java.instrument", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icons/app.ico"))
                menu = true
                menuGroup = "APPSC MCQ 90"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // fixed id: a newer installer upgrades the installed app in place (progress is kept in %APPDATA%)
                upgradeUuid = "6f1c2c7e-5b7a-4a51-9f0e-3d8a1c6b2e90"
            }
            linux { iconFile.set(project.file("icons/app.png")) }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}
