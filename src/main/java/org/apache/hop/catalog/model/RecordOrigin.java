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

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Provenance metadata describing where a record definition originated and how it was updated. */
@Getter
@Setter
@NoArgsConstructor
public class RecordOrigin {

  @HopMetadataProperty private String modelType;

  @HopMetadataProperty private String modelName;

  @HopMetadataProperty private String modelFilename;

  @HopMetadataProperty private String modelElementName;

  @HopMetadataProperty private String hopProject;

  @HopMetadataProperty private Date createdAt;

  @HopMetadataProperty private Date updatedAt;

  @HopMetadataProperty private Date lastDiscoveredAt;

  @HopMetadataProperty private String updatedBy;

  @HopMetadataProperty private String lastWorkflow;

  @HopMetadataProperty private String lastPipeline;
}
