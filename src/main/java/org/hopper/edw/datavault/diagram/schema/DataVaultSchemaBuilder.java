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

import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.IDvTable;

/** Builds a Data Vault {@link SchemaDiagram} (hubs, links, satellites). */
public final class DataVaultSchemaBuilder {

  private DataVaultSchemaBuilder() {}

  public static SchemaDiagram build(DataVaultModel model) {
    SchemaDiagram diagram = new SchemaDiagram();
    if (model == null) {
      return diagram;
    }
    diagram.setTitle(model.getName());
    for (IDvTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      diagram.addEntity(entityFor(table));
    }
    for (IDvTable table : model.getTables()) {
      if (table instanceof DvLink link && link.getHubNames() != null) {
        for (String hubName : link.getHubNames()) {
          if (!Utils.isEmpty(hubName)) {
            addManyToOne(diagram, link.getName(), hubName, "hub");
          }
        }
      }
      if (table instanceof DvSatellite satellite) {
        String parent =
            !Utils.isEmpty(satellite.getHubName())
                ? satellite.getHubName()
                : satellite.getLinkName();
        if (!Utils.isEmpty(parent)) {
          addManyToOne(diagram, satellite.getName(), parent, "describes");
        }
      }
    }
    return diagram;
  }

  private static SchemaEntity entityFor(IDvTable table) {
    String stereotype = SchemaStereotypes.TABLE;
    if (table instanceof DvHub) {
      stereotype = SchemaStereotypes.HUB;
    } else if (table instanceof DvLink) {
      stereotype = SchemaStereotypes.LINK;
    } else if (table instanceof DvSatellite) {
      stereotype = SchemaStereotypes.SATELLITE;
    }
    SchemaEntity entity = new SchemaEntity(table.getName(), stereotype);
    if (table instanceof DvHub hub && hub.getBusinessKeys() != null) {
      for (BusinessKey key : hub.getBusinessKeys()) {
        if (key != null && !Utils.isEmpty(key.getName())) {
          entity.addField(new SchemaField(key.getName()).pk());
        }
      }
    }
    if (table instanceof DvLink link && link.getHubNames() != null) {
      for (String hubName : link.getHubNames()) {
        if (!Utils.isEmpty(hubName)) {
          entity.addField(new SchemaField(hubName).fk());
        }
      }
    }
    if (table instanceof DvSatellite satellite) {
      String parent =
          !Utils.isEmpty(satellite.getHubName()) ? satellite.getHubName() : satellite.getLinkName();
      if (!Utils.isEmpty(parent)) {
        entity.addField(new SchemaField(parent).fk());
      }
    }
    return entity;
  }

  private static void addManyToOne(SchemaDiagram diagram, String from, String to, String label) {
    SchemaRelation relation = new SchemaRelation(from, to, label);
    relation.setFromCardinality(SchemaCardinality.ONE_OR_MANY);
    relation.setToCardinality(SchemaCardinality.ONE);
    diagram.addRelation(relation);
  }
}
