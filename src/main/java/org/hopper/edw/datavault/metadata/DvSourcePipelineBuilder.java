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
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingPipelineSupport;
import org.hopper.edw.datavault.metadata.datatypemapping.DvCatalogMappingStreamFieldsSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * When reading from a DataVaultSource we can have different types of sources: Databases, files, and
 * so on. It makes sense to have the various types of different sources (e.g. DvDatabaseSource)
 * generate the appropriate transforms to not only read the source data in a consistent way but also
 * to sort it as needed for the updates of Hubs, Satellites and Links. This class receives
 * information about what's required: - The DataVaultSource and IDvSource to reference - the
 * pipeline to put the transforms in - where to start placing the transforms - what the last
 * transform them, and
 */
@Getter
@Setter
public abstract class DvSourcePipelineBuilder {

  public static final int TRANSFORM_SPACING_X = 128;

  protected final IVariables variables;
  protected final IHopMetadataProvider metadataProvider;
  protected final DataVaultModel model;
  protected final DataVaultConfiguration configuration;
  protected final DataVaultSource recordSource;
  protected final IDvSource dvSource;
  protected final IDvTable dvTable;
  protected final PipelineMeta pipelineMeta;
  protected final Point startPoint;

  /** The transform that will continue the source data. */
  protected TransformMeta resultTransform;

  // Fields specific for Link / link-satellite updates
  protected DvLink linkedLink;
  protected DvLink.DvLinkHubSource dvLinkHubSource;
  protected DvLink.DvLinkSatelliteSource dvLinkSatelliteSource;

  public DvSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      IDvTable dvTable,
      Point startPoint) {
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.model = model;
    this.configuration = model.getConfigurationOrDefault();
    this.pipelineMeta = pipelineMeta;
    this.recordSource = recordSource;
    this.dvSource = dvSource;
    this.dvTable = dvTable;
    this.startPoint = startPoint;
  }

  public abstract void build() throws HopException;

  /**
   * After the source-specific chain is built, apply catalog data type mapping metadata (lengths,
   * conversion masks, renames) via Select Values when present on {@link #recordSource} fields.
   */
  public void applyCatalogDataTypeMappings() throws HopException {
    if (resultTransform == null || recordSource == null) {
      return;
    }
    Point location =
        resultTransform.getLocation() != null
            ? new Point(
                resultTransform.getLocation().x + TRANSFORM_SPACING_X,
                resultTransform.getLocation().y)
            : (startPoint != null
                ? new Point(startPoint.x + TRANSFORM_SPACING_X, startPoint.y)
                : new Point(300, 100));
    List<SourceField> fields = recordSource.getFields(metadataProvider);
    String sourceIndicator = resolveSourceIndicatorField();
    String targetRecordSource = resolveTargetRecordSourceField();
    fields =
        DataTypeMappingPipelineSupport.rewriteSourceIndicatorLookup(
            fields, sourceIndicator, targetRecordSource);
    resultTransform =
        DataTypeMappingPipelineSupport.injectFromSourceFields(
            pipelineMeta,
            resultTransform,
            fields,
            location,
            resolveIncomingStreamFieldNames(targetRecordSource));
  }

  /**
   * Runtime field names after the generated source SQL / file projection. Never returns {@code
   * null}: an unknown layout yields an empty list so catalog mappings are skipped instead of being
   * applied to unused source columns (link loads must not rewrite satellite attributes).
   */
  protected Collection<String> resolveIncomingStreamFieldNames(String targetRecordSourceField) {
    List<String> expected =
        DvCatalogMappingStreamFieldsSupport.expectedStreamFieldNames(
            dvTable, recordSource, model, variables, targetRecordSourceField, dvLinkHubSource);
    if (!expected.isEmpty()) {
      return expected;
    }
    Collection<String> live = resolveLiveTransformFieldNames();
    return live != null ? live : List.of();
  }

  private String resolveSourceIndicatorField() {
    if (recordSource == null) {
      return null;
    }
    String field = recordSource.getSourceIndicatorField();
    return variables != null ? variables.resolve(field) : field;
  }

  private String resolveTargetRecordSourceField() {
    try {
      return DvSourceFieldMappingSupport.resolveRecordSourceFieldName(
          configuration, dvTable, variables);
    } catch (Exception e) {
      return null;
    }
  }

  private Collection<String> resolveLiveTransformFieldNames() {
    if (pipelineMeta == null || resultTransform == null) {
      return null;
    }
    try {
      IRowMeta rowMeta = pipelineMeta.getTransformFields(variables, resultTransform);
      if (rowMeta == null || rowMeta.isEmpty()) {
        return null;
      }
      List<String> names = new ArrayList<>();
      for (int i = 0; i < rowMeta.size(); i++) {
        String name = rowMeta.getValueMeta(i).getName();
        if (!Utils.isEmpty(name)) {
          names.add(name);
        }
      }
      return names.isEmpty() ? null : names;
    } catch (Exception e) {
      return null;
    }
  }

  protected DatabaseMeta loadDatabaseMeta(String name) throws HopException {
    if (StringUtils.isEmpty(name)) {
      throw new HopException("Please specify a database name to load");
    }
    DatabaseMeta databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(name);
    if (databaseMeta == null) {
      throw new HopException(
          "Referenced database '" + name + "' couldn't be found in the metadata");
    }
    return databaseMeta;
  }

  /**
   * Look for the hub with the given name in the model.
   *
   * @param hubName The hub to load
   * @return The hub from the model
   */
  protected DvHub findHub(String hubName) throws HopException {
    if (StringUtils.isEmpty(hubName)) {
      throw new HopException("No hub name provided to look for");
    }
    DvHub hub =
        DvTableResolutionSupport.resolveHub(
            model, variables.resolve(hubName), variables, metadataProvider);
    if (hub == null) {
      throw new HopException(
          "Hub " + hubName + " could not be found in data vault model " + model.getName());
    }
    return hub;
  }

  protected DvLink findLink(String linkName) throws HopException {
    if (StringUtils.isEmpty(linkName)) {
      throw new HopException("No link name provided to look for");
    }
    DvLink link =
        DvTableResolutionSupport.resolveLink(
            model, variables.resolve(linkName), variables, metadataProvider);
    if (link == null) {
      throw new HopException(
          "Link " + linkName + " could not be found in data vault model " + model.getName());
    }
    return link;
  }
}
