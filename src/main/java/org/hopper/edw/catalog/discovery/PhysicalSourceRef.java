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
package org.hopper.edw.catalog.discovery;

import java.util.regex.Pattern;
import org.apache.commons.vfs2.FileObject;
import org.hopper.edw.catalog.model.PhysicalIcebergTableRef;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;

/** Resolved physical location used for record-definition schema discovery. */
public final class PhysicalSourceRef {

  private static final Class<?> PKG = PhysicalSourceRef.class;

  private final String databaseConnectionName;
  private final String schemaName;
  private final String tableName;
  private final String filePath;
  private final String folder;
  private final String includeFileMask;
  private final String excludeFileMask;
  private final boolean includeSubfolders;
  private final String catalogUri;
  private final String warehouse;
  private final String icebergNamespace;
  private final String icebergTableName;
  private final String snapshotId;
  private final String branch;
  private final String s3Endpoint;
  private final String s3AccessKey;
  private final String s3SecretKey;

  /** Path to the {@code .hsm} source model when refreshing a COMPOSITE feed. */
  private final String compositeSourceModelFilename;

  /** Source query name inside the model when refreshing a COMPOSITE feed. */
  private final String compositeSourceQueryName;

  /** Path to the {@code .hsm} source model when refreshing a JSON feed. */
  private final String jsonSourceModelFilename;

  /** Source JSON object name when refreshing a JSON feed. */
  private final String jsonSourceName;

  /** Path to the {@code .hsm} source model when refreshing a PIPELINE feed. */
  private final String pipelineSourceModelFilename;

  /** Source pipeline object name when refreshing a PIPELINE feed from the source model. */
  private final String pipelineSourceName;

  /** Path to the {@code .hpl} when refreshing a PIPELINE feed via live transform fields. */
  private final String pipelineFilename;

  /** Output transform name when refreshing a PIPELINE feed via live transform fields. */
  private final String pipelineTransformName;

  private PhysicalSourceRef(Builder builder) {
    this.databaseConnectionName = builder.databaseConnectionName;
    this.schemaName = builder.schemaName;
    this.tableName = builder.tableName;
    this.filePath = builder.filePath;
    this.folder = builder.folder;
    this.includeFileMask = builder.includeFileMask;
    this.excludeFileMask = builder.excludeFileMask;
    this.includeSubfolders = builder.includeSubfolders;
    this.catalogUri = builder.catalogUri;
    this.warehouse = builder.warehouse;
    this.icebergNamespace = builder.icebergNamespace;
    this.icebergTableName = builder.icebergTableName;
    this.snapshotId = builder.snapshotId;
    this.branch = builder.branch;
    this.s3Endpoint = builder.s3Endpoint;
    this.s3AccessKey = builder.s3AccessKey;
    this.s3SecretKey = builder.s3SecretKey;
    this.compositeSourceModelFilename = builder.compositeSourceModelFilename;
    this.compositeSourceQueryName = builder.compositeSourceQueryName;
    this.jsonSourceModelFilename = builder.jsonSourceModelFilename;
    this.jsonSourceName = builder.jsonSourceName;
    this.pipelineSourceModelFilename = builder.pipelineSourceModelFilename;
    this.pipelineSourceName = builder.pipelineSourceName;
    this.pipelineFilename = builder.pipelineFilename;
    this.pipelineTransformName = builder.pipelineTransformName;
  }

  public String getDatabaseConnectionName() {
    return databaseConnectionName;
  }

  public String getSchemaName() {
    return schemaName;
  }

  public String getTableName() {
    return tableName;
  }

  public String getFilePath() {
    return filePath;
  }

  public String getFolder() {
    return folder;
  }

  public String getIncludeFileMask() {
    return includeFileMask;
  }

  public String getExcludeFileMask() {
    return excludeFileMask;
  }

  public boolean isIncludeSubfolders() {
    return includeSubfolders;
  }

  public String getCatalogUri() {
    return catalogUri;
  }

  public String getWarehouse() {
    return warehouse;
  }

  public String getIcebergNamespace() {
    return icebergNamespace;
  }

  public String getIcebergTableName() {
    return icebergTableName;
  }

  public String getSnapshotId() {
    return snapshotId;
  }

  public String getBranch() {
    return branch;
  }

