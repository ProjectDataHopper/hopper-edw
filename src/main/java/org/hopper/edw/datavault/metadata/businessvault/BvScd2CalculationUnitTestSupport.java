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
package org.hopper.edw.datavault.metadata.businessvault;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.RowAdapter;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dummy.DummyMeta;
import org.apache.hop.pipeline.transforms.reservoirsampling.ReservoirSamplingMeta;
import org.apache.hop.testing.DataSet;
import org.apache.hop.testing.DataSetCsvUtil;
import org.apache.hop.testing.DataSetField;
import org.apache.hop.testing.PipelineUnitTest;
import org.apache.hop.testing.PipelineUnitTestFieldMapping;
import org.apache.hop.testing.PipelineUnitTestSetLocation;
import org.apache.hop.testing.TestType;
import org.hopper.edw.datavault.catalog.CatalogModelRegistrySupport;
import org.hopper.edw.datavault.expression.SqlExpressionProgram;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.transform.sqlexpression.SqlExpressionMeta;
import org.hopper.edw.datavault.transform.sqlexpression.SqlExpressionMetaFactory;

/**
 * Builds Hop pipeline unit-test artifacts for SCD2 SQL calculations: a slim Dummy → SQL Expression
 * (Business Vault table reference) → Dummy pipeline, input/golden data sets, and an optional
 * warehouse capture pipeline that reservoir-samples collapsed rows.
 */
public final class BvScd2CalculationUnitTestSupport {

  private static final Class<?> PKG = BvScd2CalculationUnitTestSupport.class;

  public static final int DEFAULT_SAMPLE_SIZE = 100;
  public static final String TRANSFORM_COLLAPSE_SAMPLE = "collapse_sample";
  public static final String TRANSFORM_CALCULATE = "calculate";
  public static final String TRANSFORM_CALCULATED_OUT = "calculated_out";
  public static final String TRANSFORM_SAMPLE = "sample_collapse";
  private static final int SPACING_WIDTH = 160;
  private static final Point LOCATION_START = new Point(160, 160);

  private BvScd2CalculationUnitTestSupport() {}

  public record ArtifactNames(
      String sanitizedTable,
      String collapseDataSetName,
      String calculatedDataSetName,
      String unitTestName,
      String unitTestPipelineName,
      String capturePipelineName) {}

  public record GeneratedArtifacts(
      ArtifactNames names,
      PipelineMeta unitTestPipeline,
      PipelineMeta capturePipeline,
      DataSet collapseDataSet,
      DataSet calculatedDataSet,
      PipelineUnitTest unitTest,
      String unitTestPipelineFilename,
      String capturePipelineFilename,
      int collapseRowsWritten,
      int calculatedRowsWritten) {}

  public static ArtifactNames namesFor(String tableName) {
    String sanitized = sanitize(tableName);
    return new ArtifactNames(
        sanitized,
        "bv-scd2-" + sanitized + "-collapse",
        "bv-scd2-" + sanitized + "-calculated",
        "bv-scd2-" + sanitized + "-calculations",
        "test-scd2-calc-" + sanitized,
        "capture-scd2-calc-" + sanitized);
  }

  public static String sanitize(String tableName) {
    String value = Const.NVL(tableName, "table").trim();
    String sanitized = value.replaceAll("[^A-Za-z0-9._-]+", "_");
    return Utils.isEmpty(sanitized) ? "table" : sanitized;
  }

  public static PipelineMeta buildUnitTestPipeline(
      BusinessVaultModel bvModel, BvScd2Table scd2Table, IVariables variables) {
    ArtifactNames names = namesFor(scd2Table.getName());
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName(names.unitTestPipelineName());

    TransformMeta collapseSample =
        dummyTransform(TRANSFORM_COLLAPSE_SAMPLE, LOCATION_START.x, LOCATION_START.y);
    pipelineMeta.addTransform(collapseSample);

    SqlExpressionMeta sqlMeta =
        SqlExpressionMetaFactory.createFromBvTable(
            portableModelFilename(bvModel, variables), scd2Table.getName());
    TransformMeta calculate = new TransformMeta("SqlExpression", TRANSFORM_CALCULATE, sqlMeta);
    calculate.setLocation(LOCATION_START.x + SPACING_WIDTH, LOCATION_START.y);
    pipelineMeta.addTransform(calculate);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(collapseSample, calculate));

