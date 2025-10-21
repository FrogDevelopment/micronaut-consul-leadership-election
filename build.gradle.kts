plugins {
    id("io.micronaut.minimal.library") version "4.6.0"
    `maven-publish`
}

version = "1.0.0-SNAPSHOT"
group = "com.frogdevelopment.micronaut.consul"

repositories {
    mavenCentral()
}

val mockitoAgent = configurations.create("mockitoAgent")
dependencies {
    annotationProcessor(mn.lombok)
    annotationProcessor(mn.micronaut.serde.processor)

    implementation(mn.micronaut.serde.jackson)
    implementation(mn.micronaut.jackson.databind)
    implementation(mn.micronaut.discovery.client)
    implementation(mn.micronaut.management)
    implementation(mn.reactor)

    compileOnly(mn.lombok)

    runtimeOnly(mn.logback.classic)

    testCompileOnly(mn.lombok)
    testImplementation(mn.micronaut.test.junit5)
    testImplementation(mn.mockito.junit.jupiter)
    testImplementation(mn.testcontainers.consul)
    testImplementation(mn.assertj.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.awaitility)

    testRuntimeOnly(mn.micronaut.http.server.netty)
    testRuntimeOnly(mn.micronaut.http.client)

    mockitoAgent(mn.mockito.core) { isTransitive = false }
}

tasks {
    test {
        jvmArgs.add("-javaagent:${mockitoAgent.asPath}")
    }
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

    withJavadocJar()
    withSourcesJar()
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.frogdevelopment.micronaut.consul.leadership.*")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "leadership-election"

            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }

            pom {
                name = "Micronaut Consul Leadership Election"
                description = "Leadership Election for Micronaut using Consul's distributed leadership election."
                url = "https://github.com/FrogDevelopment/micronaut-consul-leadership-election/wiki"
                inceptionYear = "2025"
                issueManagement {
                    system = "GitHub"
                    url = "https://github.com/FrogDevelopment/micronaut-consul-leadership-election/issues"
                }
                developers {
                    developer {
                        id = "FrogDevelopper"
                        name = "Le Gall Benoît"
                        email = "legall.benoit@gmail.com"
                        url = "https://github.com/FrogDevelopper"
                        timezone = "Europe/Paris"
                    }
                }
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/FrogDevelopment/consul-populate.git"
                    developerConnection = "scm:git:ssh://github.com:FrogDevelopment/consul-populate.git"
                    url = "https://github.com/FrogDevelopment/micronaut-consul-leadership-election/tree/master"
                }
                distributionManagement {
                    downloadUrl = "https://github.com/FrogDevelopment/micronaut-consul-leadership-election/releases"
                }
            }
        }
    }

//    repositories {
//        maven {
//            name = "jreleaser"
//            url = uri(layout.buildDirectory.dir("staging-deploy"))
//        }
//    }
}
