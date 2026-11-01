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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.IGuiRefresher;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionLambdaBuilder;

/**
 * Binds {@code @GuiContextAction} methods for a modeler context menu.
 *
 * <p>{@link GuiActionLambdaBuilder} loads the plugin class from the loader stored when the action
 * was registered, then looks the method up with {@code Class.getMethod(name, context.getClass())}.
 * That lookup matches the parameter type by class identity. On Hop Web the registered loader and
 * the loader that defined the live context are two copies of the same class, so the lookup throws
 * {@code NoSuchMethodException} even though the method exists (issue #243). Loading the plugin
 * class from the context's own loader makes the parameter type match.
 */
public final class ContextActionBinder {

  private ContextActionBinder() {}

  public static <T> GuiAction bind(GuiAction action, T context, IGuiRefresher refresher) {
    ClassLoader contextLoader = context == null ? null : context.getClass().getClassLoader();
    if (contextLoader != null && contextLoader != action.getClassLoader()) {
      GuiAction aligned = new GuiAction(action);
      aligned.setClassLoader(contextLoader);
      try {
        return new GuiActionLambdaBuilder<T>().createLambda(aligned, context, refresher);
      } catch (RuntimeException alignedFailure) {
        if (!causedByMethodLookup(alignedFailure)) {
          throw alignedFailure;
        }
      }
    }
    return new GuiActionLambdaBuilder<T>().createLambda(action, context, refresher);
  }

  private static boolean causedByMethodLookup(Throwable failure) {
    Throwable cause = failure;
    while (cause != null) {
      if (cause instanceof NoSuchMethodException || cause instanceof ClassNotFoundException) {
        return true;
      }
      if (cause instanceof HopException
          && cause.getMessage() != null
          && cause.getMessage().startsWith("Unable to find class")) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
