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
package org.apache.hop.datavault.metadata.targettypemapping;

import lombok.Getter;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;

/** Resolved mapping plus variables for one DDL generation pass. */
@Getter
public final class TargetTypeMappingContext {

  private final TargetTypeMappingMeta mapping;
  private final IVariables variables;

  public TargetTypeMappingContext(TargetTypeMappingMeta mapping, IVariables variables) {
    this.mapping = mapping;
    this.variables = variables != null ? variables : new Variables();
  }

  public static TargetTypeMappingContext none(IVariables variables) {
    return new TargetTypeMappingContext(null, variables);
  }

  public boolean hasMapping() {
    return mapping != null && !mapping.getRules().isEmpty();
  }
}
