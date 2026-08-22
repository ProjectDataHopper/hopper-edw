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
package org.hopper.edw.datavault.metadata.dimensional;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.pipeline.HeadlessPipelineFieldSupport;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.metainject.MetaInjectMeta;
import org.apache.hop.pipeline.transforms.metainject.MetaInjectOutputField;

/** Helpers for dimensional tables that use a Hop pipeline as the staging source. */
public final class DmSourcePipelineSupport {

  private static final Class<?> PKG = DmSourcePipelineSupport.class;

  private DmSourcePipelineSupport() {}

  public static PipelineMeta loadSourcePipelineMeta(
      String pipelineFile, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(pipelineFile)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourcePipelineSupport.Error.MissingPipelineFile"));
    }

    IVariables tmpSpace = variables != null ? variables : Variables.getADefaultVariableSpace();
    String realFilename = tmpSpace.resolve(pipelineFile);
    try {
      Path localPath = Path.of(realFilename);
      if (Files.isRegularFile(localPath)) {
        try (InputStream inputStream = Files.newInputStream(localPath)) {
          PipelineMeta pipelineMeta = new PipelineMeta(inputStream, metadataProvider, tmpSpace);
          pipelineMeta.setFilename(realFilename);
          return pipelineMeta;
        }
      }
      PipelineMeta pipelineMeta = new PipelineMeta(realFilename, metadataProvider, tmpSpace);
      pipelineMeta.setFilename(realFilename);
      return pipelineMeta;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DmSourcePipelineSupport.Error.UnableToLoadPipeline", realFilename),
          e);
    }
  }

  public static List<String> listTransformNames(
      String pipelineFile, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    PipelineMeta pipelineMeta = loadSourcePipelineMeta(pipelineFile, variables, metadataProvider);
    List<String> names = new ArrayList<>();
    for (int i = 0; i < pipelineMeta.nrTransforms(); i++) {
      String name = pipelineMeta.getTransform(i).getName();
      if (!Utils.isEmpty(name)) {
        names.add(name);
      }
    }
    return names;
  }

  public static IRowMeta resolveSourceRowMeta(
      DmSourceConfiguration source, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (source == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourcePipelineSupport.Error.MissingSourceConfiguration"));
    }
    String pipelineFile = source.resolveSourcePipelineFile(variables);
    String transformName = source.resolveSourcePipelineTransform(variables);
    if (Utils.isEmpty(pipelineFile)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourcePipelineSupport.Error.MissingPipelineFile"));
    }
    if (Utils.isEmpty(transformName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourcePipelineSupport.Error.MissingTransform"));
    }

    PipelineMeta pipelineMeta = loadSourcePipelineMeta(pipelineFile, variables, metadataProvider);
    if (pipelineMeta.findTransform(transformName) == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DmSourcePipelineSupport.Error.UnknownTransform", transformName, pipelineFile));
    }

    try {
      IRowMeta rowMeta =
          HeadlessPipelineFieldSupport.resolveTransformFields(
              pipelineMeta, variables, transformName);
      if (rowMeta == null || rowMeta.isEmpty()) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "DmSourcePipelineSupport.Error.NoFields", transformName, pipelineFile));
      }
      return rowMeta;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DmSourcePipelineSupport.Error.ResolveFieldsFailed",
              transformName,
              pipelineFile),
          e);
    }
  }

  public static List<MetaInjectOutputField> toMetaInjectOutputFields(IRowMeta rowMeta) {
    List<MetaInjectOutputField> fields = new ArrayList<>();
    if (rowMeta == null) {
      return fields;
    }
    for (int i = 0; i < rowMeta.size(); i++) {
      IValueMeta valueMeta = rowMeta.getValueMeta(i);
      if (valueMeta == null || Utils.isEmpty(valueMeta.getName())) {
        continue;
      }
      fields.add(
          new MetaInjectOutputField(
              valueMeta.getName(),
              valueMeta.getType(),
              valueMeta.getLength(),
              valueMeta.getPrecision()));
    }
    return fields;
  }

  public static MetaInjectMeta buildMetaInjectMeta(
      DmSourceConfiguration source, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (source == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DmSourcePipelineSupport.Error.MissingSourceConfiguration"));
    }

    String pipelineFile = source.resolveSourcePipelineFile(variables);
    String transformName = source.resolveSourcePipelineTransform(variables);
    IRowMeta rowMeta = resolveSourceRowMeta(source, variables, metadataProvider);
    List<MetaInjectOutputField> outputFields = toMetaInjectOutputFields(rowMeta);
    if (outputFields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DmSourcePipelineSupport.Error.NoFields", transformName, pipelineFile));
    }

    MetaInjectMeta metaInjectMeta = new MetaInjectMeta();
    metaInjectMeta.setTemplateFileName(pipelineFile);
    metaInjectMeta.setSourceTransformName(transformName);
    metaInjectMeta.setSourceOutputFields(outputFields);
    metaInjectMeta.setAllowEmptyStreamOnExecution(true);
    metaInjectMeta.setRunConfigurationName(source.resolveSourcePipelineRunConfiguration(variables));
    metaInjectMeta.setNoExecution(false);

    LogChannel.GENERAL.logDetailed(
        "Configured MetaInject source from pipeline ["
            + pipelineFile
            + "] transform ["
            + transformName
            + "] with "
            + outputFields.size()
            + " output fields");
    return metaInjectMeta;
  }
}
