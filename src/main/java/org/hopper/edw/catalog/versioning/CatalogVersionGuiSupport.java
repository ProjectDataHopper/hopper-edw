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
package org.hopper.edw.catalog.versioning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.EnterStringDialog;
import org.apache.hop.ui.core.dialog.EnterTextDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;

/** GUI entry points for tagging and listing catalog versions. */
public final class CatalogVersionGuiSupport {

  private static final Class<?> PKG = CatalogVersionGuiSupport.class;

  private CatalogVersionGuiSupport() {}

  /**
   * Tag a catalog version from a known resource definition group (group editor). Uses the group's
   * default catalog connection.
   */
  public static CatalogVersionEntry tagVersionFromGroup(
      HopGui hopGui, ResourceDefinitionGroupMeta group) {
    return tagVersionWithPrompts(hopGui, group, null);
  }

  /**
   * Tag a catalog version from the Data Catalog perspective: pick a resource definition group
   * (required for source scope), then tag + description. When {@code preferredCatalogConnection} is
   * set (tree selection), that FILE catalog is used as the version storage root.
   */
  public static CatalogVersionEntry tagVersionFromPerspective(
      HopGui hopGui, String preferredCatalogConnection) {
    if (hopGui == null) {
      return null;
    }
    Shell shell = hopGui.getShell();
    try {
      IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();
      IVariables variables = hopGui.getVariables();
      List<String> groupNames =
          listGroupNamesPreferringConnection(
              preferredCatalogConnection, variables, metadataProvider);
      if (groupNames.isEmpty()) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_WARNING);
        box.setText(BaseMessages.getString(PKG, "CatalogVersionGuiSupport.PickGroup.Empty.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "CatalogVersionGuiSupport.PickGroup.Empty.Message"));
        box.open();
        return null;
      }

      EnterSelectionDialog pickGroup =
          new EnterSelectionDialog(
              shell,
              groupNames.toArray(new String[0]),
              BaseMessages.getString(PKG, "CatalogVersionGuiSupport.PickGroup.Title"),
              BaseMessages.getString(PKG, "CatalogVersionGuiSupport.PickGroup.Message"));
      String selectedName = pickGroup.open(0);
      if (Utils.isEmpty(selectedName)) {
        return null;
      }

      ResourceDefinitionGroupMeta group =
          metadataProvider
              .getSerializer(ResourceDefinitionGroupMeta.class)
              .load(selectedName.trim());
      if (group == null) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "CatalogVersionGuiSupport.PickGroup.NotFound", selectedName.trim()));
      }
      return tagVersionWithPrompts(hopGui, group, preferredCatalogConnection);
    } catch (Exception e) {
      showError(shell, e);
      return null;
    }
  }

  private static CatalogVersionEntry tagVersionWithPrompts(
      HopGui hopGui, ResourceDefinitionGroupMeta group, String catalogConnectionOverride) {
    if (hopGui == null || group == null) {
      return null;
    }
    Shell shell = hopGui.getShell();
    try {
      EnterStringDialog tagDialog =
          new EnterStringDialog(
              shell,
              "",
              BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Tag.Title"),
              BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Tag.Message"));
      tagDialog.setMandatory(true);
      String tag = tagDialog.open();
      if (Utils.isEmpty(tag)) {
        return null;
      }

      EnterStringDialog descriptionDialog =
          new EnterStringDialog(
              shell,
              "",
              BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Tag.DescriptionTitle"),
              BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Tag.DescriptionMessage"));
      String description = descriptionDialog.open();
      if (description == null) {
        description = "";
      }

      String createdBy = System.getProperty("user.name", "");
      CatalogVersionEntry entry =
          CatalogVersionService.createFromGroup(
              catalogConnectionOverride,
              group,
              tag.trim(),
              description.trim(),
              createdBy,
              hopGui.getVariables(),
              hopGui.getMetadataProvider());

      if (shell != null && !shell.isDisposed()) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Tag.Success.Title"));
        box.setMessage(
            BaseMessages.getString(
                PKG,
                "CatalogVersionGuiSupport.Tag.Success.Message",
                entry.getTag(),
                Integer.toString(entry.getRecordCount())));
        box.open();
      }
      return entry;
    } catch (Exception e) {
      showError(shell, e);
      return null;
    }
  }

  public static void listVersionsForGroup(HopGui hopGui, ResourceDefinitionGroupMeta group) {
    if (hopGui == null || group == null) {
      return;
    }
    Shell shell = hopGui.getShell();
    try {
      String connection =
          resolveConnection(group, hopGui.getVariables(), hopGui.getMetadataProvider());
      if (Utils.isEmpty(connection)) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_WARNING);
        box.setText(BaseMessages.getString(PKG, "CatalogVersionGuiSupport.List.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "CatalogVersionGuiSupport.List.MissingConnection"));
        box.open();
        return;
      }

      List<CatalogVersionEntry> versions =
          CatalogVersionService.listVersions(
              connection, hopGui.getVariables(), hopGui.getMetadataProvider());
      if (versions.isEmpty()) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "CatalogVersionGuiSupport.List.Empty.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "CatalogVersionGuiSupport.List.Empty.Message", connection));
        box.open();
        return;
      }

      StringBuilder text = new StringBuilder();
      for (CatalogVersionEntry entry : versions) {
        if (entry == null) {
          continue;
        }
        text.append(entry.getTag())
            .append("  |  ")
            .append(entry.getCreatedAt() != null ? entry.getCreatedAt() : "")
            .append("  |  records=")
            .append(entry.getRecordCount());
        if (!Utils.isEmpty(entry.getDescription())) {
          text.append("  |  ").append(entry.getDescription());
        }
        text.append('\n');
      }

      EnterTextDialog dialog =
          new EnterTextDialog(
              shell,
              BaseMessages.getString(PKG, "CatalogVersionGuiSupport.List.Title"),
              connection,
              text.toString(),
              true);
      dialog.setReadOnly();
      dialog.open();
    } catch (Exception e) {
      showError(shell, e);
    }
  }

  /**
   * Lists resource definition group names, putting groups whose catalog connection matches {@code
   * preferredCatalogConnection} first (case-insensitive). Package-visible for unit tests.
   */
  static List<String> listGroupNamesPreferringConnection(
      String preferredCatalogConnection,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (metadataProvider == null) {
      return List.of();
    }
    IHopMetadataSerializer<ResourceDefinitionGroupMeta> serializer =
        metadataProvider.getSerializer(ResourceDefinitionGroupMeta.class);
    List<String> names = new ArrayList<>(serializer.listObjectNames());
    if (names.isEmpty()) {
      return names;
    }
    String preferred =
        !Utils.isEmpty(preferredCatalogConnection) ? preferredCatalogConnection.trim() : null;
    names.sort(
        Comparator.comparing(
                (String name) -> !groupMatchesConnection(name, preferred, variables, serializer))
            .thenComparing(String.CASE_INSENSITIVE_ORDER));
    return names;
  }

  private static boolean groupMatchesConnection(
      String groupName,
      String preferredCatalogConnection,
      IVariables variables,
      IHopMetadataSerializer<ResourceDefinitionGroupMeta> serializer) {
    if (Utils.isEmpty(preferredCatalogConnection) || Utils.isEmpty(groupName)) {
      return false;
    }
    try {
      ResourceDefinitionGroupMeta group = serializer.load(groupName);
      if (group == null || Utils.isEmpty(group.getDataCatalogConnection())) {
        return false;
      }
      String connection =
          variables != null
              ? variables.resolve(group.getDataCatalogConnection())
              : group.getDataCatalogConnection();
      return preferredCatalogConnection.equalsIgnoreCase(Const.trim(connection));
    } catch (Exception e) {
      return false;
    }
  }

  private static String resolveConnection(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (group != null && !Utils.isEmpty(group.getDataCatalogConnection())) {
      return variables != null
          ? variables.resolve(group.getDataCatalogConnection())
          : group.getDataCatalogConnection();
    }
    return org.hopper.edw.datavault.catalog.DvSourceCatalogService
        .resolvePreferredCatalogConnection(null, variables, metadataProvider);
  }

  private static void showError(Shell shell, Exception e) {
    if (shell == null || shell.isDisposed()) {
      return;
    }
    new ErrorDialog(
        shell,
        BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Error.Title"),
        BaseMessages.getString(PKG, "CatalogVersionGuiSupport.Error.Message"),
        e instanceof HopException ? e : new HopException(e));
  }
}
