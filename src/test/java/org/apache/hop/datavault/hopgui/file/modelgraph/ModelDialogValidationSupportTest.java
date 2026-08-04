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
package org.apache.hop.datavault.hopgui.file.modelgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvModelCheckOptions;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModelDialogValidationSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void cloneDataVaultModelPreservesTables() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub();
    hub.setName("customer");
    hub.setTableName("h_customer");
    model.getTables().add(hub);

    DataVaultModel clone =
        ModelDialogValidationSupport.cloneDataVaultModel(model, new MemoryMetadataProvider());

    assertEquals(1, clone.getTables().size());
    assertEquals("customer", clone.getTables().getFirst().getName());
  }

  @Test
  void draftDuplicateNameProducesCheckError() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub first = new DvHub();
    first.setName("customer");
    first.setTableName("h_customer");
    DvHub second = new DvHub();
    second.setName("customer");
    second.setTableName("h_customer_2");
    model.getTables().add(first);
    model.getTables().add(second);

    DataVaultModel draft =
        ModelDialogValidationSupport.cloneDataVaultModel(model, new MemoryMetadataProvider());
    IDvTable draftSecond = draft.getTables().get(1);
    draftSecond.setName("customer");

    List<ICheckResult> remarks =
        draft.check(new MemoryMetadataProvider(), new Variables(), DvModelCheckOptions.defaults());

    assertTrue(
        remarks.stream()
            .anyMatch(
                remark ->
                    remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && remark.getText() != null
                        && remark.getText().contains("customer")));
  }

  @Test
  void tableDialogValidateChecksOnlySelectedTable() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub good = new DvHub();
    good.setName("good_hub");
    good.setTableName("h_good");
    DvHub broken = new DvHub();
    // empty name produces a check error only when this hub is validated
    broken.setName("");
    broken.setTableName("h_broken");
    model.getTables().add(good);
    model.getTables().add(broken);

    DataVaultModel draft =
        ModelDialogValidationSupport.cloneDataVaultModel(model, new MemoryMetadataProvider());
    IDvTable draftGood = draft.getTables().get(0);
    IDvTable draftBroken = draft.getTables().get(1);

    List<ICheckResult> tableOnly = new java.util.ArrayList<>();
    draftGood.check(
        tableOnly,
        new MemoryMetadataProvider(),
        new Variables(),
        DvModelCheckOptions.fastOnly(),
        draft);

    List<ICheckResult> wholeModel =
        draft.check(new MemoryMetadataProvider(), new Variables(), DvModelCheckOptions.fastOnly());

    // Single-table validate must not include remarks for other tables in the model.
    assertTrue(
        tableOnly.stream().allMatch(r -> r.getSourceInfo() == draftGood),
        () -> "Expected only good_hub remarks, got: " + tableOnly);
    assertTrue(
        wholeModel.stream().anyMatch(r -> r.getSourceInfo() == draftBroken),
        () -> "Expected whole-model check to include broken hub remarks, got: " + wholeModel);
    assertTrue(
        wholeModel.size() > tableOnly.size(),
        () ->
            "Expected whole-model check to produce more remarks than single-table check; whole="
                + wholeModel.size()
                + " tableOnly="
                + tableOnly.size());
  }
}
