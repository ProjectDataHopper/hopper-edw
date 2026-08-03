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
package org.apache.hop.datavault.metadata.sourcemodel.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJoinType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryJoin;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.junit.jupiter.api.Test;

class SourceQueryCatalogPublisherTest {

  @Test
  void buildFieldsFromProjectionUsesAliasesAndColumnMetadata() {
    SourceModel model = new SourceModel();
    SourceTable product = new SourceTable("product");
    SourceColumn productId = new SourceColumn("product_id");
    productId.setHopType(5);
    productId.setPrimaryKeyPosition(1);
    productId.setLength("20");
    product.getColumns().add(productId);
    SourceColumn typeId = new SourceColumn("type_id");
    typeId.setHopType(5);
    product.getColumns().add(typeId);
    model.getTables().add(product);

    SourceTable productType = new SourceTable("product_type");
    SourceColumn typeName = new SourceColumn("type_name");
    typeName.setHopType(2);
    typeName.setLength("50");
    productType.getColumns().add(typeName);
    model.getTables().add(productType);

    SourceQuery query = new SourceQuery("feed_product");
    query.setDrivingTableName("product");
    SourceQueryJoin join = new SourceQueryJoin();
    join.setTableName("product_type");
    join.setJoinType(SourceJoinType.LEFT);
    query.getJoins().add(join);
    SourceQueryColumn productIdCol = new SourceQueryColumn("product", "product_id");
    productIdCol.setPrimaryKeyPosition(1);
    query.getColumns().add(productIdCol);
    query.getColumns().add(new SourceQueryColumn("product_type", "type_name", "product_type_name"));

    List<SourceField> fields = SourceQueryCatalogPublisher.buildFieldsFromProjection(model, query);

    assertEquals(2, fields.size());
    assertEquals("product_id", fields.get(0).getName());
    assertEquals(1, fields.get(0).getPrimaryKeyPosition());
    assertEquals(5, fields.get(0).getHopType());
    assertEquals("20", fields.get(0).getLength());

    assertEquals("product_type_name", fields.get(1).getName());
    assertEquals(0, fields.get(1).getPrimaryKeyPosition());
    assertEquals(2, fields.get(1).getHopType());
    assertEquals("50", fields.get(1).getLength());
  }

  @Test
  void logicalKeyWinsOverPhysicalPkNameMismatch() {
    SourceModel model = new SourceModel();
    SourceTable product = new SourceTable("product");
    SourceColumn productId = new SourceColumn("product_id");
    productId.setPrimaryKeyPosition(1);
    productId.setHopType(5);
    product.getColumns().add(productId);
    model.getTables().add(product);

    SourceQuery query = new SourceQuery("q");
    SourceQueryColumn renamed = new SourceQueryColumn("product", "product_id", "pid");
    renamed.setPrimaryKeyPosition(1);
    query.getColumns().add(renamed);

    List<SourceField> fields = SourceQueryCatalogPublisher.buildFieldsFromProjection(model, query);
    assertEquals(1, fields.size());
    assertEquals("pid", fields.get(0).getName());
    assertEquals(1, fields.get(0).getPrimaryKeyPosition());
  }

  @Test
  void buildFieldsFromProjectionDefaultsStringWhenColumnUnknown() {
    SourceModel model = new SourceModel();
    SourceQuery query = new SourceQuery("q");
    query.getColumns().add(new SourceQueryColumn("missing", "col_a", "out_a"));

    List<SourceField> fields = SourceQueryCatalogPublisher.buildFieldsFromProjection(model, query);
    assertEquals(1, fields.size());
    assertEquals("out_a", fields.get(0).getName());
    assertEquals(2, fields.get(0).getHopType());
    assertFalse(fields.get(0).getPrimaryKeyPosition() > 0);
  }
}
