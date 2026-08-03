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
package org.apache.hop.datavault.hopgui.file.modelgraph;

import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.datavault.metadata.DvNote;
import org.jspecify.annotations.Nullable;

/** Logical canvas hit at mouse coordinates (tables, refs, notes, or background). */
public final class ModelGraphHit {

  public static final ModelGraphHit BACKGROUND = new ModelGraphHit(null, null, null, null);

  private final @Nullable AreaOwner areaOwner;
  private final AreaOwner.AreaType areaType;
  private final @Nullable DvNote note;
  private final @Nullable Object canvasObject;

  public ModelGraphHit(
      AreaOwner areaOwner, AreaOwner.AreaType areaType, DvNote note, Object canvasObject) {
    this.areaOwner = areaOwner;
    this.areaType = areaType;
    this.note = note;
    this.canvasObject = canvasObject;
  }

  public @Nullable AreaOwner areaOwner() {
    return areaOwner;
  }

  public AreaOwner.AreaType areaType() {
    return areaType;
  }

  public @Nullable DvNote note() {
    return note;
  }

  /** Primary domain object under the cursor (e.g. {@code IDvTable}, {@code IBvTable}). */
  public @Nullable Object canvasObject() {
    return canvasObject;
  }

  public boolean isBackground() {
    return canvasObject == null && note == null;
  }
}