    TransformMeta calculatedOut =
        dummyTransform(
            TRANSFORM_CALCULATED_OUT, LOCATION_START.x + 2 * SPACING_WIDTH, LOCATION_START.y);
    pipelineMeta.addTransform(calculatedOut);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(calculate, calculatedOut));
    return pipelineMeta;
  }

  public static PipelineMeta buildCapturePipeline(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table)
      throws HopException {
    BvScd2PipelineSupport.Scd2BuildContext ctx =
        BvScd2PipelineSupport.createContext(
            metadataProvider, variables, bvModel, dvModel, scd2Table);
    if (ctx == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvScd2CalculationUnitTestSupport.Error.MissingContext", scd2Table.getName()));
    }
    PipelineMeta pipelineMeta = BvScd2PipelineSupport.generatePipeline(ctx);
    pipelineMeta.setName(namesFor(scd2Table.getName()).capturePipelineName());
    spliceCaptureTail(pipelineMeta, portableModelFilename(bvModel, variables), scd2Table.getName());
    return pipelineMeta;
  }

  static void spliceCaptureTail(
      PipelineMeta pipelineMeta, String bvModelFilename, String scd2TableName) throws HopException {
    TransformMeta calculate = findSqlExpression(pipelineMeta);
    if (calculate == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "BvScd2CalculationUnitTestSupport.Error.MissingCalculate"));
    }
    TransformMeta predecessor = findSinglePredecessor(pipelineMeta, calculate);
    if (predecessor == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "BvScd2CalculationUnitTestSupport.Error.MissingPredecessor"));
    }
    removeDownstream(pipelineMeta, calculate);
    removeHop(pipelineMeta, predecessor, calculate);

    TransformMeta sample = addReservoirSampling(pipelineMeta, predecessor);
    TransformMeta collapseSample =
        dummyTransform(
            TRANSFORM_COLLAPSE_SAMPLE, offsetX(sample, SPACING_WIDTH), locationY(sample));
    pipelineMeta.addTransform(collapseSample);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(sample, collapseSample));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(collapseSample, calculate));
    calculate.setLocation(offsetX(collapseSample, SPACING_WIDTH), locationY(collapseSample));

    if (calculate.getTransform() instanceof SqlExpressionMeta sqlMeta) {
      sqlMeta.setBusinessVaultModelFilename(bvModelFilename);
      sqlMeta.setScd2TableName(scd2TableName);
      sqlMeta.getFields().clear();
    }
    calculate.setName(TRANSFORM_CALCULATE);

    TransformMeta calculatedOut =
        dummyTransform(
            TRANSFORM_CALCULATED_OUT, offsetX(calculate, SPACING_WIDTH), locationY(calculate));
    pipelineMeta.addTransform(calculatedOut);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(calculate, calculatedOut));
  }

  public static List<PipelineUnitTestFieldMapping> identityMappings(IRowMeta rowMeta) {
    List<PipelineUnitTestFieldMapping> mappings = new ArrayList<>();
    if (rowMeta == null) {
      return mappings;
    }
    for (int i = 0; i < rowMeta.size(); i++) {
      String name = rowMeta.getValueMeta(i).getName();
      mappings.add(new PipelineUnitTestFieldMapping(name, name));
    }
    return mappings;
  }

  public static List<String> fieldOrder(IRowMeta rowMeta) {
    List<String> order = new ArrayList<>();
    if (rowMeta == null) {
      return order;
    }
    for (int i = 0; i < rowMeta.size(); i++) {
      order.add(rowMeta.getValueMeta(i).getName());
    }
    return order;
  }

  public static List<DataSetField> fieldsFromRowMeta(IRowMeta rowMeta) {
    List<DataSetField> fields = new ArrayList<>();
    if (rowMeta == null) {
      return fields;
    }
    for (int i = 0; i < rowMeta.size(); i++) {
      IValueMeta valueMeta = rowMeta.getValueMeta(i);
      fields.add(
          new DataSetField(
              valueMeta.getName(),
              valueMeta.getType(),
              valueMeta.getLength(),
              valueMeta.getPrecision(),
              valueMeta.getComments(),
              valueMeta.getConversionMask()));
    }
    return fields;
  }

  public static DataSet createDataSet(
      String name, String description, IRowMeta rowMeta, IVariables variables) {
    DataSet dataSet = new DataSet();
    dataSet.setName(name);
    dataSet.setDescription(description);
    dataSet.setFolderName(resolveDataSetFolder(variables));
    dataSet.setBaseFilename(name + ".csv");
    dataSet.setFields(fieldsFromRowMeta(rowMeta));
    return dataSet;
  }

  public static PipelineUnitTest createUnitTest(
      ArtifactNames names,
      String pipelineFilename,
      IRowMeta collapseLayout,
      IRowMeta calculatedLayout) {
    PipelineUnitTest unitTest = new PipelineUnitTest();
    unitTest.setName(names.unitTestName());
    unitTest.setDescription("SCD2 calculation unit test for " + names.sanitizedTable());
    unitTest.setType(TestType.UNIT_TEST);
    unitTest.setAutoOpening(true);
    unitTest.setPipelineFilename(pipelineFilenameRelativeToProject(pipelineFilename));
    unitTest
        .getInputDataSets()
        .add(location(TRANSFORM_COLLAPSE_SAMPLE, names.collapseDataSetName(), collapseLayout));
    unitTest
        .getGoldenDataSets()
        .add(location(TRANSFORM_CALCULATED_OUT, names.calculatedDataSetName(), calculatedLayout));
    return unitTest;
  }

  public static IRowMeta collapseLayout(
      BvScd2Table scd2Table,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IVariables variables)
      throws HopException {
    return BvScd2PipelineSupport.buildCollapseRowLayout(
        scd2Table, bvModel.getConfigurationOrDefault(), dvModel, variables);
  }

  public static IRowMeta calculatedLayout(
      BvScd2Table scd2Table, IRowMeta collapseLayout, IVariables variables) throws HopException {
    SqlExpressionProgram program =
        SqlExpressionProgram.compile(
            BvScd2CalculationValidationSupport.toSpecs(scd2Table.getCalculations(), variables),
            collapseLayout,
            variables);
    return program.getOutputRowMeta();
  }

  public static GeneratedArtifacts generate(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table,
      boolean writeFiles)
      throws HopException {
    if (scd2Table == null || !scd2Table.hasCalculations()) {
      throw new HopException(
          BaseMessages.getString(PKG, "BvScd2CalculationUnitTestSupport.Error.NoCalculations"));
    }
    ArtifactNames names = namesFor(scd2Table.getName());
    IRowMeta collapse = collapseLayout(scd2Table, bvModel, dvModel, variables);
    IRowMeta calculated = calculatedLayout(scd2Table, collapse, variables);

    PipelineMeta unitTestPipeline = buildUnitTestPipeline(bvModel, scd2Table, variables);
    PipelineMeta capturePipeline = null;
    try {
      capturePipeline =
          buildCapturePipeline(metadataProvider, variables, bvModel, dvModel, scd2Table);
    } catch (Exception ignored) {
      // Capture needs resolvable DV/BV connections; the slim unit-test pipeline does not.
    }

    String testFolder =
        resolveTestFolder(variables, bvModel != null ? bvModel.getFilename() : null);
    String unitTestFilename =
        appendPath(testFolder, names.unitTestPipelineName() + PipelineMeta.PIPELINE_EXTENSION);
    String captureFilename =
        capturePipeline != null
            ? appendPath(testFolder, names.capturePipelineName() + PipelineMeta.PIPELINE_EXTENSION)
            : null;

    DataSet collapseDataSet =
        createDataSet(
            names.collapseDataSetName(),
            "Collapsed SCD2 sample for " + scd2Table.getName(),
            collapse,
            variables);
    DataSet calculatedDataSet =
        createDataSet(
            names.calculatedDataSetName(),
            "Calculated SCD2 golden for " + scd2Table.getName(),
            calculated,
            variables);
    String storedPipelineFilename = portablePath(unitTestFilename, variables);
    PipelineUnitTest unitTest = createUnitTest(names, storedPipelineFilename, collapse, calculated);

    if (writeFiles) {
      savePipeline(unitTestPipeline, unitTestFilename, variables);
      if (capturePipeline != null && captureFilename != null) {
        savePipeline(capturePipeline, captureFilename, variables);
      }
      ensureDataSetStorage(collapseDataSet, collapse, variables);
      ensureDataSetStorage(calculatedDataSet, calculated, variables);
      if (metadataProvider != null) {
        metadataProvider.getSerializer(DataSet.class).save(collapseDataSet);
        metadataProvider.getSerializer(DataSet.class).save(calculatedDataSet);
        metadataProvider.getSerializer(PipelineUnitTest.class).save(unitTest);
      }
    }

    return new GeneratedArtifacts(
        names,
        unitTestPipeline,
        capturePipeline,
        collapseDataSet,
        calculatedDataSet,
        unitTest,
        unitTestFilename,
        captureFilename,
        0,
        0);
  }

  public static GeneratedArtifacts runCapture(
      GeneratedArtifacts generated, IHopMetadataProvider metadataProvider, IVariables variables)
      throws HopException {
    if (generated == null || generated.capturePipeline() == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "BvScd2CalculationUnitTestSupport.Error.NoCapturePipeline"));
    }
    PipelineMeta capture = generated.capturePipeline();
    capture.setMetadataProvider(metadataProvider);
    List<Object[]> collapseRows = new ArrayList<>();
    List<Object[]> calculatedRows = new ArrayList<>();
    IRowMeta[] collapseMeta = new IRowMeta[1];
    IRowMeta[] calculatedMeta = new IRowMeta[1];

    Pipeline pipeline = new LocalPipelineEngine(capture);
    pipeline.setMetadataProvider(metadataProvider);
    if (variables != null) {
      pipeline.copyFrom(variables);
    }
    pipeline.prepareExecution();
    addCollector(pipeline, TRANSFORM_COLLAPSE_SAMPLE, collapseRows, collapseMeta);
    addCollector(pipeline, TRANSFORM_CALCULATED_OUT, calculatedRows, calculatedMeta);
    pipeline.startThreads();
    pipeline.waitUntilFinished();
    if (pipeline.getErrors() > 0) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BvScd2CalculationUnitTestSupport.Error.CaptureFailed",
              String.valueOf(pipeline.getErrors())));
    }

    if (collapseMeta[0] != null) {
      generated.collapseDataSet().setFields(fieldsFromRowMeta(collapseMeta[0]));
    }
    if (calculatedMeta[0] != null) {
      generated.calculatedDataSet().setFields(fieldsFromRowMeta(calculatedMeta[0]));
    }
    writeDataSetCsv(
        generated.collapseDataSet(),
        collapseMeta[0] != null ? collapseMeta[0] : generated.collapseDataSet().getSetRowMeta(),
        collapseRows,
        variables);
    writeDataSetCsv(
        generated.calculatedDataSet(),
        calculatedMeta[0] != null
            ? calculatedMeta[0]
            : generated.calculatedDataSet().getSetRowMeta(),
        calculatedRows,
        variables);

    PipelineUnitTest unitTest = generated.unitTest();
    unitTest.getInputDataSets().clear();
    unitTest
        .getInputDataSets()
        .add(
            location(
                TRANSFORM_COLLAPSE_SAMPLE,
                generated.names().collapseDataSetName(),
                collapseMeta[0] != null
                    ? collapseMeta[0]
                    : generated.collapseDataSet().getSetRowMeta()));
    unitTest.getGoldenDataSets().clear();
    unitTest
        .getGoldenDataSets()
        .add(
            location(
                TRANSFORM_CALCULATED_OUT,
                generated.names().calculatedDataSetName(),
                calculatedMeta[0] != null
                    ? calculatedMeta[0]
                    : generated.calculatedDataSet().getSetRowMeta()));

    if (metadataProvider != null) {
      metadataProvider.getSerializer(DataSet.class).save(generated.collapseDataSet());
      metadataProvider.getSerializer(DataSet.class).save(generated.calculatedDataSet());
      metadataProvider.getSerializer(PipelineUnitTest.class).save(unitTest);
    }

    return new GeneratedArtifacts(
        generated.names(),
        generated.unitTestPipeline(),
        generated.capturePipeline(),
        generated.collapseDataSet(),
        generated.calculatedDataSet(),
        unitTest,
        generated.unitTestPipelineFilename(),
        generated.capturePipelineFilename(),
        collapseRows.size(),
        calculatedRows.size());
  }

  public static String savePipeline(
      PipelineMeta pipelineMeta, String filename, IVariables variables) throws HopException {
    if (pipelineMeta == null || Utils.isEmpty(filename)) {
      return filename;
    }
    pipelineMeta.setFilename(filename);
    try {
      FileObject file = HopVfs.getFileObject(filename, variables);
      FileObject parent = file.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
      String xml = pipelineMeta.getXml(variables);
      try (OutputStreamWriter writer =
          new OutputStreamWriter(HopVfs.getOutputStream(file, false), StandardCharsets.UTF_8)) {
        writer.write(xml);
      }
      return filename;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvScd2CalculationUnitTestSupport.Error.SavePipeline", filename, e.getMessage()),
          e);
    }
  }

  public static String portableModelFilename(BusinessVaultModel bvModel, IVariables variables) {
    if (bvModel == null) {
      return null;
    }
    return portablePath(bvModel.getFilename(), variables);
  }

  public static String resolveTestFolder(IVariables variables, String modelFilename) {
    String projectHome = variables != null ? variables.getVariable("PROJECT_HOME") : null;
    if (!Utils.isEmpty(projectHome) && !projectHome.contains("${")) {
      String resolved = variables.resolve(projectHome);
      return appendPath(resolved, "test");
    }
    if (Utils.isEmpty(modelFilename)) {
      return "test";
    }
    try {
      String resolved =
          HopVfs.normalize(variables != null ? variables.resolve(modelFilename) : modelFilename);
      FileObject file = HopVfs.getFileObject(resolved);
      FileObject parent = file.getParent();
      if (parent != null && "models".equalsIgnoreCase(parent.getName().getBaseName())) {
        FileObject project = parent.getParent();
        if (project != null) {
          return appendPath(HopVfs.getFilename(project), "test");
        }
      }
      if (parent != null) {
        return appendPath(HopVfs.getFilename(parent), "test");
      }
    } catch (Exception ignored) {
      // Fall through to a relative test folder.
    }
    return "test";
  }

  /**
   * Creates the data set folder if needed, and writes a header-only CSV when the file is missing so
   * Hop unit tests can open it before capture has run.
   */
  public static void ensureDataSetStorage(DataSet dataSet, IRowMeta rowMeta, IVariables variables)
      throws HopException {
    if (dataSet == null) {
      return;
    }
    FileObject file = dataSetFile(dataSet, variables);
    ensureParentFolder(file);
    try {
      if (file.exists()) {
        return;
      }
      IRowMeta layout = rowMeta != null ? rowMeta : dataSet.getSetRowMeta();
      writeHeaderOnlyCsv(file, layout);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BvScd2CalculationUnitTestSupport.Error.CreateDataSetFolder",
              dataSet.getActualDataSetFilename(variables),
              e.getMessage()),
          e);
    }
  }

  static void writeDataSetCsv(
      DataSet dataSet, IRowMeta rowMeta, List<Object[]> rows, IVariables variables)
      throws HopException {
    if (dataSet == null) {
      return;
    }
    ensureParentFolder(dataSetFile(dataSet, variables));
    DataSetCsvUtil.writeDataSetData(variables, dataSet, rowMeta, rows != null ? rows : List.of());
  }

  private static void writeHeaderOnlyCsv(FileObject file, IRowMeta rowMeta) throws Exception {
    String header =
        rowMeta != null && rowMeta.size() > 0 ? String.join(",", rowMeta.getFieldNames()) : "";
    try (OutputStreamWriter writer =
        new OutputStreamWriter(HopVfs.getOutputStream(file, false), StandardCharsets.UTF_8)) {
      writer.write(header);
      writer.write(Const.CR);
    }
  }

  private static FileObject dataSetFile(DataSet dataSet, IVariables variables) throws HopException {
    return HopVfs.getFileObject(dataSet.getActualDataSetFilename(variables), variables);
  }

  private static void ensureParentFolder(FileObject file) throws HopException {
    if (file == null) {
      return;
    }
    try {
      FileObject parent = file.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "BvScd2CalculationUnitTestSupport.Error.CreateDataSetFolder",
              file.getName() != null ? file.getName().getURI() : "",
              e.getMessage()),
          e);
    }
  }

  static String resolveDataSetFolder(IVariables variables) {
    if (variables != null
        && !Utils.isEmpty(variables.getVariable(DataSet.VARIABLE_HOP_DATASETS_FOLDER))) {
      return "${HOP_DATASETS_FOLDER}";
    }
    if (variables != null && !Utils.isEmpty(variables.getVariable("PROJECT_HOME"))) {
      return "${PROJECT_HOME}/datasets";
    }
    return ".";
  }

  private static PipelineUnitTestSetLocation location(
      String transformName, String dataSetName, IRowMeta rowMeta) {
    PipelineUnitTestSetLocation location = new PipelineUnitTestSetLocation();
    location.setTransformName(transformName);
    location.setDataSetName(dataSetName);
    location.setFieldMappings(identityMappings(rowMeta));
    location.setFieldOrder(fieldOrder(rowMeta));
    return location;
  }

  private static void addCollector(
      Pipeline pipeline, String transformName, List<Object[]> rows, IRowMeta[] metaHolder)
      throws HopException {
    var runThread = pipeline.findRunThread(transformName);
    if (runThread == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvScd2CalculationUnitTestSupport.Error.MissingTransform", transformName));
    }
    runThread.addRowListener(
        new RowAdapter() {
          @Override
          public void rowWrittenEvent(IRowMeta rowMeta, Object[] row) {
            if (metaHolder[0] == null && rowMeta != null) {
              metaHolder[0] = rowMeta.clone();
            }
            try {
              rows.add(rowMeta.cloneRow(row));
            } catch (Exception e) {
              rows.add(row);
            }
          }
        });
  }

  private static TransformMeta findSqlExpression(PipelineMeta pipelineMeta) {
    for (TransformMeta transform : pipelineMeta.getTransforms()) {
      if (transform != null && "SqlExpression".equals(transform.getTransformPluginId())) {
        return transform;
      }
    }
    return null;
  }

  private static TransformMeta findSinglePredecessor(PipelineMeta pipelineMeta, TransformMeta to) {
    TransformMeta found = null;
    for (PipelineHopMeta hop : pipelineMeta.getPipelineHops()) {
      if (hop != null && hop.getToTransform() == to) {
        if (found != null) {
          return found;
        }
        found = hop.getFromTransform();
      }
    }
    return found;
  }

  private static void removeDownstream(PipelineMeta pipelineMeta, TransformMeta from) {
    Set<TransformMeta> downstream = new LinkedHashSet<>();
    Deque<TransformMeta> queue = new ArrayDeque<>();
    for (PipelineHopMeta hop : pipelineMeta.getPipelineHops()) {
      if (hop != null && hop.getFromTransform() == from && hop.getToTransform() != null) {
        queue.add(hop.getToTransform());
      }
    }
    while (!queue.isEmpty()) {
      TransformMeta current = queue.removeFirst();
      if (!downstream.add(current)) {
        continue;
      }
      for (PipelineHopMeta hop : pipelineMeta.getPipelineHops()) {
        if (hop != null && hop.getFromTransform() == current && hop.getToTransform() != null) {
          queue.add(hop.getToTransform());
        }
      }
    }
    List<PipelineHopMeta> hops = new ArrayList<>(pipelineMeta.getPipelineHops());
    for (PipelineHopMeta hop : hops) {
      if (hop == null) {
        continue;
      }
      if (hop.getFromTransform() == from
          || downstream.contains(hop.getFromTransform())
          || downstream.contains(hop.getToTransform())) {
        pipelineMeta.removePipelineHop(hop);
      }
    }
    for (TransformMeta transform : downstream) {
      int index = pipelineMeta.indexOfTransform(transform);
      if (index >= 0) {
        pipelineMeta.removeTransform(index);
      }
    }
  }

  private static void removeHop(PipelineMeta pipelineMeta, TransformMeta from, TransformMeta to) {
    for (PipelineHopMeta hop : new ArrayList<>(pipelineMeta.getPipelineHops())) {
      if (hop != null && hop.getFromTransform() == from && hop.getToTransform() == to) {
        pipelineMeta.removePipelineHop(hop);
      }
    }
  }

  private static TransformMeta addReservoirSampling(
      PipelineMeta pipelineMeta, TransformMeta predecessor) {
    ReservoirSamplingMeta meta = new ReservoirSamplingMeta();
    meta.setDefault();
    meta.setSampleSize(Integer.toString(DEFAULT_SAMPLE_SIZE));
    TransformMeta sample = new TransformMeta("ReservoirSampling", TRANSFORM_SAMPLE, meta);
    sample.setLocation(offsetX(predecessor, SPACING_WIDTH), locationY(predecessor));
    pipelineMeta.addTransform(sample);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, sample));
    return sample;
  }

  private static TransformMeta dummyTransform(String name, int x, int y) {
    TransformMeta transform = new TransformMeta("Dummy", name, new DummyMeta());
    transform.setLocation(x, y);
    return transform;
  }

  private static int offsetX(TransformMeta transform, int dx) {
    Point location = transform != null ? transform.getLocation() : null;
    return (location != null ? location.x : LOCATION_START.x) + dx;
  }

  private static int locationY(TransformMeta transform) {
    Point location = transform != null ? transform.getLocation() : null;
    return location != null ? location.y : LOCATION_START.y;
  }

  private static String portablePath(String filename, IVariables variables) {
    if (Utils.isEmpty(filename)) {
      return filename;
    }
    return CatalogModelRegistrySupport.portableModelPath(filename, variables);
  }

  /**
   * Pipeline unit tests already resolve against the project ({@code HOP_UNIT_TESTS_FOLDER} / {@code
   * PROJECT_HOME}). Store {@code test/foo.hpl}, not {@code ${PROJECT_HOME}/test/foo.hpl}.
   */
  static String pipelineFilenameRelativeToProject(String filename) {
    if (Utils.isEmpty(filename)) {
      return filename;
    }
    String normalized = filename.replace('\\', '/').trim();
    String prefix = "${PROJECT_HOME}/";
    if (normalized.startsWith(prefix)) {
      return normalized.substring(prefix.length());
    }
    if ("${PROJECT_HOME}".equals(normalized)) {
      return "";
    }
    return normalized;
  }

  private static String appendPath(String folder, String child) {
    if (Utils.isEmpty(folder)) {
      return child;
    }
    String base = folder.replace('\\', '/');
    if (base.endsWith("/")) {
      return base + child;
    }
    return base + "/" + child;
  }
}
