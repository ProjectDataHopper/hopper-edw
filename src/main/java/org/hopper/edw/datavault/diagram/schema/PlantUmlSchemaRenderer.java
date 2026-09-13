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

/** Renders a {@link SchemaDiagram} as PlantUML class or entity syntax. */
public final class PlantUmlSchemaRenderer {

  private PlantUmlSchemaRenderer() {}

  public static String render(SchemaDiagram diagram, SchemaRenderOptions options) {
    SchemaRenderOptions opts = options != null ? options : new SchemaRenderOptions();
    String keyword =
        opts.getSyntaxStyle() == SchemaRenderOptions.SyntaxStyle.ENTITY ? "entity" : "class";
    StringBuilder sb = new StringBuilder();
    sb.append("@startuml\n");
    sb.append("!theme plain\n");
    sb.append("skinparam linetype ortho\n");
    appendSkinparam(sb, "class");
    if (opts.getSyntaxStyle() == SchemaRenderOptions.SyntaxStyle.ENTITY) {
      appendSkinparam(sb, "entity");
    }
    sb.append('\n');
    if (diagram == null) {
      sb.append("@enduml\n");
      return sb.toString();
    }
    if (!Utils.isEmpty(diagram.getTitle())) {
      sb.append("title ").append(SchemaIdentifiers.display(diagram.getTitle())).append('\n');
    }
    for (SchemaEntity entity : diagram.getEntities()) {
      appendEntity(sb, entity, keyword, opts);
    }
    for (SchemaRelation relation : diagram.getRelations()) {
      appendRelation(sb, relation);
    }
    if (opts.isIncludeGrain()) {
      for (SchemaEntity entity : diagram.getEntities()) {
        if (Utils.isEmpty(entity.getGrain())) {
          continue;
        }
        sb.append("note right of ")
            .append(SchemaIdentifiers.alias(entity.getName()))
            .append("\n  Grain: ")
            .append(SchemaIdentifiers.display(entity.getGrain()))
            .append("\nend note\n");
      }
    }
    sb.append("@enduml\n");
    return sb.toString();
  }

  private static void appendSkinparam(StringBuilder sb, String target) {
    sb.append("skinparam ").append(target).append(" {\n");
    appendStereotypeColors(sb, SchemaStereotypes.FACT);
    appendStereotypeColors(sb, SchemaStereotypes.DIMENSION);
    appendStereotypeColors(sb, SchemaStereotypes.BRIDGE);
    appendStereotypeColors(sb, SchemaStereotypes.HUB);
    appendStereotypeColors(sb, SchemaStereotypes.LINK);
    appendStereotypeColors(sb, SchemaStereotypes.SATELLITE);
    sb.append("}\n");
  }

  private static void appendStereotypeColors(StringBuilder sb, String stereotype) {
    sb.append("  BackgroundColor<<")
        .append(stereotype)
        .append(">> ")
        .append(SchemaStereotypes.headerColor(stereotype))
        .append('\n');
    sb.append("  BorderColor<<")
        .append(stereotype)
        .append(">> ")
        .append(SchemaStereotypes.borderColor(stereotype))
        .append('\n');
  }

  private static void appendEntity(
      StringBuilder sb, SchemaEntity entity, String keyword, SchemaRenderOptions opts) {
    if (entity == null || Utils.isEmpty(entity.getName())) {
      return;
    }
    String alias = SchemaIdentifiers.alias(entity.getName());
    sb.append(keyword)
        .append(" \"")
        .append(SchemaIdentifiers.display(entity.getName()))
        .append("\" as ")
        .append(alias);
    if (!Utils.isEmpty(entity.getStereotype())) {
      sb.append(" <<").append(entity.getStereotype()).append(">>");
    }
    sb.append(" {\n");
    boolean wrotePk = false;
    for (SchemaField field : entity.getFields()) {
      if (field.isPrimaryKey()) {
        appendField(sb, field, opts);
        wrotePk = true;
      }
    }
    boolean wroteOther = false;
    for (SchemaField field : entity.getFields()) {
      if (field.isPrimaryKey()) {
        continue;
      }
      if (wrotePk && !wroteOther) {
        sb.append("  --\n");
        wroteOther = true;
      }
      appendField(sb, field, opts);
    }
    sb.append("}\n\n");
  }

  private static void appendField(StringBuilder sb, SchemaField field, SchemaRenderOptions opts) {
    if (field == null || Utils.isEmpty(field.getName())) {
      return;
    }
    sb.append("  ");
    if (field.isPrimaryKey()) {
      sb.append("* ");
    }
    sb.append(SchemaIdentifiers.display(field.getName()));
    if (opts.isIncludeDataTypes() && !Utils.isEmpty(field.getType())) {
      sb.append(" : ").append(SchemaIdentifiers.display(field.getType()));
    }
    if (field.isPrimaryKey()) {
      sb.append(" <<PK>>");
    } else if (field.isForeignKey()) {
      sb.append(" <<FK>>");
    }
    sb.append('\n');
  }

  private static void appendRelation(StringBuilder sb, SchemaRelation relation) {
    if (relation == null
        || Utils.isEmpty(relation.getFromName())
        || Utils.isEmpty(relation.getToName())) {
      return;
    }
    sb.append(SchemaIdentifiers.alias(relation.getFromName()))
        .append(' ')
        .append(plantUmlEnd(relation.getFromCardinality()))
        .append("--")
        .append(plantUmlEnd(relation.getToCardinality()))
        .append(' ')
        .append(SchemaIdentifiers.alias(relation.getToName()));
    if (!Utils.isEmpty(relation.getLabel())) {
      sb.append(" : \"").append(SchemaIdentifiers.display(relation.getLabel())).append('"');
    }
    sb.append('\n');
  }

  private static String plantUmlEnd(SchemaCardinality cardinality) {
    if (cardinality == null) {
      return "}|";
    }
    return switch (cardinality) {
      case ONE -> "||";
      case ZERO_OR_ONE -> "o|";
      case ONE_OR_MANY -> "}|";
      case ZERO_OR_MANY -> "}o";
    };
  }
}
