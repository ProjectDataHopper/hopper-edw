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
package org.apache.hop.datavault.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HopAiM2PromptSupportTest {

  @Test
  void includesM2SchemaAndStandards() throws Exception {
    String supplement = HopAiM2PromptSupport.buildM2Supplement();
    assertTrue(supplement.contains("hop_proposals"));
    assertTrue(supplement.contains("ADD_TRANSFORM"));
    assertTrue(supplement.contains("Transform plugin type index JSON"));
  }
}
