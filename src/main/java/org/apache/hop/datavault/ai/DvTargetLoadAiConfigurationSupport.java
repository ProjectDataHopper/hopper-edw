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

import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;

/** Shared AI allow-list and applier logic for target-load configuration properties. */
public final class DvTargetLoadAiConfigurationSupport {

  public static final Set<String> COMMON_TARGET_LOAD_PROPERTIES =
      Set.of(
          "targetTableParallelCopies",
          "targetTableBatchSize",
          "targetLoadMode",
          "bulkLoadStagingFolder",
          "bulkLoadDelimiter",
          "bulkLoadEnclosure",
          "bulkLoadEncoding",
          "bulkLoadLocalFileRequired",
          "targetDatabase",
          "generatedPipelineFolder",
          "generatedWorkflowNamePrefix");

  private DvTargetLoadAiConfigurationSupport() {}

  public static boolean isCommonTargetLoadProperty(String propertyName) {
    return propertyName != null && COMMON_TARGET_LOAD_PROPERTIES.contains(propertyName.trim());
  }

  public static void appendTargetLoadSummaryJson(
      StringBuilder json, DataVaultConfiguration config) {
    json.append(",\"targetTableParallelCopies\":")
        .append(DvAiContextBuilder.jsonString(config.getTargetTableParallelCopies()));
    json.append(",\"targetTableBatchSize\":")
        .append(DvAiContextBuilder.jsonString(config.getTargetTableBatchSize()));
    json.append(",\"targetLoadMode\":")
        .append(DvAiContextBuilder.jsonString(config.resolveTargetLoadMode().getCode()));
    json.append(",\"bulkLoadStagingFolder\":")
        .append(DvAiContextBuilder.jsonString(config.getBulkLoadStagingFolder()));
    json.append(",\"bulkLoadDelimiter\":")
        .append(DvAiContextBuilder.jsonString(config.getBulkLoadDelimiter()));
    json.append(",\"bulkLoadEnclosure\":")
        .append(DvAiContextBuilder.jsonString(config.getBulkLoadEnclosure()));
    json.append(",\"bulkLoadEncoding\":")
        .append(DvAiContextBuilder.jsonString(config.getBulkLoadEncoding()));
    json.append(",\"bulkLoadLocalFileRequired\":").append(config.isBulkLoadLocalFileRequired());
    json.append(",\"generatedPipelineFolder\":")
        .append(DvAiContextBuilder.jsonString(config.getGeneratedPipelineFolder()));
    json.append(",\"generatedWorkflowNamePrefix\":")
        .append(DvAiContextBuilder.jsonString(config.getGeneratedWorkflowNamePrefix()));
  }

  public static void appendTargetLoadSummaryJson(
      StringBuilder json, BusinessVaultConfiguration config) {
    json.append(",\"targetTableParallelCopies\":")
        .append(DvAiContextBuilder.jsonString(config.getTargetTableParallelCopies()));
    json.append(",\"targetTableBatchSize\":")
        .append(DvAiContextBuilder.jsonString(config.getTargetTableBatchSize()));
    json.append(",\"targetLoadMode\":")
        .append(DvAiContextBuilder.jsonString(config.resolveTargetLoadMode().getCode()));
    json.append(",\"bulkLoadStagingFolder\":")
        .append(DvAiContextBuilder.jsonString(config.getBulkLoadStagingFolder()));
    json.append(",\"bulkLoadDelimiter\":")
        .append(DvAiContextBuilder.jsonString(config.getBulkLoadDelimiter()));
    json.append(",\"bulkLoadEnclosure\":")
        .append(DvAiContextBuilder.jsonString(config.getBulkLoadEnclosure()));
    json.append(",\"bulkLoadEncoding\":")
        .append(DvAiContextBuilder.jsonString(config.getBulkLoadEncoding()));
    json.append(",\"bulkLoadLocalFileRequired\":").append(config.isBulkLoadLocalFileRequired());
    json.append(",\"generatedPipelineFolder\":")
        .append(DvAiContextBuilder.jsonString(config.getGeneratedPipelineFolder()));
    json.append(",\"generatedWorkflowNamePrefix\":")
        .append(DvAiContextBuilder.jsonString(config.getGeneratedWorkflowNamePrefix()));
    json.append(",\"scd2PipelineNamePrefix\":")
        .append(DvAiContextBuilder.jsonString(config.getScd2PipelineNamePrefix()));
    json.append(",\"pitPipelineNamePrefix\":")
        .append(DvAiContextBuilder.jsonString(config.getPitPipelineNamePrefix()));
    json.append(",\"businessTablePipelineNamePrefix\":")
        .append(DvAiContextBuilder.jsonString(config.getBusinessTablePipelineNamePrefix()));
  }

