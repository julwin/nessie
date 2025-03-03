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
package org.projectnessie.versioned.tests;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.projectnessie.versioned.testworker.OnRefOnly.newOnRef;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.Namespace;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.KeyEntry;
import org.projectnessie.versioned.Ref;
import org.projectnessie.versioned.VersionStore;
import org.projectnessie.versioned.paging.PaginationIterator;

@ExtendWith(SoftAssertionsExtension.class)
public abstract class AbstractEntries extends AbstractNestedVersionStore {
  @InjectSoftAssertions protected SoftAssertions soft;

  protected AbstractEntries(VersionStore store) {
    super(store);
  }

  @Test
  public void entriesWrongParameters() {
    assumeThat(store().getClass().getName()).endsWith("VersionStoreImpl");

    soft.assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                keysAsList(
                    store().noAncestorHash(),
                    ContentKey.of("foo"),
                    ContentKey.of("foo"),
                    ContentKey.of("foo"),
                    null))
        .withMessageContaining(
            "Combining prefixKey with either minKey or maxKey is not supported.");
    soft.assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                keysAsList(
                    store().noAncestorHash(),
                    null,
                    ContentKey.of("foo"),
                    ContentKey.of("foo"),
                    null))
        .withMessageContaining(
            "Combining prefixKey with either minKey or maxKey is not supported.");
    soft.assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                keysAsList(
                    store().noAncestorHash(),
                    ContentKey.of("foo"),
                    null,
                    ContentKey.of("foo"),
                    null))
        .withMessageContaining(
            "Combining prefixKey with either minKey or maxKey is not supported.");
  }

  @Test
  public void entriesRanges() throws Exception {
    assumeThat(store().getClass().getName()).endsWith("VersionStoreImpl");

    BranchName branch = BranchName.of("foo");
    ContentKey key1 = ContentKey.of("k1");
    ContentKey key2 = ContentKey.of("k2");
    ContentKey key2a = ContentKey.of("k2", "a");
    ContentKey key2b = ContentKey.of("k2", "aπ"); // UNICODE CHAR
    ContentKey key2c = ContentKey.of("k2", "πa"); // UNICODE CHAR, This is GREATER than k2.k3 !
    ContentKey key2d = ContentKey.of("k2", "aa");
    ContentKey key23 = ContentKey.of("k2", "k3");
    ContentKey key23a = ContentKey.of("k2", "k3", "a");
    ContentKey key23b = ContentKey.of("k2", "k3", "b");
    ContentKey key3 = ContentKey.of("k3");
    store().create(branch, Optional.empty()).getHash();
    Hash initialCommit =
        commit("Initial Commit")
            .put(key1, newOnRef("v1"))
            .put(key2, Namespace.of(key2))
            .put(key23, Namespace.of(key23))
            .put(key2a, newOnRef("v2a"))
            .put(key2b, newOnRef("v2b"))
            .put(key2c, newOnRef("v2c"))
            .put(key2d, newOnRef("v2d"))
            .put(key23a, newOnRef("v23a"))
            .put(key23b, newOnRef("v23b"))
            .put(key3, newOnRef("v3"))
            .toBranch(branch);

    soft.assertThat(keysAsList(initialCommit, null, null, null, null))
        .map(KeyEntry::getKey)
        .containsExactlyInAnyOrder(
            key1, key2, key2a, key2b, key2c, key2d, key23, key23a, key23b, key3);
    soft.assertThat(keysAsList(initialCommit, key23, null, null, null))
        .map(KeyEntry::getKey)
        .containsExactlyInAnyOrder(key23, key23a, key23b, key3, key2c);
    soft.assertThat(keysAsList(initialCommit, null, null, key23, null))
        .map(KeyEntry::getKey)
        .containsExactlyInAnyOrder(key23, key23a, key23b);
    soft.assertThat(keysAsList(initialCommit, null, key23, null, null))
        .map(KeyEntry::getKey)
        .containsExactlyInAnyOrder(key1, key2, key2a, key2b, key2d, key23);
    soft.assertThat(keysAsList(initialCommit, key23, key23a, null, null))
        .map(KeyEntry::getKey)
        .containsExactlyInAnyOrder(key23, key23a);
    soft.assertThat(keysAsList(initialCommit, null, null, ContentKey.of("k"), null))
        .map(KeyEntry::getKey)
        .isEmpty();
    soft.assertThat(keysAsList(initialCommit, null, ContentKey.of("k"), null, null))
        .map(KeyEntry::getKey)
        .isEmpty();
    soft.assertThat(keysAsList(initialCommit, null, null, ContentKey.of("x"), null))
        .map(KeyEntry::getKey)
        .isEmpty();
    soft.assertThat(
            keysAsList(
                initialCommit,
                null,
                null,
                null,
                k -> k.toPathString().startsWith(key2.toPathString())))
        .map(KeyEntry::getKey)
        .containsExactlyInAnyOrder(key2, key2a, key2b, key2c, key2d, key23, key23a, key23b);
  }

  List<KeyEntry> keysAsList(
      Ref ref,
      ContentKey minKey,
      ContentKey maxKey,
      ContentKey prefixKey,
      Predicate<ContentKey> contentKeyPredicate)
      throws Exception {
    try (PaginationIterator<KeyEntry> keys =
        store().getKeys(ref, null, false, minKey, maxKey, prefixKey, contentKeyPredicate)) {
      return newArrayList(keys);
    }
  }
}
