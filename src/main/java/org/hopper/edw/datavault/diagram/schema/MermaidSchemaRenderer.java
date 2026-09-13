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

/** Renders a {@link SchemaDiagram} as Mermaid {@code erDiagram} or {@code classDiagram}. */
public final class MermaidSchemaRenderer {

  private MermaidSchemaRenderer() {}

  public static String render(SchemaDiagram diagram, SchemaRenderOptions options) {
    SchemaRenderOptions opts = options != null ? options : new SchemaRenderOptions();
    String body =
        opts.getDiagramType() == SchemaRenderOptions.DiagramType.CLASS
            ? renderClassDiagram(diagram, opts)
            : renderErDiagram(diagram, opts);
    if (opts.isMarkdownFence()) {
      return "```mermaid\n" + body + "```\n";
    }
    return body;
  }

  private static String renderErDiagram(SchemaDiagram diagram, SchemaRenderOptions opts) {
    StringBuilder sb = new StringBuilder();
    sb.append("erDiagram\n");
    if (diagram == null) {
      return sb.toString();
    }
    for (SchemaRelation relation : diagram.getRelations()) {
      if (relation == null
          || Utils.isEmpty(relation.getFromName())
          || Utils.isEmpty(relation.getToName())) {
        continue;
      }
      sb.append("    ")
          .append(SchemaIdentifiers.alias(relation.getToName()))
          .append(' ')
          .append(mermaidEnd(relation.getToCardinality()))
          .append("--")
          .append(mermaidMany(relation.getFromCardinality()))
          .append(' ')
          .append(SchemaIdentifiers.alias(relation.getFromName()))
          .append(" : \"")
          .append(Utils.isEmpty(relation.getLabel()) ? "" : escape(relation.getLabel()))
          .append("\"\n");
    }
    for (SchemaEntity entity : diagram.getEntities()) {
      appendErEntity(sb, entity, opts);
    }
    return sb.toString();
  }

  private static void appendErEntity(
      StringBuilder sb, SchemaEntity entity, SchemaRenderOptions opts) {
    if (entity == null || Utils.isEmpty(entity.getName()) || entity.getFields().isEmpty()) {
      return;
    }
    sb.append("    ").append(SchemaIdentifiers.alias(entity.getName())).append(" {\n");
    for (SchemaField field : entity.getFields()) {
      if (field == null || Utils.isEmpty(field.getName())) {
        continue;
      }
      String type =
          opts.isIncludeDataTypes() && !Utils.isEmpty(field.getType())
              ? sanitizeType(field.getType())
              : "string";
      sb.append("        ")
          .append(type)
          .append(' ')
          .append(SchemaIdentifiers.alias(field.getName()));
      if (field.isPrimaryKey()) {
        sb.append(" PK");
      } else if (field.isForeignKey()) {
        sb.append(" FK");
      }
      sb.append('\n');
    }
    sb.append("    }\n");
  }

  private static String renderClassDiagram(SchemaDiagram diagram, SchemaRenderOptions opts) {
    StringBuilder sb = new StringBuilder();
    sb.append("classDiagram\n");
    if (diagram == null) {
      return sb.toString();
    }
    for (SchemaEntity entity : diagram.getEntities()) {
      if (entity == null || Utils.isEmpty(entity.getName())) {
        continue;
      }
      String alias = SchemaIdentifiers.alias(entity.getName());
      sb.append("    class ").append(alias);
      if (!Utils.isEmpty(entity.getStereotype())) {
        sb.append(" {\n        <<").append(entity.getStereotype()).append(">>\n");
        for (SchemaField field : entity.getFields()) {
          if (field == null || Utils.isEmpty(field.getName())) {
            continue;
          }
          sb.append("        +").append(SchemaIdentifiers.alias(field.getName()));
          if (opts.isIncludeDataTypes() && !Utils.isEmpty(field.getType())) {
            sb.append(" : ").append(sanitizeType(field.getType()));
          }
          sb.append('\n');
        }
        sb.append("    }\n");
      } else {
        sb.append('\n');
      }
    }
    for (SchemaRelation relation : diagram.getRelations()) {
      if (relation == null
          || Utils.isEmpty(relation.getFromName())
          || Utils.isEmpty(relation.getToName())) {
        continue;
      }
      sb.append("    ")
          .append(SchemaIdentifiers.alias(relation.getFromName()))
          .append(" --> ")
          .append(SchemaIdentifiers.alias(relation.getToName()));
      if (!Utils.isEmpty(relation.getLabel())) {
        sb.append(" : ").append(escape(relation.getLabel()));
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  private static String mermaidEnd(SchemaCardinality cardinality) {
    if (cardinality == null) {
      return "||";
    }
    return switch (cardinality) {
      case ONE -> "||";
      case ZERO_OR_ONE -> "|o";
      case ONE_OR_MANY, ZERO_OR_MANY -> "||";
    };
  }

  private static String mermaidMany(SchemaCardinality cardinality) {
    if (cardinality == null) {
      return "o{";
    }
    return switch (cardinality) {
      case ONE -> "||";
      case ZERO_OR_ONE -> "o|";
      case ONE_OR_MANY -> "|{";
      case ZERO_OR_MANY -> "o{";
    };
  }

  private static String sanitizeType(String type) {
    return SchemaIdentifiers.alias(type);
  }

  private static String escape(String text) {
    return text.replace("\"", "'").replace("\n", " ");
  }
}
