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
package org.hopper.edw.datavault.hopgui.lineageview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionLambdaBuilder;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.util.TranslateUtil;
import org.hopper.edw.datavault.hopgui.file.lineageview.HopGuiLineageViewGraph;
import org.hopper.edw.datavault.hopgui.file.lineageview.HopGuiLineageViewNodeContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LineageViewContextGuiPluginTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void pluginHasPublicNoArgConstructor() throws Exception {
    assertTrue(Modifier.isPublic(LineageViewContextGuiPlugin.class.getModifiers()));
    assertNotNull(LineageViewContextGuiPlugin.class.getDeclaredConstructor());
    assertNotNull(LineageViewContextGuiPlugin.class.getDeclaredConstructor().newInstance());
  }

  @Test
  void createLambdaFindsAiHelpOnContextPlugin() {
    GuiAction pluginAction =
        new GuiAction(
            LineageViewContextGuiPlugin.ACTION_ID_AI_HELP,
            GuiActionType.Modify,
            "AI Help",
            "Ask the AI assistant",
            "ai-provider.svg",
            LineageViewContextGuiPlugin.class.getName(),
            "openAiAdvisorNodeContext");
    pluginAction.setClassLoader(LineageViewContextGuiPlugin.class.getClassLoader());
    HopGuiLineageViewNodeContext context = new HopGuiLineageViewNodeContext(null, null, null, null);
    GuiAction bound =
        new GuiActionLambdaBuilder<HopGuiLineageViewNodeContext>()
            .createLambda(pluginAction, context, null);
    assertNotNull(bound.getActionLambda());
    assertEquals(LineageViewContextGuiPlugin.ACTION_ID_AI_HELP, bound.getId());
  }

  @Test
  void lineageGraphDoesNotRegisterAiHelpContextAction() {
    for (Method method : HopGuiLineageViewGraph.class.getDeclaredMethods()) {
      GuiContextAction action = method.getAnnotation(GuiContextAction.class);
      if (action != null) {
        assertFalse(
            LineageViewContextGuiPlugin.ACTION_ID_AI_HELP.equals(action.id()),
            "AI Help must not be a @GuiContextAction on the RAP widget class");
      }
      assertFalse(
          "openAiAdvisorNodeContext".equals(method.getName()),
          "openAiAdvisorNodeContext must live on LineageViewContextGuiPlugin for Hop Web");
    }
  }

  @Test
  void contextPluginDeclaresAiHelp() throws Exception {
    Method method =
        LineageViewContextGuiPlugin.class.getMethod(
            "openAiAdvisorNodeContext", HopGuiLineageViewNodeContext.class);
    GuiContextAction action = method.getAnnotation(GuiContextAction.class);
    assertNotNull(action);
    assertEquals(LineageViewContextGuiPlugin.ACTION_ID_AI_HELP, action.id());
    assertEquals(HopGuiLineageViewNodeContext.CONTEXT_ID, action.parentId());
    assertEquals("i18n::LineageViewContextGuiPlugin.AiHelp.Name", action.name());
    assertEquals(
        "AI Help", TranslateUtil.translate(action.name(), LineageViewContextGuiPlugin.class));
    assertEquals(
        "Ask the AI assistant about this lineage graph (chat only, read-only)",
        TranslateUtil.translate(action.tooltip(), LineageViewContextGuiPlugin.class));
  }

  @Test
  void contextActionLabelsResolveOnThePluginClass() throws Exception {
    for (Method method : LineageViewContextGuiPlugin.class.getDeclaredMethods()) {
      GuiContextAction action = method.getAnnotation(GuiContextAction.class);
      if (action == null) {
        continue;
      }
      String name = TranslateUtil.translate(action.name(), LineageViewContextGuiPlugin.class);
      String tooltip = TranslateUtil.translate(action.tooltip(), LineageViewContextGuiPlugin.class);
      assertFalse(name.startsWith("i18n:"), method.getName() + " name: " + name);
      assertFalse(name.startsWith("!"), method.getName() + " name: " + name);
      assertFalse(tooltip.startsWith("i18n:"), method.getName() + " tooltip: " + tooltip);
      assertFalse(tooltip.startsWith("!"), method.getName() + " tooltip: " + tooltip);
    }
  }
}
