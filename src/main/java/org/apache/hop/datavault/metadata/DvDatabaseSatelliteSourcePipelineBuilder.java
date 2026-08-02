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
 *
 */

package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourcePipelineBuilder;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

public class DvDatabaseSatelliteSourcePipelineBuilder extends DvDatabaseSourcePipelineBuilder {
  public DvDatabaseSatelliteSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvSatellite dvTable,
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
    DvSatellite satellite = (DvSatellite) dvTable;
    DvDatabaseSource dbSource = (DvDatabaseSource) dvSource;
    DatabaseMeta sourceDbMeta = loadDatabaseMeta(variables.resolve(dbSource.getDatabaseName()));

    if (!Utils.isEmpty(satellite.getLinkName())) {
      return getLinkSatelliteSql(satellite, dbSource, sourceDbMeta);
    }

    DvHub hub = findHub(satellite.getHubName());
    StringBuilder sql = new StringBuilder("SELECT ");
    // Parent BKs come from the satellite feed (optional renames on the satellite).
    appendFields(
        sql,
        DvSatelliteParentKeySupport.quotedSelectExpressions(
            hub, satellite, sourceDbMeta, variables));
    appendComma(sql);
    appendFields(sql, quotedHubSatelliteAttributeFields(satellite, sourceDbMeta));

    if (satellite.hasDrivingKey()) {
      appendComma(sql);
      sql.append(sourceDbMeta.quoteField(variables.resolve(satellite.getDrivingKeySourceField())));
    }

    appendComma(sql);
    appendSourceField(hub, sql, sourceDbMeta);
    appendFrom(sourceDbMeta, dbSource, sql);

    return sql.toString();
  }

  private String getLinkSatelliteSql(
      DvSatellite satellite, DvDatabaseSource dbSource, DatabaseMeta sourceDbMeta)
      throws HopException {
    if (linkedLink == null) {
      linkedLink = findLink(satellite.getLinkName());
    }
    if (linkedLink == null) {
      throw new HopException(
          "Link '"
              + satellite.getLinkName()
              + "' not found in model for link satellite "
              + satellite.getName());
    }
    if (dvLinkHubSource == null) {
      throw new HopException(
          "No link hub source mapping found on link "
              + linkedLink.getName()
              + " for record source "
              + recordSource.getName()
              + " (required for link satellite "
              + satellite.getName()
              + ")");
    }

    StringBuilder sql = new StringBuilder("SELECT ");
    List<String> quotedFields = new ArrayList<>();

    for (String hubName : linkedLink.getHubNames()) {
      DvHub hub = findHub(hubName);
      if (hub == null) {
        throw new HopException(
            "Hub '"
                + hubName
                + "' referenced by link "
                + linkedLink.getName()
                + " was not found in the model");
      }
      DvLink.HubSourceKeyField sourceKeyField =
          DvLinkHubSourceKeyFieldSupport.findHubSourceKeyField(dvLinkHubSource, hubName);
      for (String sourceFieldName :
          DvLinkHubSourceKeyFieldSupport.resolveSourceFieldNames(hub, sourceKeyField, variables)) {
        quotedFields.add(sourceDbMeta.quoteField(sourceFieldName));
      }
    }

    // Dependent child keys are part of the link identity / link hash (same as link load).
    for (String dckSourceField : linkedLink.resolveDependentChildSourceFieldNames(variables)) {
      quotedFields.add(sourceDbMeta.quoteField(dckSourceField));
    }

    if (quotedFields.isEmpty()) {
      throw new HopException(
          "No hub business key source fields found for link "
              + linkedLink.getName()
              + " and record source "
              + recordSource.getName());
    }

    appendFields(sql, quotedFields);
    appendComma(sql);
    appendFields(sql, quotedLinkSatelliteAttributeFields(satellite, sourceDbMeta));

    if (satellite.hasDrivingKey()) {
      appendComma(sql);
      sql.append(sourceDbMeta.quoteField(variables.resolve(satellite.getDrivingKeySourceField())));
    }

    appendComma(sql);
    appendSourceField(satellite, sql, sourceDbMeta);
    appendFrom(sourceDbMeta, dbSource, sql);

    return sql.toString();
  }

  private List<String> quotedHubSatelliteAttributeFields(
      DvSatellite satellite, DatabaseMeta sourceDbMeta) throws HopException {
    List<String> fields = new ArrayList<>();
    if (satellite.getAttributes().isEmpty()) {
      throw new HopException(
          "Please specify at least one attribute field for satellite " + satellite.getName());
    }
    for (SatelliteAttribute attribute : satellite.getAttributes()) {
      fields.add(sourceDbMeta.quoteField(attribute.getName()));
    }
    return fields;
  }

  private List<String> quotedLinkSatelliteAttributeFields(
      DvSatellite satellite, DatabaseMeta sourceDbMeta) throws HopException {
    List<String> fields = new ArrayList<>();
    Set<String> added = new LinkedHashSet<>();

    if (dvLinkSatelliteSource != null
        && dvLinkSatelliteSource.getSatelliteSourceKeyFields() != null) {
      for (DvLink.SatelliteSourceKeyField skf :
          dvLinkSatelliteSource.getSatelliteSourceKeyFields()) {
        if (skf == null || !satellite.getName().equals(skf.getSatelliteName())) {
          continue;
        }
        if (skf.getAttributeSources() != null) {
          for (AttributeSource attributeSource : skf.getAttributeSources()) {
            if (attributeSource != null
                && StringUtils.isNotEmpty(attributeSource.getSourceFieldName())) {
              String quoted =
                  sourceDbMeta.quoteField(variables.resolve(attributeSource.getSourceFieldName()));
              if (added.add(quoted)) {
                fields.add(quoted);
              }
            }
          }
        }
      }
    }

    if (fields.isEmpty()) {
      return quotedHubSatelliteAttributeFields(satellite, sourceDbMeta);
    }
    return fields;
  }
}