  public String getS3Endpoint() {
    return s3Endpoint;
  }

  public String getS3AccessKey() {
    return s3AccessKey;
  }

  public String getS3SecretKey() {
    return s3SecretKey;
  }

  public String getCompositeSourceModelFilename() {
    return compositeSourceModelFilename;
  }

  public String getCompositeSourceQueryName() {
    return compositeSourceQueryName;
  }

  public String getJsonSourceModelFilename() {
    return jsonSourceModelFilename;
  }

  public String getJsonSourceName() {
    return jsonSourceName;
  }

  public String getPipelineSourceModelFilename() {
    return pipelineSourceModelFilename;
  }

  public String getPipelineSourceName() {
    return pipelineSourceName;
  }

  public String getPipelineFilename() {
    return pipelineFilename;
  }

  public String getPipelineTransformName() {
    return pipelineTransformName;
  }

  public void validateIcebergLocation(IVariables variables) throws HopException {
    HopVariableResolutionSupport.requireResolved(variables, catalogUri, "Iceberg catalog URI");
    HopVariableResolutionSupport.requireResolved(variables, icebergNamespace, "Iceberg namespace");
    HopVariableResolutionSupport.requireResolved(variables, icebergTableName, "Iceberg table name");

    if (Utils.isEmpty(resolve(variables, catalogUri))
        || Utils.isEmpty(resolve(variables, icebergNamespace))
        || Utils.isEmpty(resolve(variables, icebergTableName))) {
      throw new HopException(
          BaseMessages.getString(PKG, "PhysicalSourceRef.Error.MissingIcebergLocation"));
    }
  }

  public PhysicalIcebergTableRef toPhysicalIcebergTableRef(IVariables variables) {
    PhysicalIcebergTableRef ref = new PhysicalIcebergTableRef();
    ref.setCatalogUri(resolve(variables, catalogUri));
    ref.setWarehouse(resolve(variables, warehouse));
    ref.setNamespace(resolve(variables, icebergNamespace));
    ref.setTableName(resolve(variables, icebergTableName));
    ref.setSnapshotId(resolve(variables, snapshotId));
    ref.setBranch(resolve(variables, branch));
    ref.setS3Endpoint(resolve(variables, s3Endpoint));
    ref.setS3AccessKey(resolve(variables, s3AccessKey));
    ref.setS3SecretKey(resolve(variables, s3SecretKey));
    return ref;
  }

  public static PhysicalSourceRef fromPhysicalIcebergTableRef(PhysicalIcebergTableRef ref) {
    if (ref == null) {
      return null;
    }
    return builder()
        .catalogUri(ref.getCatalogUri())
        .warehouse(ref.getWarehouse())
        .icebergNamespace(ref.getNamespace())
        .icebergTableName(ref.getTableName())
        .snapshotId(ref.getSnapshotId())
        .branch(ref.getBranch())
        .s3Endpoint(ref.getS3Endpoint())
        .s3AccessKey(ref.getS3AccessKey())
        .s3SecretKey(ref.getS3SecretKey())
        .build();
  }

