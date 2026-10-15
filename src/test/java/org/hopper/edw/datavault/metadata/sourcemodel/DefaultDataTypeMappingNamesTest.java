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
package org.hopper.edw.datavault.metadata.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.hopper.edw.datavault.metadata.sourcemodel.DefaultDataTypeMappingNames.EditAction;
import org.junit.jupiter.api.Test;

class DefaultDataTypeMappingNamesTest {

  @Test
  void parseSplitsCommaAndSemicolon() {
    assertEquals(
        List.of("premodel-defaults", "crm-strings"),
        DefaultDataTypeMappingNames.parse(" premodel-defaults , crm-strings; "));
    assertEquals(List.of(), DefaultDataTypeMappingNames.parse(null));
    assertEquals("", DefaultDataTypeMappingNames.format(List.of()));
    assertEquals(
        "premodel-defaults, crm-strings",
        DefaultDataTypeMappingNames.format(List.of("premodel-defaults", "crm-strings")));
  }

  @Test
  void editActionFollowsTheField() {
    assertEquals(EditAction.CREATE, DefaultDataTypeMappingNames.editAction(List.of()));
    assertEquals(EditAction.CREATE, DefaultDataTypeMappingNames.editAction(null));
    assertEquals(EditAction.EDIT_ONE, DefaultDataTypeMappingNames.editAction(List.of("one")));
    assertEquals(
        EditAction.ASK, DefaultDataTypeMappingNames.editAction(List.of("one", "two")));
  }

  @Test
  void addAndRenameKeepTheList() {
    assertEquals(List.of("one", "two"), DefaultDataTypeMappingNames.add(List.of("one"), "two"));
    assertEquals(List.of("one"), DefaultDataTypeMappingNames.add(List.of("one"), "one"));

    assertEquals(
        List.of("renamed", "two"),
        DefaultDataTypeMappingNames.rename(List.of("one", "two"), "one", "renamed"));
    assertEquals(
        List.of("two"), DefaultDataTypeMappingNames.rename(List.of("one", "two"), "one", "two"));
    assertEquals(
        List.of("one"), DefaultDataTypeMappingNames.rename(List.of("one"), "missing", "other"));
  }
}
