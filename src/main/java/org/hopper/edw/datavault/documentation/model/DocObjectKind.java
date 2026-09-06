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
package org.hopper.edw.datavault.documentation.model;

/** Kind of object recorded in the generated documentation set and search index. */
public enum DocObjectKind {
  OVERVIEW("Overview"),
  PIPELINE("Pipelines"),
  WORKFLOW("Workflows"),
  TRANSFORM("Transforms"),
  ACTION("Actions"),
  SOURCE_MODEL("Source models"),
  DATA_VAULT_MODEL("Data Vault models"),
  BUSINESS_VAULT_MODEL("Business Vault models"),
  DIMENSIONAL_MODEL("Dimensional models"),
  EXECUTION_MAP("Execution maps"),
  TABLE("Tables"),
  METADATA("Metadata"),
  CATALOG("Catalog"),
  NOTE("Notes"),
  ENVIRONMENT("Environment");

  private final String navLabel;

  DocObjectKind(String navLabel) {
    this.navLabel = navLabel;
  }

  public String navLabel() {
    return navLabel;
  }

  public String indexKey() {
    return name().toLowerCase().replace('_', '-');
  }
}
