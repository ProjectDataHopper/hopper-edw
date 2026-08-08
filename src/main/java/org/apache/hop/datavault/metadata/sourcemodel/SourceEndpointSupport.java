/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;

/** Resolve source-model endpoints (table / query / JSON) for relationships and validation. */
public final class SourceEndpointSupport {

  private SourceEndpointSupport() {}

  public static boolean exists(SourceModel model, SourceEndpointKind kind, String name) {
    if (model == null || Utils.isEmpty(name)) {
      return false;
    }
    SourceEndpointKind resolved = kind != null ? kind : SourceEndpointKind.TABLE;
    return switch (resolved) {
      case TABLE -> model.findTable(name) != null;
      case QUERY -> model.findQuery(name) != null;
      case JSON -> model.findJsonSource(name) != null;
    };
  }

  public static String displayName(SourceEndpointKind kind, String name) {
    String n = Utils.isEmpty(name) ? "?" : name;
    SourceEndpointKind resolved = kind != null ? kind : SourceEndpointKind.TABLE;
    return switch (resolved) {
      case TABLE -> n;
      case QUERY -> "query:" + n;
      case JSON -> "json:" + n;
    };
  }

  /** Field / column names available for join mapping on the endpoint. */
  public static List<String> fieldNames(SourceModel model, SourceEndpointKind kind, String name) {
    List<String> names = new ArrayList<>();
    if (model == null || Utils.isEmpty(name)) {
      return names;
    }
    SourceEndpointKind resolved = kind != null ? kind : SourceEndpointKind.TABLE;
    switch (resolved) {
      case TABLE -> {
        SourceTable table = model.findTable(name);
        if (table != null) {
          for (SourceColumn column : table.getColumns()) {
            if (column != null && !Utils.isEmpty(column.getName())) {
              names.add(column.getName());
            }
          }
        }
      }
      case QUERY -> {
        SourceQuery query = model.findQuery(name);
        if (query != null) {
          for (SourceQueryColumn column : query.getColumns()) {
            if (column != null) {
              String alias = column.resolveAlias();
              if (!Utils.isEmpty(alias)) {
                names.add(alias);
              }
            }
          }
        }
      }
      case JSON -> {
        SourceJson json = model.findJsonSource(name);
        if (json != null) {
          for (SourceJsonField field : json.getFields()) {
            if (field != null) {
              String fieldName = field.resolveName();
              if (!Utils.isEmpty(fieldName)) {
                names.add(fieldName);
              }
            }
          }
        }
      }
    }
    return names;
  }

  public static List<String> primaryKeyFieldNames(
      SourceModel model, SourceEndpointKind kind, String name) {
    List<String> names = new ArrayList<>();
    if (model == null || Utils.isEmpty(name)) {
      return names;
    }
    SourceEndpointKind resolved = kind != null ? kind : SourceEndpointKind.TABLE;
    switch (resolved) {
      case TABLE -> {
        SourceTable table = model.findTable(name);
        if (table != null) {
          for (SourceColumn column : table.primaryKeyColumns()) {
            if (column != null && !Utils.isEmpty(column.getName())) {
              names.add(column.getName());
            }
          }
        }
      }
      case QUERY -> {
        SourceQuery query = model.findQuery(name);
        if (query != null) {
          for (SourceQueryColumn column : query.getColumns()) {
            if (column != null && column.isPrimaryKey()) {
              names.add(column.resolveAlias());
            }
          }
        }
      }
      case JSON -> {
        SourceJson json = model.findJsonSource(name);
        if (json != null) {
          for (SourceJsonField field : json.getFields()) {
            if (field != null && field.isPrimaryKey()) {
              names.add(field.resolveName());
            }
          }
        }
      }
    }
    return names;
  }

  public static Point locationOf(SourceModel model, SourceEndpointKind kind, String name) {
    if (model == null || Utils.isEmpty(name)) {
      return null;
    }
    SourceEndpointKind resolved = kind != null ? kind : SourceEndpointKind.TABLE;
    return switch (resolved) {
      case TABLE -> {
        SourceTable table = model.findTable(name);
        yield table != null ? table.getLocation() : null;
      }
      case QUERY -> {
        SourceQuery query = model.findQuery(name);
        yield query != null ? query.getLocation() : null;
      }
      case JSON -> {
        SourceJson json = model.findJsonSource(name);
        yield json != null ? json.getLocation() : null;
      }
    };
  }

  public static SourceEndpointKind kindOf(Object node) {
    if (node instanceof SourceTable) {
      return SourceEndpointKind.TABLE;
    }
    if (node instanceof SourceQuery) {
      return SourceEndpointKind.QUERY;
    }
    if (node instanceof SourceJson) {
      return SourceEndpointKind.JSON;
    }
    return SourceEndpointKind.TABLE;
  }

  public static String nameOf(Object node) {
    if (node instanceof SourceTable table) {
      return table.getName();
    }
    if (node instanceof SourceQuery query) {
      return query.getName();
    }
    if (node instanceof SourceJson json) {
      return json.getName();
    }
    return null;
  }
}
