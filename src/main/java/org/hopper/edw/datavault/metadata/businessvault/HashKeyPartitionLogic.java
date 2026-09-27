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

import org.hopper.edw.datavault.metadata.HashKeyDataType;

/**
 * First-byte modulus used when an identity map moves the partition key from the raw satellite hash
 * to {@code hk_durable}. Binary keys use the first byte. Hex keys use the first two characters.
 * String keys use the integer before the first dash, matching the SQL partition predicate.
 */
public final class HashKeyPartitionLogic {

  private HashKeyPartitionLogic() {}

  public static boolean inPartition(Object key, HashKeyDataType type, int count, int number) {
    if (count <= 1) {
      return true;
    }
    if (number < 0 || number >= count) {
      return false;
    }
    int first = firstUnit(key, type);
    return Math.floorMod(first, count) == number;
  }

  static int firstUnit(Object key, HashKeyDataType type) {
    if (key instanceof byte[] bytes) {
      return bytes.length == 0 ? 0 : bytes[0] & 0xff;
    }
    String text = key == null ? "" : key.toString();
    HashKeyDataType resolved = type != null ? type : HashKeyDataType.HEX;
    if (resolved == HashKeyDataType.STRING) {
      int dash = text.indexOf('-');
      String token = dash < 0 ? text : text.substring(0, dash);
      try {
        return Integer.parseInt(token.trim());
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    if (text.length() < 2) {
      return 0;
    }
    try {
      return Integer.parseInt(text.substring(0, 2), 16);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
