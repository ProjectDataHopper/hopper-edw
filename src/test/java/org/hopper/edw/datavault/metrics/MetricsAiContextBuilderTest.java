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
package org.hopper.edw.datavault.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metrics.metadata.ExecutionMetricsProfileMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsAiContextBuilderTest {

  private MemoryMetadataProvider metadataProvider;

  @BeforeEach
  void setUp() throws Exception {
    metadataProvider = new MemoryMetadataProvider();
    ExecutionMetricsProfileMeta profile =
        new ExecutionMetricsProfileMeta("retail-execution-metrics");
    profile.setEnabled(true);
    profile.setTargetDatabaseConnection("OPS");
    profile.setOperationsSchema("dv_ops");
    metadataProvider.getSerializer(ExecutionMetricsProfileMeta.class).save(profile);
  }

  @Test
  void detectsPerformanceRelatedQuestions() {
    assertTrue(
        MetricsAiContextBuilder.isPerformanceRelatedQuestion(
            "How can I tune sort memory for this model?"));
    assertFalse(
        MetricsAiContextBuilder.isPerformanceRelatedQuestion("What hubs should I add next?"));
  }

  @Test
  void resolvesMetricsDatabaseFromEnabledProfile() {
    assertEquals("OPS", MetricsAiContextBuilder.resolveMetricsDatabaseName(metadataProvider, null));
    assertEquals("dv_ops", MetricsAiContextBuilder.resolveOperationsSchema(metadataProvider, null));
  }

  @Test
  void skipsMetricsContextForNonPerformanceQuestions() {
    assertEquals(
        "",
        MetricsAiContextBuilder.buildMetricsContextForPrompt(
            "Explain hub modeling", "retail-360", "dv", metadataProvider, null));
  }

  @Test
  void shouldIncludeMetricsForExplicitFlagScenarioOrKeywords() {
    assertTrue(MetricsAiContextBuilder.shouldIncludeMetrics(true, false, "anything"));
    assertTrue(MetricsAiContextBuilder.shouldIncludeMetrics(false, true, "anything"));
    assertFalse(
        MetricsAiContextBuilder.shouldIncludeMetrics(false, false, "What hubs should I add?"));
    assertTrue(
        MetricsAiContextBuilder.shouldIncludeMetrics(
            false, false, "How can I tune sort memory for this model?"));
  }

  @Test
  void buildMetricsContextHonorsPerformanceTuningScenario() {
    assertEquals(
        "",
        MetricsAiContextBuilder.buildMetricsContext(
            false, true, "General question", "retail-360", "dv", metadataProvider, null));
  }
}
