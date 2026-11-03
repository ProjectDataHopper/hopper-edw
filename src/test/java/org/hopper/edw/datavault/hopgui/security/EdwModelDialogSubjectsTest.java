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
package org.hopper.edw.datavault.hopgui.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.EnumSet;
import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.gui.plugin.action.GuiAction;
import org.apache.hop.core.gui.plugin.action.GuiActionType;
import org.apache.hop.core.security.ActionPermissionMapper;
import org.apache.hop.core.security.HopDialogEditGuard;
import org.apache.hop.core.security.HopRole;
import org.apache.hop.core.security.HopSecurity;
import org.apache.hop.core.security.HopSecurityContext;
import org.hopper.edw.datavault.hopgui.file.businessvault.BusinessVaultContextGuiPlugin;
import org.hopper.edw.datavault.hopgui.file.dimensional.DimensionalModelContextGuiPlugin;
import org.hopper.edw.datavault.hopgui.file.sourcemodel.SourceModelContextGuiPlugin;
import org.hopper.edw.datavault.hopgui.file.vault.VaultContextGuiPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Issue #245: view-only roles can open table properties, and the dialog subject is read-only. */
class EdwModelDialogSubjectsTest {

  @AfterEach
  void resetSecurity() {
    HopSecurity.reset();
  }

  @Test
  void fileEditSubjectFollowsTheSessionRole() {
    HopSecurity.reset();
    assertFalse(HopDialogEditGuard.isReadOnly(EdwModelDialogSubjects.FILE_EDIT));

    as(HopRole.ADMIN);
    assertFalse(HopDialogEditGuard.isReadOnly(EdwModelDialogSubjects.FILE_EDIT));

    as(HopRole.USER);
    assertFalse(HopDialogEditGuard.isReadOnly(EdwModelDialogSubjects.FILE_EDIT));

    as(HopRole.READ_ONLY);
    assertTrue(HopDialogEditGuard.isReadOnly(EdwModelDialogSubjects.FILE_EDIT));

    as(HopRole.OPERATOR);
    assertTrue(HopDialogEditGuard.isReadOnly(EdwModelDialogSubjects.FILE_EDIT));
  }

  @Test
  void readOnlyKeepsInfoTableEditAndLosesModify() {
    as(HopRole.READ_ONLY);
    assertTrue(
        ActionPermissionMapper.allowsGuiAction(
            action("source-model-table-edit", GuiActionType.Info)));
    assertFalse(
        ActionPermissionMapper.allowsGuiAction(
            action("source-model-table-edit", GuiActionType.Modify)));

    as(HopRole.OPERATOR);
    assertTrue(
        ActionPermissionMapper.allowsGuiAction(action("bv-graph-edit-table", GuiActionType.Info)));
  }

  @Test
  void tablePropertyActionsAreInfo() {
    assertTableEditIsInfo(
        SourceModelContextGuiPlugin.class, "editTableAction", "source-model-table-edit");
    assertTableEditIsInfo(VaultContextGuiPlugin.class, "editTableAction", "vault-graph-edit-table");
    assertTableEditIsInfo(
        BusinessVaultContextGuiPlugin.class, "editBvTableAction", "bv-graph-edit-table");
    assertTableEditIsInfo(
        DimensionalModelContextGuiPlugin.class, "editTableAction", "dm-graph-edit-table");
  }

  private static void assertTableEditIsInfo(Class<?> plugin, String methodName, String id) {
    Method match = null;
    for (Method method : plugin.getDeclaredMethods()) {
      if (method.getName().equals(methodName)
          && method.getAnnotation(GuiContextAction.class) != null) {
        match = method;
        break;
      }
    }
    assertNotNull(match, plugin.getSimpleName() + "#" + methodName);
    GuiContextAction action = match.getAnnotation(GuiContextAction.class);
    assertEquals(id, action.id());
    assertEquals(GuiActionType.Info, action.type());
  }

  private static GuiAction action(String id, GuiActionType type) {
    GuiAction action = new GuiAction();
    action.setId(id);
    action.setType(type);
    action.setName("Edit");
    return action;
  }

  private static void as(HopRole role) {
    HopSecurity.setProvider(() -> HopSecurityContext.forUser("ada", EnumSet.of(role)));
  }
}
