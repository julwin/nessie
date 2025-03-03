/*
 * Copyright (C) 2023 Dremio
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
package org.projectnessie.tools.contentgenerator.cli;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.tuple;
import static org.projectnessie.jaxrs.ext.NessieJaxRsExtension.jaxRsExtensionForDatabaseAdapter;
import static org.projectnessie.model.CommitMeta.fromMessage;
import static org.projectnessie.model.Content.Type.NAMESPACE;
import static org.projectnessie.tools.contentgenerator.RunContentGenerator.runGeneratorCmd;
import static org.projectnessie.tools.contentgenerator.cli.CreateMissingNamespaces.branchesStream;
import static org.projectnessie.tools.contentgenerator.cli.CreateMissingNamespaces.collectMissingNamespaceKeys;
import static org.projectnessie.tools.contentgenerator.cli.CreateMissingNamespaces.commitCreateNamespaces;

import java.net.URI;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.projectnessie.client.api.NessieApiV2;
import org.projectnessie.client.ext.NessieClientFactory;
import org.projectnessie.client.ext.NessieClientUri;
import org.projectnessie.jaxrs.ext.NessieJaxRsExtension;
import org.projectnessie.model.Branch;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.EntriesResponse;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.Namespace;
import org.projectnessie.model.Operation.Put;
import org.projectnessie.model.Reference;
import org.projectnessie.tools.contentgenerator.RunContentGenerator.ProcessResult;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapter;
import org.projectnessie.versioned.persist.inmem.InmemoryDatabaseAdapterFactory;
import org.projectnessie.versioned.persist.inmem.InmemoryTestConnectionProviderSource;
import org.projectnessie.versioned.persist.tests.extension.DatabaseAdapterExtension;
import org.projectnessie.versioned.persist.tests.extension.NessieDbAdapter;
import org.projectnessie.versioned.persist.tests.extension.NessieDbAdapterConfigItem;
import org.projectnessie.versioned.persist.tests.extension.NessieDbAdapterName;
import org.projectnessie.versioned.persist.tests.extension.NessieExternalDatabase;

@NessieDbAdapterName(InmemoryDatabaseAdapterFactory.NAME)
@NessieExternalDatabase(InmemoryTestConnectionProviderSource.class)
@ExtendWith({DatabaseAdapterExtension.class, SoftAssertionsExtension.class})
public class TestCreateMissingNamespaces {
  @InjectSoftAssertions protected SoftAssertions soft;

  @NessieDbAdapter
  @NessieDbAdapterConfigItem(name = "validate.namespaces", value = "false")
  static DatabaseAdapter databaseAdapter;

  @RegisterExtension
  static NessieJaxRsExtension server = jaxRsExtensionForDatabaseAdapter(() -> databaseAdapter);

  private NessieApiV2 nessieApi;
  private URI uri;

  @BeforeEach
  public void setUp(NessieClientFactory clientFactory, @NessieClientUri URI uri) {
    nessieApi = (NessieApiV2) clientFactory.make();
    this.uri = uri;
  }

  @AfterEach
  public void tearDown() {
    nessieApi.close();
  }

  @Test
  public void roundtrip() throws Exception {
    prepareRoundtrip();

    ProcessResult result =
        runGeneratorCmd("create-missing-namespaces", "--verbose", "--uri", uri.toString());

    soft.assertThat(result)
        .extracting(
            ProcessResult::getExitCode,
            ProcessResult::getStdOutLines,
            ProcessResult::getStdErrLines)
        .containsExactly(
            0,
            asList(
                "Start fetching and processing references...",
                "  processing branch main...",
                "    all namespaces present.",
                "  processing branch branch1...",
                "    creating 3 namespaces...",
                "      - a",
                "      - a.b",
                "      - x",
                "    ... committed.",
                "  processing branch branch2...",
                "    creating 2 namespaces...",
                "      - x",
                "      - x.y",
                "    ... committed.",
                "  processing branch branch3...",
                "    all namespaces present.",
                "Successfully processed 4 branches, created 5 namespaces."),
            singletonList(""));
  }

  @Test
  public void roundtripNonExistingBranch() throws Exception {
    prepareRoundtrip();

    ProcessResult result =
        runGeneratorCmd(
            "create-missing-namespaces", "--verbose", "--uri", uri.toString(), "--branch", "foo");
    soft.assertThat(result)
        .extracting(
            ProcessResult::getExitCode,
            ProcessResult::getStdOutLines,
            ProcessResult::getStdErrLines)
        .containsExactly(
            1,
            asList(
                "Start fetching and processing references...",
                "Successfully processed 0 branches, created 0 namespaces."),
            asList(
                "Could not find branch(es) foo specified as command line arguments.",
                "See above messages for errors!"));
  }

  @Test
  public void roundtripSomeBranches() throws Exception {
    prepareRoundtrip();

    ProcessResult result =
        runGeneratorCmd(
            "create-missing-namespaces",
            "--verbose",
            "--uri",
            uri.toString(),
            "--branch",
            "branch1",
            "--branch",
            "branch3");

    soft.assertThat(result)
        .extracting(
            ProcessResult::getExitCode,
            ProcessResult::getStdOutLines,
            ProcessResult::getStdErrLines)
        .containsExactly(
            0,
            asList(
                "Start fetching and processing references...",
                "  processing branch branch1...",
                "    creating 3 namespaces...",
                "      - a",
                "      - a.b",
                "      - x",
                "    ... committed.",
                "  processing branch branch3...",
                "    all namespaces present.",
                "Successfully processed 2 branches, created 3 namespaces."),
            singletonList(""));
  }

  protected void prepareRoundtrip() throws Exception {
    Branch defaultBranch = nessieApi.getDefaultBranch();
    Reference branch1 =
        nessieApi
            .createReference()
            .sourceRefName(defaultBranch.getName())
            .reference(Branch.of("branch1", defaultBranch.getHash()))
            .create();
    Reference branch2 =
        nessieApi
            .createReference()
            .sourceRefName(defaultBranch.getName())
            .reference(Branch.of("branch2", defaultBranch.getHash()))
            .create();
    Reference branch3 =
        nessieApi
            .createReference()
            .sourceRefName(defaultBranch.getName())
            .reference(Branch.of("branch3", defaultBranch.getHash()))
            .create();

    nessieApi
        .commitMultipleOperations()
        .commitMeta(fromMessage("foo"))
        .branchName(branch1.getName())
        .operation(Put.of(ContentKey.of("a", "b", "Table"), IcebergTable.of("meta1", 1, 2, 3, 4)))
        .operation(Put.of(ContentKey.of("a", "b", "Data"), IcebergTable.of("meta2", 1, 2, 3, 4)))
        .operation(Put.of(ContentKey.of("Data"), IcebergTable.of("meta3", 1, 2, 3, 4)))
        .operation(Put.of(ContentKey.of("x", "Data"), IcebergTable.of("meta3", 1, 2, 3, 4)))
        .commit();

    nessieApi
        .commitMultipleOperations()
        .commitMeta(fromMessage("foo"))
        .branchName(branch2.getName())
        .operation(Put.of(ContentKey.of("x", "y", "Table"), IcebergTable.of("meta1", 1, 2, 3, 4)))
        .commit();

    nessieApi
        .commitMultipleOperations()
        .commitMeta(fromMessage("foo"))
        .branchName(branch3.getName())
        .operation(Put.of(ContentKey.of("Table"), IcebergTable.of("meta1", 1, 2, 3, 4)))
        .commit();
  }

  @Test
  public void testBranchesStream() throws Exception {
    Branch defaultBranch = nessieApi.getDefaultBranch();
    nessieApi
        .createReference()
        .sourceRefName(defaultBranch.getName())
        .reference(Branch.of("branch1", defaultBranch.getHash()))
        .create();
    nessieApi
        .createReference()
        .sourceRefName(defaultBranch.getName())
        .reference(Branch.of("branch2", defaultBranch.getHash()))
        .create();
    nessieApi
        .createReference()
        .sourceRefName(defaultBranch.getName())
        .reference(Branch.of("branch3", defaultBranch.getHash()))
        .create();

    soft.assertThat(branchesStream(nessieApi, n -> true))
        .map(Branch::getName)
        .containsExactlyInAnyOrder("branch1", "branch2", "branch3", defaultBranch.getName());

    soft.assertThat(branchesStream(nessieApi, n -> n.equals("branch2") || n.equals("branch3")))
        .map(Branch::getName)
        .containsExactlyInAnyOrder("branch2", "branch3");

    soft.assertThat(branchesStream(nessieApi, n -> n.equals("branch2")))
        .map(Branch::getName)
        .containsExactlyInAnyOrder("branch2");
  }

  @Test
  public void testCollectMissingNamespaceKeys() throws Exception {
    Branch defaultBranch = nessieApi.getDefaultBranch();
    nessieApi
        .createReference()
        .sourceRefName(defaultBranch.getName())
        .reference(Branch.of("branch", defaultBranch.getHash()))
        .create();

    Branch head =
        nessieApi
            .commitMultipleOperations()
            .commitMeta(fromMessage("foo"))
            .branchName("branch")
            .operation(
                Put.of(ContentKey.of("a", "b", "Table"), IcebergTable.of("meta1", 1, 2, 3, 4)))
            .operation(
                Put.of(ContentKey.of("a", "b", "Data"), IcebergTable.of("meta2", 1, 2, 3, 4)))
            .operation(Put.of(ContentKey.of("Data"), IcebergTable.of("meta3", 1, 2, 3, 4)))
            .operation(Put.of(ContentKey.of("x", "Data"), IcebergTable.of("meta3", 1, 2, 3, 4)))
            .commit();

    soft.assertThat(collectMissingNamespaceKeys(nessieApi, head))
        .containsExactlyInAnyOrder(ContentKey.of("a"), ContentKey.of("a", "b"), ContentKey.of("x"));

    head =
        nessieApi
            .commitMultipleOperations()
            .branch(head)
            .commitMeta(fromMessage("a"))
            .operation(Put.of(ContentKey.of("a"), Namespace.of("a")))
            .commit();

    soft.assertThat(collectMissingNamespaceKeys(nessieApi, head))
        .containsExactlyInAnyOrder(ContentKey.of("a", "b"), ContentKey.of("x"));

    head =
        nessieApi
            .commitMultipleOperations()
            .branch(head)
            .commitMeta(fromMessage("a.b"))
            .operation(Put.of(ContentKey.of("a", "b"), Namespace.of("a", "b")))
            .commit();

    soft.assertThat(collectMissingNamespaceKeys(nessieApi, head))
        .containsExactly(ContentKey.of("x"));

    head =
        nessieApi
            .commitMultipleOperations()
            .branch(head)
            .commitMeta(fromMessage("x"))
            .operation(Put.of(ContentKey.of("x"), Namespace.of("x")))
            .commit();

    soft.assertThat(collectMissingNamespaceKeys(nessieApi, head)).isEmpty();
  }

  @Test
  public void testCommitCreateNamespaces() throws Exception {
    Branch defaultBranch = nessieApi.getDefaultBranch();

    commitCreateNamespaces(
        nessieApi,
        defaultBranch,
        asList(
            ContentKey.of("a"),
            ContentKey.of("a", "b"),
            ContentKey.of("x"),
            ContentKey.of("x", "y")));

    soft.assertThat(nessieApi.getEntries().refName(defaultBranch.getName()).stream())
        .extracting(EntriesResponse.Entry::getName, EntriesResponse.Entry::getType)
        .containsExactlyInAnyOrder(
            tuple(ContentKey.of("a"), NAMESPACE),
            tuple(ContentKey.of("a", "b"), NAMESPACE),
            tuple(ContentKey.of("x"), NAMESPACE),
            tuple(ContentKey.of("x", "y"), NAMESPACE));
  }
}
