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
package org.hopper.edw.datavault.metadata.sourcemodel.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.maskfields.MaskField;
import org.apache.hop.pipeline.transforms.maskfields.MaskFieldsMeta;
import org.apache.hop.pipeline.transforms.maskfields.MaskingPattern;
import org.apache.hop.pipeline.transforms.maskfields.MaskingStorage;
import org.apache.hop.pipeline.transforms.maskfields.MaskingToken;
import org.apache.hop.pipeline.transforms.maskfields.MaskingValueSource;
import org.apache.hop.pipeline.transforms.rowgenerator.GeneratorField;
import org.apache.hop.pipeline.transforms.rowgenerator.RowGeneratorMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.masking.DvMaskingSource;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMasking;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMaskingField;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMaskingParentKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.virtualization.calcite.SourceModelMaskingTable;
import org.hopper.edw.datavault.virtualization.calcite.SourceModelSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SourceMaskingPipelineGeneratorTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    PluginRegistry registry = PluginRegistry.getInstance();
    registry.registerPluginClass(
        MaskFieldsMeta.class.getName(), TransformPluginType.class, Transform.class);
    registry.registerPluginClass(
        RowGeneratorMeta.class.getName(), TransformPluginType.class, Transform.class);
  }

  @Test
  void generatesTableInputMaskFieldsAndSelect() throws Exception {
    SourceModel model = sampleModel();
    IHopMetadataProvider metadataProvider = memoryProvider();
    PipelineMeta pipeline =
        SourceMaskingPipelineGenerator.generate(
            model, model.findMaskingSource("customer_masked"), new Variables(), metadataProvider);

    assertTrue(
        pipeline.getTransforms().stream().anyMatch(t -> t.getTransform() instanceof TableInputMeta));
    MaskFieldsMeta mask =
        (MaskFieldsMeta)
            find(pipeline, MaskFieldsMeta.class).getTransform();
    assertEquals(1, mask.getFields().size());
    MaskField masked = mask.getFields().get(0);
    assertEquals("first_name", masked.getFieldName());
    assertEquals("First name", masked.getPatternName());
    assertTrue(
        pipeline.getTransforms().stream().anyMatch(t -> t.getTransform() instanceof SelectValuesMeta));
    assertEquals(metadataProvider, pipeline.getMetadataProvider());
  }

  @Test
  void omitsMaskFieldsWhenNoPatternIsSet() throws Exception {
    SourceModel model = sampleModel();
    model.findMaskingSource("customer_masked").getFields().get(1).setPatternName("");
    PipelineMeta pipeline =
        SourceMaskingPipelineGenerator.generate(
            model, model.findMaskingSource("customer_masked"), new Variables(), memoryProvider());
    assertTrue(
        pipeline.getTransforms().stream().noneMatch(t -> t.getTransform() instanceof MaskFieldsMeta));
  }

  @Test
  void rejectsAMaskingCycle() {
    SourceModel model = sampleModel();
    SourceMasking other = new SourceMasking("other");
    other.setParentSourceKind(SourceMaskingParentKind.MASKING);
    other.setParentSourceName("customer_masked");
    other.getFields().add(field("customer_id", IValueMeta.TYPE_INTEGER, ""));
    model.getMaskingSources().add(other);
    model.findMaskingSource("customer_masked").setParentSourceKind(SourceMaskingParentKind.MASKING);
    model.findMaskingSource("customer_masked").setParentSourceName("other");

    HopException ex =
        assertThrows(
            HopException.class,
            () ->
                SourceMaskingPipelineGenerator.generate(
                    model, other, new Variables(), memoryProvider()));
    assertTrue(ex.getMessage().toLowerCase().contains("cyclic"));
  }

  @Test
  void schemaRegistersTheCardAndFactoryBuildsTheSource() throws HopException {
    SourceModel model = sampleModel();
    SourceModelSchema schema = new SourceModelSchema(model);
    assertTrue(schema.findTable("customer_masked") instanceof SourceModelMaskingTable);
    Object source =
        new org.hopper.edw.datavault.metadata.IDvSource.DvSourceFactory()
            .createObject(DvSourceType.MASKING.name(), null);
    assertTrue(source instanceof DvMaskingSource);
  }

  @Test
  void memoryStorageReusesATokenAndNoneDoesNot() throws Exception {
    List<Object> memory = runMasking(MaskingStorage.MEMORY);
    assertEquals("first-name-1", memory.get(0));
    assertEquals("first-name-1", memory.get(1));

    List<Object> fresh = runMasking(MaskingStorage.NONE);
    assertEquals("first-name-1", fresh.get(0));
    assertEquals("first-name-2", fresh.get(1));
  }

  private static List<Object> runMasking(MaskingStorage storage) throws Exception {
    MaskingPattern pattern = new MaskingPattern();
    pattern.setName("First name");
    pattern.setValueSource(MaskingValueSource.SYNTHETIC);
    pattern.setToken(MaskingToken.SEQUENCE);
    pattern.setStorage(storage);
    pattern.setPrefix("first-name-");
    pattern.setSequenceStart("1");
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(MaskingPattern.class).save(pattern);

    RowGeneratorMeta generator = new RowGeneratorMeta();
    generator.setRowLimit("2");
    GeneratorField field = new GeneratorField();
    field.setName("first_name");
    field.setType("String");
    field.setValue("Matt");
    generator.getFields().add(field);

    MaskFieldsMeta mask = new MaskFieldsMeta();
    mask.getFields().add(new MaskField("first_name", "First name"));

    PipelineMeta pipeline = new PipelineMeta();
    pipeline.setName("mask-runtime");
    pipeline.setMetadataProvider(provider);
    TransformMeta gen = new TransformMeta("RowGenerator", "rows", generator);
    gen.setLocation(50, 50);
    TransformMeta masked = new TransformMeta("MaskFields", "mask", mask);
    masked.setLocation(250, 50);
    pipeline.addTransform(gen);
    pipeline.addTransform(masked);
    pipeline.addPipelineHop(new org.apache.hop.pipeline.PipelineHopMeta(gen, masked));
    return collect(pipeline, "mask");
  }

  private static List<Object> collect(PipelineMeta pipeline, String transformName) throws Exception {
    List<Object> values = new java.util.ArrayList<>();
    org.apache.hop.pipeline.Pipeline engine =
        new org.apache.hop.pipeline.engines.local.LocalPipelineEngine(pipeline);
    engine.setMetadataProvider(pipeline.getMetadataProvider());
    engine.prepareExecution();
    org.apache.hop.pipeline.transform.ITransform thread = engine.findRunThread(transformName);
    thread.addRowListener(
        new org.apache.hop.pipeline.transform.RowAdapter() {
          @Override
          public void rowWrittenEvent(org.apache.hop.core.row.IRowMeta rowMeta, Object[] row) {
            values.add(row[0]);
          }
        });
    engine.startThreads();
    engine.waitUntilFinished();
    if (engine.getErrors() > 0) {
      throw new HopException("Mask fields pipeline failed");
    }
    return values;
  }

  private static TransformMeta find(PipelineMeta pipeline, Class<?> type) {
    return pipeline.getTransforms().stream()
        .filter(t -> type.isInstance(t.getTransform()))
        .findFirst()
        .orElseThrow();
  }

  private static IHopMetadataProvider memoryProvider() throws HopException {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DatabaseMeta database = new DatabaseMeta();
    database.setName("CRM");
    database.setDatabaseType("POSTGRESQL");
    provider.getSerializer(DatabaseMeta.class).save(database);
    return provider;
  }

  private static SourceModel sampleModel() {
    SourceModel model = new SourceModel();
    model.getConfigurationOrDefault().setDefaultDatabase("CRM");
    SourceTable table = new SourceTable("customer");
    table.setPhysicalType(DvSourceType.DATABASE);
    table.setDatabaseName("CRM");
    table.setTableName("customer");
    SourceColumn id = new SourceColumn("customer_id");
    id.setHopType(IValueMeta.TYPE_INTEGER);
    id.setPrimaryKeyPosition(1);
    SourceColumn name = new SourceColumn("first_name");
    name.setHopType(IValueMeta.TYPE_STRING);
    table.getColumns().add(id);
    table.getColumns().add(name);
    model.getTables().add(table);

    SourceMasking masking = new SourceMasking("customer_masked");
    masking.setParentSourceName("customer");
    masking.getFields().add(field("customer_id", IValueMeta.TYPE_INTEGER, ""));
    masking.getFields().get(0).setPrimaryKeyPosition(1);
    masking.getFields().add(field("first_name", IValueMeta.TYPE_STRING, "First name"));
    model.getMaskingSources().add(masking);
    return model;
  }

  private static SourceMaskingField field(String name, int type, String pattern) {
    SourceMaskingField field = new SourceMaskingField(name);
    field.setHopType(type);
    field.setPatternName(pattern);
    return field;
  }
}
