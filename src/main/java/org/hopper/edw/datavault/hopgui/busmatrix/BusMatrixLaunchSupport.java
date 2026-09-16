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
package org.hopper.edw.datavault.hopgui.busmatrix;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.datavault.resourcedefinition.ResourceDefinitionGroupResolver;

/** Opens the bus matrix viewer for a resource definition group. */
public final class BusMatrixLaunchSupport {

  private static final Class<?> PKG = BusMatrixLaunchSupport.class;

  private BusMatrixLaunchSupport() {}

  public static void open(HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return;
    }
    try {
      new BusMatrixViewerDialog(
              hopGui.getShell(), hopGui, group, hopGui.getVariables(), hopGui.getMetadataProvider())
          .open();
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "BusMatrixLaunchSupport.Error.Title"),
          BaseMessages.getString(PKG, "BusMatrixLaunchSupport.Error.Message"),
          e);
    }
  }

  public static void openForModel(HopGui hopGui, String modelFilename) {
    if (hopGui == null) {
      return;
    }
    try {
      List<ResourceDefinitionGroupMeta> matches =
          findGroupsForModel(hopGui.getMetadataProvider(), hopGui.getVariables(), modelFilename);
      if (matches.isEmpty()) {
        openPicker(hopGui);
        return;
      }
      if (matches.size() == 1) {
        open(hopGui, matches.get(0));
        return;
      }
      String[] names =
          matches.stream().map(ResourceDefinitionGroupMeta::getName).toArray(String[]::new);
      EnterSelectionDialog dialog =
          new EnterSelectionDialog(
              hopGui.getShell(),
              names,
              BaseMessages.getString(PKG, "BusMatrixLaunchSupport.Pick.Title"),
              BaseMessages.getString(PKG, "BusMatrixLaunchSupport.Pick.Message"));
      String selected = dialog.open();
      if (Utils.isEmpty(selected)) {
        return;
      }
      for (ResourceDefinitionGroupMeta group : matches) {
        if (selected.equals(group.getName())) {
          open(hopGui, group);
          return;
        }
      }
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "BusMatrixLaunchSupport.Error.Title"),
          BaseMessages.getString(PKG, "BusMatrixLaunchSupport.Error.Message"),
          e);
    }
  }

  public static List<ResourceDefinitionGroupMeta> findGroupsForModel(
      IHopMetadataProvider provider, IVariables variables, String modelFilename)
      throws HopException {
    return ResourceDefinitionGroupResolver.findGroupsForDimensionalModel(
        provider, variables, modelFilename);
  }

  public static void openPicker(HopGui hopGui) {
    if (hopGui == null) {
      return;
    }
    try {
      IHopMetadataProvider provider = hopGui.getMetadataProvider();
      List<String> names =
          provider.getSerializer(ResourceDefinitionGroupMeta.class).listObjectNames();
      if (names == null || names.isEmpty()) {
        throw new HopException(
            BaseMessages.getString(PKG, "BusMatrixLaunchSupport.Error.NoGroups"));
      }
      EnterSelectionDialog dialog =
          new EnterSelectionDialog(
              hopGui.getShell(),
              names.toArray(String[]::new),
              BaseMessages.getString(PKG, "BusMatrixLaunchSupport.Pick.Title"),
              BaseMessages.getString(PKG, "BusMatrixLaunchSupport.Pick.Message"));
      String selected = dialog.open();
      if (Utils.isEmpty(selected)) {
        return;
      }
      ResourceDefinitionGroupMeta group =
          ResourceDefinitionGroupResolver.loadGroup(selected, provider);
      open(hopGui, group);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "BusMatrixLaunchSupport.Error.Title"),
          BaseMessages.getString(PKG, "BusMatrixLaunchSupport.Error.Message"),
          e);
    }
  }
}
