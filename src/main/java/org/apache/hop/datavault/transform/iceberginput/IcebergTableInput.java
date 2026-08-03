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
package org.apache.hop.datavault.transform.iceberginput;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.iceberg.IcebergConnectionSettings;
import org.apache.hop.datavault.metadata.iceberg.IcebergTableReader;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Reads rows from an Iceberg table registered in a REST catalog. */
public class IcebergTableInput extends BaseTransform<IcebergTableInputMeta, IcebergTableInputData> {

  private static final Class<?> PKG = IcebergTableInputMeta.class;

  public IcebergTableInput(
      TransformMeta transformMeta,
      IcebergTableInputMeta meta,
      IcebergTableInputData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean init() {
    if (!super.init()) {
      return false;
    }
    try {
      IcebergConnectionSettings settings = IcebergConnectionSettings.from(meta, this);
      data.reader = new IcebergTableReader(settings, selectedFieldNames());
      data.outputRowMeta = data.reader.getOutputRowMeta();
      return true;
    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "IcebergTableInput.Log.UnableToInitialize"), e);
      return false;
    }
  }

  @Override
  public boolean processRow() throws HopException {
    if (data.reader == null) {
      setOutputDone();
      return false;
    }
    if (data.reader.hasNext()) {
      Object[] row = data.reader.nextRow();
      putRow(data.outputRowMeta, row);
      return true;
    }
    setOutputDone();
    return false;
  }

  @Override
  public void dispose() {
    if (data.reader != null) {
      try {
        data.reader.close();
      } catch (Exception e) {
        logError(BaseMessages.getString(PKG, "IcebergTableInput.Log.UnableToCloseReader"), e);
      } finally {
        data.reader = null;
      }
    }
    super.dispose();
  }

  private List<String> selectedFieldNames() {
    List<String> names = new ArrayList<>();
    if (meta.getFields() != null) {
      for (IcebergTableInputField field : meta.getFields()) {
        if (field != null && !Utils.isEmpty(field.getName())) {
          names.add(resolve(field.getName()));
        }
      }
    }
    return names;
  }
}
