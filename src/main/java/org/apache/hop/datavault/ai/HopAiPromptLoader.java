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
package org.apache.hop.datavault.ai;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.hop.core.exception.HopException;

/** Loads AI prompt templates from classpath resources. */
public final class HopAiPromptLoader {

  private HopAiPromptLoader() {}

  public static String loadResource(String classpathRoot, String fileName) throws HopException {
    String root = classpathRoot.endsWith("/") ? classpathRoot : classpathRoot + "/";
    String path = root + fileName;
    try (InputStream in = HopAiPromptLoader.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new HopException("AI prompt resource not found: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to load AI prompt resource: " + path, e);
    }
  }
}
