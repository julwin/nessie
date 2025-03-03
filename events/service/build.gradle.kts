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

extra["maven.name"] = "Nessie - Events - Service"

dependencies {
  implementation(project(":nessie-model"))
  implementation(project(":nessie-versioned-spi"))
  implementation(project(":nessie-events-api"))
  implementation(project(":nessie-events-spi"))

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)

  implementation(libs.slf4j.api)

  compileOnly(libs.microprofile.openapi)
  compileOnly(libs.jakarta.annotation.api)

  compileOnly(libs.immutables.builder)
  compileOnly(libs.immutables.value.annotations)
  annotationProcessor(libs.immutables.value.processor)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.bundles.junit.testing)
  testImplementation(libs.guava)
  testImplementation(libs.logback.classic)

  testCompileOnly(libs.microprofile.openapi)
}
