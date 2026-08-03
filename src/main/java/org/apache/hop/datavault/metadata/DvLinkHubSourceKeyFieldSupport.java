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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/** Resolves link hub source business key fields, including implicit hub-name fallback. */
public final class DvLinkHubSourceKeyFieldSupport {

  private static final Class<?> PKG = DvLinkHubSourceKeyFieldSupport.class;

  private DvLinkHubSourceKeyFieldSupport() {}

  public static final class ResolvedBusinessKeySource {
    private final String businessKeyField;
    private final String sourceFieldName;

    public ResolvedBusinessKeySource(String businessKeyField, String sourceFieldName) {
      this.businessKeyField = businessKeyField;
      this.sourceFieldName = sourceFieldName;
    }

    public String getBusinessKeyField() {
      return businessKeyField;
    }

    public String getSourceFieldName() {
      return sourceFieldName;
    }
  }

  public static DvLink.HubSourceKeyField findHubSourceKeyField(
      DvLink.DvLinkHubSource linkHubSource, String hubName) throws HopException {
    DvLink.HubSourceKeyField field = findHubSourceKeyFieldOrNull(linkHubSource, hubName);
    if (field == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DvLinkHubSourceKeyFieldSupport.Error.MissingHubMapping",
              hubName,
              linkHubSource.getSourceName()));
    }
    return field;
  }

  public static DvLink.HubSourceKeyField findHubSourceKeyFieldOrNull(
      DvLink.DvLinkHubSource linkHubSource, String hubName) {
    if (linkHubSource == null
        || Utils.isEmpty(hubName)
        || linkHubSource.getHubSourceKeyFields() == null) {
      return null;
    }
    for (DvLink.HubSourceKeyField hubSourceKeyField : linkHubSource.getHubSourceKeyFields()) {
      if (hubSourceKeyField != null && hubName.equals(hubSourceKeyField.getHubName())) {
        return hubSourceKeyField;
      }
    }
    return null;
  }

  public static List<ResolvedBusinessKeySource> resolveBusinessKeySources(
      DvHub hub, DvLink.HubSourceKeyField hubSourceKeyField, IVariables variables) {
    List<ResolvedBusinessKeySource> resolved = new ArrayList<>();
    if (hub == null || hub.getBusinessKeys() == null) {
      return resolved;
    }

    Map<String, BusinessKeySource> explicitByBusinessKey = new LinkedHashMap<>();
    if (hubSourceKeyField != null && hubSourceKeyField.getSourceBusinessKeyFields() != null) {
      for (BusinessKeySource businessKeySource : hubSourceKeyField.getSourceBusinessKeyFields()) {
        if (businessKeySource == null || Utils.isEmpty(businessKeySource.getBusinessKeyField())) {
          continue;
        }
        explicitByBusinessKey.putIfAbsent(
            businessKeySource.getBusinessKeyField(), businessKeySource);
      }
    }

    for (BusinessKey businessKey : hub.getDistinctBusinessKeys()) {
      if (businessKey == null || Utils.isEmpty(businessKey.getName())) {
        continue;
      }
      String businessKeyName = resolve(variables, businessKey.getName());
      BusinessKeySource explicit = explicitByBusinessKey.get(businessKey.getName());
      String sourceFieldName;
      if (explicit != null && !Utils.isEmpty(explicit.getSourceFieldName())) {
        sourceFieldName = resolve(variables, explicit.getSourceFieldName());
      } else {
        sourceFieldName = businessKeyName;
      }
      if (!Utils.isEmpty(sourceFieldName)) {
        resolved.add(new ResolvedBusinessKeySource(businessKeyName, sourceFieldName));
      }
    }
    return resolved;
  }

  public static List<String> resolveSourceFieldNames(
      DvHub hub, DvLink.HubSourceKeyField hubSourceKeyField, IVariables variables) {
    List<String> sourceFieldNames = new ArrayList<>();
    for (ResolvedBusinessKeySource resolved :
        resolveBusinessKeySources(hub, hubSourceKeyField, variables)) {
      sourceFieldNames.add(resolved.getSourceFieldName());
    }
    return sourceFieldNames;
  }

  public static List<String> resolveSourceFieldNames(
      DvLink.DvLinkHubSource linkHubSource, String hubName, DvHub hub, IVariables variables)
      throws HopException {
    DvLink.HubSourceKeyField hubSourceKeyField = findHubSourceKeyField(linkHubSource, hubName);
    List<String> sourceFieldNames = resolveSourceFieldNames(hub, hubSourceKeyField, variables);
    if (sourceFieldNames.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DvLinkHubSourceKeyFieldSupport.Error.NoResolvableSourceFields",
              hubName,
              linkHubSource.getSourceName()));
    }
    return sourceFieldNames;
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
