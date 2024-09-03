/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.kroxylicious.test;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.message.ConsumerGroupDescribeRequestData;
import org.apache.kafka.common.message.DescribeGroupsRequestData;
import org.apache.kafka.common.message.LeaveGroupRequestData;
import org.apache.kafka.common.message.ListOffsetsRequestData;
import org.apache.kafka.common.message.MetadataRequestData;
import org.apache.kafka.common.message.OffsetFetchRequestData;
import org.apache.kafka.common.message.ProduceRequestData;
import org.apache.kafka.common.message.UpdateMetadataRequestData;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.ApiMessage;

import io.kroxylicious.test.record.RecordTestUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import info.schnatterer.mobynamesgenerator.MobyNamesGenerator;

public class RequestFactory {

    private static final short ACKS_ALL = (short) -1;
    // The special cases generally report errors on a per-entry basis rather than globally and thus need to build requests by hand
    // Hopefully they go away one day as we have a sample generator for each type.
    private static final EnumSet<ApiKeys> SPECIAL_CASES = EnumSet.of(ApiKeys.DELETE_GROUPS,
            ApiKeys.OFFSET_COMMIT, ApiKeys.CREATE_TOPICS, ApiKeys.DELETE_TOPICS, ApiKeys.DELETE_RECORDS, ApiKeys.INIT_PRODUCER_ID, ApiKeys.CREATE_ACLS,
            ApiKeys.DESCRIBE_ACLS, ApiKeys.DELETE_ACLS, ApiKeys.OFFSET_FOR_LEADER_EPOCH, ApiKeys.ELECT_LEADERS, ApiKeys.ADD_PARTITIONS_TO_TXN, ApiKeys.WRITE_TXN_MARKERS,
            ApiKeys.TXN_OFFSET_COMMIT, ApiKeys.DESCRIBE_CONFIGS, ApiKeys.ALTER_CONFIGS, ApiKeys.INCREMENTAL_ALTER_CONFIGS, ApiKeys.ALTER_REPLICA_LOG_DIRS,
            ApiKeys.CREATE_PARTITIONS, ApiKeys.ALTER_CLIENT_QUOTAS, ApiKeys.DESCRIBE_USER_SCRAM_CREDENTIALS, ApiKeys.ALTER_USER_SCRAM_CREDENTIALS,
            ApiKeys.DESCRIBE_PRODUCERS, ApiKeys.DESCRIBE_TRANSACTIONS, ApiKeys.DESCRIBE_TOPIC_PARTITIONS);

    private static final Map<ApiKeys, Consumer<ApiMessage>> messagePopulators = Map.of(
            ApiKeys.PRODUCE, RequestFactory::populateProduceRequest,
            ApiKeys.LIST_OFFSETS, RequestFactory::populateListOffsetsRequest,
            ApiKeys.OFFSET_FETCH, RequestFactory::populateOffsetFetchRequest,
            ApiKeys.METADATA, RequestFactory::populateMetadataRequest,
            ApiKeys.UPDATE_METADATA, RequestFactory::populateUpdateMetadataRequest,
            ApiKeys.LEAVE_GROUP, RequestFactory::populateLeaveGroupRequest,
            ApiKeys.DESCRIBE_GROUPS, RequestFactory::populateDescribeGroupsRequest,
            ApiKeys.CONSUMER_GROUP_DESCRIBE, RequestFactory::populateConsumeGroupDescribeRequest
    );

    private RequestFactory() {
    }

    public static Stream<ApiMessageVersion> apiMessageFor(Function<ApiKeys, Short> versionFunction) {
        return apiMessageFor(versionFunction, EnumSet.complementOf(RequestFactory.SPECIAL_CASES));
    }

    public static Stream<ApiMessageVersion> apiMessageFor(Function<ApiKeys, Short> versionFunction, ApiKeys... apiKeys) {
        return apiMessageFor(versionFunction, EnumSet.copyOf(Arrays.asList(apiKeys)));
    }

    public static Stream<ApiMessageVersion> apiMessageFor(Function<ApiKeys, Short> versionFunction, Set<ApiKeys> apiKeys) {
        return Stream.of(apiKeys)
                .flatMap(Collection::stream)
                .map(apiKey -> {
                    final ApiMessage apiMessage = apiMessageForApiKey(apiKey);
                    final Short apiVersion = versionFunction.apply(apiKey);
                    return new ApiMessageVersion(apiMessage, apiVersion);
                });
    }

    public record ApiMessageVersion(ApiMessage apiMessage, short apiVersion) {}

