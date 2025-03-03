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
package org.projectnessie.versioned.storage.dynamodb;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import org.projectnessie.versioned.storage.testextension.BackendTestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDBBackendTestFactory implements BackendTestFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDBBackendTestFactory.class);
  public static final int DYNAMODB_PORT = 8000;

  private GenericContainer<?> container;
  private String endpointURI;

  @Override
  public DynamoDBBackend createNewBackend() {
    return new DynamoDBBackend(buildNewClient(), true);
  }

  @VisibleForTesting
  DynamoDbClient buildNewClient() {
    return DynamoClientProducer.builder()
        .endpointURI(endpointURI)
        .region("US_WEST_2")
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("xxx", "xxx")))
        .build()
        .createClient();
  }

  @SuppressWarnings("resource")
  private void startDynamo(Optional<String> containerNetworkId) {
    if (container != null) {
      throw new IllegalStateException("Already started");
    }

    String version = System.getProperty("it.nessie.container.dynamodb-local.tag", "latest");
    String imageName = "amazon/dynamodb-local:" + version;

    for (int retry = 0; ; retry++) {
      GenericContainer<?> c =
          new GenericContainer<>(imageName)
              .withLogConsumer(new Slf4jLogConsumer(LOGGER))
              .withExposedPorts(DYNAMODB_PORT)
              .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb");
      containerNetworkId.ifPresent(c::withNetworkMode);
      try {
        c.start();
        container = c;
        break;
      } catch (ContainerLaunchException e) {
        c.close();
        if (e.getCause() != null && retry < 3) {
          LOGGER.warn("Launch of container {} failed, will retry...", c.getDockerImageName(), e);
          continue;
        }
        LOGGER.error("Launch of container {} failed", c.getDockerImageName(), e);
        throw new RuntimeException(e);
      }
    }

    Integer port = containerNetworkId.isPresent() ? DYNAMODB_PORT : container.getFirstMappedPort();
    String host =
        containerNetworkId.isPresent()
            ? container.getCurrentContainerInfo().getConfig().getHostName()
            : container.getHost();

    endpointURI = String.format("http://%s:%d", host, port);
  }

  @Override
  public void start() {
    startDynamo(Optional.empty());
  }

  @Override
  public void stop() {
    try {
      if (container != null) {
        container.stop();
      }
    } finally {
      container = null;
    }
  }
}
