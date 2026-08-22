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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Calculates a Data Vault hash key over one or more fields with DV-specific normalization. */
public class DvHashKey extends BaseTransform<DvHashKeyMeta, DvHashKeyData> {

  private static final Class<?> PKG = DvHashKeyMeta.class;

  public DvHashKey(
      TransformMeta transformMeta,
      DvHashKeyMeta meta,
      DvHashKeyData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    if (row == null) {
      setOutputDone();
      return false;
    }

    if (first) {
      first = false;
      data.outputRowMeta = getInputRowMeta().clone();
      data.nrInfields = data.outputRowMeta.size();
      meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);
      data.fieldnrs = new int[meta.getFields().size()];
      for (int i = 0; i < meta.getFields().size(); i++) {
        DvHashKeyField field = meta.getFields().get(i);
        data.fieldnrs[i] = getInputRowMeta().indexOfValue(field.getName());
        if (data.fieldnrs[i] < 0) {
          throw new HopException(
              BaseMessages.getString(PKG, "DvHashKey.Log.CanNotFindField", field.getName()));
        }
      }
      data.digest = DvHashKeyLogic.createDigest(meta.getHashAlgorithm());
    }

    try {
      byte[] inputBytes =
          DvHashKeyLogic.buildHashInput(row, getInputRowMeta(), data.fieldnrs, meta, this);
      Object result = null;
      if (inputBytes != null) {
        data.digest.reset();
        data.digest.update(inputBytes);
        byte[] digestBytes = data.digest.digest();
        result = DvHashKeyLogic.formatHashResult(digestBytes, meta.getHashKeyDataType());
      }
      Object[] outputRow = RowDataUtil.addValueData(row, data.nrInfields, result);
      putRow(data.outputRowMeta, outputRow);
    } catch (Exception e) {
      if (getTransformMeta().isDoingErrorHandling()) {
        putError(
            getInputRowMeta(), row, 1, e.toString(), meta.getResultFieldName(), "DvHashKey001");
      } else {
        logError(BaseMessages.getString(PKG, "DvHashKey.ErrorInTransformRunning") + e.getMessage());
        setErrors(1);
        stopAll();
        setOutputDone();
        return false;
      }
    }

    if (checkFeedback(getLinesRead()) && isDetailed()) {
      logDetailed(
          BaseMessages.getString(PKG, "DvHashKey.Log.LineNumber", Long.toString(getLinesRead())));
    }
    return true;
  }

  @Override
  public boolean init() {
    if (!super.init()) {
      return false;
    }
    if (Utils.isEmpty(meta.getResultFieldName())) {
      logError(BaseMessages.getString(PKG, "DvHashKey.Error.ResultFieldMissing"));
      return false;
    }
    if (meta.getFields() == null || meta.getFields().isEmpty()) {
      logError(BaseMessages.getString(PKG, "DvHashKey.Error.NoFields"));
      return false;
    }
    return true;
  }
}
