// Protocol logic with no Android dependency: QR of the PharmaBox, unlock token checks, BLE
// result codes. Plain JVM so it is tested fast and in isolation.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
