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
package org.hopper.edw.security;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.security.HopRole;
import org.apache.hop.core.security.HopSecurity;
import org.apache.hop.core.security.HopSecurityContext;
import org.apache.hop.core.security.Permission;

/**
 * Hop Web role policy for Data Hopper. Desktop and unauthenticated Hop Web use an unrestricted
 * context, so every check allows the action.
 *
 * <p>Catalog editing is the one exception to Hop's built-in {@code operator} role, which does not
 * grant {@link Permission#METADATA_WRITE}. An effective role id of {@code operator} may change
 * record definitions, tag catalog versions, and run validation. Model files stay on {@link
 * Permission#FILE_EDIT}.
 */
public final class EdwWebAuthorization {

  public static final String CATALOG_EDIT_DENIED =
      "Catalog changes are not allowed for the current Hop Web role";

  public static final String MODEL_EDIT_DENIED =
      "Model changes are not allowed for the current Hop Web role";

  private EdwWebAuthorization() {}

  public static boolean canView() {
    return HopSecurity.allows(Permission.FILE_VIEW);
  }

  public static boolean canEditModels() {
    return HopSecurity.allows(Permission.FILE_EDIT);
  }

  public static boolean canSaveFiles() {
    return HopSecurity.allows(Permission.FILE_SAVE);
  }

  public static boolean canCreateFiles() {
    return HopSecurity.allows(Permission.FILE_CREATE);
  }

  public static boolean canExport() {
    return HopSecurity.allows(Permission.FILE_EXPORT);
  }

  public static boolean canExecute() {
    return HopSecurity.allows(Permission.RUN_EXECUTE);
  }

  /**
   * Record-definition writes, catalog versions, and schema validation. Allowed for an unrestricted
   * session, any context with {@link Permission#METADATA_WRITE}, or the built-in {@code operator}
   * role.
   */
  public static boolean canEditCatalog() {
    HopSecurityContext context = HopSecurity.getContext();
    if (context == null || context.isUnrestricted()) {
      return true;
    }
    if (context.allows(Permission.METADATA_WRITE)) {
      return true;
    }
    return context.getRoleIds().contains(HopRole.OPERATOR.getId());
  }

  public static void requireCatalogEdit() throws HopException {
    if (!canEditCatalog()) {
      throw new HopException(CATALOG_EDIT_DENIED);
    }
  }
}
