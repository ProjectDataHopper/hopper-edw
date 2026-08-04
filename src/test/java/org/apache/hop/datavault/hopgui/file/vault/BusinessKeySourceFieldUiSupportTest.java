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
package org.apache.hop.datavault.hopgui.file.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.BusinessKeySource;
import org.junit.jupiter.api.Test;

class BusinessKeySourceFieldUiSupportTest {

  @Test
  void parseAndFormatCompositeSourceFields() {
    BusinessKey bk = new BusinessKey("burger_bk");
    BusinessKeySourceFieldUiSupport.applyToBusinessKey(bk, true, "num_seq_bkcc_bk, num_seq_bk");
    assertTrue(bk.isComposite());
    assertEquals(List.of("num_seq_bkcc_bk", "num_seq_bk"), bk.resolveSourceParts());
    assertEquals(
        "num_seq_bkcc_bk, num_seq_bk", BusinessKeySourceFieldUiSupport.formatSourceFields(bk));
  }

  @Test
  void applyNonCompositeUsesSingleSourceField() {
    BusinessKey bk = new BusinessKey("customer_id");
    BusinessKeySourceFieldUiSupport.applyToBusinessKey(bk, false, "cust_no");
    assertFalse(bk.isComposite());
    assertEquals(List.of("cust_no"), bk.resolveSourceParts());
    assertEquals("cust_no", bk.getSourceFieldName());
    assertTrue(bk.getSourceFieldNames() == null || bk.getSourceFieldNames().isEmpty());
  }

  @Test
  void applyBusinessKeySourceMultiPart() {
    BusinessKeySource source = new BusinessKeySource();
    source.setBusinessKeyField("burger_bk");
    BusinessKeySourceFieldUiSupport.applyToBusinessKeySource(source, "a; b");
    assertEquals(List.of("a", "b"), source.resolveSourceParts());
  }

  @Test
  void compositeYesNoParsing() {
    assertTrue(BusinessKeySourceFieldUiSupport.isCompositeYes("Y"));
    assertTrue(BusinessKeySourceFieldUiSupport.isCompositeYes("yes"));
    assertFalse(BusinessKeySourceFieldUiSupport.isCompositeYes("N"));
    assertFalse(BusinessKeySourceFieldUiSupport.isCompositeYes(""));
  }
}
