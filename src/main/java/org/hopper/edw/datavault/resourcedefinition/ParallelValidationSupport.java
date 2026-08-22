/*
 * Copyright 2026 i-Bridge bv
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
package org.hopper.edw.datavault.resourcedefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;

/**
 * Bounded parallel fan-out for latency-bound validation work (live source discovery, target DDL
 * checks). Preserves input order; shuts down the pool on completion.
 */
public final class ParallelValidationSupport {

  /** Default concurrent checks when the user has not configured a value. */
  public static final int DEFAULT_PARALLELISM = 8;

  /** Hard upper bound to avoid connection storms against VPN/DB gateways. */
  public static final int MAX_PARALLELISM = 64;

  private ParallelValidationSupport() {}

  /**
   * Parses a configured parallelism value (literal or already-resolved variable). Empty/invalid
   * falls back to {@code defaultValue}. Clamped to {@code 1..MAX_PARALLELISM}.
   */
  public static int resolveParallelism(String configured, int defaultValue) {
    int fallback = clamp(defaultValue <= 0 ? DEFAULT_PARALLELISM : defaultValue);
    if (Utils.isEmpty(configured)) {
      return fallback;
    }
    try {
      return clamp(Integer.parseInt(configured.trim()));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  public static int resolveParallelism(int configured) {
    return clamp(configured <= 0 ? DEFAULT_PARALLELISM : configured);
  }

  public static int clamp(int value) {
    if (value < 1) {
      return 1;
    }
    return Math.min(value, MAX_PARALLELISM);
  }

  /**
   * Maps each input item through {@code mapper} with at most {@code parallelism} concurrent tasks.
   * Results are returned in the same order as {@code items}. When {@code parallelism <= 1} or the
   * list is empty/small, runs serially on the calling thread (no pool).
   *
   * <p>Per-item exceptions are rethrown as {@link HopException} after all tasks are waited on only
   * for the first failure when {@code failFast} is true; otherwise the mapper should absorb errors
   * into result objects (preferred for validation reports).
   *
   * @param progress optional callback with completed count after each item finishes (any thread)
   */
  public static <I, O> List<O> map(
      int parallelism,
      List<I> items,
      ItemMapper<I, O> mapper,
      IntConsumer progress,
      boolean failFast)
      throws HopException {
    Objects.requireNonNull(mapper, "mapper");
    if (items == null || items.isEmpty()) {
      return List.of();
    }
    int parallel = clamp(parallelism);
    int size = items.size();
    if (parallel <= 1 || size == 1) {
      List<O> serial = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        try {
          serial.add(mapper.map(items.get(i), i));
        } catch (HopException e) {
          throw e;
        } catch (Exception e) {
          throw new HopException(
              "Parallel validation item "
                  + i
                  + " failed: "
                  + Const.NVL(e.getMessage(), e.getClass().getSimpleName()),
              e);
        }
        if (progress != null) {
          progress.accept(i + 1);
        }
      }
      return serial;
    }

    int poolSize = Math.min(parallel, size);
    ExecutorService pool =
        Executors.newFixedThreadPool(
            poolSize,
            new ThreadFactory() {
              private final AtomicInteger seq = new AtomicInteger();

              @Override
              public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "hop-dv-validate-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
              }
            });
    AtomicInteger completed = new AtomicInteger();
    try {
      List<Callable<IndexedResult<O>>> callables = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        final int index = i;
        final I item = items.get(i);
        callables.add(
            () -> {
              O value = mapper.map(item, index);
              int done = completed.incrementAndGet();
              if (progress != null) {
                progress.accept(done);
              }
              return new IndexedResult<>(index, value);
            });
      }

      List<Future<IndexedResult<O>>> futures = pool.invokeAll(callables);
      @SuppressWarnings("unchecked")
      O[] ordered = (O[]) new Object[size];
      HopException firstFailure = null;
      for (Future<IndexedResult<O>> future : futures) {
        try {
          IndexedResult<O> indexed = future.get();
          ordered[indexed.index()] = indexed.value();
        } catch (ExecutionException e) {
          Throwable cause = e.getCause() != null ? e.getCause() : e;
          HopException wrapped =
              cause instanceof HopException hop
                  ? hop
                  : new HopException(
                      "Parallel validation failed: "
                          + Const.NVL(cause.getMessage(), cause.getClass().getSimpleName()),
                      cause);
          if (failFast) {
            // Cancel remaining work; still wait in finally via shutdown.
            for (Future<IndexedResult<O>> other : futures) {
              other.cancel(true);
            }
            throw wrapped;
          }
          if (firstFailure == null) {
            firstFailure = wrapped;
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new HopException("Parallel validation interrupted", e);
        }
      }
      if (firstFailure != null) {
        throw firstFailure;
      }
      List<O> results = new ArrayList<>(size);
      for (O value : ordered) {
        results.add(value);
      }
      return results;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HopException("Parallel validation interrupted", e);
    } finally {
      pool.shutdownNow();
    }
  }

  /** Convenience: fail-fast off, no progress callback. */
  public static <I, O> List<O> map(int parallelism, List<I> items, ItemMapper<I, O> mapper)
      throws HopException {
    return map(parallelism, items, mapper, null, false);
  }

  @FunctionalInterface
  public interface ItemMapper<I, O> {
    O map(I item, int index) throws Exception;
  }

  private record IndexedResult<O>(int index, O value) {}
}
