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
package org.hopper.edw.datavault.jinja;

import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/** Context objects exposed as {@code adapter}, {@code exceptions}, and similar dbt names. */
public final class DbtJinjaContextObjects {

  private static final Class<?> PKG = DbtJinjaContextObjects.class;

  private DbtJinjaContextObjects() {}

  public static final class Adapter {
    private final DatabaseMeta databaseMeta;
    private final IVariables variables;

    public Adapter(DatabaseMeta databaseMeta, IVariables variables) {
      this.databaseMeta = databaseMeta;
      this.variables = variables;
    }

    public String quote(Object name) {
      if (name == null || Utils.isEmpty(name.toString())) {
        return "";
      }
      String raw = name.toString();
      if (databaseMeta == null) {
        return raw;
      }
      return databaseMeta.quoteField(raw);
    }

    public Object dispatch(Object... ignored) {
      throw unsupported("adapter.dispatch");
    }

    public Object get_relation(Object... ignored) {
      throw unsupported("adapter.get_relation");
    }
  }

  public static final class Exceptions {
    public Object raise_compiler_error(Object message) {
      String text = message != null ? message.toString() : "compiler error";
      throw new IllegalStateException(text);
    }

    public Object warn(Object message) {
      throw unsupported("exceptions.warn");
    }
  }

  public static final class UnsupportedName {
    private final String name;

    public UnsupportedName(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      throw unsupported(name);
    }

    public Object get(Object ignored) {
      throw unsupported(name);
    }
  }

  static UnsupportedOperationException unsupported(String name) {
    return new UnsupportedOperationException(
        BaseMessages.getString(PKG, "DbtJinjaBuiltins.Error.Unsupported", name));
  }
}
