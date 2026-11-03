plugins {
    id("com.android.application") version "9.5.0" apply false
    // AGP 9 compiles Kotlin itself; this only pins a newer Kotlin than AGP's bundled default.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}
