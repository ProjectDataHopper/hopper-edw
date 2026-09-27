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
package org.hopper.edw.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hopper.edw.datavault.metadata.HashKeyDataType;
import org.junit.jupiter.api.Test;

class HashKeyPartitionLogicTest {

  @Test
  void hexAndBinaryUseTheFirstByte() {
    assertTrue(HashKeyPartitionLogic.inPartition("0a11", HashKeyDataType.HEX, 4, 10 % 4));
    assertFalse(HashKeyPartitionLogic.inPartition("0a11", HashKeyDataType.HEX, 4, 0));
    assertTrue(
        HashKeyPartitionLogic.inPartition(new byte[] {(byte) 0x0a}, HashKeyDataType.BINARY, 4, 10 % 4));
  }

  @Test
  void stringUsesTheTokenBeforeTheDash() {
    assertTrue(HashKeyPartitionLogic.inPartition("7-2-9", HashKeyDataType.STRING, 4, 7 % 4));
    assertFalse(HashKeyPartitionLogic.inPartition("7-2-9", HashKeyDataType.STRING, 4, 0));
  }
}
