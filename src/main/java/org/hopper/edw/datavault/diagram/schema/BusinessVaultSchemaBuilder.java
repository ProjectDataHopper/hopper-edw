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
package org.hopper.edw.datavault.diagram.schema;

import java.util.List;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvDerivativeRef;
import org.hopper.edw.datavault.metadata.businessvault.BvSourceQueryRef;
import org.hopper.edw.datavault.metadata.businessvault.BvTableBase;
import org.hopper.edw.datavault.metadata.businessvault.BvTableType;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;

/** Builds a Business Vault {@link SchemaDiagram} (SCD2, PIT, business tables, source queries). */
public final class BusinessVaultSchemaBuilder {

  private BusinessVaultSchemaBuilder() {}

  public static SchemaDiagram build(BusinessVaultModel model) {
    SchemaDiagram diagram = new SchemaDiagram();
    if (model == null) {
      return diagram;
    }
    diagram.setTitle(model.getName());
    for (IBvTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      diagram.addEntity(new SchemaEntity(table.getName(), stereotypeFor(table.getTableType())));
    }
    for (IBvTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      List<BvDerivativeRef> derivatives = table.getDerivatives();
      if (derivatives != null) {
        for (BvDerivativeRef derivative : derivatives) {
          if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
            continue;
          }
          diagram.addEntity(
              new SchemaEntity(derivative.getDvTableName(), SchemaStereotypes.DATA_VAULT));
          SchemaRelation relation =
              new SchemaRelation(table.getName(), derivative.getDvTableName(), "derives");
          relation.setFromCardinality(SchemaCardinality.ONE);
          relation.setToCardinality(SchemaCardinality.ONE);
          diagram.addRelation(relation);
        }
      }
      if (table instanceof BvTableBase base) {
        for (BvSourceQueryRef ref : base.getSourceQueryRefs()) {
          if (ref == null || Utils.isEmpty(ref.getSourceQueryName())) {
            continue;
          }
          SchemaRelation relation =
              new SchemaRelation(table.getName(), ref.getSourceQueryName(), "source");
          relation.setFromCardinality(SchemaCardinality.ONE_OR_MANY);
          relation.setToCardinality(SchemaCardinality.ONE);
          diagram.addRelation(relation);
        }
      }
    }
    return diagram;
  }

  private static String stereotypeFor(BvTableType type) {
    if (type == null) {
      return SchemaStereotypes.TABLE;
    }
    return switch (type) {
      case SCD2 -> SchemaStereotypes.SCD2;
      case PIT -> SchemaStereotypes.PIT;
      case BUSINESS_TABLE -> SchemaStereotypes.BUSINESS_TABLE;
      case SOURCE_QUERY -> SchemaStereotypes.SOURCE_QUERY;
    };
  }
}
