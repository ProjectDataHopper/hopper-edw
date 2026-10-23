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
package org.hopper.edw.datavault.virtualization.dimensional;

/** A dimensional JDBC statement could not be planned or was rejected by the masking policy. */
public class DimensionalSqlException extends Exception {

  private final boolean rejected;

  public DimensionalSqlException(String message) {
    this(message, false, null);
  }

  public DimensionalSqlException(String message, Throwable cause) {
    this(message, false, cause);
  }

  public static DimensionalSqlException rejected(String message) {
    return new DimensionalSqlException(message, true, null);
  }

  private DimensionalSqlException(String message, boolean rejected, Throwable cause) {
    super(message, cause);
    this.rejected = rejected;
  }

  /** True when the Safe masking policy refused the statement. The warehouse was not queried. */
  public boolean isRejected() {
    return rejected;
  }
}
