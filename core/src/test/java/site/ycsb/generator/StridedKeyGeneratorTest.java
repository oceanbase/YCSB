/**
 * Copyright (c) 2017 YCSB contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package site.ycsb.generator;

import org.testng.annotations.Test;

import java.util.*;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests for the StridedKeyGenerator class.
 */
public class StridedKeyGeneratorTest {

  @Test
  public void testAllKeysGenerated() {
    long start = 100;
    long count = 1000;
    StridedKeyGenerator generator = new StridedKeyGenerator(start, count);

    Set<Long> generatedKeys = new HashSet<>();
    for (int i = 0; i < count; i++) {
      Long key = generator.nextValue();
      assertTrue(key >= start && key < start + count,
                 "Key should be in range [" + start + ", " + (start + count - 1) + "]: " + key);
      assertTrue(generatedKeys.add(key), "Key should not be duplicated: " + key);
    }

    assertEquals(generatedKeys.size(), count, "All keys should be generated");
    // Verify all keys in the range are generated
    for (long i = start; i < start + count; i++) {
      assertTrue(generatedKeys.contains(i), "Key " + i + " should be generated");
    }
  }

  @Test
  public void testKeyDistribution() {
    long start = 0;
    long count = 100;
    StridedKeyGenerator generator = new StridedKeyGenerator(start, count);

    // Generate first 20 keys and check if they are distributed
    long[] firstKeys = new long[20];
    for (int i = 0; i < 20; i++) {
      firstKeys[i] = generator.nextValue();
    }

    // Calculate intervals between adjacent keys, should not be consecutive
    boolean hasNonConsecutive = false;
    for (int i = 1; i < firstKeys.length; i++) {
      long diff = Math.abs(firstKeys[i] - firstKeys[i - 1]);
      if (diff > 1) {
        hasNonConsecutive = true;
        break;
      }
    }
    assertTrue(hasNonConsecutive, "Keys should be distributed (not consecutive)");

    // Verify keys are distributed across multiple ranges
    int[] ranges = new int[10]; // Divide 100 keys into 10 ranges
    for (long key : firstKeys) {
      int rangeIndex = (int) ((key - start) / 10);
      ranges[rangeIndex]++;
    }
    // At least multiple ranges should be used
    int usedRanges = 0;
    for (int cnt : ranges) {
      if (cnt > 0) usedRanges++;
    }
    assertTrue(usedRanges > 1, "Keys should be distributed across multiple ranges");
  }

  @Test
  public void testThreadSafety() throws InterruptedException {
    long start = 0;
    long count = 10000;
    StridedKeyGenerator generator = new StridedKeyGenerator(start, count);

    int threadCount = 10;
    Set<Long> allKeys = Collections.synchronizedSet(new HashSet<>());
    List<Thread> threads = new ArrayList<>();

    // Each thread generates count/threadCount keys
    int keysPerThread = (int) (count / threadCount);
    for (int t = 0; t < threadCount; t++) {
      Thread thread = new Thread(() -> {
        for (int i = 0; i < keysPerThread; i++) {
          Long key = generator.nextValue();
          assertTrue(key >= start && key < start + count, "Key should be in range: " + key);
          allKeys.add(key);
        }
      });
      threads.add(thread);
      thread.start();
    }

    // Wait for all threads to complete
    for (Thread thread : threads) {
      thread.join();
    }

    // Verify no duplicates
    assertEquals(allKeys.size(), keysPerThread * threadCount,
                 "No duplicate keys in concurrent access");
  }

  @Test
  public void testSmallCount() {
    long start = 10;
    long count = 5;
    StridedKeyGenerator generator = new StridedKeyGenerator(start, count);

    Set<Long> keys = new HashSet<>();
    for (int i = 0; i < count; i++) {
      keys.add(generator.nextValue());
    }
    assertEquals(keys.size(), count, "All keys should be generated for small count");
  }