  public String resolveDiscoveryFilePath(IVariables variables) throws HopException {
    String resolvedPath = resolve(variables, filePath);
    if (!Utils.isEmpty(resolvedPath)) {
      return resolvedPath;
    }

    String resolvedFolder = resolve(variables, folder);
    if (Utils.isEmpty(resolvedFolder)) {
      throw new HopException(
          BaseMessages.getString(PKG, "PhysicalSourceRef.Error.MissingFileLocation"));
    }

    String includeMask = Const.NVL(resolve(variables, includeFileMask), ".*");
    String excludeMask = Const.NVL(resolve(variables, excludeFileMask), "");
    Pattern includePattern = Pattern.compile(includeMask);
    Pattern excludePattern = Utils.isEmpty(excludeMask) ? null : Pattern.compile(excludeMask);

    try {
      FileObject folderObject = HopVfs.getFileObject(resolvedFolder, variables);
      if (!folderObject.exists() || !folderObject.isFolder()) {
        throw new HopException(
            BaseMessages.getString(PKG, "PhysicalSourceRef.Error.MissingFolder", resolvedFolder));
      }
      String match = findFirstMatchingFile(folderObject, includePattern, excludePattern);
      if (Utils.isEmpty(match)) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "PhysicalSourceRef.Error.NoMatchingFile", resolvedFolder, includeMask));
      }
      return match;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "PhysicalSourceRef.Error.ResolveFile", resolvedFolder), e);
    }
  }

  private static String findFirstMatchingFile(
      FileObject folderObject, Pattern includePattern, Pattern excludePattern) throws Exception {
    FileObject[] children = folderObject.getChildren();
    if (children == null) {
      return null;
    }
    for (FileObject child : children) {
      if (child == null || !child.exists()) {
        continue;
      }
      if (child.isFolder()) {
        continue;
      }
      String baseName = child.getName().getBaseName();
      if (!includePattern.matcher(baseName).matches()) {
        continue;
      }
      if (excludePattern != null && excludePattern.matcher(baseName).matches()) {
        continue;
      }
      return HopVfs.getFilename(child);
    }
    return null;
  }

  private static String resolve(IVariables variables, String value) {
    return variables != null ? variables.resolve(value) : value;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String databaseConnectionName;
    private String schemaName;
    private String tableName;
    private String filePath;
    private String folder;
    private String includeFileMask;
    private String excludeFileMask;
    private boolean includeSubfolders;
    private String catalogUri;
    private String warehouse;
    private String icebergNamespace;
    private String icebergTableName;
    private String snapshotId;
    private String branch;
    private String s3Endpoint;
    private String s3AccessKey;
    private String s3SecretKey;
    private String compositeSourceModelFilename;
    private String compositeSourceQueryName;
    private String jsonSourceModelFilename;
    private String jsonSourceName;
    private String pipelineSourceModelFilename;
    private String pipelineSourceName;
    private String pipelineFilename;
    private String pipelineTransformName;

    public Builder databaseConnectionName(String value) {
      this.databaseConnectionName = value;
      return this;
    }

    public Builder schemaName(String value) {
      this.schemaName = value;
      return this;
    }

    public Builder tableName(String value) {
      this.tableName = value;
      return this;
    }

    public Builder filePath(String value) {
      this.filePath = value;
      return this;
    }

    public Builder folder(String value) {
      this.folder = value;
      return this;
    }

    public Builder includeFileMask(String value) {
      this.includeFileMask = value;
      return this;
    }

    public Builder excludeFileMask(String value) {
      this.excludeFileMask = value;
      return this;
    }

    public Builder includeSubfolders(boolean value) {
      this.includeSubfolders = value;
      return this;
    }

    public Builder catalogUri(String value) {
      this.catalogUri = value;
      return this;
    }

    public Builder warehouse(String value) {
      this.warehouse = value;
      return this;
    }

    public Builder icebergNamespace(String value) {
      this.icebergNamespace = value;
      return this;
    }

    public Builder icebergTableName(String value) {
      this.icebergTableName = value;
      return this;
    }

    public Builder snapshotId(String value) {
      this.snapshotId = value;
      return this;
    }

    public Builder branch(String value) {
      this.branch = value;
      return this;
    }

    public Builder s3Endpoint(String value) {
      this.s3Endpoint = value;
      return this;
    }

    public Builder s3AccessKey(String value) {
      this.s3AccessKey = value;
      return this;
    }

    public Builder s3SecretKey(String value) {
      this.s3SecretKey = value;
      return this;
    }

    public Builder compositeSourceModelFilename(String value) {
      this.compositeSourceModelFilename = value;
      return this;
    }

    public Builder compositeSourceQueryName(String value) {
      this.compositeSourceQueryName = value;
      return this;
    }

    public Builder jsonSourceModelFilename(String value) {
      this.jsonSourceModelFilename = value;
      return this;
    }

    public Builder jsonSourceName(String value) {
      this.jsonSourceName = value;
      return this;
    }

    public Builder pipelineSourceModelFilename(String value) {
      this.pipelineSourceModelFilename = value;
      return this;
    }

    public Builder pipelineSourceName(String value) {
      this.pipelineSourceName = value;
      return this;
    }

    public Builder pipelineFilename(String value) {
      this.pipelineFilename = value;
      return this;
    }

    public Builder pipelineTransformName(String value) {
      this.pipelineTransformName = value;
      return this;
    }

    public PhysicalSourceRef build() {
      return new PhysicalSourceRef(this);
    }
  }
}
