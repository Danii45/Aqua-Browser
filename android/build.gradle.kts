plugins {
    // AGP 9 compiles Kotlin itself ("built-in Kotlin", KGP 2.2.10), so only the Compose compiler plugin is added.
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
