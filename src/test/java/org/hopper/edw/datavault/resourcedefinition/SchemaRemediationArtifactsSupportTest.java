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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaRemediationArtifactsSupportTest {

  @Test
  void markdownMentionsWorkflowAndChanges() {
    String md =
        SchemaRemediationArtifactsSupport.formatMarkdown(
            "Schema remediation",
            "address-line1-75",
            List.of("Updated sat_customer_address.address_line1 50 -> 75"),
            "/project/workflows/schema-remediation/address-line1-75/apply.hwf",
            "/project/workflows/schema-remediation/address-line1-75/apply.sql");
    assertTrue(md.contains("address-line1-75"));
    assertTrue(md.contains("apply.hwf"));
    assertTrue(md.contains("sat_customer_address"));
  }

  @Test
  void htmlEscapesContent() {
    String html =
        SchemaRemediationArtifactsSupport.formatHtml("Title", "name", List.of("a < b"), null, null);
    assertTrue(html.contains("a &lt; b"));
  }

  @Test
  void sanitizeRemediationName() {
    assertTrue(
        SchemaRemediationArtifactsSupport.sanitizeRemediationName("Accept address_line1!")
            .matches("[A-Za-z0-9._-]+"));
  }
}
