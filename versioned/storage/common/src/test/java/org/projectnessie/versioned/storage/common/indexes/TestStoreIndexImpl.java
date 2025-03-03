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
package org.projectnessie.versioned.storage.common.indexes;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptySet;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;
import static org.projectnessie.versioned.storage.common.indexes.StoreIndexElement.indexElement;
import static org.projectnessie.versioned.storage.common.indexes.StoreIndexes.deserializeStoreIndex;
import static org.projectnessie.versioned.storage.common.indexes.StoreIndexes.newStoreIndex;
import static org.projectnessie.versioned.storage.common.indexes.StoreKey.key;
import static org.projectnessie.versioned.storage.common.objtypes.CommitOp.Action.ADD;
import static org.projectnessie.versioned.storage.common.objtypes.CommitOp.Action.NONE;
import static org.projectnessie.versioned.storage.common.objtypes.CommitOp.COMMIT_OP_SERIALIZER;
import static org.projectnessie.versioned.storage.common.objtypes.CommitOp.commitOp;
import static org.projectnessie.versioned.storage.common.objtypes.ObjIdSerializer.OBJ_ID_SERIALIZER;
import static org.projectnessie.versioned.storage.common.persist.ObjId.EMPTY_OBJ_ID;
import static org.projectnessie.versioned.storage.common.persist.ObjId.objIdFromString;
import static org.projectnessie.versioned.storage.common.persist.ObjId.randomObjId;
import static org.projectnessie.versioned.storage.common.util.Util.asHex;
import static org.projectnessie.versioned.storage.commontests.KeyIndexTestSet.basicIndexTestSet;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.projectnessie.versioned.storage.common.objtypes.CommitOp;
import org.projectnessie.versioned.storage.common.persist.ObjId;
import org.projectnessie.versioned.storage.commontests.KeyIndexTestSet;

@ExtendWith(SoftAssertionsExtension.class)
public class TestStoreIndexImpl {
  @InjectSoftAssertions SoftAssertions soft;

  @Test
  public void isModified() {
    StoreIndex<ObjId> segment = newStoreIndex(OBJ_ID_SERIALIZER);
    soft.assertThat(segment.isModified()).isFalse();

    segment.add(indexElement(key("foo"), EMPTY_OBJ_ID));
    soft.assertThat(segment.isModified()).isTrue();

    segment = deserializeStoreIndex(segment.serialize(), OBJ_ID_SERIALIZER);
    soft.assertThat(segment.isModified()).isFalse();
    segment.add(indexElement(key("foo"), randomObjId()));
    soft.assertThat(segment.isModified()).isTrue();

    segment = deserializeStoreIndex(segment.serialize(), OBJ_ID_SERIALIZER);
    soft.assertThat(segment.isModified()).isFalse();
    segment.updateAll(el -> randomObjId());
    soft.assertThat(segment.isModified()).isTrue();

    segment = deserializeStoreIndex(segment.serialize(), OBJ_ID_SERIALIZER);
    soft.assertThat(segment.isModified()).isFalse();
    segment.remove(key("foo"));
    soft.assertThat(segment.isModified()).isTrue();

    segment = deserializeStoreIndex(segment.serialize(), OBJ_ID_SERIALIZER);
    soft.assertThat(segment.isModified()).isFalse();
    segment.updateAll(el -> randomObjId());
    // Index is empty, nothing updated
    soft.assertThat(segment.isModified()).isFalse();
  }