  public static void appendTargetLoadSummaryJson(
      StringBuilder json, DimensionalConfiguration config) {
    json.append(",\"targetTableParallelCopies\":")
        .append(DvAiContextBuilder.jsonString(config.getTargetTableParallelCopies()));
    json.append(",\"targetTableBatchSize\":")
        .append(DvAiContextBuilder.jsonString(config.getTargetTableBatchSize()));
    json.append(",\"targetLoadMode\":")
        .append(DvAiContextBuilder.jsonString(config.resolveTargetLoadMode().getCode()));
    json.append(",\"bulkLoadStagingFolder\":")
        .append(DvAiContextBuilder.jsonString(config.getBulkLoadStagingFolder()));
    json.append(",\"bulkLoadDelimiter\":")
        .append(DvAiContextBuilder.jsonString(config.getBulkLoadDelimiter()));
    json.append(",\"bulkLoadEnclosure\":")
        .append(DvAiContextBuilder.jsonString(config.getBulkLoadEnclosure()));
    json.append(",\"bulkLoadEncoding\":")
        .append(DvAiContextBuilder.jsonString(config.getBulkLoadEncoding()));
    json.append(",\"bulkLoadLocalFileRequired\":").append(config.isBulkLoadLocalFileRequired());
    json.append(",\"generatedPipelineFolder\":")
        .append(DvAiContextBuilder.jsonString(config.getGeneratedPipelineFolder()));
    json.append(",\"generatedWorkflowNamePrefix\":")
        .append(DvAiContextBuilder.jsonString(config.getGeneratedWorkflowNamePrefix()));
    json.append(",\"dimensionPipelineNamePrefix\":")
        .append(DvAiContextBuilder.jsonString(config.getDimensionPipelineNamePrefix()));
    json.append(",\"junkDimensionPipelineNamePrefix\":")
        .append(DvAiContextBuilder.jsonString(config.getJunkDimensionPipelineNamePrefix()));
    json.append(",\"bridgePipelineNamePrefix\":")
        .append(DvAiContextBuilder.jsonString(config.getBridgePipelineNamePrefix()));
    json.append(",\"factPipelineNamePrefix\":")
        .append(DvAiContextBuilder.jsonString(config.getFactPipelineNamePrefix()));
  }

  public static void applyToDataVault(
      DataVaultConfiguration config, String propertyName, String value) throws HopException {
    switch (propertyName.trim()) {
      case "sortRowsSize" -> config.setSortRowsSize(value);
      case "targetTableParallelCopies" -> config.setTargetTableParallelCopies(value);
      case "targetTableBatchSize" -> config.setTargetTableBatchSize(value);
      case "targetLoadMode" -> config.setTargetLoadMode(value);
      case "bulkLoadStagingFolder" -> config.setBulkLoadStagingFolder(value);
      case "bulkLoadDelimiter" -> config.setBulkLoadDelimiter(value);
      case "bulkLoadEnclosure" -> config.setBulkLoadEnclosure(value);
      case "bulkLoadEncoding" -> config.setBulkLoadEncoding(value);
      case "bulkLoadLocalFileRequired" -> config.setBulkLoadLocalFileRequired(parseBoolean(value));

      case "targetDatabase" -> config.setTargetDatabase(value);
      case "dataCatalogConnection" -> config.setDataCatalogConnection(value);
      case "generatedPipelineFolder" -> config.setGeneratedPipelineFolder(value);
      case "generatedWorkflowNamePrefix" -> config.setGeneratedWorkflowNamePrefix(value);
      default -> throw new HopException("Configuration property not allowed: " + propertyName);
    }
  }

  public static void applyToBusinessVault(
      BusinessVaultConfiguration config, String propertyName, String value) throws HopException {
    if (isCommonTargetLoadProperty(propertyName)) {
      applyCommonToBusinessVault(config, propertyName, value);
      return;
    }
    switch (propertyName.trim()) {
      case "functionalTimestampField" -> config.setFunctionalTimestampField(value);
      case "loadDateFieldFallback" -> config.setLoadDateFieldFallback(value);
      case "validFromField" -> config.setValidFromField(value);
      case "validToField" -> config.setValidToField(value);
      case "scd2PipelineNamePrefix" -> config.setScd2PipelineNamePrefix(value);
      case "pitPipelineNamePrefix" -> config.setPitPipelineNamePrefix(value);
      case "businessTablePipelineNamePrefix" -> config.setBusinessTablePipelineNamePrefix(value);
      default -> throw new HopException("Configuration property not allowed: " + propertyName);
    }
  }

