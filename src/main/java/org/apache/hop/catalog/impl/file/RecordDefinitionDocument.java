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
package org.apache.hop.catalog.impl.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.catalog.model.CatalogCustomProperty;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.PhysicalFileRef;
import org.apache.hop.catalog.model.PhysicalIcebergTableRef;
import org.apache.hop.catalog.model.PhysicalTableRef;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.model.RecordDefinitionValidationAcknowledgement;
import org.apache.hop.catalog.model.RecordOrigin;
import org.apache.hop.catalog.util.RowMetaCatalogSupport;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.catalog.DvSourceFieldSupport;
import org.apache.hop.quality.model.RecordQualityRuleBinding;

/** JSON-serializable document stored by {@link FileDataCatalog} and catalog version snapshots. */
@Getter
@Setter
@NoArgsConstructor
public class RecordDefinitionDocument {

  private String namespace;
  private String name;
  private String type;
  private String description;

  /**
   * Legacy Hop {@code IRowMeta} XML. Read-only for migration into structured field lists. Never
   * written on new catalog saves ({@link JsonInclude.Include#NON_NULL} + left null in {@link
   * #from}).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Deprecated
  private String rowMetaXml;

  private RecordOrigin origin;
  private PhysicalTableRef physicalTable;
  private PhysicalFileRef physicalFile;
  private PhysicalIcebergTableRef physicalIcebergTable;
  private List<String> tags = new ArrayList<>();
  private List<String> glossaryTerms = new ArrayList<>();
  private Map<String, CatalogCustomProperty> customProperties = new HashMap<>();
  private List<RecordDefinitionValidationAcknowledgement> validationAcknowledgements =
      new ArrayList<>();
  private List<RecordQualityRuleBinding> qualityRules = new ArrayList<>();
  private DvSourceRecord dvSource;

  public static RecordDefinitionDocument from(RecordDefinition definition) throws HopException {
    definition.validate();
    // Structured fields are authoritative; rebuild transient IRowMeta only (no rowMetaXml).
    DvSourceFieldSupport.prepareForPersistence(definition);
    RecordDefinitionDocument doc = new RecordDefinitionDocument();
    doc.namespace = definition.getKey().getNamespace();
    doc.name = definition.getKey().getName();
    doc.type =
        definition.getType() != null
            ? definition.getType().name()
            : RecordDefinitionType.UNKNOWN.name();
    doc.description = definition.getDescription();
    // Intentionally omit rowMetaXml — layout lives on dvSource.fields / physicalTable.fields.
    doc.rowMetaXml = null;
    doc.origin = definition.getOrigin();
    doc.physicalTable = definition.getPhysicalTable();
    doc.physicalFile = definition.getPhysicalFile();
    doc.physicalIcebergTable = definition.getPhysicalIcebergTable();
    if (definition.getTags() != null) {
      doc.tags = new ArrayList<>(definition.getTags());
    }
    if (definition.getGlossaryTerms() != null) {
      doc.glossaryTerms = new ArrayList<>(definition.getGlossaryTerms());
    }
    if (definition.getCustomProperties() != null) {
      doc.customProperties = new HashMap<>(definition.getCustomProperties());
    }
    doc.dvSource = definition.getDvSource();
    if (definition.getValidationAcknowledgements() != null) {
      doc.validationAcknowledgements = new ArrayList<>(definition.getValidationAcknowledgements());
    }
    if (definition.getQualityRules() != null) {
      doc.qualityRules = new ArrayList<>(definition.getQualityRules());
    }
    return doc;
  }

  public RecordDefinition toRecordDefinition() throws HopException {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, name));
    definition.setType(parseType(type));
    definition.setDescription(description);
    definition.setOrigin(origin);
    definition.setPhysicalTable(physicalTable);
    definition.setPhysicalFile(physicalFile);
    definition.setPhysicalIcebergTable(physicalIcebergTable);
    definition.setTags(tags != null ? new ArrayList<>(tags) : new ArrayList<>());
    definition.setGlossaryTerms(
        glossaryTerms != null ? new ArrayList<>(glossaryTerms) : new ArrayList<>());
    definition.setCustomProperties(
        customProperties != null ? new HashMap<>(customProperties) : new HashMap<>());
    definition.setDvSource(dvSource);
    definition.setValidationAcknowledgements(
        validationAcknowledgements != null
            ? new ArrayList<>(validationAcknowledgements)
            : new ArrayList<>());
    definition.setQualityRules(
        qualityRules != null ? new ArrayList<>(qualityRules) : new ArrayList<>());

    // Legacy documents: hold parsed rowMetaXml only as a temporary migration aid.
    IRowMeta legacyRowMeta = readLegacyRowMetaLenient();
    if (legacyRowMeta != null && !legacyRowMeta.isEmpty()) {
      definition.setFields(legacyRowMeta);
    } else {
      definition.setFields(new RowMeta());
    }

    try {
      DvSourceFieldSupport.synchronizeLayoutAfterLoad(definition);
    } catch (HopException e) {
      LogChannel.GENERAL.logError(
          "Unable to synchronize catalog field layout for "
              + namespace
              + "/"
              + name
              + "; continuing with loaded fields",
          e);
    }
    return definition;
  }

  private IRowMeta readLegacyRowMetaLenient() {
    if (Utils.isEmpty(rowMetaXml)) {
      return null;
    }
    try {
      return RowMetaCatalogSupport.fromXml(rowMetaXml);
    } catch (HopException e) {
      LogChannel.GENERAL.logError(
          "Unable to deserialize legacy rowMetaXml for catalog record "
              + namespace
              + "/"
              + name
              + "; continuing without legacy field layout",
          e);
      return null;
    }
  }

  private static RecordDefinitionType parseType(String raw) {
    if (raw == null || raw.isBlank()) {
      return RecordDefinitionType.UNKNOWN;
    }
    try {
      return RecordDefinitionType.valueOf(raw);
    } catch (IllegalArgumentException e) {
      return RecordDefinitionType.UNKNOWN;
    }
  }
}
