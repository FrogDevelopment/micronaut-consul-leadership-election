plugins {
    id("io.micronaut.minimal.library") version "4.5.4"
    id("com.gradleup.shadow") version "8.3.7"
//    id("io.micronaut.aot") version "4.5.4"
}

version = "1.0.0-SNAPSHOT"
group = "micronaut.consul.leadership.election"

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor(mn.lombok)
    annotationProcessor(mn.micronaut.serde.processor)

    implementation(mn.micronaut.serde.jackson)
    implementation(mn.micronaut.jackson.databind)
    implementation(mn.micronaut.discovery.client)
    implementation(mn.reactor)

    compileOnly(mn.lombok)

    runtimeOnly(mn.logback.classic)

    testCompileOnly(mn.lombok)
    testImplementation(mn.micronaut.test.junit5)
    testImplementation(mn.mockito.junit.jupiter)
//    testImplementation("io.projectreactor:reactor-test:3.6.7")
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use JUnit Jupiter test framework
            useJUnitJupiter("5.12.1")
        }
    }
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.frogdevelopment.micronaut.consul.leadership.*")
    }
//    aot {
//        // Please review carefully the optimizations enabled below
//        // Check https://micronaut-projects.github.io/micronaut-aot/latest/guide/ for more details
//        optimizeServiceLoading = false
//        convertYamlToJava = false
//        precomputeOperations = true
//        cacheEnvironment = true
//        optimizeClassLoading = true
//        deduceEnvironment = true
//        optimizeNetty = true
//        replaceLogbackXml = true
//    }
}
