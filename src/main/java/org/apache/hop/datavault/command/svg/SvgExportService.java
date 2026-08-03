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
package org.apache.hop.datavault.command.svg;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.apache.commons.vfs2.FileExtensionSelector;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.executionmap.ExecutionMapPersistence;
import org.apache.hop.datavault.hopgui.file.businessvault.BusinessVaultModelSvgPainter;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.hopgui.file.dimensional.DimensionalModelSvgPainter;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.hopgui.file.executionmap.ExecutionMapSvgPainter;
import org.apache.hop.datavault.hopgui.file.vault.DataVaultModelSvgPainter;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.PipelineSvgPainter;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.WorkflowSvgPainter;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Loads Hop/DV/BV artifacts and renders them to SVG. */
public final class SvgExportService {

  public static final String EXT_HPL = "hpl";
  public static final String EXT_HWF = "hwf";
  public static final String EXT_HDV = "hdv";
  public static final String EXT_HBV = "hbv";
  public static final String EXT_HDM = "hdm";
  public static final String EXT_HEM = "hem";

  private static final Set<String> SUPPORTED_EXTENSIONS =
      Set.of(EXT_HPL, EXT_HWF, EXT_HDV, EXT_HBV, EXT_HDM, EXT_HEM);

  private SvgExportService() {}

  public static boolean isSupportedExtension(String extension) {
    return extension != null && SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
  }

  public static String generateDataVaultModelSvg(
      DataVaultModel model, SvgRenderOptions options, IVariables variables) throws HopException {
    return DataVaultModelSvgPainter.generateDataVaultModelSvg(model, options, variables);
  }

  public static String generateBusinessVaultModelSvg(
      BusinessVaultModel model,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return BusinessVaultModelSvgPainter.generateBusinessVaultModelSvg(
        model, options, variables, metadataProvider);
  }

  public static String generateDimensionalModelSvg(
      DimensionalModel model,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    return DimensionalModelSvgPainter.generateDimensionalModelSvg(
        model, options, variables, metadataProvider);
  }

  public static String generateExecutionMapSvg(
      ExecutionMapDocument document, SvgRenderOptions options, IVariables variables)
      throws HopException {
    return ExecutionMapSvgPainter.generateExecutionMapSvg(document, options, variables);
  }

  public static String generateSvg(
      String filename,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String resolvedFilename = variables.resolve(filename);
    String extension = extensionOf(resolvedFilename);
    if (!isSupportedExtension(extension)) {
      throw new HopException("Unsupported file extension for SVG export: " + extension);
    }
    SvgRenderOptions renderOptions = options != null ? options : SvgRenderOptions.defaults();
    return switch (extension) {
      case EXT_HPL ->
          generatePipelineSvg(resolvedFilename, renderOptions, variables, metadataProvider);
      case EXT_HWF ->
          generateWorkflowSvg(resolvedFilename, renderOptions, variables, metadataProvider);
      case EXT_HDV ->
          generateDataVaultModelSvg(resolvedFilename, renderOptions, variables, metadataProvider);
      case EXT_HBV ->
          generateBusinessVaultModelSvg(
              resolvedFilename, renderOptions, variables, metadataProvider);
      case EXT_HDM ->
          generateDimensionalModelSvg(resolvedFilename, renderOptions, variables, metadataProvider);
      case EXT_HEM ->
          generateExecutionMapSvg(resolvedFilename, renderOptions, variables, metadataProvider);
      default -> throw new HopException("Unsupported file extension for SVG export: " + extension);
    };
  }

  public static void exportFile(
      String inputFilename,
      String outputFilename,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log)
      throws HopException {
    String svg = generateSvg(inputFilename, options, variables, metadataProvider);
    String resolvedOutput = variables.resolve(outputFilename);
    writeSvg(resolvedOutput, svg);
    if (log != null) {
      log.logBasic("Exported SVG: " + resolvedOutput);
    }
  }

  public static void exportFolder(
      String sourceFolder,
      String targetFolder,
      boolean recursive,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log)
      throws HopException {
    try {
      FileObject sourceRoot = HopVfs.getFileObject(variables.resolve(sourceFolder));
      FileObject targetRoot = HopVfs.getFileObject(variables.resolve(targetFolder));
      if (!sourceRoot.exists()) {
        throw new HopException("Source folder does not exist: " + sourceFolder);
      }
      if (!targetRoot.exists()) {
        targetRoot.createFolder();
      }
      processFolder(
          sourceRoot, targetRoot, sourceRoot, recursive, options, variables, metadataProvider, log);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to export folder to SVG: " + sourceFolder, e);
    }
  }

