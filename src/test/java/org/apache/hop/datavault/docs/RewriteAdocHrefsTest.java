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
package org.apache.hop.datavault.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RewriteAdocHrefsTest {

  @Test
  void rewritesHrefAndStripsAdocLabel() {
    String html =
        "<a href=\"architecture.adoc\">architecture.adoc</a>"
            + "<a href='getting-started-edw.adoc#build'>getting-started-edw.adoc build</a>";
    int[] counts = new int[1];
    String out = RewriteAdocHrefs.rewrite(html, counts);
    assertEquals(4, counts[0]);
    assertTrue(out.contains("href=\"architecture.html\""));
    assertTrue(out.contains(">architecture</a>"));
    assertTrue(out.contains("href='getting-started-edw.html#build'"));
    assertTrue(out.contains(">getting-started-edw build</a>"));
    assertFalse(out.contains(".adoc"));
  }
}