  @Test
  public void keyIndexSegment() {
    StoreIndex<ObjId> segment = newStoreIndex(OBJ_ID_SERIALIZER);
    ObjId id1 = objIdFromString("12345678");
    ObjId id2 = objIdFromString("1234567812345678123456781234567812345678123456781234567812345678");
    ObjId id3 = objIdFromString("1111111122222222111111112222222211111111222222221111111122222222");
    ObjId id4 =
        objIdFromString(IntStream.range(0, 256).mapToObj(i -> "10").collect(Collectors.joining()));

    StoreKey keyA = key("a", "x", "A");
    StoreKey keyB = key("b", "x", "A");
    StoreKey keyC = key("c", "x", "A");
    StoreKey keyD = key("d", "x", "A");
    StoreKey keyE = key("e", "x", "A");
    StoreKey keyExB = key("e", "x", "B");
    StoreKey keyExD = key("e", "x", "D");
    StoreKey keyEyC = key("e", "y", "C");
    StoreKey keyExC = key("e", "x", "C");
    StoreKey keyNotExist = key("does", "not", "exist");

    String serializationFormatVersion = "01";

    String serializedA =
        "61007800410000"
            + "04" // 4 bytes hash
            + id1;
    String serializedB =
        "62007800410000"
            + "20" // 32 bytes hash
            + id2;
    String serializedC =
        "63007800410000"
            + "20" // 32 bytes hash
            + id3;
    String serializedD =
        "64007800410000"
            + "8002" // 256 bytes hash (0 == 256 here!)
            + id4;
    String serializedE =
        "65007800410000"
            + "04" // 4 bytes hash
            + id1;
    String serializedExB =
        "420000"
            + "20" // 32 bytes hash
            + id2;
    String serializedExD =
        "440000"
            + "20" // 32 bytes hash
            + id3;
    String serializedEyC =
        "7900430000"
            + "8002" // 256 bytes hash (0 == 256 here!)
            + id4;
    String serializedExC =
        "430000"
            + "04" // 4 bytes hash
            + id1;
    String serializedExCmodified =
        "430000"
            + "20" // 32 bytes hash
            + id2;

    Function<StoreIndex<ObjId>, StoreIndex<ObjId>> reSerialize =
        seg -> deserializeStoreIndex(seg.serialize(), OBJ_ID_SERIALIZER);

    soft.assertThat(asHex(segment.serialize())).isEqualTo(serializationFormatVersion);
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.asKeyList()).isEmpty();
    soft.assertThat(segment.elementCount()).isEqualTo(0);

