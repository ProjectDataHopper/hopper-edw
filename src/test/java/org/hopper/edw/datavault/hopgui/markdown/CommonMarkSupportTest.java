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
package org.hopper.edw.datavault.hopgui.markdown;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommonMarkSupportTest {

  @Test
  void rendersGfmTableAsHtml() {
    String html =
        CommonMarkSupport.toHtmlBody(
            """
            | Field | Description |
            |-------|---------------|
            | Name | Link name |
            """);

    assertTrue(html.contains("<table>"));
    assertTrue(html.contains("<th>Field</th>"));
    assertTrue(html.contains("<td>Name</td>"));
  }

  @Test
  void rendersFencedCodeBlockAsHtml() {
    String html = CommonMarkSupport.toHtmlBody("```\nSELECT 1\n```");

    assertTrue(html.contains("<pre>"));
    assertTrue(html.contains("SELECT 1"));
  }
}
