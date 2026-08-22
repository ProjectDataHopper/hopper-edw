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
package org.apache.hop.datavault.metadata.pipeline;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.pipeline.transform.RowAdapter;
import org.apache.hop.pipeline.transforms.metainject.MetaInjectMeta;
import org.apache.hop.pipeline.transforms.metainject.MetaInjectOutputField;

/** Helpers for {@link DvPipelineSource}: load pipeline meta, MetaInject, and preview. */
public final class DvPipelineSourceSupport {

  private static final Class<?> PKG = DvPipelineSourceSupport.class;

  private DvPipelineSourceSupport() {}

  public static String resolvePipelineFilename(DvPipelineSource source, IVariables variables) {
    if (source == null) {
      return "";
    }
    String filename = source.getPipelineFilename();
    return variables != null ? variables.resolve(ConstNvl(filename)) : ConstNvl(filename);
  }

  public static String resolveOutputTransform(DvPipelineSource source, IVariables variables) {
    if (source == null) {
      return "";
    }
    String transform = source.getOutputTransformName();
    return variables != null ? variables.resolve(ConstNvl(transform)) : ConstNvl(transform);
  }

  public static String resolveRunConfiguration(DvPipelineSource source, IVariables variables) {
    if (source == null) {
      return "local";
    }
    String runConfig = source.getPipelineRunConfiguration();
    if (Utils.isEmpty(runConfig)) {
      return "local";
    }
    return variables != null ? variables.resolve(runConfig) : runConfig;
  }

