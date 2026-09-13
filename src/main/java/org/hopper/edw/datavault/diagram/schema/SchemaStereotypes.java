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
package org.hopper.edw.datavault.diagram.schema;

/** Kimball / Data Vault stereotypes and matching header/border colors. */
public final class SchemaStereotypes {

  public static final String FACT = "Fact";
  public static final String DIMENSION = "Dimension";
  public static final String BRIDGE = "Bridge";
  public static final String HUB = "Hub";
  public static final String LINK = "Link";
  public static final String SATELLITE = "Satellite";
  public static final String SCD2 = "SCD2";
  public static final String PIT = "PIT";
  public static final String BUSINESS_TABLE = "BusinessTable";
  public static final String SOURCE_QUERY = "SourceQuery";
  public static final String TABLE = "Table";
  public static final String DATA_VAULT = "DataVault";

  private SchemaStereotypes() {}

  public static String headerColor(String stereotype) {
    if (stereotype == null) {
      return "#ffffff";
    }
    return switch (stereotype) {
      case FACT -> "#E8F4F8";
      case DIMENSION -> "#EBF8F2";
      case BRIDGE -> "#FEFCBF";
      case HUB -> "#d5e8d4";
      case LINK -> "#dae8fc";
      case SATELLITE -> "#fff2cc";
      case SCD2 -> "#e1d5e7";
      case PIT -> "#dae8fc";
      case BUSINESS_TABLE -> "#d5e8d4";
      case SOURCE_QUERY -> "#ffe6cc";
      case DATA_VAULT -> "#f5f5f5";
      default -> "#ffffff";
    };
  }

  public static String borderColor(String stereotype) {
    if (stereotype == null) {
      return "#666666";
    }
    return switch (stereotype) {
      case FACT -> "#2B6CB0";
      case DIMENSION -> "#276749";
      case BRIDGE -> "#B7791F";
      case HUB -> "#82b366";
      case LINK -> "#6c8ebf";
      case SATELLITE -> "#d6b656";
      case SCD2 -> "#9673a6";
      case PIT -> "#6c8ebf";
      case BUSINESS_TABLE -> "#82b366";
      case SOURCE_QUERY -> "#d79b00";
      default -> "#666666";
    };
  }
}
