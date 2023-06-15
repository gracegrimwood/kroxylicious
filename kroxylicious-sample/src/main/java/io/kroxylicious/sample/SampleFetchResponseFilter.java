/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.kroxylicious.sample;

import org.apache.kafka.common.message.FetchResponseData;
import org.apache.kafka.common.message.ResponseHeaderData;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

import io.kroxylicious.proxy.filter.FetchResponseFilter;
import io.kroxylicious.proxy.filter.KrpcFilterContext;
import io.kroxylicious.sample.config.SampleFilterConfig;
import io.kroxylicious.sample.util.SampleFilterTransformer;

/**
 * A sample FetchResponseFilter implementation, intended to demonstrate how custom filters work with
 * Kroxylicious.<br />
 * <br/>
 * This filter transforms the topic data sent by a Kafka broker in response to a fetch request sent by a
 * Kafka consumer, by replacing all occurrences of the String "bar" with the String "baz". These strings are
 * configurable in the config file, so you could substitute this with any text you want.<br/>
 * <br/>
 * An example of a use case where this might be applicable is when producers are sending data to Kafka
 * using different formats from what consumers are expecting. You could configure this filter to transform
 * the data sent by Kafka to the consumers into the format they expect. In this example use case, the filter
 * could be further modified to apply different transformations to different topics, or when sending to
 * particular consumers.
 */
public class SampleFetchResponseFilter implements FetchResponseFilter {

    private final SampleFilterConfig config;
    private final Timer timer;

    public SampleFetchResponseFilter(SampleFilterConfig config) {
        this.config = config;
        this.timer = Timer
                .builder("sample_fetch_response_filter_transform")
                .description("Time taken for the SampleFetchResponseFilter to transform the produce data.")
                .tag("filter", "SampleFetchResponseFilter")
                .register(Metrics.globalRegistry);
    }

    /**
     * Handle the given response, transforming the data in-place according to the configuration, returning
     * the FetchResponseData instance to be passed to the next filter.
     * @param apiVersion the apiVersion of the response
     * @param header response header.
     * @param response The KRPC message to handle.
     * @param context The context.
     */
    @Override
    public void onFetchResponse(short apiVersion, ResponseHeaderData header, FetchResponseData response, KrpcFilterContext context) {
        this.timer.record(() -> applyTransformation(response, context)); // We're timing this to report how long it takes through Micrometer
        context.forwardResponse(header, response);
    }

    /**
     * Applies the transformation to the response data.
     * @param response the response to be transformed
     * @param context the context
     */
    private void applyTransformation(FetchResponseData response, KrpcFilterContext context) {
        response.responses().forEach(responseData -> {
            for (FetchResponseData.PartitionData partitionData : responseData.partitions()) {
                SampleFilterTransformer.transform(partitionData, context, this.config);
            }
        });
    }
}
