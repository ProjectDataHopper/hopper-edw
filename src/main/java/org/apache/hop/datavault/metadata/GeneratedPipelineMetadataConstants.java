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
package org.apache.hop.datavault.metadata;

/** Attribute namespace and keys stamped onto generated update pipelines and transforms. */
public final class GeneratedPipelineMetadataConstants {

  public static final String NAMESPACE = "hop-datavault";

  public static final String MODEL_TYPE = "model_type";
  public static final String MODEL_NAME = "model_name";
  public static final String MODEL_FILENAME = "model_filename";
  public static final String ELEMENT_TYPE = "element_type";
  public static final String ELEMENT_NAME = "element_name";
  public static final String TARGET_TABLE = "target_table";
  public static final String SOURCE_NAME = "source_name";
  public static final String PIPELINE_NAME = "pipeline_name";

  public static final String LOGICAL_ROLE = "logical_role";
  public static final String PARENT_ELEMENT_NAME = "parent_element_name";
  public static final String PHYSICAL_TABLE = "physical_table";
  public static final String CONNECTION = "connection";
  public static final String LOOKUP_CACHE_MODE = "lookup_cache_mode";

  public static final String MODEL_TYPE_DV = "dv";
  public static final String MODEL_TYPE_BV = "bv";
  public static final String MODEL_TYPE_DM = "dm";

  public static final String ROLE_SOURCE_READ = "source_read";
  public static final String ROLE_TARGET_READ = "target_read";
  public static final String ROLE_CDC_MERGE = "cdc_merge";
  public static final String ROLE_HASH_KEY = "hash_key";
  public static final String ROLE_WRITE_TARGET = "write_target";
  public static final String ROLE_DIMENSION_LOOKUP = "dimension_lookup";
  public static final String ROLE_FILTER = "filter";
  public static final String ROLE_SORT = "sort";
  public static final String ROLE_GROUP_BY = "group_by";

  public static final String LOOKUP_CACHE_PRELOAD = "preload";
  public static final String LOOKUP_CACHE_DATABASE = "database";

  private GeneratedPipelineMetadataConstants() {}
}
