/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink;

import java.awt.*;
import java.nio.file.Path;

import org.junit.experimental.theories.FromDataPoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.shaded.com.github.dockerjava.core.dockerfile.Dockerfile;
import org.testcontainers.utility.DockerImageName;

/**
 * A Java program that starts a few containerized processes using {@link Testcontainers}:
 *
 * <ul>
 *   <li>A set of StateFun processes, including a manager and a worker.
 *   <li>A Kafka broker, to be used as an ingress and egress for our User Greeter application.
 *   <li>A single-threaded Kafka producer that writes mock user login events (JSON messages) to the
 *       ingress Kafka topic.
 * </ul>
 *
 * <p>This program is intended to run side-by-side against functions that are served by the {@link
 * TestBedAppServer} program.
 *
 */
public final class StatefulFunctionsRuntimeProcesses {

  private static final Logger LOG =
      LoggerFactory.getLogger(StatefulFunctionsRuntimeProcesses.class);

  private static final Network NETWORK = Network.newNetwork();

  protected static final KafkaContainer KAFKA = kafkaContainer(NETWORK);
  private static final GenericContainer<?> STATEFUN_MANAGER = managerContainer(NETWORK);
  protected static final GenericContainer<?> STATEFUN_WORKER =
      workerContainer(NETWORK).dependsOn(STATEFUN_MANAGER, KAFKA);

  private static final GenericContainer<?> KAFKA_JSON_PRODUCER_ONE =
            ProducerOneContainer(NETWORK).dependsOn(STATEFUN_WORKER, KAFKA);

  private static final GenericContainer<?> KAFKA_JSON_PRODUCER_TWO =
            ProducerTwoContainer(NETWORK).dependsOn(STATEFUN_WORKER, KAFKA);

  public static void main(String[] args) throws Exception {
    try {
      KAFKA.start();
      STATEFUN_MANAGER.start();

      Testcontainers.exposeHostPorts(TestBedAppServer.PORT);
      STATEFUN_WORKER.start();

//      KAFKA_JSON_PRODUCER_ONE.start();
//      KAFKA_JSON_PRODUCER_TWO.start();

      sleep();
    } finally {
//      KAFKA_JSON_PRODUCER_ONE.stop();
//      KAFKA_JSON_PRODUCER_TWO.stop();

      STATEFUN_WORKER.stop();
      STATEFUN_MANAGER.stop();
      KAFKA.stop();
    }
  }

  private static KafkaContainer kafkaContainer(Network network) {
    return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
        .withNetwork(network)
        .withNetworkAliases("kafka");
  }

//  private static GenericContainer<?> managerContainer(Network network) {
//    return new GenericContainer<>(DockerImageName.parse("apache/flink-statefun:3.2.0"))
//        .withNetwork(network)
//        .withNetworkAliases("statefun-manager")
//        .withEnv("ROLE", "master")
//        .withEnv("MASTER_HOST", "statefun-manager")
//        .withExposedPorts(8081)
//        .withLogConsumer(new Slf4jLogConsumer(LOG))
//        .withClasspathResourceMapping(
//            "module.yaml", "/opt/statefun/modules/greeter/module.yaml", BindMode.READ_ONLY);
//  }

  private static GenericContainer<?> managerContainer(Network network) {
    ImageFromDockerfile img = new ImageFromDockerfile().
            withFileFromPath(".", Path.of("/Users/jerry/projects/statefun/flink-statefun-docker/3.2.0-java11"));

    return new GenericContainer<>(img)
            .withNetwork(network)
            .withNetworkAliases("statefun-manager")
            .withEnv("ROLE", "master")
            .withEnv("MASTER_HOST", "statefun-manager")
            .withExposedPorts(8081)
            .withLogConsumer(new Slf4jLogConsumer(LOG))
            .withClasspathResourceMapping(
                    "module.yaml", "/opt/statefun/modules/greeter/module.yaml", BindMode.READ_ONLY);
  }

  private static GenericContainer<?> workerContainer(Network network) {
    return new GenericContainer<>(DockerImageName.parse("apache/flink-statefun:3.2.0"))
        .withNetwork(network)
        .withNetworkAliases("statefun-worker")
        .withEnv("ROLE", "worker")
        .withEnv("MASTER_HOST", "statefun-manager")
        .withClasspathResourceMapping(
            "module.yaml", "/opt/statefun/modules/greeter/module.yaml", BindMode.READ_ONLY);
  }

  private static GenericContainer<?> ProducerOneContainer(Network network) {
    return new GenericContainer<>(
            DockerImageName.parse("ververica/statefun-playground-producer:latest"))
            .withNetwork(network)
            .withClasspathResourceMapping(
                    "text1.txt", "/opt/statefun/text1.txt", BindMode.READ_ONLY)
            .withEnv("APP_PATH", "/opt/statefun/text1.txt")
            .withEnv("APP_KAFKA_HOST", "kafka:9092")
            .withEnv("APP_KAFKA_TOPIC", "word-frequency")
            .withEnv("APP_JSON_PATH", "text");
  }

  private static GenericContainer<?> ProducerTwoContainer(Network network) {
    return new GenericContainer<>(
            DockerImageName.parse("ververica/statefun-playground-producer:latest"))
            .withNetwork(network)
            .withClasspathResourceMapping(
                    "text2.txt", "/opt/statefun/text2.txt", BindMode.READ_ONLY)
            .withEnv("APP_PATH", "/opt/statefun/text2.txt")
            .withEnv("APP_KAFKA_HOST", "kafka:9092")
            .withEnv("APP_KAFKA_TOPIC", "word-frequency")
            .withEnv("APP_JSON_PATH", "text");
  }

  private static void sleep() throws Exception {
    while (true) {
      Thread.sleep(10000);
    }
  }
}
