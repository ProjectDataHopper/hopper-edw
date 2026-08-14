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
package org.apache.hop.datavault.lineageview;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.lineage.LineageBackendMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;

/** Lists lineage backends and picks the single enabled one when a view has no backend yet. */
public final class LineageBackendSelectionSupport {

  private LineageBackendSelectionSupport() {}

  public static List<String> listNames(IHopMetadataProvider metadataProvider) throws HopException {
    IHopMetadataSerializer<LineageBackendMeta> serializer = serializer(metadataProvider);
    if (serializer == null) {
      return List.of();
    }
    List<String> names = serializer.listObjectNames();
    return names != null ? List.copyOf(names) : List.of();
  }

  public static List<String> listEnabledNames(IHopMetadataProvider metadataProvider)
      throws HopException {
    IHopMetadataSerializer<LineageBackendMeta> serializer = serializer(metadataProvider);
    if (serializer == null) {
      return List.of();
    }
    List<String> names = serializer.listObjectNames();
    if (names == null || names.isEmpty()) {
      return List.of();
    }
    List<String> enabled = new ArrayList<>();
    for (String name : names) {
      LineageBackendMeta backend = serializer.load(name);
      if (backend != null && backend.isEnabled()) {
        enabled.add(name);
      }
    }
    return enabled;
  }

  /**
   * Keep an already-chosen name. Otherwise, if exactly one enabled backend exists, use it; else
   * return {@code null} so the wizard / picker asks.
   */
  public static String defaultBackendName(IHopMetadataProvider metadataProvider, String currentName)
      throws HopException {
    if (!Utils.isEmpty(currentName)) {
      return currentName;
    }
    List<String> enabled = listEnabledNames(metadataProvider);
    if (enabled.size() == 1) {
      return enabled.get(0);
    }
    return null;
  }

  private static IHopMetadataSerializer<LineageBackendMeta> serializer(
      IHopMetadataProvider metadataProvider) throws HopException {
    if (metadataProvider == null) {
      return null;
    }
    return metadataProvider.getSerializer(LineageBackendMeta.class);
  }
}
