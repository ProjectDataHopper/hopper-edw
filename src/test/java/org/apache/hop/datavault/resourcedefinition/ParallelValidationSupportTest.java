/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.Test;

class ParallelValidationSupportTest {

  @Test
  void resolveParallelism_clampsAndDefaults() {
    assertEquals(8, ParallelValidationSupport.resolveParallelism(null, 8));
    assertEquals(8, ParallelValidationSupport.resolveParallelism("", 8));
    assertEquals(8, ParallelValidationSupport.resolveParallelism("not-a-number", 8));
    assertEquals(1, ParallelValidationSupport.resolveParallelism("0", 8));
    assertEquals(1, ParallelValidationSupport.resolveParallelism("-3", 8));
    assertEquals(4, ParallelValidationSupport.resolveParallelism("4", 8));
    assertEquals(
        ParallelValidationSupport.MAX_PARALLELISM,
        ParallelValidationSupport.resolveParallelism("999", 8));
    assertEquals(8, ParallelValidationSupport.resolveParallelism(0));
    assertEquals(16, ParallelValidationSupport.resolveParallelism(16));
  }

  @Test
  void map_preservesOrderWithParallelism() throws Exception {
    List<Integer> inputs = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    List<String> results =
        ParallelValidationSupport.map(
            4,
            inputs,
            (item, index) -> {
              Thread.sleep(5 + (item % 3) * 3L);
              return "v" + item;
            });
    assertEquals(10, results.size());
    for (int i = 0; i < 10; i++) {
      assertEquals("v" + i, results.get(i));
    }
  }

  @Test
  void map_serialPathForParallelismOne() throws Exception {
    List<Integer> inputs = List.of(1, 2, 3);
    List<Integer> results = ParallelValidationSupport.map(1, inputs, (item, index) -> item * 10);
    assertEquals(List.of(10, 20, 30), results);
  }

  @Test
  void map_runsConcurrently() throws Exception {
    int n = 8;
    List<Integer> inputs = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      inputs.add(i);
    }
    ConcurrentHashMap<Long, Boolean> threads = new ConcurrentHashMap<>();
    AtomicInteger maxInFlight = new AtomicInteger();
    AtomicInteger inFlight = new AtomicInteger();

    ParallelValidationSupport.map(
        4,
        inputs,
        (item, index) -> {
          threads.put(Thread.currentThread().threadId(), true);
          int now = inFlight.incrementAndGet();
          maxInFlight.updateAndGet(prev -> Math.max(prev, now));
          try {
            Thread.sleep(40);
          } finally {
            inFlight.decrementAndGet();
          }
          return item;
        });

    assertTrue(threads.size() > 1, "expected multiple worker threads, got " + threads.size());
    assertTrue(maxInFlight.get() > 1, "expected concurrent in-flight work");
  }

  @Test
  void map_progressCallbackSeesAllCompletions() throws Exception {
    List<Integer> inputs = List.of(1, 2, 3, 4);
    AtomicInteger last = new AtomicInteger();
    ParallelValidationSupport.map(2, inputs, (item, index) -> item, done -> last.set(done), false);
    assertEquals(4, last.get());
  }

  @Test
  void map_failFastRethrows() {
    List<Integer> inputs = List.of(1, 2, 3);
    assertThrows(
        HopException.class,
        () ->
            ParallelValidationSupport.map(
                2,
                inputs,
                (item, index) -> {
                  if (item == 2) {
                    throw new HopException("boom");
                  }
                  return item;
                },
                null,
                true));
  }
}
