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
package org.hopper.edw.datavault.hopgui.widget;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.hopper.edw.datavault.hopgui.file.lineageview.HopGuiLineageViewGraph;
import org.junit.jupiter.api.Test;

/**
 * Hop Web / RAP does not ship {@code org.eclipse.swt.custom.StyledText}, and RAP {@code FontData}
 * has no copy constructor. Classes constructed on that path must not mention those APIs in the
 * class file, or opening Lineage View fails with {@code NoClassDefFoundError} / {@code
 * NoSuchMethodError}.
 */
class MarkdownWebCompatibilityTest {

  private static final String SWT_STYLED_TEXT = "org/eclipse/swt/custom/StyledText";
  private static final String SWT_STYLE_RANGE = "org/eclipse/swt/custom/StyleRange";

  /** Bytecode descriptor of {@code FontData(FontData)}, which RAP does not implement. */
  private static final String FONT_DATA_COPY_CTOR = "(Lorg/eclipse/swt/graphics/FontData;)V";

  @Test
  void lineageViewAndMarkdownFacadesAvoidStyledText() throws IOException {
    assertFalse(classFileContains(MarkdownStyledTextComp.class, SWT_STYLED_TEXT));
    assertFalse(classFileContains(MarkdownStyledTextComp.class, SWT_STYLE_RANGE));
    assertFalse(classFileContains(MarkdownEditPreviewComposite.class, SWT_STYLED_TEXT));
    assertFalse(classFileContains(MarkdownEditPreviewComposite.class, SWT_STYLE_RANGE));
    assertFalse(classFileContains(HopGuiLineageViewGraph.class, SWT_STYLED_TEXT));
    assertFalse(classFileContains(HopGuiLineageViewGraph.class, SWT_STYLE_RANGE));
  }

  @Test
  void markdownFacadeAvoidsFontDataCopyConstructor() throws IOException {
    assertFalse(
        classFileContains(MarkdownStyledTextComp.class, FONT_DATA_COPY_CTOR),
        "MarkdownStyledTextComp must not call FontData(FontData); RAP has no copy constructor");
    assertFalse(classFileContains(MarkdownEditPreviewComposite.class, FONT_DATA_COPY_CTOR));
    assertFalse(classFileContains(HopGuiLineageViewGraph.class, FONT_DATA_COPY_CTOR));
    assertTrue(
        classFileContains(MarkdownStyledTextComp.class, "(Ljava/lang/String;II)V"),
        "Expected FontData(String, int, int), the RAP-safe constructor");
  }

  @Test
  void desktopHelperKeepsStyledText() throws IOException {
    assertTrue(classFileContains(MarkdownDesktopStyledText.class, SWT_STYLED_TEXT));
    assertTrue(classFileContains(MarkdownDesktopStyledText.class, SWT_STYLE_RANGE));
  }

  private static boolean classFileContains(Class<?> type, String token) throws IOException {
    String resource = type.getName().replace('.', '/') + ".class";
    try (InputStream in = type.getClassLoader().getResourceAsStream(resource)) {
      assertTrue(in != null, "Missing class file " + resource);
      String haystack = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
      return haystack.contains(token);
    }
  }
}
