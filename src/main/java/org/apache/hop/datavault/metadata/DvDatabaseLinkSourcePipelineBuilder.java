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
package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourcePipelineBuilder;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

@Getter
@Setter
public class DvDatabaseLinkSourcePipelineBuilder extends DvDatabaseSourcePipelineBuilder {
  private Map<String, List<String>> hubKeyFields = new HashMap<>();
  private Map<String, List<String>> hubDrivingKeyFields = new HashMap<>();

  public DvDatabaseLinkSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvLink dvTable,
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

  /**
   * We're querying one source of a list of sources in a satellite.
   *
   * @return The SQL needed to query this source table
   * @throws HopException In case there's an error in the model
   */
  protected String getSql() throws HopException {
    // DISTINCT on hub BKs / driving keys / dependent child keys — same natural grain that becomes
    // the link hash. Without this, repeated source rows (or multi-file history of the same pair)
    // produce multiple identical LHK values; MergeRows then emits multiple "new" rows and bulk
    // load fails on PRIMARY KEY (lnk_*_hk).
    StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
    DvLink link = (DvLink) dvTable;
    DvDatabaseSource source = (DvDatabaseSource) dvSource;
    DatabaseMeta sourceDbMeta = loadDatabaseMeta(variables.resolve(source.getDatabaseName()));

    if (dvLinkHubSource == null) {
      throw new HopException("No DV link hub source was configured");
    }

    for (String hubName : link.getHubNames()) {
      DvHub hub = findHub(hubName);
      if (hub == null) {
        throw new HopException(
            "No DV link source key field found for hub: " + hubName + " in Link " + link.getName());
      }
      DvLink.HubSourceKeyField sourceKeyField =
          DvLinkHubSourceKeyFieldSupport.findHubSourceKeyField(dvLinkHubSource, hubName);
      findKeySourceFieldsForHub(hubName, sourceKeyField, hub, link, sourceDbMeta);
      findDrivingKeySourceFieldsForHub(hubName, sourceKeyField, link, sourceDbMeta);
    }

    // These are now the fields to retrieve:
    // For every hub we retrieve the business key and driving key source fields.
    //
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

    // Dependent child key source fields (transactional link identity)
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

    // Append the source field as well
    appendComma(sql);
    appendSourceField(link, sql, sourceDbMeta);

    // FROM
    appendFrom(sourceDbMeta, source, sql);

    return sql.toString();
  }

  private void findKeySourceFieldsForHub(
      String hubName,
      DvLink.HubSourceKeyField sourceKeyField,
      DvHub hub,
      DvLink link,
      DatabaseMeta sourceDbMeta)
      throws HopException {
    List<String> keyFields = hubKeyFields.computeIfAbsent(hubName, f -> new ArrayList<>());

    for (DvLinkHubSourceKeyFieldSupport.ResolvedBusinessKeySource resolvedMapping :
        DvLinkHubSourceKeyFieldSupport.resolveBusinessKeySources(hub, sourceKeyField, variables)) {
      if (hub.findBusinessKey(resolvedMapping.getBusinessKeyField()) == null) {
        throw new HopException(
            "The specified business key field "
                + resolvedMapping.getBusinessKeyField()
                + " can not be found in Link table "
                + link.getName()
                + " with record source "
                + recordSource.getName());
      }
      keyFields.add(sourceDbMeta.quoteField(resolvedMapping.getSourceFieldName()));
    }
  }

  private void findDrivingKeySourceFieldsForHub(
      String hubName,
      DvLink.HubSourceKeyField sourceKeyField,
      DvLink link,
      DatabaseMeta sourceDbMeta)
      throws HopException {
    List<String> drivingKeyFields =
        hubDrivingKeyFields.computeIfAbsent(hubName, f -> new ArrayList<>());

    for (DrivingKeySource keySource : sourceKeyField.getDrivingKeySources()) {
      String drivingKey = keySource.getDrivingKey();
      String drivingKeySource = keySource.getSourceField();

      // Verify that the driving key exists
      //
      if (!link.getDrivingKeyNames().contains(drivingKey)) {
        throw new HopException(
            "The referenced driving key "
                + drivingKey
                + " doesn't exist in Link table "
                + link.getName()
                + ", reading from source "
                + recordSource.getName());
      }

      drivingKeyFields.add(sourceDbMeta.quoteField(drivingKeySource));
    }
  }
}