    private static @NonNull ApiMessage apiMessageForApiKey(ApiKeys apiKey) {
        final ApiMessage apiMessage = apiKey.messageType.newRequest();
        messagePopulators.getOrDefault(apiKey, message -> {
        }).accept(apiMessage);
        return apiMessage;
    }

    private static void populateProduceRequest(ApiMessage apiMessage) {
        final ProduceRequestData produceRequestData = (ProduceRequestData) apiMessage;
        produceRequestData.setAcks(ACKS_ALL);
        final ProduceRequestData.TopicProduceDataCollection v = new ProduceRequestData.TopicProduceDataCollection(1);
        final ProduceRequestData.TopicProduceData topicProduceData = new ProduceRequestData.TopicProduceData();
        final ProduceRequestData.PartitionProduceData produceData = new ProduceRequestData.PartitionProduceData();
        produceData.setRecords(RecordTestUtils.memoryRecords(List.of(RecordTestUtils.record(MobyNamesGenerator.getRandomName()))));
        topicProduceData.setPartitionData(List.of(produceData));
        v.add(topicProduceData);
        produceRequestData.setTopicData(v);
    }

    private static void populateListOffsetsRequest(ApiMessage apiMessage) {
        final ListOffsetsRequestData listOffsetsRequestData = (ListOffsetsRequestData) apiMessage;
        final ListOffsetsRequestData.ListOffsetsPartition p1 = new ListOffsetsRequestData.ListOffsetsPartition();
        p1.setPartitionIndex(0);
        p1.setCurrentLeaderEpoch(1);
        final ListOffsetsRequestData.ListOffsetsTopic listOffsetsTopic = new ListOffsetsRequestData.ListOffsetsTopic();
        listOffsetsTopic.setName(MobyNamesGenerator.getRandomName());
        listOffsetsTopic.setPartitions(List.of(p1));
        listOffsetsRequestData.setReplicaId(-1);
        listOffsetsRequestData.setTopics(List.of(listOffsetsTopic));
    }

    private static void populateOffsetFetchRequest(ApiMessage apiMessage) {
        final OffsetFetchRequestData offsetFetchRequestData = (OffsetFetchRequestData) apiMessage;
        final OffsetFetchRequestData.OffsetFetchRequestTopic t1 = new OffsetFetchRequestData.OffsetFetchRequestTopic();
        t1.setName(MobyNamesGenerator.getRandomName());
        t1.setPartitionIndexes(List.of(0, 1));
        offsetFetchRequestData.setGroupId(MobyNamesGenerator.getRandomName());
        offsetFetchRequestData.setTopics(List.of(t1));
    }

    private static void populateMetadataRequest(ApiMessage apiMessage) {
        final MetadataRequestData metadataRequestData = (MetadataRequestData) apiMessage;
        final MetadataRequestData.MetadataRequestTopic t1 = new MetadataRequestData.MetadataRequestTopic();
        t1.setName(MobyNamesGenerator.getRandomName());
        t1.setTopicId(Uuid.randomUuid());
        metadataRequestData.setTopics(List.of(t1));
    }

    private static void populateUpdateMetadataRequest(ApiMessage apiMessage) {
        final UpdateMetadataRequestData updateMetadataRequestData = (UpdateMetadataRequestData) apiMessage;
        final UpdateMetadataRequestData.UpdateMetadataTopicState t1 = new UpdateMetadataRequestData.UpdateMetadataTopicState();
        t1.setTopicId(Uuid.randomUuid());
        updateMetadataRequestData.setTopicStates(List.of(t1));
    }

    private static void populateLeaveGroupRequest(ApiMessage apiMessage) {
        final LeaveGroupRequestData leaveGroupRequestData = (LeaveGroupRequestData) apiMessage;
        final LeaveGroupRequestData.MemberIdentity memberIdentity = new LeaveGroupRequestData.MemberIdentity();
        memberIdentity.setMemberId(MobyNamesGenerator.getRandomName());
        leaveGroupRequestData.setMembers(List.of(memberIdentity));
    }

    private static void populateDescribeGroupsRequest(ApiMessage apiMessage) {
        final DescribeGroupsRequestData describeGroupsRequestData = (DescribeGroupsRequestData) apiMessage;
        describeGroupsRequestData.setGroups(List.of(MobyNamesGenerator.getRandomName(), MobyNamesGenerator.getRandomName()));
    }

    private static void populateConsumeGroupDescribeRequest(ApiMessage apiMessage) {
        final ConsumerGroupDescribeRequestData consumerGroupDescribeRequestData = (ConsumerGroupDescribeRequestData) apiMessage;
        consumerGroupDescribeRequestData.setGroupIds(List.of(MobyNamesGenerator.getRandomName(), MobyNamesGenerator.getRandomName()));
    }
}
