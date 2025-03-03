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
  alias(libs.plugins.annotations.stripper)
}

extra["maven.name"] = "Nessie - REST Services"

dependencies {
  implementation(project(":nessie-model"))
  implementation(project(":nessie-services"))
  implementation(project(":nessie-versioned-spi"))
  implementation(libs.slf4j.api)
  implementation(libs.guava)

  // javax/jakarta
  compileOnly(libs.jakarta.enterprise.cdi.api)
  compileOnly(libs.javax.enterprise.cdi.api)
  compileOnly(libs.jakarta.annotation.api)
  compileOnly(libs.findbugs.jsr305)
  compileOnly(libs.jakarta.validation.api)
  compileOnly(libs.javax.validation.api)
  compileOnly(libs.jakarta.ws.rs.api)
  compileOnly(libs.javax.ws.rs21)
  compileOnly(libs.jakarta.servlet.api)
  compileOnly(libs.javax.servlet)

  compileOnly(libs.microprofile.openapi)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.databind)
  compileOnly(libs.jackson.annotations)

  // javax/jakarta
  testImplementation(libs.jakarta.validation.api)
  testImplementation(libs.javax.validation.api)
  testImplementation(libs.jakarta.ws.rs.api)
  testImplementation(libs.javax.ws.rs21)

  testCompileOnly(libs.microprofile.openapi)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.bundles.junit.testing)
}

annotationStripper {
  registerDefault().configure {
    annotationsToDrop("^jakarta[.].+".toRegex())
    unmodifiedClassesForJavaVersion.set(11)
  }
}
