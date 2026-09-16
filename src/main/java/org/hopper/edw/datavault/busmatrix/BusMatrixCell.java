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
package org.hopper.edw.datavault.busmatrix;

import java.util.List;

/** Intersection of a fact and a conformed dimension. */
public record BusMatrixCell(List<String> roleNames) {

  public static final BusMatrixCell EMPTY = new BusMatrixCell(List.of());

  public BusMatrixCell {
    roleNames = roleNames != null ? List.copyOf(roleNames) : List.of();
  }

  public boolean used() {
    return !roleNames.isEmpty();
  }

  public int roleCount() {
    return roleNames.size();
  }

  public String mark() {
    if (!used()) {
      return "";
    }
    return roleCount() > 1 ? Integer.toString(roleCount()) : "X";
  }

  public String tooltip() {
    if (!used()) {
      return "";
    }
    return String.join(", ", roleNames);
  }
}
