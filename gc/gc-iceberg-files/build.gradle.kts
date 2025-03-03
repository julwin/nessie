/*
 * Copyright (C) 2022 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  `java-library`
  jacoco
  `maven-publish`
  signing
  `nessie-conventions`
}

extra["maven.name"] = "Nessie - GC - Iceberg FileIO connector"

description =
  "Nessie GC integration tests with Spark, Iceberg and S3 as a separate project " +
    "due to the hugely different set of dependencies"

dependencies {

  // hadoop-common brings Jackson in ancient versions, pulling in the Jackson BOM to avoid that
  implementation(platform(libs.jackson.bom))
  implementation(libs.hadoop.common) {
    exclude("javax.servlet.jsp", "jsp-api")
    exclude("javax.ws.rs", "javax.ws.rs-api")
    exclude("log4j", "log4j")
    exclude("org.slf4j", "slf4j-log4j12")
    exclude("org.slf4j", "slf4j-reload4j")
    exclude("com.sun.jersey")
    exclude("org.eclipse.jetty")
    exclude("org.apache.zookeeper")
  }
  // Bump the jabx-impl version 2.2.3-1 via hadoop-common to make it work with Java 17+
  implementation(libs.jaxb.impl)

  implementation(libs.iceberg.core)
  implementation(libs.iceberg.aws)

  compileOnly(libs.errorprone.annotations)
  compileOnly(libs.immutables.value.annotations)
  annotationProcessor(libs.immutables.value.processor)

  implementation(nessieProject("nessie-gc-base"))

  implementation(libs.slf4j.api)

  testFixturesApi(nessieProject("nessie-gc-base-tests"))
  testFixturesApi(nessieProject("nessie-s3mock"))
  testFixturesApi(nessieProject("nessie-s3minio"))

  testFixturesRuntimeOnly(libs.logback.classic)

  testFixturesCompileOnly(platform(libs.jackson.bom))
  testFixturesCompileOnly(libs.jackson.annotations)

  testFixturesCompileOnly(libs.microprofile.openapi)

  testFixturesApi(platform(libs.awssdk.bom))
  testFixturesApi(libs.awssdk.s3)
  testFixturesRuntimeOnly(libs.awssdk.sts)
  testFixturesRuntimeOnly(libs.hadoop.aws)

  testFixturesApi(platform(libs.junit.bom))
  testFixturesApi(libs.bundles.junit.testing)
}

tasks.withType(Test::class.java).configureEach {
  systemProperty("aws.region", "us-east-1")
  val tmpdir = project.buildDir.resolve("tmpdir")
  jvmArgumentProviders.add(
    CommandLineArgumentProvider {
      tmpdir.mkdirs()
      listOf("-Djava.io.tmpdir=$tmpdir")
    }
  )
}
