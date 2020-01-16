/*
 *  Copyright 2017, Yahoo Inc.
 *  Licensed under the terms of the Apache License, Version 2.0.
 *  See the LICENSE file associated with the project for terms.
 */
package com.yahoo.bullet.storm.drpc;

import com.yahoo.bullet.common.Config;
import com.yahoo.bullet.pubsub.PubSub;
import com.yahoo.bullet.pubsub.Publisher;
import com.yahoo.bullet.pubsub.Subscriber;
import com.yahoo.bullet.storm.testing.CustomTopologyContext;
import org.apache.storm.LocalDRPC;
import org.apache.storm.utils.DRPCClient;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

public class DRPCPubSubTest {
    private DRPCConfig config;
    private DRPCClient.LocalOverride override;

    @BeforeClass
    public void bootstrap() {
        // This will force the DRPCSpout to work in local mode and prevent the DRPCQuerySubscriber from killing
        // DRPCSpout Adder threads because DRPCSpout.open will not be called.
        override = new DRPCClient.LocalOverride(new LocalDRPC());
    }

    @AfterClass
    public void destroy() throws Exception {
        override.close();
    }

    @BeforeMethod
    public void setup() {
        config = new DRPCConfig("test_drpc_config.yaml");

        // 1 task for the component named "foo" with task index 0
        CustomTopologyContext context = new CustomTopologyContext(Collections.singletonList(1), "foo", 0);
        config.set(DRPCConfig.STORM_CONTEXT, context);

        Map stormConfig = new Config("test_storm_config.yaml").getAll(Optional.empty());
        config.set(DRPCConfig.STORM_CONFIG, stormConfig);
    }

    @Test
    public void testCreation() throws Exception {
        DRPCPubSub pubSub = new DRPCPubSub(config);
        Assert.assertNotNull(pubSub);
    }

    @Test
    public void testQueryProcessingSingleInstanceTypes() throws Exception {
        config.set(DRPCConfig.PUBSUB_CONTEXT_NAME, PubSub.Context.QUERY_PROCESSING.name());
        DRPCPubSub pubSub = new DRPCPubSub(config);

        Publisher publisher = pubSub.getPublisher();
        Subscriber subscriber = pubSub.getSubscriber();

        Assert.assertTrue(publisher instanceof DRPCResultPublisher);
        Assert.assertTrue(subscriber instanceof DRPCQuerySubscriber);
    }

    @Test
    public void testQueryProcessingMultipleInstancesTypes() throws Exception {
        config.set(DRPCConfig.PUBSUB_CONTEXT_NAME, PubSub.Context.QUERY_PROCESSING.name());
        DRPCPubSub pubSub = new DRPCPubSub(config);

        List<Publisher> publishers = pubSub.getPublishers(2);
        List<Subscriber> subscribers = pubSub.getSubscribers(4);

        Assert.assertNotNull(publishers);
        Assert.assertNotNull(subscribers);

        Assert.assertEquals(publishers.size(), 2);
        publishers.forEach(p -> Assert.assertTrue(p instanceof DRPCResultPublisher));

        Assert.assertEquals(subscribers.size(), 4);
        subscribers.forEach(s -> Assert.assertTrue(s instanceof DRPCQuerySubscriber));
    }

    @Test
    public void testQuerySubmissionSingleInstanceTypes() throws Exception {
        config.set(DRPCConfig.PUBSUB_CONTEXT_NAME, PubSub.Context.QUERY_SUBMISSION.name());
        DRPCPubSub pubSub = new DRPCPubSub(config);

        Publisher publisher = pubSub.getPublisher();
        Subscriber subscriber = pubSub.getSubscriber();

        Assert.assertTrue(publisher instanceof DRPCQueryResultPubscriber);
        Assert.assertTrue(subscriber instanceof DRPCQueryResultPubscriber);
    }

    @Test
    public void testQuerySubmissionOneInstanceIsTheSameInstance() throws Exception {
        config.set(DRPCConfig.PUBSUB_CONTEXT_NAME, PubSub.Context.QUERY_SUBMISSION.name());
        DRPCPubSub pubSub = new DRPCPubSub(config);

        Publisher publisher = pubSub.getPublisher();
        Subscriber subscriber = pubSub.getSubscriber();

        Assert.assertSame(publisher, subscriber);

        // All future calls just return the same instance
        Assert.assertSame(publisher, pubSub.getPublisher());
        Assert.assertSame(subscriber, pubSub.getSubscriber());
        Assert.assertSame(pubSub.getPublisher(), pubSub.getSubscriber());

        // So do calls to get multiples after
        List<Publisher> publishers = pubSub.getPublishers(42);
        List<Subscriber> subscribers = pubSub.getSubscribers(20);

        Assert.assertEquals(publishers.size(), 1);
        Assert.assertEquals(subscribers.size(), 1);
        Assert.assertEquals(publishers.get(0), publisher);
        Assert.assertEquals(subscribers.get(0), subscriber);
    }

    @Test
    public void testQuerySubmissionMultipleInstancesAreTheSameInstances() throws Exception {
        config.set(DRPCConfig.PUBSUB_CONTEXT_NAME, PubSub.Context.QUERY_SUBMISSION.name());
        DRPCPubSub pubSub = new DRPCPubSub(config);

        List<Publisher> publishers = pubSub.getPublishers(10);
        List<Subscriber> subscribers = pubSub.getSubscribers(20);

        Assert.assertEquals(publishers.size(), 10);
        // If we ask for more, the size is the same as the first call
        Assert.assertEquals(subscribers.size(), 10);
        // If we ask for less, the size is the same as the first call
        Assert.assertEquals(pubSub.getSubscribers(1).size(), 10);
        // If we ask for the same, the size is the same as the first call
        Assert.assertEquals(pubSub.getSubscribers(10).size(), 10);

        // The corresponding items are the same
        IntStream.range(0, 9).forEach(i -> Assert.assertSame(publishers.get(i), subscribers.get(i)));

        Publisher publisher = pubSub.getPublisher();
        Subscriber subscriber = pubSub.getSubscriber();

        // If you ask for one, it's the first one in the list
        Assert.assertSame(publisher, subscriber);
        Assert.assertSame(publishers.get(0), publisher);
    }
}
