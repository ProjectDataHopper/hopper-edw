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
package org.apache.hop.datavault.metadata.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;

/** Factory for a standard calendar date dimension and common role-playing aliases. */
public final class DmDateDimensionTemplate {

  public static final String DEFAULT_DIMENSION_NAME = "dim_date";
  public static final String DEFAULT_TABLE_NAME = "d_date";

  public static final List<String> COMMON_DATE_ALIAS_NAMES =
      List.of("dim_order_date", "dim_shipment_date", "dim_delivery_date", "dim_return_date");

  private DmDateDimensionTemplate() {}

  public static DmDimension createDateDimension(Point location) {
    DmDimension dimension = new DmDimension();
    dimension.setName(DEFAULT_DIMENSION_NAME);
    dimension.setTableName(DEFAULT_TABLE_NAME);
    dimension.setDescription("Calendar date dimension");
    dimension.setScdType(DmDimensionScdType.TYPE1);
    dimension.setLocation(location != null ? location.x : 0, location != null ? location.y : 0);
    dimension.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.NONE);
    DmSourceConfiguration source = dimension.getSourceOrDefault();
    source.setSourceType(DmSourceType.DATE_GENERATOR);
    source.setDateGenerator(DmDateGeneratorConfiguration.createDefault());
    source.setSourceSql("");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("date_key"));
    addAttribute(dimension, "full_date");
    addAttribute(dimension, "day_of_week");
    addAttribute(dimension, "day_of_month");
    addAttribute(dimension, "day_of_year");
    addAttribute(dimension, "week_of_year");
    addAttribute(dimension, "month");
    addAttribute(dimension, "month_name");
    addAttribute(dimension, "quarter");
    addAttribute(dimension, "year");
    addAttribute(dimension, "is_weekend");
    return dimension;
  }

  public static DmDimensionAlias createDateAlias(
      String aliasName, String referencedDimensionName, Point location) {
    DmDimensionAlias alias = new DmDimensionAlias();
    alias.setName(aliasName);
    alias.setReferencedDimensionName(referencedDimensionName);
    alias.setDescription("Role-playing alias of " + referencedDimensionName);
    alias.setTableName(DEFAULT_TABLE_NAME);
    alias.setLocation(location != null ? location.x : 0, location != null ? location.y : 0);
    return alias;
  }

  public static List<DmDimensionAlias> createCommonDateAliases(
      String referencedDimensionName, Point startLocation) {
    List<DmDimensionAlias> aliases = new ArrayList<>();
    int x = startLocation != null ? startLocation.x : 0;
    int y = startLocation != null ? startLocation.y : 0;
    for (String aliasName : COMMON_DATE_ALIAS_NAMES) {
      aliases.add(createDateAlias(aliasName, referencedDimensionName, new Point(x, y)));
      y += 120;
    }
    return aliases;
  }

  public static boolean isDateDimensionPresent(DimensionalModel model) {
    if (model == null) {
      return false;
    }
    for (IDmTable table : model.getTables()) {
      if (table != null && DEFAULT_DIMENSION_NAME.equals(table.getName())) {
        return true;
      }
    }
    return false;
  }

  public static List<String> missingCommonAliases(
      DimensionalModel model, String referencedDimensionName) {
    List<String> missing = new ArrayList<>();
    if (model == null || Utils.isEmpty(referencedDimensionName)) {
      return missing;
    }
    for (String aliasName : COMMON_DATE_ALIAS_NAMES) {
      if (model.findTable(aliasName) == null) {
        missing.add(aliasName);
      }
    }
    return missing;
  }

  private static void addAttribute(DmDimension dimension, String fieldName) {
    DmDimensionAttribute attribute = new DmDimensionAttribute();
    attribute.setFieldName(fieldName);
    attribute.setScdUpdatePolicy(DmScdUpdatePolicy.TYPE1);
    dimension.getAttributes().add(attribute);
  }
}
