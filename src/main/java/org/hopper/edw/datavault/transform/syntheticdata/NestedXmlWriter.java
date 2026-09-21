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
package org.hopper.edw.datavault.transform.syntheticdata;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/** Writes a nested attribute-style XML document from flat, pre-grouped rows. */
public final class NestedXmlWriter {

  private NestedXmlWriter() {}

  public static void write(
      OutputStream out,
      SyntheticDataMeta meta,
      IVariables variables,
      List<Map<String, Object>> rows)
      throws HopException {
    try {
      XMLStreamWriter xml =
          XMLOutputFactory.newFactory().createXMLStreamWriter(out, encoding(meta));
      xml.writeStartDocument(encoding(meta), "1.0");
      xml.writeCharacters("\n");
      xml.writeStartElement(required(meta.getDocumentRootElement(), "XML root element"));
      writeRootAttributes(xml, meta.getDocumentRootAttributes(), variables);
      writeChildren(xml, childrenOf("", meta.getXmlNodes()), rows, 1);
      xml.writeCharacters("\n");
      xml.writeEndElement();
      xml.writeCharacters("\n");
      xml.writeEndDocument();
      xml.flush();
    } catch (XMLStreamException e) {
      throw new HopException("Unable to write synthetic XML document", e);
    }
  }

  private static void writeChildren(
      XMLStreamWriter xml, List<Node> nodes, List<Map<String, Object>> rows, int depth)
      throws XMLStreamException, HopException {
    for (Node node : nodes) {
      switch (node.spec.getNodeType() == null ? "" : node.spec.getNodeType().trim().toUpperCase()) {
        case "WRAPPER" -> {
          indent(xml, depth);
          xml.writeStartElement(required(node.spec.getElement(), "XML element"));
          writeChildren(xml, node.children, rows, depth + 1);
          indent(xml, depth);
          xml.writeEndElement();
        }
        case "ELEMENT" -> {
          indent(xml, depth);
          xml.writeEmptyElement(required(node.spec.getElement(), "XML element"));
          if (!rows.isEmpty()) {
            writeAttributes(xml, node.spec.getAttributes(), rows.get(0));
          }
        }
        case "ROW" -> {
          for (Map<String, Object> row : rows) {
            indent(xml, depth);
            xml.writeEmptyElement(required(node.spec.getElement(), "XML element"));
            writeAttributes(xml, node.spec.getAttributes(), row);
          }
        }
        case "GROUP" -> writeGroups(xml, node, rows, depth);
        default ->
            throw new HopException(
                "Unknown XML node type '" + node.spec.getNodeType() + "' on '" + node.spec.getId() + "'");
      }
    }
  }

  private static void writeGroups(
      XMLStreamWriter xml, Node node, List<Map<String, Object>> rows, int depth)
      throws XMLStreamException, HopException {
    String field = required(node.spec.getGroupField(), "XML group field");
    List<List<Map<String, Object>>> groups = new ArrayList<>();
    List<Map<String, Object>> current = null;
    String currentKey = null;
    for (Map<String, Object> row : rows) {
      String key = TemplateRenderer.stringify(row.get(field));
      if (current == null || !key.equals(currentKey)) {
        current = new ArrayList<>();
        groups.add(current);
        currentKey = key;
      }
      current.add(row);
    }
    for (List<Map<String, Object>> group : groups) {
      indent(xml, depth);
      xml.writeStartElement(required(node.spec.getElement(), "XML element"));
      if (!group.isEmpty()) {
        writeAttributes(xml, node.spec.getAttributes(), group.get(0));
      }
      writeChildren(xml, node.children, group, depth + 1);
      indent(xml, depth);
      xml.writeEndElement();
    }
  }

  private static void writeRootAttributes(XMLStreamWriter xml, String spec, IVariables variables)
      throws XMLStreamException {
    if (Utils.isEmpty(spec)) {
      return;
    }
    for (String part : spec.split(";")) {
      int eq = part.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      String name = part.substring(0, eq).trim();
      String value = part.substring(eq + 1).trim();
      if (variables != null) {
        value = variables.resolve(value);
      }
      xml.writeAttribute(name, value);
    }
  }

  private static void writeAttributes(XMLStreamWriter xml, String spec, Map<String, Object> row)
      throws XMLStreamException {
    if (Utils.isEmpty(spec) || row == null) {
      return;
    }
    for (String part : spec.split(",")) {
      String trimmed = part.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int colon = trimmed.indexOf(':');
      String attr = colon < 0 ? trimmed : trimmed.substring(0, colon).trim();
      String field = colon < 0 ? trimmed : trimmed.substring(colon + 1).trim();
      xml.writeAttribute(attr, TemplateRenderer.stringify(row.get(field)));
    }
  }

  private static void indent(XMLStreamWriter xml, int depth) throws XMLStreamException {
    xml.writeCharacters("\n");
    xml.writeCharacters("  ".repeat(Math.max(0, depth)));
  }

  private static String encoding(SyntheticDataMeta meta) {
    return Utils.isEmpty(meta.getDocumentEncoding()) ? "UTF-8" : meta.getDocumentEncoding();
  }

  private static String required(String value, String label) throws HopException {
    if (Utils.isEmpty(value)) {
      throw new HopException(label + " is required");
    }
    return value.trim();
  }

  private static List<Node> childrenOf(String parentId, List<SyntheticXmlNode> specs) {
    Map<String, Node> byId = new LinkedHashMap<>();
    List<Node> roots = new ArrayList<>();
    if (specs == null) {
      return roots;
    }
    for (SyntheticXmlNode spec : specs) {
      byId.put(spec.getId(), new Node(spec));
    }
    for (Node node : byId.values()) {
      String parent = node.spec.getParentId() == null ? "" : node.spec.getParentId().trim();
      if (parent.isEmpty()) {
        roots.add(node);
      } else {
        Node owner = byId.get(parent);
        if (owner != null) {
          owner.children.add(node);
        } else {
          roots.add(node);
        }
      }
    }
    if (!parentId.isEmpty()) {
      Node parent = byId.get(parentId);
      return parent == null ? List.of() : parent.children;
    }
    return roots;
  }

  private static final class Node {
    private final SyntheticXmlNode spec;
    private final List<Node> children = new ArrayList<>();

    private Node(SyntheticXmlNode spec) {
      this.spec = spec;
    }
  }
}
