import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ebean)
}

group = "my.baas"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.javalin)
    implementation(libs.javalin.openapi.plugin)
    implementation(libs.javalin.swagger.plugin)

    kapt(libs.openapi.annotation.processor) {
        exclude("ch.qos.logback", "logback-classic")
    }
    implementation(libs.ebean)
    implementation(libs.cactusthorn.config)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jose4j)
    implementation(libs.json.schema.validator)
    implementation(libs.json.path)
    
    implementation(libs.ebean.ddl.generator)
    implementation(libs.ebean.platform.postgres)
    implementation(libs.ebean.migration)
    implementation(libs.delight.nashorn.sandbox)
    implementation(libs.jedis)
    
    // MinIO client for S3-compatible object storage
    implementation(libs.minio)
    
    // CSV generation
    implementation(libs.opencsv)
    
    // Excel generation
    implementation(libs.apache.poi)
    implementation(libs.apache.poi.ooxml)
    
    // SFTP and Email
    implementation(libs.jsch)
    implementation(libs.jakarta.mail)
    implementation(libs.jakarta.mail.impl)
    
    // Annotation processing for DTO generation
    implementation(libs.kotlinpoet)
    testImplementation(libs.kotlin.compile.testing)
    
    implementation(libs.postgresql)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.core)
    implementation(libs.jakarta.persistence.api)
    kapt(libs.cactusthorn.config.compiler)
    
    // Test dependencies
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
}

tasks.test {
    useJUnitPlatform()
}
tasks {
    jar {
        manifest {
            attributes(
                "Main-Class" to "my.baas.ApplicationKt"
            )
        }
    }
}
