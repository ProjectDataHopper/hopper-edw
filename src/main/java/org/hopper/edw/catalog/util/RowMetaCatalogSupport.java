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
package org.hopper.edw.catalog.util;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.xml.XmlHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Serialize and deserialize {@link IRowMeta} for catalog persistence. */
public final class RowMetaCatalogSupport {

  private static final String VALUE_META_TAG = "value-meta";

  private RowMetaCatalogSupport() {}

  public static String toXml(IRowMeta rowMeta) throws HopException {
    if (rowMeta == null) {
      return null;
    }
    try {
      return rowMeta.getMetaXml();
    } catch (Exception e) {
      throw new HopException("Unable to serialize row metadata to XML", e);
    }
  }

  public static IRowMeta fromXml(String rowMetaXml) throws HopException {
    if (rowMetaXml == null || rowMetaXml.isBlank()) {
      return null;
    }
    try {
      Document document = XmlHandler.loadXmlString(rowMetaXml.trim());
      Node rowMetaNode = document.getDocumentElement();
      if (rowMetaNode == null || !RowMeta.XML_META_TAG.equals(rowMetaNode.getNodeName())) {
        throw new HopException("Missing row-meta element in stored record definition");
      }
      normalizeValueMetaNodes(document, rowMetaNode);
      return new RowMeta(rowMetaNode);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to deserialize row metadata from XML", e);
    }
  }

  /**
   * Hand-authored catalog JSON often omits length/precision and other fields that Hop's row-meta
   * parser requires. Fill safe defaults before constructing {@link RowMeta}.
   */
  static void normalizeValueMetaNodes(Document document, Node rowMetaNode) {
    if (document == null || rowMetaNode == null) {
      return;
    }
    NodeList children = rowMetaNode.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child == null || child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      if (!VALUE_META_TAG.equals(child.getNodeName())) {
        continue;
      }
      normalizeValueMeta(document, (Element) child);
    }
  }

  private static void normalizeValueMeta(Document document, Element valueMeta) {
    ensureChildText(document, valueMeta, "storagetype", "normal");
    String type = childText(valueMeta, "type");
    if (Utils.isEmpty(type)) {
      return;
    }

    switch (type) {
      case "String" -> {
        ensureChildText(document, valueMeta, "length", childText(valueMeta, "length", "-1"));
        ensureChildText(document, valueMeta, "precision", "-1");
      }
      case "Integer", "Number", "BigNumber" -> {
        ensureChildText(document, valueMeta, "length", childText(valueMeta, "length", "-1"));
        ensureChildText(document, valueMeta, "precision", childText(valueMeta, "precision", "0"));
      }
      case "Date", "Timestamp" -> {
        ensureChildText(document, valueMeta, "length", "-1");
        ensureChildText(document, valueMeta, "precision", "-1");
      }
      case "Boolean" -> {
        ensureChildText(document, valueMeta, "length", "-1");
        ensureChildText(document, valueMeta, "precision", "0");
      }
      default -> {
        if (!hasChild(valueMeta, "length")) {
          ensureChildText(document, valueMeta, "length", "-1");
        }
        if (!hasChild(valueMeta, "precision")) {
          ensureChildText(document, valueMeta, "precision", "-1");
        }
      }
    }

    ensureChildText(document, valueMeta, "trim_type", "none");
    ensureChildText(document, valueMeta, "case_insensitive", "N");
    ensureChildText(document, valueMeta, "collator_disabled", "Y");
    ensureChildText(document, valueMeta, "collator_strength", "0");
    ensureChildText(document, valueMeta, "sort_descending", "N");
    ensureChildText(document, valueMeta, "output_padding", "N");
    ensureEmptyChild(document, valueMeta, "origin");
    ensureEmptyChild(document, valueMeta, "comments");
  }

  private static void ensureChildText(
      Document document, Element parent, String tagName, String value) {
    if (hasChild(parent, tagName)) {
      return;
    }
    Element child = document.createElement(tagName);
    child.appendChild(document.createTextNode(value));
    parent.appendChild(child);
  }

  private static void ensureEmptyChild(Document document, Element parent, String tagName) {
    if (hasChild(parent, tagName)) {
      return;
    }
    parent.appendChild(document.createElement(tagName));
  }

  private static boolean hasChild(Element parent, String tagName) {
    return findDirectChild(parent, tagName) != null;
  }

  private static Node findDirectChild(Element parent, String tagName) {
    NodeList nodes = parent.getElementsByTagName(tagName);
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (node != null && node.getParentNode() == parent) {
        return node;
      }
    }
    return null;
  }

  private static String childText(Element parent, String tagName) {
    return childText(parent, tagName, null);
  }

  private static String childText(Element parent, String tagName, String defaultValue) {
    Node node = findDirectChild(parent, tagName);
    if (node == null) {
      return defaultValue;
    }
    String text = XmlHandler.getNodeValue(node);
    return Utils.isEmpty(text) ? defaultValue : text;
  }
}
