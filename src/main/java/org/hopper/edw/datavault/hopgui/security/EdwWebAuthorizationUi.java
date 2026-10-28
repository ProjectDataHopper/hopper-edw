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

import org.apache.hop.core.security.Permission;
import org.apache.hop.ui.core.security.HopSecurityUi;
import org.hopper.edw.security.EdwWebAuthorization;

/** Dialog-producing checks for deliberate GUI actions. Drag paths stay silent. */
public final class EdwWebAuthorizationUi {

  private EdwWebAuthorizationUi() {}

  public static boolean allowModelEdit() {
    return HopSecurityUi.check(Permission.FILE_EDIT);
  }

  public static boolean allowSaveFiles() {
    return HopSecurityUi.check(Permission.FILE_SAVE);
  }

  public static boolean allowCreateFiles() {
    return HopSecurityUi.check(Permission.FILE_CREATE);
  }

  public static boolean allowExecute() {
    return HopSecurityUi.check(Permission.RUN_EXECUTE);
  }

  public static boolean allowView() {
    return HopSecurityUi.check(Permission.FILE_VIEW);
  }

  public static boolean allowCatalogEdit() {
    if (EdwWebAuthorization.canEditCatalog()) {
      return true;
    }
    HopSecurityUi.deny(Permission.METADATA_WRITE);
    return false;
  }
}
