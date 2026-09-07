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
package org.hopper.edw.datavault.hopgui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.hopper.edw.datavault.hopgui.file.lineageview.HopGuiLineageViewGraph;
import org.junit.jupiter.api.Test;

/**
 * Hop Web reflects over every {@link GuiPlugin} at startup. A field or method signature that names
 * a desktop-only SWT type makes RAP fail with {@code NoClassDefFoundError}.
 */
class GuiPluginWebCompatibilityTest {

  private static final Set<String> DESKTOP_ONLY_SWT_TYPES =
      new HashSet<>(
          Arrays.asList(
              "org.eclipse.swt.custom.BidiSegmentListener",
              "org.eclipse.swt.custom.Bullet",
              "org.eclipse.swt.custom.CTabFolderRenderer",
              "org.eclipse.swt.custom.CaretListener",
              "org.eclipse.swt.custom.ExtendedModifyListener",
              "org.eclipse.swt.custom.LineBackgroundListener",
              "org.eclipse.swt.custom.LineStyleEvent",
              "org.eclipse.swt.custom.LineStyleListener",
              "org.eclipse.swt.custom.PaintObjectListener",
              "org.eclipse.swt.custom.PopupList",
              "org.eclipse.swt.custom.ST",
              "org.eclipse.swt.custom.StyleRange",
              "org.eclipse.swt.custom.StyledText",
              "org.eclipse.swt.custom.StyledTextContent",
              "org.eclipse.swt.custom.TableCursor",
              "org.eclipse.swt.custom.TreeCursor",
              "org.eclipse.swt.custom.VerifyKeyListener",
              "org.eclipse.swt.graphics.GlyphMetrics",
              "org.eclipse.swt.graphics.Pattern",
              "org.eclipse.swt.graphics.Region",
              "org.eclipse.swt.graphics.TextLayout",
              "org.eclipse.swt.graphics.TextStyle",
              "org.eclipse.swt.widgets.Caret",
              "org.eclipse.swt.widgets.Tracker"));

  @Test
  void guiPluginSignaturesAvoidDesktopOnlySwtTypes() throws Exception {
    List<String> violations = new ArrayList<>();

    for (Class<?> guiPluginClass : findGuiPluginClasses()) {
      for (Field field : guiPluginClass.getDeclaredFields()) {
        if (DESKTOP_ONLY_SWT_TYPES.contains(field.getType().getName())) {
          violations.add(
              guiPluginClass.getName()
                  + ": field "
                  + field.getName()
                  + " of type "
                  + field.getType().getName());
        }
      }
      for (Method method : guiPluginClass.getDeclaredMethods()) {
        List<Class<?>> types = new ArrayList<>();
        types.add(method.getReturnType());
        types.addAll(Arrays.asList(method.getParameterTypes()));
        for (Class<?> type : types) {
          if (DESKTOP_ONLY_SWT_TYPES.contains(type.getName())) {
            violations.add(
                guiPluginClass.getName()
                    + ": method "
                    + method.getName()
                    + " uses "
                    + type.getName());
          }
        }
      }
    }

    assertTrue(
        violations.isEmpty(),
        "@GuiPlugin classes must not name desktop-only SWT types in field or method signatures, "
            + "they break Hop Web startup: "
            + violations);
  }

  private static List<Class<?>> findGuiPluginClasses() throws URISyntaxException, IOException {
    Path root =
        Path.of(
            HopGuiLineageViewGraph.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    ClassLoader classLoader = GuiPluginWebCompatibilityTest.class.getClassLoader();
    List<Class<?>> classes = new ArrayList<>();

    try (Stream<Path> files = Files.walk(root)) {
      for (Path file :
          (Iterable<Path>) files.filter(GuiPluginWebCompatibilityTest::isClassFile)::iterator) {
        String className =
            root.relativize(file)
                .toString()
                .replace(java.io.File.separatorChar, '.')
                .replaceAll("\\.class$", "");
        try {
          Class<?> clazz = Class.forName(className, false, classLoader);
          if (clazz.getAnnotation(GuiPlugin.class) != null) {
            classes.add(clazz);
          }
        } catch (Throwable e) {
          // Classes we can't even load here are not what this test is about.
        }
      }
    }

    assertTrue(
        classes.size() > 10, "Expected to find hopper-edw @GuiPlugin classes, scanned " + root);
    return classes;
  }

  private static boolean isClassFile(Path path) {
    String name = path.getFileName().toString();
    return name.endsWith(".class") && !name.contains("$");
  }
}
