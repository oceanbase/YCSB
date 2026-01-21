/**
 * Copyright (c) 2010 Yahoo! Inc., Copyright (c) 2017 YCSB contributors. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package site.ycsb.generator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates a sequence of integers with stride mapping to distribute keys across the key space.
 * This helps avoid hotspots during concurrent inserts by ensuring keys are distributed
 * rather than sequential.
 * <p>
 * The generator uses a stride (step size) that is coprime with the count to ensure
 * all keys in the range [start, start+count) are generated exactly once, with good
 * distribution across the key space.
 */
public class StridedKeyGenerator extends NumberGenerator {
  private final long start;
  private final long count;
  private final AtomicLong index;
  private final long stride;  // Stride that is coprime with count

  /**
   * Create a strided key generator that starts at start and generates count keys.
   *
   * @param start The starting key value
   * @param count The number of keys to generate
   */
  public StridedKeyGenerator(long start, long count) {
    this.start = start;
    this.count = count;
    this.index = new AtomicLong(0);
    this.stride = findCoprimeStride(count);
  }

  @Override
  public Long nextValue() {
    long idx = index.getAndIncrement();
    if (idx >= count) {
      throw new IllegalStateException("All keys have been allocated: " + idx + " >= " + count);
    }

    // Stride mapping: key = start + (index * stride) % count
    // Since stride is coprime with count, this is a bijection
    long key = start + (idx * stride) % count;

    setLastValue(key);
    return key;
  }

  @Override
  public Long lastValue() {
    return (Long) super.lastValue();
  }

  @Override
  public double mean() {
    throw new UnsupportedOperationException("Can't compute mean of non-stationary distribution!");
  }

  /**
   * Find a stride that is coprime with n.
   * This ensures the mapping is a bijection (one-to-one and onto).
   *
   * @param n The count value
   * @return A stride value that is coprime with n
   */
  private long findCoprimeStride(long n) {
    if (n <= 1) {
      return 1;
    }

    // If n is a power of 2, any odd number is coprime
    if ((n & (n - 1)) == 0) {
      return 1103515245L; // Odd number, coprime with powers of 2
    }

    // Try some common primes/odd numbers that are often coprime
    long[] candidates = {1103515245L, 1664525L, 2147483647L, 48271L};
    for (long candidate : candidates) {
      if (gcd(candidate, n) == 1) {
        return candidate;
      }
    }

    // If none of the candidates work, find an odd number starting from n+1
    long s = n + 1;
    if (s % 2 == 0) {
      s++;
    }
    while (gcd(s, n) != 1) {
      s += 2;
    }
    return s;
  }

  /**
   * Calculate the greatest common divisor (GCD) of two numbers.
   *
   * @param a First number
   * @param b Second number
   * @return GCD of a and b
   */
  private long gcd(long a, long b) {
    while (b != 0) {
      long temp = b;
      b = a % b;
      a = temp;
    }
    return Math.abs(a);
  }
}

