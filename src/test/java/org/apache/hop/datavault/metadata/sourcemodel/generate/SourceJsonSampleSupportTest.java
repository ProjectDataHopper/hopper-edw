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
package org.apache.hop.datavault.metadata.sourcemodel.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonField;
import org.junit.jupiter.api.Test;

class SourceJsonSampleSupportTest {

  @Test
  void discoversTopLevelAndArrayPaths() throws Exception {
    String json =
        """
        {"id":1,"name":"x","lines":[{"sku":"A","qty":2},{"sku":"B","qty":3}],"tags":["t1"]}
        """;
    Set<String> paths = SourceJsonSampleSupport.discoverPaths(List.of(json));
    assertTrue(paths.stream().anyMatch(p -> p.contains("id")));
    assertTrue(paths.stream().anyMatch(p -> p.contains("sku")));
    assertTrue(paths.stream().anyMatch(p -> p.contains("lines")));
  }

  @Test
  void proposeFieldsWithArrayFocusKeepsOneArrayBase() throws Exception {
    String json = """
        {"id":1,"lines":[{"sku":"A","qty":2}],"tags":["t1","t2"]}
        """;
    List<SourceJsonField> fields =
        SourceJsonSampleSupport.proposeFields(List.of(json), "$.lines.*");
    long arrayExpanding = fields.stream().filter(SourceJsonField::isArrayExpandingPath).count();
    assertTrue(arrayExpanding >= 1);
    // tags array should become a chaining JSON-string field, not expand leaves under tags
    boolean hasTagsExpandLeaf =
        fields.stream()
            .anyMatch(
                f ->
                    f.getPath() != null
                        && f.getPath().contains("tags")
                        && f.isArrayExpandingPath()
                        && f.getPath().contains("tags.*"));
    assertFalse(hasTagsExpandLeaf);
  }

  @Test
  void proposesEpochMillisAsDate() throws Exception {
    String json = """
        {"event_ts":1700000000000,"label":"hi"}
        """;
    List<SourceJsonField> fields = SourceJsonSampleSupport.proposeFields(List.of(json), null);
    SourceJsonField ts =
        fields.stream()
            .filter(f -> f.getPath() != null && f.getPath().contains("event_ts"))
            .findFirst()
            .orElseThrow();
    assertEquals(IValueMeta.TYPE_DATE, ts.getHopType());
  }

  @Test
  void discoverArrayBasesListsDistinctArrays() throws Exception {
    String json = """
        {"a":[{"x":1}],"b":[{"y":2}]}
        """;
    List<String> bases = SourceJsonSampleSupport.discoverArrayBases(List.of(json));
    assertEquals(2, bases.size());
    assertFalse(bases.isEmpty());
  }
}
