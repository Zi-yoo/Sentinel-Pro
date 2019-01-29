/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.node;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

/**
 * Test cases for {@link ClusterNode}.
 *
 * @author cdfive
 * @date 2019-01-11
 */
public class ClusterNodeTest {

    @Test
    public void testGetOrCreateOriginNodeSingleThread() {
        ClusterNode clusterNode = new ClusterNode();

        String origin1 = "origin1";
        Node originNode1 = clusterNode.getOrCreateOriginNode(origin1);
        assertNotNull(originNode1);
        assertEquals(1, clusterNode.getOriginCountMap().size());

        String origin2 = "origin2";
        Node originNode2 = clusterNode.getOrCreateOriginNode(origin2);
        assertNotNull(originNode2);
        assertEquals(2, clusterNode.getOriginCountMap().size());
        assertNotSame(originNode1, originNode2);

        // test same origin, no StatisticNode added into the originCountMap
        Node tmpOriginNode = clusterNode.getOrCreateOriginNode(origin1);
        assertEquals(2, clusterNode.getOriginCountMap().size());
        assertSame(tmpOriginNode, originNode1);

        assertTrue(clusterNode.getOriginCountMap().containsKey(origin1));
        assertTrue(clusterNode.getOriginCountMap().containsKey(origin2));
    }

    @Test
    public void testGetOrCreateOriginNodeMultiThread() {
        // note: in junit4, repeat execute a test method is not very convenient
        // for simple, here use a loop instead
        // https://stackoverflow.com/questions/1492856/easy-way-of-running-the-same-junit-test-over-and-over
        // in junit5, use @RepeatedTest(10)
        int testTimes = 10;// execute 10 times, test will have chance to failed, if remove the lock in ClusterNode
        for (int times = 0; times < testTimes; times++) {
            final ClusterNode clusterNode = new ClusterNode();

            // store all distinct nodes by calling ClusterNode#getOrCreateOriginNode
            final Set<Node> createdNodes = new HashSet<Node>();

            final Random random = new Random();

            // 10 threads, 3 origins, 20 tasks(in total, calling 20 times of ClusterNode#getOrCreateOriginNode concurrently)
            final ExecutorService es = Executors.newFixedThreadPool(10);
            final List<String> origins = Arrays.asList("origin1", "origin2", "origin3");
            int taskCount = 20;

            List<Callable<Object>> tasks = new ArrayList<Callable<Object>>(taskCount);
            for (int i = 0; i < taskCount; i++) {
                tasks.add(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        // one task call one times of ClusterNode#getOrCreateOriginNode
                        Node node = clusterNode.getOrCreateOriginNode(origins.get(random.nextInt(origins.size())));
                        // add the result node to the createdNodes set
                        // node: since HashSet is non-threadsafe, synchronized the ClusterNodeTest.class
                        synchronized (ClusterNodeTest.class) {
                            createdNodes.add(node);
                        }
                        return null;
                    }
                });
            }

            try {
                es.invokeAll(tasks);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            es.shutdown();

            // origins.size() origins, the same count as the originCountMap
            assertEquals(origins.size(), clusterNode.getOriginCountMap().size());

            // not use `assertEquals(origins.size(), createdNodes.size());`, but a compare judgement for debug
            if (origins.size() != createdNodes.size()) {
                // for debug, we can add a breakpoint here to see the detail info of createdNodes, if remove the lock in ClusterNode
                fail("originCountMap's size should " + origins.size() + ", but actual " + createdNodes.size());
            }

            // verify originCountMap's key
            for (String origin : origins) {
                assertTrue(clusterNode.getOriginCountMap().containsKey(origin));
            }
        }
    }


    @Test
    public void testTrace() {
        ClusterNode clusterNode = new ClusterNode();

        Exception exception = new RuntimeException("test");

        // test count<=0, no exceptionQps added
        clusterNode.trace(exception, 0);
        clusterNode.trace(exception, -1);
        assertEquals(0, clusterNode.exceptionQps());
        assertEquals(0, clusterNode.totalException());

        // test count=1, not BlockException, 1 exceptionQps added
        clusterNode.trace(exception, 1);
        assertEquals(1, clusterNode.exceptionQps());
        assertEquals(1, clusterNode.totalException());

        // test count=1, BlockException, no exceptionQps added
        FlowException flowException = new FlowException("flow");
        clusterNode.trace(flowException, 1);
        assertEquals(1, clusterNode.exceptionQps());
        assertEquals(1, clusterNode.totalException());
    }
}