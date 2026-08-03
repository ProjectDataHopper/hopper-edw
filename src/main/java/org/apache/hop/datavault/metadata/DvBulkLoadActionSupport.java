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
package org.apache.hop.datavault.metadata;

import java.lang.reflect.Method;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.plugins.ActionPluginType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.util.Utils;
import org.apache.hop.workflow.action.IAction;

/** Loads workflow bulk-load actions via the Hop plugin registry. */
public final class DvBulkLoadActionSupport {

  private DvBulkLoadActionSupport() {}

  public static IAction loadAction(String actionPluginId) throws HopException {
    if (Utils.isEmpty(actionPluginId)) {
      throw new HopException("Workflow action plugin id is required");
    }
    try {
      IAction action =
          PluginRegistry.getInstance()
              .loadClass(ActionPluginType.class, actionPluginId, IAction.class);
      if (action == null) {
        throw new HopException("Workflow action plugin '" + actionPluginId + "' is not installed");
      }
      return action;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to load workflow action plugin '" + actionPluginId + "'", e);
    }
  }

  public static IAction newConfiguredAction(String actionPluginId, String actionName)
      throws HopException {
    IAction action = loadAction(actionPluginId);
    action.setName(actionName);
    return action;
  }

  public static void invoke(IAction action, String methodName, Class<?> argType, Object arg)
      throws HopException {
    try {
      Method method = action.getClass().getMethod(methodName, argType);
      method.invoke(action, arg);
    } catch (Exception e) {
      throw new HopException(
          "Unable to configure workflow action '" + action.getName() + "' (" + methodName + ")", e);
    }
  }
}
