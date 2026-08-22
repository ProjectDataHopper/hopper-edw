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
package org.hopper.edw.datavault.metadata.file;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Loads Parquet Input transform metadata via the Hop Parquet tech plugin classloader. */
public final class DvParquetPluginSupport {

  private static final String PARQUET_INPUT_PLUGIN_ID = "ParquetFileInput";
  private static final String PARQUET_FIELD_CLASS =
      "org.apache.hop.parquet.transforms.input.ParquetField";

  private DvParquetPluginSupport() {}

  public static ITransformMeta loadParquetInputMeta() throws HopException {
    try {
      ITransformMeta meta =
          PluginRegistry.getInstance()
              .loadClass(TransformPluginType.class, PARQUET_INPUT_PLUGIN_ID, ITransformMeta.class);
      meta.setDefault();
      return meta;
    } catch (Exception e) {
      throw new HopException("Unable to load Parquet File Input transform plugin", e);
    }
  }

  public static TransformMeta newParquetInputTransform(String name, ITransformMeta meta) {
    return new TransformMeta(PARQUET_INPUT_PLUGIN_ID, name, meta);
  }

  public static void configureForMetadataFile(ITransformMeta meta, String filePath)
      throws HopException {
    invoke(meta, "setMetadataFilename", String.class, filePath);
    invoke(meta, "setFilenameField", String.class, "");
    invoke(meta, "setSendingNullsRowWhenEmpty", boolean.class, true);
    setParquetFields(meta, List.of());
  }

  public static void configureForFilenameField(
      ITransformMeta meta, String filenameField, String metadataFilename) throws HopException {
    invoke(meta, "setFilenameField", String.class, filenameField);
    invoke(meta, "setMetadataFilename", String.class, metadataFilename);
    invoke(meta, "setSendingNullsRowWhenEmpty", boolean.class, false);
  }

  public static Object createParquetField(
      ITransformMeta parquetMeta,
      String sourceField,
      String targetField,
      String targetType,
      String targetFormat,
      String targetLength,
      String targetPrecision)
      throws HopException {
    try {
      Class<?> fieldClass =
          Class.forName(PARQUET_FIELD_CLASS, true, parquetMeta.getClass().getClassLoader());
      Constructor<?> constructor =
          fieldClass.getConstructor(
              String.class, String.class, String.class, String.class, String.class, String.class);
      return constructor.newInstance(
          sourceField, targetField, targetType, targetFormat, targetLength, targetPrecision);
    } catch (Exception e) {
      throw new HopException("Unable to create Parquet field mapping", e);
    }
  }

  public static void setParquetFields(ITransformMeta meta, List<?> fields) throws HopException {
    invoke(meta, "setFields", List.class, new ArrayList<>(fields));
  }

  private static void invoke(ITransformMeta meta, String methodName, Class<?> argType, Object arg)
      throws HopException {
    try {
      Method method = meta.getClass().getMethod(methodName, argType);
      method.invoke(meta, arg);
    } catch (Exception e) {
      throw new HopException(
          "Unable to configure Parquet File Input transform (" + methodName + ")", e);
    }
  }
}
