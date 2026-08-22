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
package org.hopper.edw.datavault.layout;

import lombok.Getter;
import org.apache.hop.core.gui.IGuiPosition;

/** A node in an abstract graph passed to the ELK layout engine. */
@Getter
public final class ElkLayoutNode {

  private final String id;
  private final String label;
  private final double width;
  private final double height;
  private final IGuiPosition target;

  public ElkLayoutNode(String id, String label, double width, double height, IGuiPosition target) {
    this.id = id;
    this.label = label;
    this.width = width;
    this.height = height;
    this.target = target;
  }
}
