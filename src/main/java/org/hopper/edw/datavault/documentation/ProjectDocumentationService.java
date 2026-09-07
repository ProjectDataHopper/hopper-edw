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
package org.hopper.edw.datavault.documentation;

import java.util.Date;
import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.IProgressMonitor;
import org.apache.hop.core.ProgressNullMonitorListener;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.hopper.edw.datavault.documentation.render.DocPaths;
import org.hopper.edw.datavault.documentation.render.DocumentationIo;
import org.hopper.edw.datavault.documentation.render.HtmlPageWriter;
import org.hopper.edw.datavault.documentation.scan.ProjectFileScanner;
import org.hopper.edw.datavault.documentation.scan.ScannedFile;
import org.hopper.edw.datavault.documentation.writers.AssetCopyWriter;
import org.hopper.edw.datavault.documentation.writers.CatalogDocWriter;
import org.hopper.edw.datavault.documentation.writers.IndexWriter;
import org.hopper.edw.datavault.documentation.writers.MetadataDocWriter;
import org.hopper.edw.datavault.documentation.writers.ModelDocWriter;
import org.hopper.edw.datavault.documentation.writers.PipelineDocWriter;
import org.hopper.edw.datavault.documentation.writers.SearchIndexWriter;
import org.hopper.edw.datavault.documentation.writers.TableDocWriter;
import org.hopper.edw.datavault.documentation.writers.ThemingWriter;
import org.hopper.edw.datavault.documentation.writers.WorkflowDocWriter;

/** Generates a self-contained HTML+JS documentation folder for a Hop project. */
public final class ProjectDocumentationService {

  public static final String VAR_PROJECT_HOME = "PROJECT_HOME";
  public static final String VAR_PROJECT_NAME = "HOP_PROJECT_NAME";

  private ProjectDocumentationService() {}

  public static ProjectDocumentationResult generate(
      ProjectDocumentationOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log)
      throws HopException {
    return generate(options, variables, metadataProvider, log, new ProgressNullMonitorListener());
  }

