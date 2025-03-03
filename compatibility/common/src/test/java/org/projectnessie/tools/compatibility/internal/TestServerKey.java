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
package org.projectnessie.tools.compatibility.internal;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.projectnessie.tools.compatibility.internal.ServerKey.StorageKind.DATABASE_ADAPTER;
import static org.projectnessie.tools.compatibility.internal.ServerKey.StorageKind.PERSIST;

import com.google.common.collect.ImmutableSortedMap;
import java.util.function.Supplier;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.projectnessie.tools.compatibility.api.Version;

@ExtendWith(SoftAssertionsExtension.class)
class TestServerKey {
  @InjectSoftAssertions protected SoftAssertions soft;

  @Test
  void nulls() {
    soft.assertThatThrownBy(() -> new ServerKey(null, "abc", DATABASE_ADAPTER, emptyMap()))
        .isInstanceOf(NullPointerException.class);
    soft.assertThatThrownBy(
            () -> new ServerKey(Version.CURRENT, null, DATABASE_ADAPTER, emptyMap()))
        .isInstanceOf(NullPointerException.class);
    soft.assertThatThrownBy(() -> new ServerKey(Version.CURRENT, "abc", null, emptyMap()))
        .isInstanceOf(NullPointerException.class);
    soft.assertThatThrownBy(() -> new ServerKey(Version.CURRENT, "abc", DATABASE_ADAPTER, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void equalsHash() {
    Supplier<ServerKey> factory =
        () -> new ServerKey(Version.CURRENT, "foo", DATABASE_ADAPTER, singletonMap("key", "value"));

    soft.assertThat(factory.get())
        .isEqualTo(factory.get())
        .isNotEqualTo(new ServerKey(Version.CURRENT, "foo", DATABASE_ADAPTER, emptyMap()))
        .isNotEqualTo(
            new ServerKey(Version.CURRENT, "bar", DATABASE_ADAPTER, singletonMap("key", "value")))
        .isNotEqualTo(new ServerKey(Version.CURRENT, "foo", PERSIST, singletonMap("key", "value")))
        .isNotEqualTo(
            new ServerKey(
                Version.NOT_CURRENT, "foo", DATABASE_ADAPTER, singletonMap("key", "value")))
        .isNotEqualTo("meep");
  }

  @Test
  void properties() {
    soft.assertThat(
            new ServerKey(
                Version.CURRENT,
                "foo",
                DATABASE_ADAPTER,
                ImmutableSortedMap.of("key", "value", "foo", "bar")))
        .extracting(
            ServerKey::getVersion,
            ServerKey::getStorageName,
            ServerKey::getConfig,
            ServerKey::toString)
        .containsExactly(
            Version.CURRENT,
            "foo",
            ImmutableSortedMap.of("key", "value", "foo", "bar"),
            "server-current-foo-DATABASE_ADAPTER-foo=bar_key=value");
  }
}
