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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourcePipelineBuilder;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

@Getter
@Setter
public class DvDatabaseHubSourcePipelineBuilder extends DvDatabaseSourcePipelineBuilder {
  public DvDatabaseHubSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvHub dvTable,
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

  protected String getSql() throws HopException {
    StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
    DvHub hub = (DvHub) dvTable;
    DvDatabaseSource source = (DvDatabaseSource) dvSource;
    List<BusinessKey> businessKeys =
        hub.getBusinessKeysForSource(variables.resolve(recordSource.getName()), variables);
    List<String> pkQuotedFields = getQuotedPkFields(hub, sourceDbMeta);

    // PK
    appendFields(sql, pkQuotedFields);

    // Source indicator
    appendComma(sql);
    appendSourceField(hub, sql, sourceDbMeta);

    // FROM
    appendFrom(sourceDbMeta, source, sql);

    // ORDER BY (auto COLLATE on SQL Server when source/target collations or types differ).
    // SQL Server forbids ORDER BY expressions that are not in the SELECT list when DISTINCT is
    // used; "col COLLATE x" is a different expression than "col", so wrap DISTINCT in a subquery
    // and order in the outer query when a bridge COLLATE is present.
    DvSqlOrderByCollationSupport.Session session = loadHubOrderByCollationSession(hub, source);
    StringBuilder orderBy = new StringBuilder();
    appendOrderByPk(orderBy, businessKeys, pkQuotedFields, sourceDbMeta, session);
    if (DvSqlOrderBySupport.isCollationOrderBySupported(sourceDbMeta)
        && orderBy.indexOf("COLLATE") >= 0) {
      return "SELECT * FROM (" + sql + ") collate_sort_src" + orderBy;
    }
    sql.append(orderBy);
    return sql.toString();
  }

  private DvSqlOrderByCollationSupport.Session loadHubOrderByCollationSession(
      DvHub hub, DvDatabaseSource source) {
    try {
      DatabaseMeta targetDatabaseMeta = null;
      String targetTable = null;
      if (configuration != null && !Utils.isEmpty(configuration.getTargetDatabase())) {
        targetDatabaseMeta =
            metadataProvider
                .getSerializer(org.apache.hop.core.database.DatabaseMeta.class)
                .load(configuration.getTargetDatabase());
        targetTable = !Utils.isEmpty(hub.getTableName()) ? hub.getTableName() : hub.getName();
        if (variables != null && targetTable != null) {
          targetTable = variables.resolve(targetTable);
        }
      }
      return DvSqlOrderByCollationSupport.loadSession(
          sourceDbMeta,
          source != null ? source.getSchemaName() : null,
          source != null ? source.getTableName() : null,
          targetDatabaseMeta,
          null,
          targetTable,
          variables);
    } catch (Exception e) {
      return DvSqlOrderByCollationSupport.Session.empty();
    }
  }
}
