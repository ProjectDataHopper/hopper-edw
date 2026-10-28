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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.security.HopRole;
import org.apache.hop.core.security.HopSecurity;
import org.apache.hop.core.security.HopSecurityContext;
import org.apache.hop.core.security.Permission;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EdwWebAuthorizationTest {

  @AfterEach
  void resetSecurity() {
    HopSecurity.reset();
  }

  @Test
  void unrestrictedSessionCanEditModelsAndCatalog() {
    HopSecurity.reset();
    assertTrue(EdwWebAuthorization.canEditModels());
    assertTrue(EdwWebAuthorization.canSaveFiles());
    assertTrue(EdwWebAuthorization.canCreateFiles());
    assertTrue(EdwWebAuthorization.canExecute());
    assertTrue(EdwWebAuthorization.canExport());
    assertTrue(EdwWebAuthorization.canEditCatalog());
  }

  @Test
  void adminAndUserCanEditModels() {
    as(HopRole.ADMIN);
    assertTrue(EdwWebAuthorization.canEditModels());
    assertTrue(EdwWebAuthorization.canEditCatalog());

    as(HopRole.USER);
    assertTrue(EdwWebAuthorization.canEditModels());
    assertTrue(EdwWebAuthorization.canSaveFiles());
    assertTrue(EdwWebAuthorization.canEditCatalog());
  }

  @Test
  void operatorCanRunAndEditCatalogButNotModels() {
    as(HopRole.OPERATOR);
    assertTrue(EdwWebAuthorization.canView());
    assertTrue(EdwWebAuthorization.canExecute());
    assertTrue(EdwWebAuthorization.canExport());
    assertTrue(EdwWebAuthorization.canEditCatalog());
    assertFalse(EdwWebAuthorization.canEditModels());
    assertFalse(EdwWebAuthorization.canSaveFiles());
    assertFalse(EdwWebAuthorization.canCreateFiles());
  }

  @Test
  void readOnlyIsViewOnly() {
    as(HopRole.READ_ONLY);
    assertTrue(EdwWebAuthorization.canView());
    assertFalse(EdwWebAuthorization.canEditModels());
    assertFalse(EdwWebAuthorization.canSaveFiles());
    assertFalse(EdwWebAuthorization.canCreateFiles());
    assertFalse(EdwWebAuthorization.canExecute());
    assertFalse(EdwWebAuthorization.canExport());
    assertFalse(EdwWebAuthorization.canEditCatalog());
  }

  @Test
  void operatorCombinedWithUserKeepsModelEdit() {
    HopSecurity.setProvider(
        () -> HopSecurityContext.forUser("ada", EnumSet.of(HopRole.OPERATOR, HopRole.USER)));
    assertTrue(EdwWebAuthorization.canEditModels());
    assertTrue(EdwWebAuthorization.canEditCatalog());
  }

  @Test
  void customRoleWithoutMetadataWriteCannotEditCatalog() {
    HopSecurity.setProvider(
        () ->
            HopSecurityContext.forUserWithPermissions(
                "ada", Set.of("auditor"), EnumSet.of(Permission.FILE_VIEW, Permission.METADATA_READ)));
    assertFalse(EdwWebAuthorization.canEditCatalog());
    assertFalse(EdwWebAuthorization.canEditModels());
  }

  @Test
  void catalogRegistryRejectsReadOnlyAndAllowsOperator() {
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();

    as(HopRole.READ_ONLY);
    HopException denied =
        assertThrows(HopException.class, () -> registry.create("catalog", null, null, null));
    assertTrue(denied.getMessage().contains(EdwWebAuthorization.CATALOG_EDIT_DENIED));

    as(HopRole.OPERATOR);
    assertDoesNotThrow(EdwWebAuthorization::requireCatalogEdit);

    HopSecurity.reset();
    assertDoesNotThrow(EdwWebAuthorization::requireCatalogEdit);
  }

  private static void as(HopRole role) {
    HopSecurity.setProvider(() -> HopSecurityContext.forUser("ada", EnumSet.of(role)));
  }
}
