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
package org.hopper.edw.datavault.expression;

import org.apache.hop.core.exception.HopException;

/** Failures while rewriting, parsing, validating, or evaluating a SQL scalar expression. */
public class SqlExpressionException extends HopException {

  public SqlExpressionException(String message) {
    super(message);
  }

  public SqlExpressionException(String message, Throwable cause) {
    super(message, cause);
  }
}
