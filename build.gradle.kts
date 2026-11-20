// Top-level build configuration
// AGP 9.1.1: compileSdk 37 support (Miuix 0.9.4 + Compose 1.12 need 37).
// AGP 9 has built-in Kotlin: org.jetbrains.kotlin.android is REMOVED
// (applying it errors: duplicate 'kotlin' extension). Compiler plugins
// (compose, serialization) and KSP stay, bumped to the Kotlin 2.2.10 line.
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
}