  @Test
  public void testPowerOfTwoCount() {
    long start = 0;
    long count = 1024; // 2^10
    StridedKeyGenerator generator = new StridedKeyGenerator(start, count);

    Set<Long> keys = new HashSet<>();
    for (int i = 0; i < count; i++) {
      keys.add(generator.nextValue());
    }
    assertEquals(keys.size(), count, "All keys should be generated for power of two");
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void testExceedCount() {
    long start = 0;
    long count = 10;
    StridedKeyGenerator generator = new StridedKeyGenerator(start, count);

    // Generate count+1 keys, should throw exception
    for (int i = 0; i <= count; i++) {
      generator.nextValue();
    }
  }

  @Test
  public void testDistributionVsCounterGenerator() {
    long start = 0;
    long count = 1000;

    StridedKeyGenerator stridedGen = new StridedKeyGenerator(start, count);
    CounterGenerator counterGen = new CounterGenerator(start);

    // Generate first 100 keys and compare distribution
    long[] stridedKeys = new long[100];
    long[] counterKeys = new long[100];

    for (int i = 0; i < 100; i++) {
      stridedKeys[i] = stridedGen.nextValue();
      counterKeys[i] = counterGen.nextValue();
    }

    // Calculate key range span
    long stridedSpan = Arrays.stream(stridedKeys).max().getAsLong() -
                       Arrays.stream(stridedKeys).min().getAsLong();
    long counterSpan = Arrays.stream(counterKeys).max().getAsLong() -
                       Arrays.stream(counterKeys).min().getAsLong();

    // StridedKeyGenerator should have larger span (more distributed)
    assertTrue(stridedSpan > counterSpan,
               "StridedKeyGenerator should have larger span than CounterGenerator: " +
               "strided=" + stridedSpan + ", counter=" + counterSpan);

    // Calculate standard deviation, StridedKeyGenerator should be larger
    double stridedStdDev = calculateStdDev(stridedKeys);
    double counterStdDev = calculateStdDev(counterKeys);
    assertTrue(stridedStdDev > counterStdDev,
               "StridedKeyGenerator should have larger standard deviation: " +
               "strided=" + stridedStdDev + ", counter=" + counterStdDev);
  }

  @Test
  public void testLargeCount() {
    long start = 0;
    long count = 1000000; // 1 million
    StridedKeyGenerator generator = new StridedKeyGenerator(start, count);

    // Use List to maintain generation order
    List<Long> keys = new ArrayList<>();
    Set<Long> keySet = new HashSet<>(); // For duplicate verification

    // Generate first 10000 keys
    for (int i = 0; i < 10000; i++) {
      Long key = generator.nextValue();
      assertTrue(key >= start && key < start + count, "Key should be in range: " + key);
      keys.add(key); // Maintain order
      assertTrue(keySet.add(key), "No duplicates");
    }

    assertEquals(keySet.size(), 10000, "No duplicates in first 10000 keys");

    // Verify distribution using interval analysis (more intuitive)
    long totalInterval = 0;
    long maxInterval = 0;
    for (int i = 1; i < keys.size(); i++) {
      long interval = Math.abs(keys.get(i) - keys.get(i - 1));
      totalInterval += interval;
      maxInterval = Math.max(maxInterval, interval);
    }

    double avgInterval = (double) totalInterval / (keys.size() - 1);
    // If completely sequential, average interval would be 1
    // StridedKeyGenerator's average interval should be much larger than 1, indicating distribution
    assertTrue(avgInterval > 10,
               "Average interval should be much larger than 1 for strided generator: " + avgInterval);

    // Max interval should also be large
    assertTrue(maxInterval > 100, "Max interval should be large: " + maxInterval);

    // Verify keys are distributed across multiple ranges (based on generation order)
    int numRanges = 100;
    int[] ranges = new int[numRanges];
    for (Long key : keys) {
      // Map [start, start+count) to [0, numRanges)
      long offset = key - start;
      double normalized = (double) offset / count; // [0, 1)
      int rangeIndex = Math.min((int) (normalized * numRanges), numRanges - 1);
      ranges[rangeIndex]++;
    }

    int usedRanges = 0;
    for (int cnt : ranges) {
      if (cnt > 0) usedRanges++;
    }
    assertTrue(usedRanges > 50,
               "Keys should be distributed across many ranges: " + usedRanges);
  }

  /**
   * Calculate standard deviation of an array of values.
   */
  private double calculateStdDev(long[] values) {
    double mean = Arrays.stream(values).average().getAsDouble();
    double variance = Arrays.stream(values)
        .mapToDouble(v -> Math.pow(v - mean, 2))
        .average()
        .getAsDouble();
    return Math.sqrt(variance);
  }
}

