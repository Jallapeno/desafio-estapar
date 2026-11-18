plugins {
    jacoco
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.allopen") version "2.1.21"
    id("com.google.devtools.ksp") version "2.1.21-2.0.1"
    id("io.micronaut.application") version "4.5.3"
    id("com.gradleup.shadow") version "8.3.6"
    id("io.micronaut.aot") version "4.5.3"
}

version = "0.1"
group = "br.com.estapar.parking"

val kotlinVersion = project.properties["kotlinVersion"]

repositories {
    mavenCentral()
}

dependencies {
    ksp("io.micronaut.data:micronaut-data-processor")
    ksp("io.micronaut:micronaut-http-validation")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    ksp("io.micronaut.validation:micronaut-validation-processor")

    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut:micronaut-retry")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")

    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("org.flywaydb:flyway-mysql")
    runtimeOnly("org.yaml:snakeyaml")

    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:testcontainers")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "br.com.estapar.parking.ApplicationKt"
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
}

kotlin {
    jvmToolchain(21)
}

graalvmNative.toolchainDetection = false

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("br.com.estapar.parking.*")
    }
    aot {
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }
}

tasks.named<io.micronaut.gradle.docker.MicronautDockerfile>("dockerfile") {
    baseImage = "eclipse-temurin:21-jre"
}

// Cobertura apenas do bytecode da aplicação; metadados gerados pelo Micronaut/KSP são excluídos.
jacoco {
    toolVersion = "0.8.13"
}

val coverageClasses = sourceSets.main.get().output.asFileTree.matching {
    exclude("**/*\$Definition*", "**/*\$Introspection*", "**/*\$Intercepted*", "**/*\$Exec*")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(coverageClasses)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(coverageClasses)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
