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
package org.apache.hop.datavault.metadata.composite;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvSqlOrderByCollationSupport;
import org.apache.hop.datavault.metadata.DvSqlOrderBySupport;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Hub load from a composite (multi-table) source query. */
public class DvCompositeHubSourcePipelineBuilder extends DvCompositeSourcePipelineBuilder {

  public DvCompositeHubSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvHub hub,
      Point startPoint) {
    super(
        variables, metadataProvider, model, pipelineMeta, recordSource, dvSource, hub, startPoint);
  }

  @Override
  protected String getSql(String innerSql) throws HopException {
    StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
    DvHub hub = (DvHub) dvTable;
    List<BusinessKey> businessKeys =
        hub.getBusinessKeysForSource(variables.resolve(recordSource.getName()), variables);
    List<String> pkQuotedFields = getQuotedPkFields(hub, sourceDbMeta);

    appendFields(sql, pkQuotedFields);
    appendComma(sql);
    appendSourceField(hub, sql, sourceDbMeta);
    appendFromSubquery(sql, innerSql);

    StringBuilder orderBy = new StringBuilder();
    DvSqlOrderBySupport.appendOrderBy(
        orderBy,
        businessKeys,
        pkQuotedFields,
        sourceDbMeta,
        configuration,
        variables,
        DvSqlOrderByCollationSupport.Session.empty());
    if (DvSqlOrderBySupport.isCollationOrderBySupported(sourceDbMeta)
        && orderBy.indexOf("COLLATE") >= 0) {
      return "SELECT * FROM (" + sql + ") collate_sort_src" + orderBy;
    }
    sql.append(orderBy);
    return sql.toString();
  }
}