  private static void processFolder(
      FileObject sourceRoot,
      FileObject targetRoot,
      FileObject sourceFolder,
      boolean recursive,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log)
      throws Exception {
    String relativeFolder = sourceRoot.getName().getRelativeName(sourceFolder.getName());
    FileObject targetFolder =
        ".".equals(relativeFolder)
            ? targetRoot
            : HopVfs.getFileObject(targetRoot.getName().getPath() + "/" + relativeFolder);
    if (!targetFolder.exists()) {
      targetFolder.createFolder();
    }

    FileObject[] childFiles =
        sourceFolder.findFiles(
            new FileExtensionSelector(EXT_HPL, EXT_HWF, EXT_HDV, EXT_HBV, EXT_HDM, EXT_HEM));
    for (FileObject childFile : childFiles) {
      if (!childFile.getParent().equals(sourceFolder)) {
        continue;
      }
      String extension = childFile.getName().getExtension();
      if (!isSupportedExtension(extension)) {
        continue;
      }
      String baseName = childFile.getName().getBaseName();
      String svgBaseName = baseName.substring(0, baseName.lastIndexOf('.')) + ".svg";
      String outputPath = targetFolder.getName().getPath() + "/" + svgBaseName;
      String inputPath = childFile.getName().getPath();
      try {
        exportFile(inputPath, outputPath, options, variables, metadataProvider, log);
      } catch (HopException e) {
        if (log != null) {
          log.logError("Failed to export " + inputPath, e);
        }
      }
    }

    if (recursive) {
      for (FileObject child : sourceFolder.getChildren()) {
        if (child.isFolder() && !child.isHidden()) {
          processFolder(
              sourceRoot, targetRoot, child, true, options, variables, metadataProvider, log);
        }
      }
    }
  }

  public static String defaultOutputPath(String inputFilename) {
    int dot = inputFilename.lastIndexOf('.');
    if (dot > 0) {
      return inputFilename.substring(0, dot) + ".svg";
    }
    return inputFilename + ".svg";
  }

  public static void writeSvg(String outputFilename, String svgXml) throws HopException {
    try (OutputStream outputStream = HopVfs.getOutputStream(outputFilename, false)) {
      outputStream.write(svgXml.getBytes(StandardCharsets.UTF_8));
      outputStream.flush();
    } catch (Exception e) {
      throw new HopException("Unable to write SVG file: " + outputFilename, e);
    }
  }

  private static String generatePipelineSvg(
      String filename,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    try {
      PipelineMeta pipelineMeta = new PipelineMeta(filename, metadataProvider, variables);
      if (!options.isIncludeNotes()) {
        pipelineMeta = (PipelineMeta) pipelineMeta.clone();
        pipelineMeta.getNotes().clear();
      }
      return PipelineSvgPainter.generatePipelineSvg(
          pipelineMeta, options.getMagnification(), variables);
    } catch (Exception e) {
      throw new HopException("Unable to generate SVG for pipeline " + filename, e);
    }
  }

  private static String generateWorkflowSvg(
      String filename,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    try {
      WorkflowMeta workflowMeta = new WorkflowMeta(variables, filename, metadataProvider);
      if (!options.isIncludeNotes()) {
        workflowMeta = (WorkflowMeta) workflowMeta.clone();
        workflowMeta.getNotes().clear();
      }
      return WorkflowSvgPainter.generateWorkflowSvg(
          workflowMeta, options.getMagnification(), variables);
    } catch (Exception e) {
      throw new HopException("Unable to generate SVG for workflow " + filename, e);
    }
  }

  private static String generateDataVaultModelSvg(
      String filename,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    try {
      DataVaultModel model = loadDataVaultModel(filename, metadataProvider);
      return DataVaultModelSvgPainter.generateDataVaultModelSvg(model, options, variables);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to generate SVG for Data Vault model " + filename, e);
    }
  }

  private static String generateBusinessVaultModelSvg(
      String filename,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    try {
      BusinessVaultModel model = loadBusinessVaultModel(filename, metadataProvider);
      return BusinessVaultModelSvgPainter.generateBusinessVaultModelSvg(
          model, options, variables, metadataProvider);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to generate SVG for Business Vault model " + filename, e);
    }
  }

  private static String generateDimensionalModelSvg(
      String filename,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    try {
      DimensionalModel model = loadDimensionalModel(filename, metadataProvider);
      return DimensionalModelSvgPainter.generateDimensionalModelSvg(
          model, options, variables, metadataProvider);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to generate SVG for dimensional model " + filename, e);
    }
  }

  private static DataVaultModel loadDataVaultModel(
      String filename, IHopMetadataProvider metadataProvider) throws Exception {
    Document document = XmlHandler.loadXmlFile(filename);
    Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, metadataProvider);
    return model;
  }

  private static BusinessVaultModel loadBusinessVaultModel(
      String filename, IHopMetadataProvider metadataProvider) throws Exception {
    Document document = XmlHandler.loadXmlFile(filename);
    Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
    BusinessVaultModel model = new BusinessVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BusinessVaultModel.class, model, metadataProvider);
    return model;
  }

  private static DimensionalModel loadDimensionalModel(
      String filename, IHopMetadataProvider metadataProvider) throws Exception {
    Document document = XmlHandler.loadXmlFile(filename);
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel model = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, metadataProvider);
    return model;
  }

  private static String generateExecutionMapSvg(
      String filename,
      SvgRenderOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    try {
      ExecutionMapDocument document =
          ExecutionMapPersistence.load(filename, metadataProvider, variables);
      return generateExecutionMapSvg(document, options, variables);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to generate SVG for execution map " + filename, e);
    }
  }

  private static String extensionOf(String filename) {
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return "";
    }
    return filename.substring(dot + 1).toLowerCase();
  }
}
