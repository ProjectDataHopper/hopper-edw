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
package org.apache.hop.datavault.transform.dvhashkey;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;

/** Builds {@link DvHashKeyMeta} instances from Data Vault configuration and field lists. */
public final class DvHashKeyMetaFactory {

  private DvHashKeyMetaFactory() {}

  public static DvHashKeyMeta create(
      DataVaultConfiguration config, List<String> inputFieldNames, String resultFieldName) {
    DvHashKeyMeta meta = new DvHashKeyMeta();
    applyConfiguration(meta, config);
    meta.setResultFieldName(resultFieldName);
    if (inputFieldNames != null) {
      for (String fieldName : inputFieldNames) {
        if (!Utils.isEmpty(fieldName)) {
          meta.getFields().add(new DvHashKeyField(fieldName));
        }
      }
    }
    return meta;
  }

  public static void applyConfiguration(DvHashKeyMeta meta, DataVaultConfiguration config) {
    if (meta == null || config == null) {
      return;
    }
    meta.setHashAlgorithm(config.resolveHashAlgorithm());
    meta.setHashKeyDataType(config.resolveHashKeyDataType());
    meta.setHashContentCasing(config.resolveHashContentCasing());
    if (config.getBusinessKeyDelimiter() != null) {
      meta.setBusinessKeyDelimiter(config.getBusinessKeyDelimiter());
    }
    if (config.getHashContentPrefix() != null) {
      meta.setHashContentPrefix(config.getHashContentPrefix());
    }
    if (config.getHashContentSuffix() != null) {
      meta.setHashContentSuffix(config.getHashContentSuffix());
    }
    if (config.getNullPlaceholder() != null) {
      meta.setNullPlaceholder(config.getNullPlaceholder());
    }
    meta.setTrimBusinessKeys(config.isTrimBusinessKeys());
  }

  public static List<String> nonEmptyFieldNames(List<String> fieldNames) {
    List<String> names = new ArrayList<>();
    if (fieldNames == null) {
      return names;
    }
    for (String fieldName : fieldNames) {
      if (!Utils.isEmpty(fieldName)) {
        names.add(fieldName);
      }
    }
    return names;
  }
}
