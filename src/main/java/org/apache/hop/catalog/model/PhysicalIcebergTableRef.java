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
package org.apache.hop.catalog.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Optional link from a record definition to a physical Iceberg table. */
@Getter
@Setter
@NoArgsConstructor
public class PhysicalIcebergTableRef {

  @HopMetadataProperty private String catalogUri;

  @HopMetadataProperty private String warehouse;

  @HopMetadataProperty private String namespace;

  @HopMetadataProperty private String tableName;

  @HopMetadataProperty private String snapshotId;

  @HopMetadataProperty private String branch;

  @HopMetadataProperty private String s3Endpoint;

  @HopMetadataProperty private String s3AccessKey;

  @HopMetadataProperty private String s3SecretKey;
}