    soft.assertThat(segment.add(indexElement(keyD, id4))).isTrue();
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.asKeyList()).containsExactly(keyD);
    soft.assertThat(asHex(segment.serialize()))
        .isEqualTo(
            serializationFormatVersion //
                + serializedD);

    soft.assertThat(segment.add(indexElement(keyB, id2))).isTrue();
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.asKeyList()).containsExactly(keyB, keyD);
    soft.assertThat(asHex(segment.serialize()))
        .isEqualTo(
            serializationFormatVersion //
                + serializedB
                + "07" // strip
                + serializedD);

    soft.assertThat(segment.add(indexElement(keyC, id3))).isTrue();
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.asKeyList()).containsExactly(keyB, keyC, keyD);
    soft.assertThat(asHex(segment.serialize()))
        .isEqualTo(
            serializationFormatVersion //
                + serializedB
                + "07" // strip
                + serializedC
                + "07" // strip
                + serializedD);

    soft.assertThat(segment.add(indexElement(keyE, id1))).isTrue();
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.asKeyList()).containsExactly(keyB, keyC, keyD, keyE);
    soft.assertThat(asHex(segment.serialize()))
        .isEqualTo(
            serializationFormatVersion //
                + serializedB
                + "07" // strip
                + serializedC
                + "07" // strip
                + serializedD
                + "07" // strip
                + serializedE);

    soft.assertThat(segment.add(indexElement(keyA, id1))).isTrue();
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.asKeyList()).containsExactly(keyA, keyB, keyC, keyD, keyE);
    soft.assertThat(asHex(segment.serialize()))
        .isEqualTo(
            serializationFormatVersion //
                + serializedA
                + "07" // strip
                + serializedB
                + "07" // strip
                + serializedC
                + "07" // strip
                + serializedD
                + "07" // strip
                + serializedE);

    soft.assertThat(segment.add(indexElement(keyExB, id2))).isTrue();
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.asKeyList()).containsExactly(keyA, keyB, keyC, keyD, keyE, keyExB);
    soft.assertThat(asHex(segment.serialize()))
        .isEqualTo(
            serializationFormatVersion //
                + serializedA
                + "07" // strip
                + serializedB
                + "07" // strip
                + serializedC
                + "07" // strip
                + serializedD
                + "07" // strip
                + serializedE
                + "03" // strip
                + serializedExB);

    soft.assertThat(segment.add(indexElement(keyExD, id3))).isTrue();
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.asKeyList())
        .containsExactly(keyA, keyB, keyC, keyD, keyE, keyExB, keyExD);
    soft.assertThat(asHex(segment.serialize()))
        .isEqualTo(
            serializationFormatVersion //
                + serializedA
                + "07" // strip
                + serializedB
                + "07" // strip
                + serializedC
                + "07" // strip
                + serializedD
                + "07" // strip
                + serializedE
                + "03" // strip
                + serializedExB
                + "03" // strip
                + serializedExD);

    soft.assertThat(segment.add(indexElement(keyEyC, id4))).isTrue();
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.asKeyList())
        .containsExactly(keyA, keyB, keyC, keyD, keyE, keyExB, keyExD, keyEyC);
    soft.assertThat(asHex(segment.serialize()))
        .isEqualTo(
            serializationFormatVersion //
                + serializedA
                + "07" // add
                + serializedB
                + "07" // add
                + serializedC
                + "07" // add
                + serializedD
                + "07" // add
                + serializedE
                + "03" // strip
                + serializedExB
                + "03" // strip
                + serializedExD
                + "05" // strip
                + serializedEyC);

    soft.assertThat(segment.add(indexElement(keyExC, id1))).isTrue();
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.asKeyList())
        .containsExactly(keyA, keyB, keyC, keyD, keyE, keyExB, keyExC, keyExD, keyEyC);
    soft.assertThat(asHex(segment.serialize()))
        .isEqualTo(
            serializationFormatVersion //
                + serializedA
                + "07" // add
                + serializedB
                + "07" // add
                + serializedC
                + "07" // add
                + serializedD
                + "07" // add
                + serializedE
                + "03" // strip
                + serializedExB
                + "03" // strip
                + serializedExC
                + "03" // strip
                + serializedExD
                + "05" // strip
                + serializedEyC);
    soft.assertThat(segment.get(keyExC)).isEqualTo(indexElement(keyExC, id1));

    // Re-add with a BIGGER serialized object-id
    soft.assertThat(segment.add(indexElement(keyExC, id2))).isFalse();
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.asKeyList())
        .containsExactly(keyA, keyB, keyC, keyD, keyE, keyExB, keyExC, keyExD, keyEyC);
    soft.assertThat(asHex(segment.serialize()))
        .isEqualTo(
            serializationFormatVersion //
                + serializedA
                + "07" // add
                + serializedB
                + "07" // add
                + serializedC
                + "07" // add
                + serializedD
                + "07" // add
                + serializedE
                + "03" // strip
                + serializedExB
                + "03" // strip
                + serializedExCmodified
                + "03" // strip
                + serializedExD
                + "05" // strip
                + serializedEyC);
    soft.assertThat(segment.get(keyExC)).isEqualTo(indexElement(keyExC, id2));

    soft.assertThat(segment.remove(keyNotExist)).isFalse();
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.asKeyList())
        .containsExactly(keyA, keyB, keyC, keyD, keyE, keyExB, keyExC, keyExD, keyEyC);
    soft.assertThat(segment.contains(keyNotExist)).isFalse();

    soft.assertThat(segment.remove(keyD)).isTrue();
    soft.assertThat(reSerialize.apply(segment)).isEqualTo(segment);
    soft.assertThat(segment.elementCount()).isEqualTo(8);
    soft.assertThat(asHex(segment.serialize()))
        .isEqualTo(
            serializationFormatVersion //
                + serializedA
                + "07" // add
                + serializedB
                + "07" // add
                + serializedC
                + "07" // add
                + serializedE
                + "03" // strip
                + serializedExB
                + "03" // strip
                + serializedExCmodified
                + "03" // strip
                + serializedExD
                + "05" // strip
                + serializedEyC);
    soft.assertThat(segment.asKeyList())
        .containsExactly(keyA, keyB, keyC, keyE, keyExB, keyExC, keyExD, keyEyC);
    soft.assertThat(segment.contains(keyD)).isFalse();
    soft.assertThat(segment.contains(keyNotExist)).isFalse();
    soft.assertThat(segment.get(keyD)).isNull();
  }

  @Test
  public void getFirstLast() {
    StoreIndex<CommitOp> index = newStoreIndex(COMMIT_OP_SERIALIZER);

    ObjId id = objIdFromString("12344568");
    for (char e1 = 'j'; e1 >= 'a'; e1--) {
      for (char e2 = 'J'; e2 >= 'A'; e2--) {
        StoreKey key = key("" + e1, "" + e2);
        index.add(indexElement(key, commitOp(ADD, 1, id)));
      }
    }

    soft.assertThat(index.elementCount()).isEqualTo(10 * 10);
    soft.assertThat(index.first()).isEqualTo(key("a", "A"));
    soft.assertThat(index.last()).isEqualTo(key("j", "J"));
  }

  @Test
  public void iterator() {
    StoreIndex<CommitOp> index = newStoreIndex(COMMIT_OP_SERIALIZER);

    ObjId id = objIdFromString("12344568");
    for (char e1 = 'j'; e1 >= 'a'; e1--) {
      for (char e2 = 'J'; e2 >= 'A'; e2--) {
        StoreKey key = key("" + e1, "" + e2);
        index.add(indexElement(key, commitOp(ADD, 1, id)));
      }
    }

    soft.assertThat(index.elementCount()).isEqualTo(10 * 10);

    soft.assertThat(newArrayList(index.iterator())).hasSize(10 * 10);
    soft.assertThat(newArrayList(index.iterator(null, null, false))).hasSize(10 * 10);

    soft.assertThat(newArrayList(index.iterator(key("a"), null, false))).hasSize(10 * 10);
    soft.assertThat(newArrayList(index.iterator(key("a"), key("j"), false))).hasSize(9 * 10);
    soft.assertThat(newArrayList(index.iterator(null, key("j"), false))).hasSize(9 * 10);
    soft.assertThat(newArrayList(index.iterator(key("b"), key("j"), false))).hasSize(8 * 10);
    soft.assertThat(newArrayList(index.iterator(key("j"), null, false))).hasSize(10);
    soft.assertThat(newArrayList(index.iterator(key("a", "C"), key("a", "Z"), false))).hasSize(8);

    soft.assertThat(newArrayList(index.iterator(key("b", "B"), key("b", "B"), false))).hasSize(1);
    soft.assertThat(newArrayList(index.iterator(key("b"), key("b", "B"), false))).hasSize(2);
    soft.assertThat(newArrayList(index.iterator(key("b"), key("b"), false)))
        .allMatch(el -> el.key().startsWith(key("b")));
    soft.assertThat(newArrayList(index.iterator(key("b"), key("c"), false))).hasSize(10);
    soft.assertThat(newArrayList(index.iterator(key("b"), key("c", "A"), false))).hasSize(11);

    soft.assertThatIllegalArgumentException()
        .isThrownBy(() -> index.iterator(key("z"), key("a"), false));
  }

  @Test
  public void updateAll() {
    KeyIndexTestSet<CommitOp> indexTestSet = basicIndexTestSet();

    soft.assertThat(indexTestSet.keyIndex())
        .isNotEmpty()
        .allMatch(el -> el.content().action() == ADD);

    indexTestSet
        .keyIndex()
        .updateAll(el -> commitOp(NONE, el.content().payload(), el.content().value()));

    soft.assertThat(indexTestSet.keyIndex())
        .isNotEmpty()
        .allMatch(el -> el.content().action() == NONE);

    indexTestSet.keyIndex().updateAll(el -> null);

    soft.assertThat(indexTestSet.keyIndex()).isEmpty();
  }

  @Test
  public void emptyIndexDivide() {
    for (int i = -5; i < 5; i++) {
      int parts = i;
      soft.assertThatIllegalArgumentException()
          .isThrownBy(() -> newStoreIndex(COMMIT_OP_SERIALIZER).divide(parts))
          .withMessageStartingWith("Number of parts ")
          .withMessageContaining(
              " must be greater than 0 and less or equal to number of elements ");
    }
  }

  @Test
  public void impossibleDivide() {
    KeyIndexTestSet<CommitOp> indexTestSet = basicIndexTestSet();
    StoreIndex<CommitOp> index = indexTestSet.keyIndex();

    soft.assertThatIllegalArgumentException()
        .isThrownBy(() -> index.divide(index.elementCount() + 1))
        .withMessageStartingWith("Number of parts ")
        .withMessageContaining(" must be greater than 0 and less or equal to number of elements ");
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3, 4, 5, 6})
  public void divide(int parts) {
    KeyIndexTestSet<CommitOp> indexTestSet = basicIndexTestSet();
    StoreIndex<CommitOp> index = indexTestSet.keyIndex();

    List<StoreIndex<CommitOp>> splits = index.divide(parts);

    soft.assertThat(splits.stream().mapToInt(StoreIndex::elementCount).sum())
        .isEqualTo(index.elementCount());
    soft.assertThat(splits.stream().flatMap(i -> i.asKeyList().stream()))
        .containsExactlyElementsOf(index.asKeyList());
    soft.assertThat(
            splits.stream().flatMap(i -> stream(spliteratorUnknownSize(i.iterator(), 0), false)))
        .containsExactlyElementsOf(newArrayList(index));
    soft.assertThat(splits.get(0).first()).isEqualTo(index.first());
    soft.assertThat(splits.get(splits.size() - 1).last()).isEqualTo(index.last());
  }

  @Test
  public void stateRelated() {
    KeyIndexTestSet<CommitOp> indexTestSet = basicIndexTestSet();
    StoreIndex<CommitOp> index = indexTestSet.keyIndex();

    soft.assertThat(index.asMutableIndex()).isSameAs(index);
    soft.assertThat(index.loadIfNecessary(emptySet())).isSameAs(index);
    soft.assertThat(index.isMutable()).isTrue();
    soft.assertThatCode(() -> index.divide(3)).doesNotThrowAnyException();
  }
}
