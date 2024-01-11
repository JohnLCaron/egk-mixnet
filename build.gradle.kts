plugins {
    kotlin("jvm") version "1.9.21"
    alias(libs.plugins.serialization)
}

group = "org.cryptobiotic"
version = "0.6-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("libs/egklib-jvm-2.0.3-SNAPSHOT.jar"))
    implementation(files("libs/verificatum-vcr-3.1.0.jar"))
    implementation(files("libs/verificatum-vmn-3.1.0.jar"))

    implementation(libs.bundles.eglib)
    implementation(libs.bundles.xmlutil)
    implementation(libs.bundles.logging)
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(19)
}