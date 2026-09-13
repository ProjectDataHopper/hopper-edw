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
package org.hopper.edw.datavault.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EdwPdfSupportTest {

  @Test
  void transcodesSvgToPdf() throws Exception {
    String svg =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <svg xmlns="http://www.w3.org/2000/svg" width="120" height="80">
          <rect x="8" y="8" width="104" height="64" fill="#E8F4F8" stroke="#2B6CB0"/>
          <text x="20" y="48">fact_orders</text>
        </svg>
        """;
    byte[] pdf = EdwPdfSupport.svgToPdf(svg);
    assertTrue(pdf.length > 8);
    assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
  }
}
