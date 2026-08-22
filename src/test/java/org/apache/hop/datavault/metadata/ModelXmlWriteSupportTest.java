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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelXmlWriteSupportTest {

  @TempDir Path tempDir;

  @Test
  void formatModelXmlOmitsCommentWhenLicenseVariableUnset() throws Exception {
    Variables variables = new Variables();

    String xml =
        ModelXmlWriteSupport.formatModelXml("data-vault-model", "<name>test</name>", variables);

    assertFalse(xml.startsWith("<!--"));
    assertTrue(xml.contains("<data-vault-model>"));
    assertTrue(xml.contains("<name>test</name>"));
  }

  @Test
  void formatModelXmlPrependsCommentWhenLicenseFileConfigured() throws Exception {
    Path header = tempDir.resolve("license.txt");
    Files.writeString(header, "Licensed to the ASF");
    Variables variables = new Variables();
    variables.setVariable(Const.HOP_LICENSE_HEADER_FILE, header.toString());

    String xml =
        ModelXmlWriteSupport.formatModelXml("business-vault-model", "<name>bv</name>", variables);

    assertTrue(xml.startsWith("<!--"));
    assertTrue(xml.contains("Licensed to the ASF"));
    assertTrue(xml.contains("-->"));
    assertTrue(xml.contains("<business-vault-model>"));
  }

  @Test
  void formatModelXmlFailsWhenLicenseFileMissing() {
    Variables variables = new Variables();
    variables.setVariable(Const.HOP_LICENSE_HEADER_FILE, tempDir.resolve("missing.txt").toString());

    assertThrows(
        HopException.class,
        () ->
            ModelXmlWriteSupport.formatModelXml("dimensional-model", "<name>dm</name>", variables));
  }

  @Test
  void resolveVariablesFallsBackToDefaultSpace() throws Exception {
    String xml = ModelXmlWriteSupport.formatModelXml("hop-execution-map", "<name>hem</name>", null);

    assertFalse(xml.startsWith("<!--"));
    assertTrue(xml.contains("<hop-execution-map>"));
  }
}
