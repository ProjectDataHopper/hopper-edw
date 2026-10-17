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

/** Decides which source-model cards are JDBC virtual tables. */
public final class SourceJdbcExposure {

  private SourceJdbcExposure() {}

  /**
   * Whether {@code card} is exposed through JDBC. A hidden {@link IJdbcHiddenSource} is omitted. Any
   * other non-null object stays exposed.
   */
  public static boolean include(Object card) {
    if (card instanceof IJdbcHiddenSource source) {
      return !source.isHidden();
    }
    return card != null;
  }
}
