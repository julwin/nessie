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
  `maven-publish`
  signing
  `nessie-conventions`
}

extra["maven.name"] = "Nessie - Versioned - Persist - Testextension"

dependencies {
  implementation(project(":nessie-versioned-persist-adapter"))
  implementation(project(":nessie-versioned-persist-store"))
  compileOnly(project(":nessie-versioned-tests"))
  implementation(project(":nessie-versioned-spi"))
  implementation(libs.micrometer.core)
  implementation(libs.opentracing.mock)

  implementation(platform(libs.junit.bom))
  implementation(libs.junit.jupiter.api)
  implementation(libs.junit.jupiter.params)
}
