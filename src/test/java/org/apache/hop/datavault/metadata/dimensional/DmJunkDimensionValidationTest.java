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
package org.apache.hop.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmJunkDimensionValidationTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void sharedHashColumnPassesValidationWithoutExplicitHashField() throws HopException {
    DmJunkDimension junk = buildEmbeddedJunk();
    junk.setSurrogateKeyField("orders_junk_hk");
    junk.setSurrogateKeyStrategy(DmJunkSurrogateKeyStrategy.COMPUTE_HASH_KEY);
    junk.setHashCodeStrategy(DmJunkHashCodeStrategy.MD5);
    junk.setUseSurrogateKeyAsHashCodeField(true);

    List<ICheckResult> remarks = new ArrayList<>();
    junk.check(remarks, new MemoryMetadataProvider(), new Variables(), buildModel(junk));

    assertFalse(hasError(remarks));
    assertFalse(hasWarningContaining(remarks, "MissingJunkHashCodeField"));
  }

  @Test
  void sharedHashColumnWithSameExplicitFieldDoesNotProduceDuplicateLayoutError()
      throws HopException {
    DmJunkDimension junk = buildEmbeddedJunk();
    junk.setSurrogateKeyField("orders_junk_hk");
    junk.setSurrogateKeyStrategy(DmJunkSurrogateKeyStrategy.COMPUTE_HASH_KEY);
    junk.setHashCodeStrategy(DmJunkHashCodeStrategy.MD5);
    junk.setUseSurrogateKeyAsHashCodeField(true);
    junk.setHashCodeField("orders_junk_hk");

    List<ICheckResult> remarks = new ArrayList<>();
    junk.check(remarks, new MemoryMetadataProvider(), new Variables(), buildModel(junk));

    assertFalse(hasErrorContaining(remarks, "Duplicate target column"));
  }

  private static DmJunkDimension buildEmbeddedJunk() {
    DmJunkDimension junk = new DmJunkDimension();
    junk.setName("d_orders_junk");
    junk.setTableName("d_orders_junk");
    DmJunkDimensionSupport.applyFactTableSource(junk, "f_orders");
    junk.getKeyFields().add(new DmNaturalKeyField("j1"));
    return junk;
  }

  private static DimensionalModel buildModel(DmJunkDimension junk) {
    DimensionalModel model = new DimensionalModel();
    DmFact fact = new DmFact();
    fact.setName("f_orders");
    fact.setTableName("f_orders");
    fact.getSourceOrDefault().setSourceType(DmSourceType.SQL);
    fact.getSourceOrDefault().setSourceSql("SELECT j1 FROM staging.orders");
    fact.getJunkDimensionRoles()
        .add(new DmFactJunkDimensionRole("d_orders_junk", "d_orders_junk_key"));
    model.getTables().add(fact);
    model.getTables().add(junk);
    return model;
  }

  private static boolean hasError(List<ICheckResult> remarks) {
    return remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
  }

  private static boolean hasErrorContaining(List<ICheckResult> remarks, String text) {
    return remarks.stream()
        .filter(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR)
        .anyMatch(r -> r.getText() != null && r.getText().contains(text));
  }

  private static boolean hasWarningContaining(List<ICheckResult> remarks, String text) {
    return remarks.stream()
        .filter(r -> r.getType() == ICheckResult.TYPE_RESULT_WARNING)
        .anyMatch(r -> r.getText() != null && r.getText().contains(text));
  }
}
