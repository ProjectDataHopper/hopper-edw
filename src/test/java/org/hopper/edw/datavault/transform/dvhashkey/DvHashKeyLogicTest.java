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
package org.hopper.edw.datavault.transform.dvhashkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.HashContentCasing;
import org.hopper.edw.datavault.metadata.HashKeyDataType;
import org.junit.jupiter.api.Test;

class DvHashKeyLogicTest {

  @Test
  void integerLengthDoesNotPadHashInput() throws Exception {
    ValueMetaInteger formatted = lengthOnlyIntegerMeta(9);
    assertEquals(" 000001001", formatted.getString(1001L));
    assertEquals("1001", formatted.getCompatibleString(1001L));

    byte[] paddedMeta = hashWith(formatted, 1001L);
    byte[] fromString = hashString("1001");

    assertEquals(format(fromString), format(paddedMeta));
    assertEquals("184-195-126-51-222-253-229-28-249-30-30-3-229-22-87-218", format(fromString));
    assertNotEquals(
        format(fromString),
        format(hashString(formatted.getString(1001L))),
        "formatted getString() must not be used as hash input");
  }

  private static ValueMetaInteger lengthOnlyIntegerMeta(int length) {
    // Select Values type/length metadata clears the default ####0 mask, so getString() pads.
    ValueMetaInteger meta = new ValueMetaInteger("customer_id");
    meta.setLength(length);
    meta.setPrecision(0);
    meta.setConversionMask(null);
    return meta;
  }

  private static byte[] hashWith(ValueMetaInteger meta, long value) throws Exception {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(meta);
    return DvHashKeyLogic.buildHashInput(
        new Object[] {value}, rowMeta, new int[] {0}, hashMeta(), new Variables());
  }

  private static byte[] hashString(String value) throws Exception {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("customer_id"));
    return DvHashKeyLogic.buildHashInput(
        new Object[] {value}, rowMeta, new int[] {0}, hashMeta(), new Variables());
  }

  private static DvHashKeyMeta hashMeta() {
    DvHashKeyMeta meta = new DvHashKeyMeta();
    meta.setHashKeyDataType(HashKeyDataType.STRING);
    meta.setHashContentCasing(HashContentCasing.UPPER);
    meta.getFields().add(new DvHashKeyField("customer_id"));
    meta.setResultFieldName("customer_hk");
    return meta;
  }

  private static String format(byte[] digest) throws Exception {
    java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
    return (String) DvHashKeyLogic.formatHashResult(md.digest(digest), HashKeyDataType.STRING);
  }
}
