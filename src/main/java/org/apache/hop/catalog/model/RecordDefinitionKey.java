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

import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Qualified identifier for a record definition within a single catalog connection. */
@Getter
@Setter
@NoArgsConstructor
public class RecordDefinitionKey {

  @HopMetadataProperty private String namespace;

  @HopMetadataProperty private String name;

  public RecordDefinitionKey(String namespace, String name) {
    this.namespace = namespace;
    this.name = name;
  }

  public void validate() {
    if (Utils.isEmpty(namespace)) {
      throw new IllegalArgumentException("Record definition namespace is required");
    }
    if (Utils.isEmpty(name)) {
      throw new IllegalArgumentException("Record definition name is required");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RecordDefinitionKey that)) {
      return false;
    }
    return Objects.equals(namespace, that.namespace) && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(namespace, name);
  }

  @Override
  public String toString() {
    return namespace + "::" + name;
  }
}
