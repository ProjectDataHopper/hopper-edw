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
package org.hopper.edw.semantic.hopgui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticModelPersistence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HopSemanticFileTypeTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void capabilitiesAndExtension() {
    HopSemanticFileType fileType = new HopSemanticFileType();
    assertEquals(".hsl", fileType.getDefaultFileExtension());
    assertEquals("Semantic Layer", fileType.getName());
    assertEquals("true", fileType.getCapabilities().getProperty(IHopFileType.CAPABILITY_NEW));
    assertEquals("true", fileType.getCapabilities().getProperty(IHopFileType.CAPABILITY_SAVE));
    assertEquals("true", fileType.getCapabilities().getProperty(IHopFileType.CAPABILITY_SAVE_AS));
    assertEquals("true", fileType.getCapabilities().getProperty(IHopFileType.CAPABILITY_CLOSE));
    assertEquals(
        "true", fileType.getCapabilities().getProperty(IHopFileType.CAPABILITY_FILE_HISTORY));

    assertArrayEquals(new String[] {"*.hsl"}, fileType.getFilterExtensions());
    assertArrayEquals(new String[] {"Semantic Layers"}, fileType.getFilterNames());

    assertTrue(fileType.supportsFile(new SemanticModel()));
    assertFalse(fileType.supportsFile(null));
  }

  @Test
  void isHandledByExtensionAndContent() throws Exception {
    HopSemanticFileType fileType = new HopSemanticFileType();
    assertTrue(fileType.isHandledBy("models/orders.hsl", false));
    assertTrue(fileType.isHandledBy("MODELS/ORDERS.HSL", false));
    assertFalse(fileType.isHandledBy("models/orders.hdm", false));

    Path dir = Files.createTempDirectory("hsl-type");
    Path hsl = dir.resolve("model.hsl");
    SemanticModel model = new SemanticModel();
    model.setName("model");
    SemanticModelPersistence.save(model, hsl.toString(), new Variables());
    assertTrue(fileType.isHandledBy(hsl.toString(), true));

    Path other = dir.resolve("not-a-model.xml");
    Files.writeString(other, "<something/>", StandardCharsets.UTF_8);
    assertFalse(fileType.isHandledBy(other.toString(), true));
  }

  @Test
  void editorImplementsCloseAndIsCloseable() throws Exception {
    Method closeMethod = HopGuiSemanticLayerEditor.class.getMethod("close");
    assertEquals(void.class, closeMethod.getReturnType());
    assertEquals(0, closeMethod.getParameterCount());

    Method isCloseableMethod = HopGuiSemanticLayerEditor.class.getMethod("isCloseable");
    assertEquals(boolean.class, isCloseableMethod.getReturnType());
    assertEquals(0, isCloseableMethod.getParameterCount());

    assertTrue(IHopFileTypeHandler.class.isAssignableFrom(HopGuiSemanticLayerEditor.class));
  }
}
