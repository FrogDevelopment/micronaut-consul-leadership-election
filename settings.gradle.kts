plugins {
    // Apply the foojay-resolver plugin to allow automatic download of JDKs
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("io.micronaut.platform.catalog") version "4.6.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "micronaut-consul-leadership-election"