  public static ProjectDocumentationResult generate(
      ProjectDocumentationOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILogChannel log,
      IProgressMonitor monitor)
      throws HopException {
    if (options == null) {
      throw new HopException("Documentation options are required");
    }
    if (variables == null) {
      throw new HopException("Variables are required");
    }
    IProgressMonitor progress = monitor != null ? monitor : new ProgressNullMonitorListener();
    String source =
        firstNonEmpty(options.getSourceFolder(), variables.getVariable(VAR_PROJECT_HOME));
    if (Utils.isEmpty(source)) {
      throw new HopException("No source folder or ${PROJECT_HOME} specified");
    }
    String target = variables.resolve(Const.NVL(options.getTargetFolder(), ""));
    if (Utils.isEmpty(target)) {
      throw new HopException("Target folder is required");
    }
    source = variables.resolve(source);
    options.setSourceFolder(source);
    options.setTargetFolder(target);

    FileObject sourceRoot;
    FileObject targetRoot;
    try {
      sourceRoot = HopVfs.getFileObject(source);
      targetRoot = HopVfs.getFileObject(target);
      if (!sourceRoot.exists()) {
        throw new HopException("Source folder does not exist: " + source);
      }
      if (!targetRoot.exists()) {
        targetRoot.createFolder();
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to open documentation folders", e);
    }

    ProjectDocumentationResult result = new ProjectDocumentationResult();
    result.setOutputFolder(target);
    DocumentationSite site = new DocumentationSite();
    site.setOptions(options);
    site.setResult(result);
    site.setVariables(variables);
    site.setMetadataProvider(metadataProvider);
    site.setLog(log);
    site.setSourceRoot(sourceRoot);
    site.setTargetRoot(targetRoot);
    site.setGeneratedAt(new Date());
    site.setProjectName(
        firstNonEmpty(
            options.getProjectName(),
            variables.getVariable(VAR_PROJECT_NAME),
            sourceRoot.getName().getBaseName(),
            "Hop project"));
    options.setProjectName(site.getProjectName());

    if (log != null) {
      log.logBasic(
          "Generating project documentation for " + site.getProjectName() + " → " + target);
    }

    progress.beginTask("Generating project documentation for " + site.getProjectName(), 1);
    progress.subTask("Scanning project files");
    List<ScannedFile> files = ProjectFileScanner.scan(sourceRoot, targetRoot);
    int extra =
        3 + (options.isIncludingMetadata() ? 1 : 0) + (options.isIncludingCatalog() ? 1 : 0);
    progress.beginTask(
        "Generating project documentation for " + site.getProjectName(), files.size() + extra);

    if (canceled(progress, result)) {
      return finish(site, log, result);
    }

    progress.subTask("Copying site assets");
    AssetCopyWriter.write(site);
    if (options.isIncludingMetadata()) {
      MetadataDocWriter.rememberHrefs(site);
    }
    progress.worked(1);

    for (ScannedFile file : files) {
      site.rememberPage(
          file.relativePath(), DocPaths.htmlForSource(file.relativePath(), file.extension()));
    }
    for (ScannedFile file : files) {
      if (canceled(progress, result)) {
        break;
      }
      progress.subTask(file.relativePath());
      try {
        documentFile(site, file);
      } catch (Exception e) {
        site.error("Failed to document " + file.relativePath(), e);
      }
      progress.worked(1);
    }

    if (!result.isCancelled()) {
      progress.subTask("Documenting tables");
      TableDocWriter.writeAll(site);
      progress.worked(1);
    }
    if (!result.isCancelled() && options.isIncludingMetadata()) {
      progress.subTask("Documenting metadata");
      MetadataDocWriter.writeAll(site);
      progress.worked(1);
    }
    if (!result.isCancelled() && options.isIncludingCatalog()) {
      progress.subTask("Documenting catalog records");
      CatalogDocWriter.writeAll(site);
      progress.worked(1);
    }
    progress.subTask("Writing site index");
    ThemingWriter.write(site);
    IndexWriter.write(site);
    SearchIndexWriter.write(site);
    HtmlPageWriter.flushQueued(site);
    progress.worked(1);

    return finish(site, log, result);
  }

  private static boolean canceled(IProgressMonitor progress, ProjectDocumentationResult result) {
    if (progress != null && progress.isCanceled()) {
      result.setCancelled(true);
      result.addWarning("Documentation generation was cancelled");
      return true;
    }
    return false;
  }

  private static ProjectDocumentationResult finish(
      DocumentationSite site, ILogChannel log, ProjectDocumentationResult result) {
    if (log != null) {
      log.logBasic(
          (result.isCancelled() ? "Cancelled" : "Finished")
              + " project documentation: "
              + result.getPagesWritten()
              + " pages, "
              + result.getSearchEntries()
              + " search entries, "
              + result.getErrors()
              + " errors");
    }
    return result;
  }

  private static void documentFile(DocumentationSite site, ScannedFile file) throws HopException {
    // HopVfs.getFilename, not FileName.getPath(): the VFS path drops the Windows drive letter.
    String path = DocumentationIo.filenameForLoad(file.file());
    switch (file.extension()) {
      case "hpl" -> {
        PipelineMeta meta = new PipelineMeta(path, site.getMetadataProvider(), site.getVariables());
        PipelineDocWriter.write(site, file, meta);
      }
      case "hwf" -> {
        WorkflowMeta meta = new WorkflowMeta(site.getVariables(), path, site.getMetadataProvider());
        WorkflowDocWriter.write(site, file, meta);
      }
      case "hsm", "hdv", "hbv", "hdm", "hem" -> ModelDocWriter.write(site, file);
      default -> {
        // Ignore
      }
    }
  }

  private static String firstNonEmpty(String... values) {
    if (values == null) {
      return "";
    }
    for (String value : values) {
      if (!Utils.isEmpty(value)) {
        return value;
      }
    }
    return "";
  }
}
