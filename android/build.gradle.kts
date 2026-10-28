// AGP 9 compiles Kotlin itself ("built-in Kotlin"). Its bundled Kotlin is older than the one today's
// AndroidX and GeckoView libraries are built with, so a newer Kotlin goes on the build classpath.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
