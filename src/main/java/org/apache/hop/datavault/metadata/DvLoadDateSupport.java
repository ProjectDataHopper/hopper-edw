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
package org.apache.hop.datavault.metadata;

import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/** Resolves optional fixed load dates for Data Vault batch updates. */
public final class DvLoadDateSupport {

  public static final String LOAD_DATE_FORMAT_MASK = ValueMetaBase.DEFAULT_DATE_FORMAT_MASK;

  private DvLoadDateSupport() {}

  /**
   * Resolves the load date for a batch update.
   *
   * @param configuredLoadDate optional fixed load date string (variables resolved when provided)
   * @param variables workflow variables used for substitution
   * @return configured load date when set, otherwise the current date/time
   * @throws HopException when a non-empty value cannot be parsed
   */
  public static Date resolveLoadDate(String configuredLoadDate, IVariables variables)
      throws HopException {
    String resolved =
        variables != null ? variables.resolve(configuredLoadDate) : configuredLoadDate;
    if (Utils.isEmpty(resolved)) {
      return new Date();
    }

    ValueMetaDate valueMeta = new ValueMetaDate("loadDate");
    valueMeta.setConversionMask(LOAD_DATE_FORMAT_MASK);
    try {
      return valueMeta.convertStringToDate(resolved);
    } catch (Exception e) {
      throw new HopException(
          "Invalid load date '" + resolved + "'. Expected format " + LOAD_DATE_FORMAT_MASK + ".",
          e);
    }
  }

  public static boolean isConfigured(String configuredLoadDate, IVariables variables) {
    String resolved =
        variables != null ? variables.resolve(configuredLoadDate) : configuredLoadDate;
    return !StringUtils.isBlank(resolved);
  }
}
