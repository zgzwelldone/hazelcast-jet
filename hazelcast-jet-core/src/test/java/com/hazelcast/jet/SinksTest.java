/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.jet;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.stream.IStreamMap;
import com.hazelcast.map.AbstractEntryProcessor;
import com.hazelcast.nio.Address;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.hazelcast.jet.Util.entry;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SinksTest extends PipelineTestSupport {
    private static HazelcastInstance hz;
    private static ClientConfig clientConfig;

    @BeforeClass
    public static void setUp() throws Exception {
        hz = Hazelcast.newHazelcastInstance();
        Hazelcast.newHazelcastInstance();

        clientConfig = new ClientConfig();
        clientConfig.getGroupConfig().setName("dev");
        clientConfig.getGroupConfig().setPassword("dev-pass");
        Address address = hz.getCluster().getLocalMember().getAddress();
        clientConfig.getNetworkConfig().addAddress(address.getHost() + ':' + address.getPort());
    }


    @AfterClass
    public static void after() throws Exception {
        Hazelcast.shutdownAll();
    }

    @Test
    public void map() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        putToSrcMap(input);

        // When
        pipeline.drawFrom(Sources.map(srcName))
                .drainTo(Sinks.map(sinkName));
        execute();

        // Then
        List<Entry<String, Integer>> expected = input.stream()
                                                     .map(i -> entry(String.valueOf(i), i))
                                                     .collect(toList());
        Set<Entry<Object, Object>> actual = jet().getMap(sinkName).entrySet();
        assertEquals(expected.size(), actual.size());
        expected.forEach(entry -> assertTrue(actual.contains(entry)));
    }


    @Test
    public void remoteMap() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        putToMap(hz.getMap(srcName), input);

        // When
        pipeline.drawFrom(Sources.remoteMap(srcName, clientConfig))
                .drainTo(Sinks.remoteMap(sinkName, clientConfig));
        execute();

        // Then
        List<Entry<String, Integer>> expected = input.stream()
                                                     .map(i -> entry(String.valueOf(i), i))
                                                     .collect(toList());
        Set<Entry<Object, Object>> actual = hz.getMap(sinkName).entrySet();
        assertEquals(expected.size(), actual.size());
        expected.forEach(entry -> assertTrue(actual.contains(entry)));
    }

    @Test
    public void mapWithMerging() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        putToSrcMap(input);

        // When
        pipeline.drawFrom(Sources.<String, Integer>map(srcName))
                .drainTo(Sinks.mapWithMerging(srcName,
                        Entry::getKey,
                        Entry::getValue,
                        (Integer oldValue, Integer newValue) -> oldValue + newValue));
        execute();

        // Then
        List<Entry<String, Integer>> expected = input.stream()
                                                     .map(i -> entry(String.valueOf(i), i + i))
                                                     .collect(toList());
        Set<Entry<Object, Object>> actual = jet().getMap(srcName).entrySet();
        assertEquals(expected.size(), actual.size());
        expected.forEach(entry -> assertTrue(actual.contains(entry)));
    }

    @Test
    public void mapWithMerging_when_functionReturnsNull_then_keyIsRemoved() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        putToSrcMap(input);

        // When
        pipeline.drawFrom(Sources.<String, Integer>map(srcName))
                .drainTo(Sinks.mapWithMerging(srcName, (Integer oldValue, Integer newValue) -> null));
        execute();

        // Then
        Set<Entry<Object, Object>> actual = jet().getMap(srcName).entrySet();
        assertEquals(0, actual.size());
    }

    @Test
    public void mapWithMerging_when_sameKeyMerged_then_returnSum() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        jet().getList(srcName).addAll(input);

        // When
        pipeline.drawFrom(Sources.<Integer>list(srcName))
                .map(e -> entry("listSum", e))
                .drainTo(Sinks.<Entry<String, Integer>, Integer>mapWithMerging(srcName,
                        (oldValue, newValue) -> oldValue + newValue));
        execute();

        // Then
        IStreamMap<Object, Object> actual = jet().getMap(srcName);
        assertEquals(1, actual.size());
        assertEquals(((ITEM_COUNT - 1) * ITEM_COUNT) / 2, actual.get("listSum"));
    }


    @Test
    public void remoteMapWithMerging() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        putToMap(hz.getMap(srcName), input);

        // When
        pipeline.drawFrom(Sources.<String, Integer>remoteMap(srcName, clientConfig))
                .drainTo(Sinks.remoteMapWithMerging(srcName, clientConfig,
                        Entry::getKey,
                        Entry::getValue,
                        (Integer oldValue, Integer newValue) -> oldValue + newValue));
        execute();

        // Then
        List<Entry<String, Integer>> expected = input.stream()
                                                     .map(i -> entry(String.valueOf(i), i + i))
                                                     .collect(toList());
        Set<Entry<Object, Object>> actual = hz.getMap(srcName).entrySet();
        assertEquals(expected.size(), actual.size());
        expected.forEach(entry -> assertTrue(actual.contains(entry)));
    }

    @Test
    public void remoteMapWithMerging_when_functionReturnsNull_then_keyIsRemoved() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        putToMap(hz.getMap(srcName), input);

        // When
        pipeline.drawFrom(Sources.<String, Integer>remoteMap(srcName, clientConfig))
                .drainTo(Sinks.remoteMapWithMerging(srcName, clientConfig,
                        (Integer oldValue, Integer newValue) -> null));
        execute();

        // Then
        Set<Entry<Object, Object>> actual = hz.getMap(srcName).entrySet();
        assertEquals(0, actual.size());
    }


    @Test
    public void mapWithUpdating() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        putToSrcMap(input);

        // When
        pipeline.drawFrom(Sources.<String, Integer>map(srcName))
                .drainTo(Sinks.mapWithUpdating(srcName,
                        Entry::getKey,
                        (Integer value, Entry<String, Integer> item) -> value + 10));
        execute();

        // Then
        List<Entry<String, Integer>> expected = input.stream()
                                                     .map(i -> entry(String.valueOf(i), i + 10))
                                                     .collect(toList());
        Set<Entry<Object, Object>> actual = jet().getMap(srcName).entrySet();
        assertEquals(expected.size(), actual.size());
        expected.forEach(entry -> assertTrue(actual.contains(entry)));
    }

    @Test
    public void mapWithUpdating_when_functionReturnsNull_then_keyIsRemoved() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        putToSrcMap(input);

        // When
        pipeline.drawFrom(Sources.<String, Integer>map(srcName))
                .drainTo(Sinks.mapWithUpdating(srcName,
                        (Integer value, Entry<String, Integer> item) -> null));
        execute();

        // Then
        Set<Entry<Object, Object>> actual = jet().getMap(srcName).entrySet();
        assertEquals(0, actual.size());
    }

    @Test
    public void remoteMapWithUpdating() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        putToMap(hz.getMap(srcName), input);

        // When
        pipeline.drawFrom(Sources.<String, Integer>remoteMap(srcName, clientConfig))
                .drainTo(Sinks.remoteMapWithUpdating(srcName, clientConfig,
                        Entry::getKey,
                        (Integer value, Entry<String, Integer> item) -> value + 10));
        execute();

        // Then
        List<Entry<String, Integer>> expected = input.stream()
                                                     .map(i -> entry(String.valueOf(i), i + 10))
                                                     .collect(toList());
        Set<Entry<Object, Object>> actual = hz.getMap(srcName).entrySet();
        assertEquals(expected.size(), actual.size());
        expected.forEach(entry -> assertTrue(actual.contains(entry)));
    }

    @Test
    public void remoteMapWithUpdating_when_functionReturnsNull_then_keyIsRemoved() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        putToMap(hz.getMap(srcName), input);

        // When
        pipeline.drawFrom(Sources.<String, Integer>remoteMap(srcName, clientConfig))
                .drainTo(Sinks.remoteMapWithUpdating(srcName, clientConfig,
                        (Integer value, Entry<String, Integer> item) -> null));
        execute();

        // Then
        Set<Entry<Object, Object>> actual = hz.getMap(srcName).entrySet();
        assertEquals(0, actual.size());
    }

    @Test
    public void mapWithEntryProcessor() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        putToSrcMap(input);

        // When
        pipeline.drawFrom(Sources.<String, Integer>map(srcName))
                .drainTo(Sinks.mapWithEntryProcessor(srcName, Entry::getKey, entry -> new IncrementEntryProcessor<>(10)));
        execute();

        // Then
        List<Entry<String, Integer>> expected = input.stream()
                                                     .map(i -> entry(String.valueOf(i), i + 10))
                                                     .collect(toList());
        Set<Entry<Object, Object>> actual = jet().getMap(srcName).entrySet();
        assertEquals(expected.size(), actual.size());
        expected.forEach(entry -> assertTrue(actual.contains(entry)));
    }

    @Test
    public void remoteMapWithEntryProcessor() {
        // Given
        List<Integer> input = sequence(ITEM_COUNT);
        putToMap(hz.getMap(srcName), input);

        // When
        pipeline.drawFrom(Sources.<String, Integer>remoteMap(srcName, clientConfig))
                .drainTo(Sinks.remoteMapWithEntryProcessor(srcName, clientConfig, Entry::getKey,
                        entry -> new IncrementEntryProcessor<>(10)));
        execute();

        // Then
        List<Entry<String, Integer>> expected = input.stream()
                                                     .map(i -> entry(String.valueOf(i), i + 10))
                                                     .collect(toList());
        Set<Entry<Object, Object>> actual = hz.getMap(srcName).entrySet();
        assertEquals(expected.size(), actual.size());
        expected.forEach(entry -> assertTrue(actual.contains(entry)));

    }

    private static class IncrementEntryProcessor<K> extends AbstractEntryProcessor<K, Integer> {

        private Integer value;

        IncrementEntryProcessor(Integer value) {
            this.value = value;
        }


        @Override
        public Object process(Entry<K, Integer> entry) {
            entry.setValue(entry.getValue() == null ? value : entry.getValue() + value);
            return null;
        }
    }

}
