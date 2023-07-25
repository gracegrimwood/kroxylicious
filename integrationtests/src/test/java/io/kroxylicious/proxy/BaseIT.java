/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.kroxylicious.proxy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicCollection;
import org.junit.jupiter.api.extension.ExtendWith;

import io.kroxylicious.test.tester.KroxyliciousTester;
import io.kroxylicious.testing.kafka.junit5ext.KafkaClusterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(KafkaClusterExtension.class)
public abstract class BaseIT {

    protected static CreateTopicsResult createTopics(Admin admin, List<NewTopic> topics) {
        try {
            var created = admin.createTopics(topics);
            assertEquals(topics.size(), created.values().size());
            created.all().get(10, TimeUnit.SECONDS);
            return created;
        }
        catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
        catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    protected static DeleteTopicsResult deleteTopics(Admin admin, TopicCollection topics) {
        try {
            var deleted = admin.deleteTopics(topics);
            deleted.all().get(10, TimeUnit.SECONDS);
            return deleted;
        }
        catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
        catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    protected static Map<String, Object> buildClientConfig(Map<String, Object>... configs) {
        Map<String, Object> clientConfig = new HashMap<>();
        for (var config : configs) {
            clientConfig.putAll(config);
        }
        return clientConfig;
    }

    protected static Consumer<String, String> getConsumerWithConfig(KroxyliciousTester tester, Optional<String> virtualCluster, Map<String, Object>... configs) {
        var consumerConfig = buildClientConfig(configs);
        if (virtualCluster.isPresent()) {
            return tester.consumer(virtualCluster.get(), consumerConfig);
        }
        return tester.consumer(consumerConfig);
    }

    protected static Producer<String, String> getProducerWithConfig(KroxyliciousTester tester, Optional<String> virtualCluster, Map<String, Object>... configs) {
        var producerConfig = buildClientConfig(configs);
        if (virtualCluster.isPresent()) {
            return tester.producer(virtualCluster.get(), producerConfig);
        }
        return tester.producer(producerConfig);
    }
}
