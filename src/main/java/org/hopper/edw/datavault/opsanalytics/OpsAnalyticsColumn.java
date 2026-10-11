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
package org.hopper.edw.datavault.opsanalytics;

import org.apache.hop.core.row.IValueMeta;

/** One column of an operational analytics source table or conformed-key query. */
public record OpsAnalyticsColumn(String name, int hopType, int length, int primaryKeyPosition) {

  public static OpsAnalyticsColumn str(String name, int length) {
    return new OpsAnalyticsColumn(name, IValueMeta.TYPE_STRING, length, 0);
  }

  public static OpsAnalyticsColumn keyStr(String name, int length, int position) {
    return new OpsAnalyticsColumn(name, IValueMeta.TYPE_STRING, length, position);
  }

  public static OpsAnalyticsColumn num(String name) {
    return new OpsAnalyticsColumn(name, IValueMeta.TYPE_INTEGER, 0, 0);
  }

  public static OpsAnalyticsColumn keyNum(String name, int position) {
    return new OpsAnalyticsColumn(name, IValueMeta.TYPE_INTEGER, 0, position);
  }

  public static OpsAnalyticsColumn date(String name) {
    return new OpsAnalyticsColumn(name, IValueMeta.TYPE_DATE, 0, 0);
  }

  public static OpsAnalyticsColumn bool(String name) {
    return new OpsAnalyticsColumn(name, IValueMeta.TYPE_BOOLEAN, 0, 0);
  }

  public boolean primaryKey() {
    return primaryKeyPosition > 0;
  }
}
