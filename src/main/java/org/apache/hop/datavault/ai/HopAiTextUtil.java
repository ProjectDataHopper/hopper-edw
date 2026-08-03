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
package org.apache.hop.datavault.ai;

import org.apache.hop.core.util.Utils;

public final class HopAiTextUtil {

  private HopAiTextUtil() {}

  public static String truncate(String value, int maxChars) {
    if (Utils.isEmpty(value) || maxChars <= 0) {
      return value != null ? value : "";
    }
    if (value.length() <= maxChars) {
      return value;
    }
    return value.substring(0, maxChars) + "\n... [truncated]";
  }

  public static String jsonString(String value) {
    if (value == null) {
      return "null";
    }
    return "\""
        + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
        + "\"";
  }
}
