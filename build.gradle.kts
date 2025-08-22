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
    
    implementation(libs.postgresql)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.core)
    implementation(libs.jakarta.persistence.api)
    kapt(libs.cactusthorn.config.compiler)
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
