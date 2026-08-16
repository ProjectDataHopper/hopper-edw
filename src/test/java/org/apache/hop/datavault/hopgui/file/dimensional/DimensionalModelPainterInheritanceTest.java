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
package org.apache.hop.datavault.hopgui.file.dimensional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.Map;
import org.apache.hop.datavault.metadata.dimensional.DmDimension;
import org.apache.hop.datavault.metadata.dimensional.DmDimensionAlias;
import org.apache.hop.datavault.metadata.dimensional.IDmTable;
import org.junit.jupiter.api.Test;

class DimensionalModelPainterInheritanceTest {

  @Test
  void externalConformedAliasDoesNotTargetItself() {
    DmDimensionAlias product = new DmDimensionAlias();
    product.setName("d_product");
    product.setReferencedDimensionName("d_product");
    product.setReferencedModelFilename("${PROJECT_HOME}/models/retail-conformed-dims.hdm");

    Map<String, IDmTable> byName = new HashMap<>();
    byName.put("d_product", product);

    assertNull(DimensionalModelPainter.resolveLocalInheritanceTarget(product, byName));
  }

  @Test
  void rolePlayingAliasTargetsLocalReferencedDimension() {
    DmDimension date = new DmDimension();
    date.setName("d_date");
    DmDimensionAlias statusDate = new DmDimensionAlias();
    statusDate.setName("d_status_date");
    statusDate.setReferencedDimensionName("d_date");

    Map<String, IDmTable> byName = new HashMap<>();
    byName.put("d_date", date);
    byName.put("d_status_date", statusDate);

    assertSame(date, DimensionalModelPainter.resolveLocalInheritanceTarget(statusDate, byName));
  }
}
