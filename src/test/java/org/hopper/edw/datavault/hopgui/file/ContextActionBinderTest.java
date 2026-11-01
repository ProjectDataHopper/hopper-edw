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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.net.URL;
import java.net.URLClassLoader;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.junit.jupiter.api.Test;

class ContextActionBinderTest {

  @Test
  void bindsWhenTheLoadersAlreadyMatch() throws Exception {
    SplitLoaderPlugin.calls = 0;
    GuiAction action = action(SplitLoaderPlugin.class.getClassLoader());
    GuiAction bound = ContextActionBinder.bind(action, new SplitLoaderContext(), null);
    bound.getActionLambda().executeAction(false, false);
    assertEquals(1, SplitLoaderPlugin.calls);
  }

  @Test
  void bindsThePluginFromTheContextClassLoader() throws Exception {
    URL location = SplitLoaderPlugin.class.getProtectionDomain().getCodeSource().getLocation();
    try (URLClassLoader actionLoader =
            new URLClassLoader(new URL[] {location}, ClassLoader.getPlatformClassLoader());
        URLClassLoader contextLoader =
            new URLClassLoader(new URL[] {location}, ClassLoader.getPlatformClassLoader())) {
      Class<?> pluginOnAction = actionLoader.loadClass(SplitLoaderPlugin.class.getName());
      Class<?> pluginOnContext = contextLoader.loadClass(SplitLoaderPlugin.class.getName());
      Class<?> contextType = contextLoader.loadClass(SplitLoaderContext.class.getName());
      assertNotSame(pluginOnAction, pluginOnContext);

      Object context = contextType.getDeclaredConstructor().newInstance();
      GuiAction action = action(actionLoader);
      GuiAction bound = ContextActionBinder.bind(action, context, null);
      bound.getActionLambda().executeAction(false, false);

      assertEquals(0, pluginOnAction.getField("calls").get(null));
      assertEquals(1, pluginOnContext.getField("calls").get(null));
    }
  }

  private static GuiAction action(ClassLoader classLoader) {
    GuiAction action =
        new GuiAction(
            "run",
            GuiActionType.Modify,
            "run",
            "run",
            null,
            SplitLoaderPlugin.class.getName(),
            "run");
    action.setClassLoader(classLoader);
    return action;
  }
}
