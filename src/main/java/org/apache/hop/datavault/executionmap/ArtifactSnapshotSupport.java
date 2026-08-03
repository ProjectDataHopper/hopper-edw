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
package org.apache.hop.datavault.executionmap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.hop.core.exception.HopException;

/** gzip + Base64 helpers for execution map artifact snapshots. */
public final class ArtifactSnapshotSupport {

  private ArtifactSnapshotSupport() {}

  public static String encodeXml(String xml) throws HopException {
    if (xml == null) {
      return null;
    }
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
        gzip.write(xml.getBytes(StandardCharsets.UTF_8));
      }
      return Base64.getEncoder().encodeToString(bytes.toByteArray());
    } catch (Exception e) {
      throw new HopException("Unable to gzip and encode artifact XML snapshot", e);
    }
  }

  public static String decodeXml(String xmlGzipBase64) throws HopException {
    if (xmlGzipBase64 == null) {
      return null;
    }
    try {
      byte[] compressed = Base64.getDecoder().decode(xmlGzipBase64);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
        gzip.transferTo(out);
      }
      return out.toString(StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new HopException("Unable to decode artifact XML snapshot", e);
    }
  }
}
