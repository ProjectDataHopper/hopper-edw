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
package org.hopper.edw.datavault.www;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.hopper.edw.datavault.metadata.sourcemodel.service.SourceModelService;
import org.junit.jupiter.api.Test;

class SourceModelDataJsonTest {

  @Test
  void errorAndPingEncode() {
    assertTrue(SourceModelDataJson.error("boom").contains("\"ok\":false"));
    assertTrue(SourceModelDataJson.error("boom").contains("boom"));
    assertTrue(SourceModelDataJson.ping("crm").contains("\"ok\":true"));
    assertTrue(SourceModelDataJson.ping("crm").contains("crm"));
  }

  @Test
  void schemasEncodeServiceNames() {
    String json =
        SourceModelDataJson.schemas(
            List.of(
                new SourceModelDataJson.SchemaInfo("crm", "Retail"),
                new SourceModelDataJson.SchemaInfo("erp", null)));
    assertTrue(json.contains("\"n\":\"crm\""));
    assertTrue(json.contains("\"n\":\"erp\""));
  }

  @Test
  void tablesIncludeSchemaField() {
    String json =
        SourceModelDataJson.tables(
            List.of(new SourceModelDataJson.TableInfo("crm", "customer", "TABLE", "Source table")));
    assertTrue(json.contains("\"schema\":\"crm\""));
    assertTrue(json.contains("\"n\":\"customer\""));
  }

  @Test
  void queryResultEncodesColumnsAndRows() throws Exception {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaInteger("id"));
    meta.addValueMeta(new ValueMetaString("name"));
    List<RowMetaAndData> rows =
        List.of(new RowMetaAndData(meta, 1L, "alice"), new RowMetaAndData(meta, 2L, "bob"));
    String json = SourceModelDataJson.queryResult(rows, false);
    assertTrue(json.contains("\"ok\":true"));
    assertTrue(json.contains("\"n\":\"id\""));
    assertTrue(json.contains("alice"));
    assertTrue(json.contains("bob"));
  }

  @Test
  void resolveRowLimitHonoursMax() {
    SourceModelService svc = new SourceModelService("crm");
    svc.setDefaultRowLimit(1000);
    svc.setMaxRowLimit(100);
    assertEquals(100, svc.resolveRowLimit(0));
    assertEquals(50, svc.resolveRowLimit(50));
    assertEquals(100, svc.resolveRowLimit(5000));
  }
}
