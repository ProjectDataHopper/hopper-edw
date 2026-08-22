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
package org.apache.hop.datavault.transform.dvhashkey;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.HashAlgorithm;
import org.apache.hop.datavault.metadata.HashContentCasing;
import org.apache.hop.datavault.metadata.HashKeyDataType;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

@Transform(
    id = "DvHashKey",
    image = "checksum.svg",
    name = "i18n::DvHashKey.Name",
    description = "i18n::DvHashKey.Description",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Transform",
    keywords = "i18n::DvHashKey.keyword",
    documentationUrl = "/pipeline/transforms/dvhashkey.html")
public class DvHashKeyMeta extends BaseTransformMeta<DvHashKey, DvHashKeyData> {

  private static final Class<?> PKG = DvHashKeyMeta.class;

  @HopMetadataProperty(
      groupKey = "fields",
      key = "field",
      injectionGroupKey = "FIELDS",
      injectionKey = "FIELD",
      injectionKeyDescription = "DvHashKey.Injection.FIELD")
  private List<DvHashKeyField> fields = new ArrayList<>();

  @HopMetadataProperty(
      key = "resultfieldName",
      injectionKey = "RESULT_FIELD",
      injectionKeyDescription = "DvHashKey.Injection.RESULT_FIELD")
  private String resultFieldName;

  @HopMetadataProperty(
      key = "hashAlgorithm",
      storeWithCode = true,
      injectionKey = "HASH_ALGORITHM",
      injectionKeyDescription = "DvHashKey.Injection.HASH_ALGORITHM")
  private HashAlgorithm hashAlgorithm = HashAlgorithm.MD5;

  @HopMetadataProperty(
      key = "hashKeyDataType",
      storeWithCode = true,
      injectionKey = "HASH_KEY_DATA_TYPE",
      injectionKeyDescription = "DvHashKey.Injection.HASH_KEY_DATA_TYPE")
  private HashKeyDataType hashKeyDataType = HashKeyDataType.HEX;

  @HopMetadataProperty(
      key = "hashContentCasing",
      storeWithCode = true,
      injectionKey = "HASH_CONTENT_CASING",
      injectionKeyDescription = "DvHashKey.Injection.HASH_CONTENT_CASING")
  private HashContentCasing hashContentCasing = HashContentCasing.UPPER;

  @HopMetadataProperty(
      key = "businessKeyDelimiter",
      injectionKey = "BUSINESS_KEY_DELIMITER",
      injectionKeyDescription = "DvHashKey.Injection.BUSINESS_KEY_DELIMITER")
  private String businessKeyDelimiter = "||";

  @HopMetadataProperty(
      key = "hashContentPrefix",
      injectionKey = "HASH_CONTENT_PREFIX",
      injectionKeyDescription = "DvHashKey.Injection.HASH_CONTENT_PREFIX")
  private String hashContentPrefix;

  @HopMetadataProperty(
      key = "hashContentSuffix",
      injectionKey = "HASH_CONTENT_SUFFIX",
      injectionKeyDescription = "DvHashKey.Injection.HASH_CONTENT_SUFFIX")
  private String hashContentSuffix;

  @HopMetadataProperty(
      key = "nullPlaceholder",
      injectionKey = "NULL_PLACEHOLDER",
      injectionKeyDescription = "DvHashKey.Injection.NULL_PLACEHOLDER")
  private String nullPlaceholder = "^^";

  @HopMetadataProperty(
      key = "trimBusinessKeys",
      injectionKey = "TRIM_BUSINESS_KEYS",
      injectionKeyDescription = "DvHashKey.Injection.TRIM_BUSINESS_KEYS")
  private boolean trimBusinessKeys = true;

  public DvHashKeyMeta() {}

  @Override
  public DvHashKeyMeta clone() {
    DvHashKeyMeta meta = new DvHashKeyMeta();
    meta.resultFieldName = resultFieldName;
    meta.hashAlgorithm = hashAlgorithm;
    meta.hashKeyDataType = hashKeyDataType;
    meta.hashContentCasing = hashContentCasing;
    meta.businessKeyDelimiter = businessKeyDelimiter;
    meta.hashContentPrefix = hashContentPrefix;
    meta.hashContentSuffix = hashContentSuffix;
    meta.nullPlaceholder = nullPlaceholder;
    meta.trimBusinessKeys = trimBusinessKeys;
    for (DvHashKeyField field : fields) {
      meta.fields.add(new DvHashKeyField(field));
    }
    return meta;
  }

