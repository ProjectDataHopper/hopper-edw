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
package org.apache.hop.catalog.hopgui.navigation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordOriginNavigationSupportTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void canNavigateWhenModelFileExists() throws Exception {
    Path modelFile = tempDir.resolve("retail.hdm");
    Files.writeString(modelFile, "<dimensional-model/>");

    RecordOrigin origin = new RecordOrigin();
    origin.setModelType(RecordOriginNavigationSupport.MODEL_TYPE_DIMENSIONAL);
    origin.setModelFilename(modelFile.toString());

    assertTrue(RecordOriginNavigationSupport.canNavigateToOrigin(origin, new Variables()));
  }

  @Test
  void canNavigateResolvesVariablesInModelPath() throws Exception {
    Path modelFile = tempDir.resolve("retail.hdm");
    Files.writeString(modelFile, "<dimensional-model/>");

    Variables variables = new Variables();
    variables.setVariable("MODEL_DIR", tempDir.toString());

    RecordOrigin origin = new RecordOrigin();
    origin.setModelType(RecordOriginNavigationSupport.MODEL_TYPE_DIMENSIONAL);
    origin.setModelFilename("${MODEL_DIR}/retail.hdm");

    assertTrue(RecordOriginNavigationSupport.canNavigateToOrigin(origin, variables));
  }

  @Test
  void cannotNavigateWithoutModelFilename() {
    RecordOrigin origin = new RecordOrigin();
    origin.setModelType(RecordOriginNavigationSupport.MODEL_TYPE_DATA_VAULT);

    assertFalse(RecordOriginNavigationSupport.canNavigateToOrigin(origin, new Variables()));
  }

  @Test
  void cannotNavigateForUnsupportedModelType() throws Exception {
    Path modelFile = tempDir.resolve("notes.txt");
    Files.writeString(modelFile, "not a model");

    RecordOrigin origin = new RecordOrigin();
    origin.setModelType("RECORD");
    origin.setModelFilename(modelFile.toString());

    assertFalse(RecordOriginNavigationSupport.canNavigateToOrigin(origin, new Variables()));
  }

  @Test
  void cannotNavigateWhenModelFileMissing() {
    RecordOrigin origin = new RecordOrigin();
    origin.setModelType(RecordOriginNavigationSupport.MODEL_TYPE_BUSINESS_VAULT);
    origin.setModelFilename(tempDir.resolve("missing.hbv").toString());

    assertFalse(RecordOriginNavigationSupport.canNavigateToOrigin(origin, new Variables()));
  }

  @Test
  void canNavigateToSourceModelOrigin() throws Exception {
    Path modelFile = tempDir.resolve("source-tables-crm.hsm");
    Files.writeString(modelFile, "<source-model/>");

    RecordOrigin origin = new RecordOrigin();
    origin.setModelType(RecordOriginNavigationSupport.MODEL_TYPE_SOURCE_MODEL);
    origin.setModelFilename(modelFile.toString());
    origin.setModelElementName("asn-package-lines");

    assertTrue(RecordOriginNavigationSupport.canNavigateToOrigin(origin, new Variables()));
  }
}