  public static void applyToDimensional(
      DimensionalConfiguration config, String propertyName, String value) throws HopException {
    if (isCommonTargetLoadProperty(propertyName)) {
      applyCommonToDimensional(config, propertyName, value);
      return;
    }
    switch (propertyName.trim()) {
      case "sourceDatabase" -> config.setSourceDatabase(value);
      case "dimKeyField" -> config.setDimKeyField(value);
      case "versionField" -> config.setVersionField(value);
      case "dateFromField" -> config.setDateFromField(value);
      case "dateToField" -> config.setDateToField(value);
      case "loadDateField" -> config.setLoadDateField(value);
      case "currentFlagField" -> config.setCurrentFlagField(value);
      case "dimensionPipelineNamePrefix" -> config.setDimensionPipelineNamePrefix(value);
      case "junkDimensionPipelineNamePrefix" -> config.setJunkDimensionPipelineNamePrefix(value);
      case "bridgePipelineNamePrefix" -> config.setBridgePipelineNamePrefix(value);
      case "factPipelineNamePrefix" -> config.setFactPipelineNamePrefix(value);
      default -> throw new HopException("Configuration property not allowed: " + propertyName);
    }
  }

  private static void applyCommonToBusinessVault(
      BusinessVaultConfiguration config, String propertyName, String value) throws HopException {
    switch (propertyName.trim()) {
      case "targetTableParallelCopies" -> config.setTargetTableParallelCopies(value);
      case "targetTableBatchSize" -> config.setTargetTableBatchSize(value);
      case "targetLoadMode" -> config.setTargetLoadMode(value);
      case "bulkLoadStagingFolder" -> config.setBulkLoadStagingFolder(value);
      case "bulkLoadDelimiter" -> config.setBulkLoadDelimiter(value);
      case "bulkLoadEnclosure" -> config.setBulkLoadEnclosure(value);
      case "bulkLoadEncoding" -> config.setBulkLoadEncoding(value);
      case "bulkLoadLocalFileRequired" -> config.setBulkLoadLocalFileRequired(parseBoolean(value));

      case "targetDatabase" -> config.setTargetDatabase(value);
      case "generatedPipelineFolder" -> config.setGeneratedPipelineFolder(value);
      case "generatedWorkflowNamePrefix" -> config.setGeneratedWorkflowNamePrefix(value);
      default -> throw new HopException("Configuration property not allowed: " + propertyName);
    }
  }

  private static void applyCommonToDimensional(
      DimensionalConfiguration config, String propertyName, String value) throws HopException {
    switch (propertyName.trim()) {
      case "targetTableParallelCopies" -> config.setTargetTableParallelCopies(value);
      case "targetTableBatchSize" -> config.setTargetTableBatchSize(value);
      case "targetLoadMode" -> config.setTargetLoadMode(value);
      case "bulkLoadStagingFolder" -> config.setBulkLoadStagingFolder(value);
      case "bulkLoadDelimiter" -> config.setBulkLoadDelimiter(value);
      case "bulkLoadEnclosure" -> config.setBulkLoadEnclosure(value);
      case "bulkLoadEncoding" -> config.setBulkLoadEncoding(value);
      case "bulkLoadLocalFileRequired" -> config.setBulkLoadLocalFileRequired(parseBoolean(value));

      case "targetDatabase" -> config.setTargetDatabase(value);
      case "generatedPipelineFolder" -> config.setGeneratedPipelineFolder(value);
      case "generatedWorkflowNamePrefix" -> config.setGeneratedWorkflowNamePrefix(value);
      default -> throw new HopException("Configuration property not allowed: " + propertyName);
    }
  }

  private static boolean parseBoolean(String value) throws HopException {
    if (Utils.isEmpty(value)) {
      throw new HopException("Boolean value is required");
    }
    String normalized = value.trim().toLowerCase();
    if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
      return true;
    }
    if ("false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
      return false;
    }
    throw new HopException("Invalid boolean value: " + value);
  }
}