  @Override
  public void getFields(
      IRowMeta inputRowMeta,
      String name,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    if (Utils.isEmpty(resultFieldName)) {
      return;
    }
    String resolvedName = variables.resolve(resultFieldName);
    IValueMeta valueMeta;
    int type = DvHashKeyLogic.resultValueMetaType(hashKeyDataType);
    if (type == IValueMeta.TYPE_BINARY) {
      valueMeta = new ValueMetaBinary(resolvedName);
    } else {
      valueMeta = new ValueMetaString(resolvedName);
    }
    valueMeta.setLength(DvHashKeyLogic.resultValueMetaLength(hashAlgorithm, hashKeyDataType));
    valueMeta.setOrigin(name);
    inputRowMeta.addValueMeta(valueMeta);
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (Utils.isEmpty(resultFieldName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvHashKeyMeta.CheckResult.ResultFieldMissing"),
              transformMeta));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvHashKeyMeta.CheckResult.ResultFieldOK"),
              transformMeta));
    }

    if (prev == null || prev.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(PKG, "DvHashKeyMeta.CheckResult.NotReceivingFields"),
              transformMeta));
      return;
    }

    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_OK,
            BaseMessages.getString(
                PKG, "DvHashKeyMeta.CheckResult.TransformRecevingData", prev.size()),
            transformMeta));

    StringBuilder missing = new StringBuilder();
    for (DvHashKeyField field : fields) {
      if (prev.indexOfValue(field.getName()) < 0) {
        missing.append("\t\t").append(field.getName()).append(Const.CR);
      }
    }
    if (!missing.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvHashKeyMeta.CheckResult.FieldsFound", missing),
              transformMeta));
    } else if (!fields.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvHashKeyMeta.CheckResult.AllFieldsFound"),
              transformMeta));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(PKG, "DvHashKeyMeta.CheckResult.NoFieldsEntered"),
              transformMeta));
    }

    if (input.length > 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvHashKeyMeta.CheckResult.TransformRecevingData2"),
              transformMeta));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DvHashKeyMeta.CheckResult.NoInputReceivedFromOtherTransforms"),
              transformMeta));
    }
  }

  @Override
  public boolean supportsErrorHandling() {
    return true;
  }

  public List<DvHashKeyField> getFields() {
    return fields;
  }

  public void setFields(List<DvHashKeyField> fields) {
    this.fields = fields;
  }

  public String getResultFieldName() {
    return resultFieldName;
  }

  public void setResultFieldName(String resultFieldName) {
    this.resultFieldName = resultFieldName;
  }

  public HashAlgorithm getHashAlgorithm() {
    return hashAlgorithm;
  }

  public void setHashAlgorithm(HashAlgorithm hashAlgorithm) {
    this.hashAlgorithm = hashAlgorithm;
  }

  public HashKeyDataType getHashKeyDataType() {
    return hashKeyDataType;
  }

  public void setHashKeyDataType(HashKeyDataType hashKeyDataType) {
    this.hashKeyDataType = hashKeyDataType;
  }

  public HashContentCasing getHashContentCasing() {
    return hashContentCasing;
  }

  public void setHashContentCasing(HashContentCasing hashContentCasing) {
    this.hashContentCasing = hashContentCasing;
  }

  public String getBusinessKeyDelimiter() {
    return businessKeyDelimiter;
  }

  public void setBusinessKeyDelimiter(String businessKeyDelimiter) {
    this.businessKeyDelimiter = businessKeyDelimiter;
  }

  public String getHashContentPrefix() {
    return hashContentPrefix;
  }

  public void setHashContentPrefix(String hashContentPrefix) {
    this.hashContentPrefix = hashContentPrefix;
  }

  public String getHashContentSuffix() {
    return hashContentSuffix;
  }

  public void setHashContentSuffix(String hashContentSuffix) {
    this.hashContentSuffix = hashContentSuffix;
  }

  public String getNullPlaceholder() {
    return nullPlaceholder;
  }

  public void setNullPlaceholder(String nullPlaceholder) {
    this.nullPlaceholder = nullPlaceholder;
  }

  public boolean isTrimBusinessKeys() {
    return trimBusinessKeys;
  }

  public void setTrimBusinessKeys(boolean trimBusinessKeys) {
    this.trimBusinessKeys = trimBusinessKeys;
  }
}