  public static PipelineMeta loadSourcePipelineMeta(
      String pipelineFile, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(pipelineFile)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvPipelineSourceSupport.Error.MissingPipelineFile"));
    }
    IVariables tmpSpace = variables != null ? variables : Variables.getADefaultVariableSpace();
    String realFilename = tmpSpace.resolve(pipelineFile);
    try {
      if (HopVfs.fileExists(realFilename)) {
        try (InputStream inputStream = HopVfs.getInputStream(realFilename)) {
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
              PKG, "DvPipelineSourceSupport.Error.UnableToLoadPipeline", realFilename),
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

  /**
   * Preferred MetaInject configuration: use declared catalog fields when present so validation and
   * load do not depend on live transform discovery.
   */
  public static MetaInjectMeta buildMetaInjectMeta(
      DvPipelineSource source, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (source == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvPipelineSourceSupport.Error.MissingSource"));
    }
    String pipelineFile = resolvePipelineFilename(source, variables);
    String transformName = resolveOutputTransform(source, variables);
    if (Utils.isEmpty(pipelineFile)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvPipelineSourceSupport.Error.MissingPipelineFile"));
    }
    if (Utils.isEmpty(transformName)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvPipelineSourceSupport.Error.MissingTransform"));
    }

    List<MetaInjectOutputField> outputFields = toMetaInjectOutputFields(source.getFields());
    if (outputFields.isEmpty()) {
      // Fall back to live transform fields only when the catalog contract is empty.
      IRowMeta rowMeta =
          resolveLiveTransformFields(pipelineFile, transformName, variables, metadataProvider);
      outputFields = toMetaInjectOutputFields(rowMeta);
    }
    if (outputFields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvPipelineSourceSupport.Error.NoFields", transformName, pipelineFile));
    }

    MetaInjectMeta metaInjectMeta = new MetaInjectMeta();
    metaInjectMeta.setTemplateFileName(pipelineFile);
    metaInjectMeta.setSourceTransformName(transformName);
    metaInjectMeta.setSourceOutputFields(outputFields);
    metaInjectMeta.setAllowEmptyStreamOnExecution(true);
    metaInjectMeta.setRunConfigurationName(resolveRunConfiguration(source, variables));
    metaInjectMeta.setNoExecution(false);
    return metaInjectMeta;
  }

  public static List<MetaInjectOutputField> toMetaInjectOutputFields(List<SourceField> fields) {
    List<MetaInjectOutputField> out = new ArrayList<>();
    if (fields == null) {
      return out;
    }
    for (SourceField field : fields) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      int type = field.getHopType() > 0 ? field.getHopType() : IValueMeta.TYPE_STRING;
      int length = parseInt(field.getLength(), -1);
      int precision = parseInt(field.getPrecision(), -1);
      out.add(new MetaInjectOutputField(field.getName(), type, length, precision));
    }
    return out;
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

  public static IRowMeta resolveLiveTransformFields(
      String pipelineFile,
      String transformName,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    PipelineMeta pipelineMeta = loadSourcePipelineMeta(pipelineFile, variables, metadataProvider);
    if (pipelineMeta.findTransform(transformName) == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "DvPipelineSourceSupport.Error.UnknownTransform", transformName, pipelineFile));
    }
    try {
      IRowMeta rowMeta =
          HeadlessPipelineFieldSupport.resolveTransformFields(
              pipelineMeta, variables, transformName);
      if (rowMeta == null || rowMeta.isEmpty()) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "DvPipelineSourceSupport.Error.NoFields", transformName, pipelineFile));
      }
      return rowMeta;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "DvPipelineSourceSupport.Error.ResolveFieldsFailed",
              transformName,
              pipelineFile),
          e);
    }
  }

  /**
   * Apply named-parameter <em>defaults</em> from {@code pipelineMeta} into {@code variables}.
   *
   * <p>Non-empty values already present on {@code variables} (project, environment, or caller
   * overrides) are left alone — same preference as {@code NamedParameters#activateParameters}.
   *
   * <p>Needed for source-pipeline preview: {@code PipelinePreviewProgressDialog} builds a {@code
   * LocalPipelineEngine} without {@code copyParametersFromDefinitions}, so parameter defaults such
   * as {@code RETAIL_CSV_WAVE=demo} never become variables and filenames like {@code
   * asn_${RETAIL_CSV_WAVE}.xml} resolve incorrectly (Get XML: "No file(s) specified").
   */
  public static void activatePipelineParameterDefaults(
      PipelineMeta pipelineMeta, IVariables variables) {
    if (pipelineMeta == null || variables == null) {
      return;
    }
    for (String name : pipelineMeta.listParameters()) {
      if (Utils.isEmpty(name)) {
        continue;
      }
      String existing = variables.getVariable(name);
      if (!Utils.isEmpty(existing)) {
        continue;
      }
      try {
        String defaultValue = pipelineMeta.getParameterDefault(name);
        if (defaultValue != null) {
          variables.setVariable(name, defaultValue);
        }
      } catch (Exception ignored) {
        // Skip parameters that cannot be read.
      }
    }
  }

  public static List<RowMetaAndData> previewRecords(
      DvPipelineSource source,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int rowLimit,
      int queryTimeoutSeconds)
      throws HopException {
    String pipelineFile = resolvePipelineFilename(source, variables);
    String transformName = resolveOutputTransform(source, variables);
    if (Utils.isEmpty(pipelineFile) || Utils.isEmpty(transformName)) {
      return List.of();
    }
    int limit = rowLimit > 0 ? rowLimit : 50;
    PipelineMeta pipelineMeta = loadSourcePipelineMeta(pipelineFile, variables, metadataProvider);
    List<RowMetaAndData> collected = new ArrayList<>();
    Pipeline pipeline = new LocalPipelineEngine(pipelineMeta);
    pipeline.setMetadataProvider(metadataProvider);
    if (variables != null) {
      pipeline.copyFrom(variables);
    }
    // Mirror Hop GUI pipeline preview: definitions + defaults before prepareExecution.
    pipeline.copyParametersFromDefinitions(pipelineMeta);
    activatePipelineParameterDefaults(pipelineMeta, pipeline);
    try {
      pipeline.prepareExecution();
      ITransform runThread = pipeline.findRunThread(transformName);
      if (runThread == null) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "DvPipelineSourceSupport.Error.UnknownTransform",
                transformName,
                pipelineFile));
      }
      runThread.addRowListener(
          new RowAdapter() {
            @Override
            public void rowWrittenEvent(IRowMeta rowMeta, Object[] row) {
              if (collected.size() < limit) {
                try {
                  collected.add(new RowMetaAndData(rowMeta.clone(), rowMeta.cloneRow(row)));
                } catch (Exception e) {
                  collected.add(new RowMetaAndData(rowMeta, row));
                }
              }
              if (collected.size() >= limit) {
                try {
                  pipeline.stopAll();
                } catch (Exception ignored) {
                  // best effort
                }
              }
            }
          });
      pipeline.startThreads();
      pipeline.waitUntilFinished();
      if (pipeline.getErrors() > 0) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "DvPipelineSourceSupport.Error.PreviewFailed",
                Long.toString(pipeline.getErrors())));
      }
      return collected;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvPipelineSourceSupport.Error.PreviewException"), e);
    } finally {
      try {
        pipeline.cleanup();
      } catch (Exception ignored) {
        // ignore
      }
    }
  }

  private static int parseInt(String value, int defaultValue) {
    if (Utils.isEmpty(value)) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static String ConstNvl(String value) {
    return value == null ? "" : value;
  }
}
