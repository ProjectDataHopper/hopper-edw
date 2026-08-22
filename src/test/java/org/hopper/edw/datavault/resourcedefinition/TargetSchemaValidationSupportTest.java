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
package org.hopper.edw.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TargetSchemaValidationSupportTest {

  @Test
  void looksLikeCreateOnlyRecognizesCreateTable() {
    assertTrue(
        TargetSchemaValidationSupport.looksLikeCreateOnly(
            List.of("CREATE TABLE sat_customer_address (\n  id INTEGER\n)")));
  }

  @Test
  void looksLikeCreateOnlyRejectsAlter() {
    assertFalse(
        TargetSchemaValidationSupport.looksLikeCreateOnly(
            List.of("ALTER TABLE sat_customer_address ADD COLUMN x VARCHAR(10)")));
  }

  @Test
  void looksLikeCreateOnlyRejectsMixedCreateAndAlter() {
    assertFalse(
        TargetSchemaValidationSupport.looksLikeCreateOnly(
            List.of("CREATE TABLE sat_x (id INT)", "ALTER TABLE sat_x ADD COLUMN y INT")));
  }

  @Test
  void looksLikeCreateOnlyAcceptsCreateIndexAlongsideCreateTable() {
    // Pending CREATE classification still holds when vault update also emits indexes.
    assertTrue(
        TargetSchemaValidationSupport.looksLikeCreateOnly(
            List.of("CREATE TABLE sat_x (id INT)", "CREATE INDEX idx_sat_x_hk ON sat_x (id)")));
  }
}
