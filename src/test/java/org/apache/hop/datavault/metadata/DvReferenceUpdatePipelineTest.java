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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Unit tests for reference table FULL_REPLACE source SQL generation. */
class DvReferenceUpdatePipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void databaseSourceSqlSelectsNaturalKeysAttributesAndRecordSource() throws Exception {
    DvReferenceTable ref = countryReference();
    DataVaultSource source = countrySource();

    PipelineMeta pipelineMeta = new PipelineMeta();
    DvDatabaseReferenceSourcePipelineBuilder builder =
        new DvDatabaseReferenceSourcePipelineBuilder(
            new Variables(),
            testMetadataProvider(),
            new DataVaultModel(),
            pipelineMeta,
            source,
            source.getDvSourceOrDefault(),
            ref,
            new Point(0, 0));
    builder.build();

    TableInputMeta sourceMeta =
        (TableInputMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> t.getName().startsWith("source"))
                .findFirst()
                .orElseThrow()
                .getTransform();

    String sql = sourceMeta.getSql().replace('\n', ' ');
    assertTrue(sql.startsWith("SELECT "), () -> sql);
    assertTrue(sql.contains("country_cd"), () -> sql);
    assertTrue(sql.contains("code"), () -> sql);
    assertTrue(sql.contains("name"), () -> sql);
    assertTrue(sql.contains("'CRM'"), () -> sql);
    assertTrue(sql.contains("FROM"), () -> sql);
    assertTrue(sql.contains("country_codes"), () -> sql);
    assertFalse(sql.toLowerCase().contains("hash"), () -> sql);
    assertFalse(sql.toLowerCase().contains("hkey"), () -> sql);
  }

  private static DvReferenceTable countryReference() {
    DvReferenceTable ref = new DvReferenceTable("ref_country");
    ref.setTableName("ref_country");
    ref.setLoadMode(DvReferenceLoadMode.FULL_REPLACE);
    ref.setRecordSources(List.of("CRM-country"));

    BusinessKey code = new BusinessKey("code");
    code.setDataType("String");
    code.setLength("3");
    code.setSourceFieldName("country_cd");
    code.setRecordSourceName("CRM-country");
    ref.setNaturalKeys(new ArrayList<>(List.of(code)));

    SatelliteAttribute name = new SatelliteAttribute("name");
    name.setDataType("String");
    name.setLength("100");
    ref.setAttributes(new ArrayList<>(List.of(name)));
    return ref;
  }

  private static DataVaultSource countrySource() {
    DataVaultSource source = new DataVaultSource("CRM-country");
    source.setSourceIndicator("CRM");
    DvDatabaseSource dbSource = new DvDatabaseSource();
    dbSource.setDatabaseName("CRM");
    dbSource.setSchemaName("public");
    dbSource.setTableName("country_codes");
    source.setSource(dbSource);
    List<SourceField> fields = new ArrayList<>();
    SourceField code = new SourceField();
    code.setName("country_cd");
    code.setSourceDataType("String");
    code.setHopType(IValueMeta.TYPE_STRING);
    fields.add(code);
    SourceField name = new SourceField();
    name.setName("name");
    name.setSourceDataType("String");
    name.setHopType(IValueMeta.TYPE_STRING);
    fields.add(name);
    source.getDvSourceOrDefault().setFields(fields);
    return source;
  }

  private static MemoryMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta crm =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return "POSTGRESQL";
          }
        };
    crm.setName("CRM");
    metadataProvider.getSerializer(DatabaseMeta.class).save(crm);
    return metadataProvider;
  }
}
