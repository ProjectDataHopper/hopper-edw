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
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;
import org.eclipse.elk.core.options.Direction;

/** Primary layout direction for ELK layered layout. */
@Getter
public enum ElkLayoutDirection implements IEnumHasCodeAndDescription {
  RIGHT(
      "RIGHT",
      BaseMessages.getString(ElkLayoutDirection.class, "ElkLayoutDirection.Right"),
      Direction.RIGHT),
  LEFT(
      "LEFT",
      BaseMessages.getString(ElkLayoutDirection.class, "ElkLayoutDirection.Left"),
      Direction.LEFT),
  DOWN(
      "DOWN",
      BaseMessages.getString(ElkLayoutDirection.class, "ElkLayoutDirection.Down"),
      Direction.DOWN),
  UP("UP", BaseMessages.getString(ElkLayoutDirection.class, "ElkLayoutDirection.Up"), Direction.UP);

  private final String code;
  private final String description;
  private final Direction elkDirection;

  ElkLayoutDirection(String code, String description, Direction elkDirection) {
    this.code = code;
    this.description = description;
    this.elkDirection = elkDirection;
  }

  public Direction toElkDirection() {
    return elkDirection;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(ElkLayoutDirection.class);
  }

  public static ElkLayoutDirection lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        ElkLayoutDirection.class, description, RIGHT);
  }

  public static ElkLayoutDirection lookupCode(String code) {
    return IEnumHasCode.lookupCode(ElkLayoutDirection.class, code, RIGHT);
  }
}
