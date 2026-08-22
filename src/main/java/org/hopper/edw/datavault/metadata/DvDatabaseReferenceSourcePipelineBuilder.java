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
package org.hopper.edw.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSource;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSourcePipelineBuilder;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Database source leg for {@link DvReferenceTable} FULL_REPLACE loads. */
@Getter
@Setter
public class DvDatabaseReferenceSourcePipelineBuilder extends DvDatabaseSourcePipelineBuilder {

  public DvDatabaseReferenceSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvReferenceTable dvTable,
      Point startPoint) {
    super(
        variables,
        metadataProvider,
        model,
        pipelineMeta,
        recordSource,
        dvSource,
        dvTable,
        startPoint);
  }

  @Override
  protected String getSql() throws HopException {
    DvReferenceTable reference = (DvReferenceTable) dvTable;
    DvDatabaseSource dbSource = (DvDatabaseSource) dvSource;
    DatabaseMeta sourceDbMeta = loadDatabaseMeta(variables.resolve(dbSource.getDatabaseName()));

    StringBuilder sql = new StringBuilder("SELECT ");
    List<String> selectFields = new ArrayList<>();

    String sourceName = variables.resolve(recordSource.getName());
    List<BusinessKey> keys = reference.getNaturalKeysForSource(sourceName, variables);
    if (keys.isEmpty()) {
      throw new HopException(
          "Please map at least one natural key to record source "
              + sourceName
              + " on reference table "
              + reference.getName());
    }
    for (BusinessKey key : keys) {
      String targetName = variables.resolve(key.getName());
      String sourceField =
          !Utils.isEmpty(key.getSourceFieldName())
              ? variables.resolve(key.getSourceFieldName())
              : targetName;
      selectFields.add(aliasedField(sourceDbMeta, sourceField, targetName));
    }

    if (reference.getAttributes() != null) {
      for (SatelliteAttribute attr : reference.getAttributes()) {
        if (attr == null || Utils.isEmpty(attr.getName())) {
          continue;
        }
        String targetName = variables.resolve(attr.getName());
        selectFields.add(aliasedField(sourceDbMeta, targetName, targetName));
      }
    }

    appendFields(sql, selectFields);
    appendComma(sql);
    appendSourceField(reference, sql, sourceDbMeta);
    appendFrom(sourceDbMeta, dbSource, sql);
    return sql.toString();
  }

  private static String aliasedField(DatabaseMeta dbMeta, String sourceField, String targetName) {
    String quotedSource = dbMeta.quoteField(sourceField);
    String quotedTarget = dbMeta.quoteField(targetName);
    if (StringUtils.equals(sourceField, targetName)) {
      return quotedSource;
    }
    return quotedSource + " AS " + quotedTarget;
  }
}
