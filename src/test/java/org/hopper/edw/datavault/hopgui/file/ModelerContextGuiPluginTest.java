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
package org.hopper.edw.datavault.hopgui.file;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.action.GuiContextActionFilter;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionLambdaBuilder;
import org.apache.hop.core.util.TranslateUtil;
import org.hopper.edw.datavault.hopgui.file.businessvault.BusinessVaultContextGuiPlugin;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopGuiBusinessVaultGraph;
import org.hopper.edw.datavault.hopgui.file.dimensional.DimensionalModelContextGuiPlugin;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopGuiDimensionalModelGraph;
import org.hopper.edw.datavault.hopgui.file.executionmap.ExecutionMapContextGuiPlugin;
import org.hopper.edw.datavault.hopgui.file.executionmap.HopGuiExecutionMapGraph;
import org.hopper.edw.datavault.hopgui.file.sourcemodel.HopGuiSourceModelGraph;
import org.hopper.edw.datavault.hopgui.file.sourcemodel.SourceModelContextGuiPlugin;
import org.hopper.edw.datavault.hopgui.file.vault.HopGuiVaultGraph;
import org.hopper.edw.datavault.hopgui.file.vault.VaultContextGuiPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Hop Web builds context-menu actions with {@link GuiActionLambdaBuilder}. That lookup fails when
 * the action is declared on a RAP graph widget (issue #239).
 */
class ModelerContextGuiPluginTest {

  private static final Class<?>[] PLUGINS = {
    SourceModelContextGuiPlugin.class,
    VaultContextGuiPlugin.class,
    BusinessVaultContextGuiPlugin.class,
    DimensionalModelContextGuiPlugin.class,
    ExecutionMapContextGuiPlugin.class
  };

  private static final Class<?>[] GRAPHS = {
    HopGuiSourceModelGraph.class,
    HopGuiVaultGraph.class,
    HopGuiBusinessVaultGraph.class,
    HopGuiDimensionalModelGraph.class,
    HopGuiExecutionMapGraph.class
  };

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void contextActionsAreNotDeclaredOnTheGraphWidget() {
    for (Class<?> graph : GRAPHS) {
      for (Method method : graph.getDeclaredMethods()) {
        assertNull(
            method.getAnnotation(GuiContextAction.class),
            graph.getSimpleName() + "#" + method.getName());
        assertNull(
            method.getAnnotation(GuiContextActionFilter.class),
            graph.getSimpleName() + "#" + method.getName());
      }
    }
  }

  @Test
  void createLambdaUsesTheNoArgPluginInsteadOfGetInstance() throws Exception {
    for (Class<?> plugin : PLUGINS) {
      assertTrue(Modifier.isPublic(plugin.getModifiers()), plugin.getName());
      Constructor<?> constructor = plugin.getDeclaredConstructor();
      assertTrue(Modifier.isPublic(constructor.getModifiers()), plugin.getName());
      assertNotNull(constructor.newInstance());
      assertTrue(noGetInstance(plugin), plugin.getSimpleName() + " must not define getInstance()");

      int actions = 0;
      for (Method method : plugin.getDeclaredMethods()) {
        GuiContextAction action = method.getAnnotation(GuiContextAction.class);
        if (action == null) {
          continue;
        }
        actions++;
        assertEqualsParameterCount(method, 1);
        Object context = newContext(method.getParameterTypes()[0]);
        GuiAction pluginAction =
            new GuiAction(
                action.id(),
                action.type(),
                "name",
                "tooltip",
                action.image(),
                plugin.getName(),
                method.getName());
        pluginAction.setClassLoader(plugin.getClassLoader());
        GuiAction bound = new GuiActionLambdaBuilder<>().createLambda(pluginAction, context, null);
        assertNotNull(bound.getActionLambda(), plugin.getSimpleName() + "#" + method.getName());
        assertResolved(action.name(), plugin, method.getName() + " name");
        assertResolved(action.tooltip(), plugin, method.getName() + " tooltip");
      }
      assertTrue(actions > 0, plugin.getSimpleName());
    }
  }

  private static boolean noGetInstance(Class<?> type) {
    try {
      type.getDeclaredMethod("getInstance");
      return false;
    } catch (NoSuchMethodException e) {
      return true;
    }
  }

  private static void assertEqualsParameterCount(Method method, int count) {
    assertTrue(
        method.getParameterCount() == count,
        method.getDeclaringClass().getSimpleName() + "#" + method.getName());
  }

  private static Object newContext(Class<?> type) throws Exception {
    Constructor<?> constructor = type.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    Object[] args = new Object[constructor.getParameterCount()];
    return constructor.newInstance(args);
  }

  private static void assertResolved(String key, Class<?> plugin, String label) {
    String translated = TranslateUtil.translate(key, plugin);
    assertFalse(translated.startsWith("i18n:"), label + ": " + translated);
    assertFalse(translated.startsWith("!"), label + ": " + translated);
    assertFalse(translated.isBlank(), label);
  }
}
