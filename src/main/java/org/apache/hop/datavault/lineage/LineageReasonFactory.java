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
package org.apache.hop.datavault.lineage;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;

/** Builds localized {@link LineageReason} instances with structured evidence. */
public final class LineageReasonFactory {

  private static final Class<?> PKG = LineageReasonFactory.class;

  private LineageReasonFactory() {}

  public static LineageReason userExplicitName(String logicalName, String physicalName) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("logicalName", nvl(logicalName));
    evidence.put("physicalTableName", nvl(physicalName));
    return new LineageReason(
        LineageReasonCode.USER_EXPLICIT_NAME,
        BaseMessages.getString(
            PKG, "LineageReason.UserExplicitName", nvl(logicalName), nvl(physicalName)),
        LineageConfidence.EXPLICIT,
        evidence);
  }

  public static LineageReason tableTypeRole(String tableType, String logicalName) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("tableType", nvl(tableType));
    evidence.put("logicalName", nvl(logicalName));
    return new LineageReason(
        LineageReasonCode.NAMING_CONVENTION,
        BaseMessages.getString(
            PKG, "LineageReason.TableTypeRole", nvl(tableType), nvl(logicalName)),
        LineageConfidence.CONVENTION,
        evidence);
  }

  public static LineageReason userExplicitMapping(
      String targetField, String sourceName, String sourceField) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("targetField", nvl(targetField));
    evidence.put("sourceName", nvl(sourceName));
    evidence.put("sourceField", nvl(sourceField));
    return new LineageReason(
        LineageReasonCode.USER_EXPLICIT_MAPPING,
        BaseMessages.getString(
            PKG,
            "LineageReason.UserExplicitMapping",
            nvl(targetField),
            nvl(sourceName),
            nvl(sourceField)),
        LineageConfidence.EXPLICIT,
        evidence);
  }

  public static LineageReason defaultSameAsSource(String fieldName, String sourceName) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("fieldName", nvl(fieldName));
    evidence.put("sourceName", nvl(sourceName));
    return new LineageReason(
        LineageReasonCode.DEFAULT_SAME_AS_SOURCE,
        BaseMessages.getString(
            PKG, "LineageReason.DefaultSameAsSource", nvl(fieldName), nvl(sourceName)),
        LineageConfidence.EXPLICIT,
        evidence);
  }

  public static LineageReason standardColumn(
      String fieldName, String configKey, String configValue) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("fieldName", nvl(fieldName));
    evidence.put("configKey", nvl(configKey));
    evidence.put("configValue", nvl(configValue));
    return new LineageReason(
        LineageReasonCode.STANDARD_COLUMN,
        BaseMessages.getString(
            PKG, "LineageReason.StandardColumn", nvl(fieldName), nvl(configKey), nvl(configValue)),
        LineageConfidence.DERIVED,
        evidence);
  }

  public static LineageReason hashFromBusinessKeys(String hashField, String businessKeyList) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("hashField", nvl(hashField));
    evidence.put("businessKeys", nvl(businessKeyList));
    return new LineageReason(
        LineageReasonCode.HASH_FROM_BUSINESS_KEYS,
        BaseMessages.getString(
            PKG, "LineageReason.HashFromBusinessKeys", nvl(hashField), nvl(businessKeyList)),
        LineageConfidence.DERIVED,
        evidence);
  }

  public static LineageReason parentHashKey(
      String hashField, String parentTable, String parentHash) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("hashField", nvl(hashField));
    evidence.put("parentTable", nvl(parentTable));
    evidence.put("parentHashField", nvl(parentHash));
    return new LineageReason(
        LineageReasonCode.PARENT_HASH_KEY,
        BaseMessages.getString(
            PKG, "LineageReason.ParentHashKey", nvl(hashField), nvl(parentTable), nvl(parentHash)),
        LineageConfidence.DERIVED,
        evidence);
  }

  public static LineageReason multiSourceHub(String targetField, int sourceCount) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("targetField", nvl(targetField));
    evidence.put("sourceCount", Integer.toString(sourceCount));
    return new LineageReason(
        LineageReasonCode.MULTI_SOURCE_HUB,
        BaseMessages.getString(
            PKG, "LineageReason.MultiSourceHub", nvl(targetField), Integer.toString(sourceCount)),
        LineageConfidence.EXPLICIT,
        evidence);
  }

  public static LineageReason linkHubKeyMapping(
      String hubName, String businessKey, String sourceName, String sourceField) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("hubName", nvl(hubName));
    evidence.put("businessKey", nvl(businessKey));
    evidence.put("sourceName", nvl(sourceName));
    evidence.put("sourceField", nvl(sourceField));
    return new LineageReason(
        LineageReasonCode.LINK_HUB_KEY_MAPPING,
        BaseMessages.getString(
            PKG,
            "LineageReason.LinkHubKeyMapping",
            nvl(hubName),
            nvl(businessKey),
            nvl(sourceName),
            nvl(sourceField)),
        LineageConfidence.EXPLICIT,
        evidence);
  }

  public static LineageReason dependentChildKey(String fieldName, String sourceField) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("fieldName", nvl(fieldName));
    evidence.put("sourceField", nvl(sourceField));
    return new LineageReason(
        LineageReasonCode.DEPENDENT_CHILD_KEY,
        BaseMessages.getString(
            PKG, "LineageReason.DependentChildKey", nvl(fieldName), nvl(sourceField)),
        LineageConfidence.EXPLICIT,
        evidence);
  }

  public static LineageReason drivingKey(String fieldName, String sourceField, String sourceName) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("fieldName", nvl(fieldName));
    evidence.put("sourceField", nvl(sourceField));
    evidence.put("sourceName", nvl(sourceName));
    return new LineageReason(
        LineageReasonCode.DRIVING_KEY,
        BaseMessages.getString(
            PKG, "LineageReason.DrivingKey", nvl(fieldName), nvl(sourceField), nvl(sourceName)),
        LineageConfidence.EXPLICIT,
        evidence);
  }

  public static LineageReason feedAttached(String sourceName, String role) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("sourceName", nvl(sourceName));
    evidence.put("role", nvl(role));
    return new LineageReason(
        LineageReasonCode.USER_EXPLICIT_MAPPING,
        BaseMessages.getString(PKG, "LineageReason.FeedAttached", nvl(sourceName), nvl(role)),
        LineageConfidence.EXPLICIT,
        evidence);
  }

  public static LineageReason bvScd2FieldMap(
      String targetField, String satelliteName, String sourceField) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("targetField", nvl(targetField));
    evidence.put("satelliteName", nvl(satelliteName));
    evidence.put("sourceField", nvl(sourceField));
    return new LineageReason(
        LineageReasonCode.BV_SCD2_FIELD_MAP,
        BaseMessages.getString(
            PKG,
            "LineageReason.BvScd2FieldMap",
            nvl(targetField),
            nvl(satelliteName),
            nvl(sourceField)),
        LineageConfidence.EXPLICIT,
        evidence);
  }

  public static LineageReason bvPassthrough(String fieldName, String satelliteName) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("fieldName", nvl(fieldName));
    evidence.put("satelliteName", nvl(satelliteName));
    return new LineageReason(
        LineageReasonCode.BV_PASSTHROUGH,
        BaseMessages.getString(
            PKG, "LineageReason.BvPassthrough", nvl(fieldName), nvl(satelliteName)),
        LineageConfidence.CONVENTION,
        evidence);
  }

  public static LineageReason dmRoleMapping(
      String targetField, String sourceLabel, String sourceField) {
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put("targetField", nvl(targetField));
    evidence.put("sourceLabel", nvl(sourceLabel));
    evidence.put("sourceField", nvl(sourceField));
    return new LineageReason(
        LineageReasonCode.DM_ROLE_MAPPING,
        BaseMessages.getString(
            PKG,
            "LineageReason.DmRoleMapping",
            nvl(targetField),
            nvl(sourceLabel),
            nvl(sourceField)),
        LineageConfidence.EXPLICIT,
        evidence);
  }

  private static String nvl(String value) {
    return Utils.isEmpty(value) ? "" : value;
  }
}
