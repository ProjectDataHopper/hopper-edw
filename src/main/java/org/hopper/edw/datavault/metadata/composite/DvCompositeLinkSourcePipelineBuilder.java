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
package org.hopper.edw.datavault.metadata.composite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DependentChildKey;
import org.hopper.edw.datavault.metadata.DrivingKeySource;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvLinkHubSourceKeyFieldSupport;
import org.hopper.edw.datavault.metadata.IDvSource;

/** Link load from a composite (multi-table) source query. */
public class DvCompositeLinkSourcePipelineBuilder extends DvCompositeSourcePipelineBuilder {

  private final Map<String, List<String>> hubKeyFields = new HashMap<>();
  private final Map<String, List<String>> hubDrivingKeyFields = new HashMap<>();

  public DvCompositeLinkSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvLink link,
      Point startPoint) {
    super(
        variables, metadataProvider, model, pipelineMeta, recordSource, dvSource, link, startPoint);
  }

  @Override
  protected String getSql(String innerSql) throws HopException {
    StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
    DvLink link = (DvLink) dvTable;

    if (dvLinkHubSource == null) {
      throw new HopException("No DV link hub source was configured");
    }

    for (String hubName : link.getHubNames()) {
      DvHub hub = findHub(hubName);
      DvLink.HubSourceKeyField sourceKeyField =
          DvLinkHubSourceKeyFieldSupport.findHubSourceKeyField(dvLinkHubSource, hubName);
      List<String> keys = new ArrayList<>();
      for (String sourceFieldName :
          DvLinkHubSourceKeyFieldSupport.resolveSourceFieldNames(hub, sourceKeyField, variables)) {
        keys.add(sourceDbMeta.quoteField(sourceFieldName));
      }
      hubKeyFields.put(hubName, keys);

      List<String> driving = new ArrayList<>();
      if (sourceKeyField != null && sourceKeyField.getDrivingKeySources() != null) {
        for (DrivingKeySource keySource : sourceKeyField.getDrivingKeySources()) {
          if (keySource != null && keySource.getSourceField() != null) {
            driving.add(sourceDbMeta.quoteField(variables.resolve(keySource.getSourceField())));
          }
        }
      }
      hubDrivingKeyFields.put(hubName, driving);
    }

    List<String> quotedFields = new ArrayList<>();
    for (String hubName : link.getHubNames()) {
      List<String> keyFields = hubKeyFields.get(hubName);
      if (keyFields != null) {
        quotedFields.addAll(keyFields);
      }
      List<String> drivingKeys = hubDrivingKeyFields.get(hubName);
      if (drivingKeys != null) {
        quotedFields.addAll(drivingKeys);
      }
    }

    if (link.getDependentChildKeys() != null) {
      for (DependentChildKey dck : link.getDependentChildKeys()) {
        if (dck == null) {
          continue;
        }
        String sourceField = variables.resolve(dck.resolveSourceFieldName());
        if (sourceField != null && !sourceField.isEmpty()) {
          quotedFields.add(sourceDbMeta.quoteField(sourceField));
        }
      }
    }

    appendFields(sql, quotedFields);
    appendComma(sql);
    appendSourceField(link, sql, sourceDbMeta);
    appendFromSubquery(sql, innerSql);
    return sql.toString();
  }
}
