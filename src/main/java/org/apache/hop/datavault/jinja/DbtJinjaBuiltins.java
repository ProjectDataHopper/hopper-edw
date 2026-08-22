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
package org.apache.hop.datavault.jinja;

import com.hubspot.jinjava.el.ext.NamedParameter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.businessvault.BvSqlRef;
import org.apache.hop.datavault.metadata.businessvault.BvSqlRefResolver;
import org.apache.hop.datavault.metadata.businessvault.BvSqlSource;
import org.apache.hop.i18n.BaseMessages;

/**
 * dbt-shaped Jinja functions registered on the sandboxed engine. They read the current {@link
 * BvSqlJinjaRenderSession} from a thread-local so EL methods can stay static.
 */
public final class DbtJinjaBuiltins {

  private static final Class<?> PKG = DbtJinjaBuiltins.class;

  private static final ThreadLocal<BvSqlJinjaRenderSession> SESSION = new ThreadLocal<>();

  private DbtJinjaBuiltins() {}

  static void bind(BvSqlJinjaRenderSession session) {
    SESSION.set(session);
  }

  static void unbind() {
    SESSION.remove();
  }

  static BvSqlJinjaRenderSession requireSession() {
    BvSqlJinjaRenderSession session = SESSION.get();
    if (session == null) {
      throw new IllegalStateException("Jinja builtins used outside a Business Vault render");
    }
    return session;
  }

  public static String ref(Object first, Object... rest) {
    BvSqlJinjaRenderSession session = requireSession();
    String firstText = stringArg("ref", first);
    String second = firstRest(rest);
    BvSqlRef sqlRef;
    if (Utils.isEmpty(second)) {
      sqlRef = new BvSqlRef(firstText);
    } else {
      sqlRef = new BvSqlRef(firstText, second);
    }
    BvSqlRefResolver.resolveRef(
        sqlRef,
        session.getTable(),
        session.getBvModel(),
        session.getDvModel(),
        session.getVariables(),
        session.getMetadataProvider());
    session.addRef(sqlRef);
    String physical =
        !Utils.isEmpty(sqlRef.getResolvedTableName())
            ? sqlRef.getResolvedTableName()
            : sqlRef.getObjectName();
    return BvSqlRefResolver.quoteTable(
        session.databaseFor(sqlRef), session.getVariables(), null, physical);
  }

  public static String source(Object sourceNameObj, Object tableNameObj) {
    BvSqlJinjaRenderSession session = requireSession();
    String sourceName = stringArg("source", sourceNameObj);
    String tableName = stringArg("source", tableNameObj);
    BvSqlSource declared = BvSqlRefResolver.findSource(session.getTable(), sourceName, tableName);
    session.addSourceUsage(new BvSqlSource(sourceName, tableName));
    String schema = declared != null ? declared.getSchemaName() : null;
    String physical =
        declared != null && !Utils.isEmpty(declared.getTableName())
            ? declared.getTableName()
            : tableName;
    return BvSqlRefResolver.quoteTable(
        session.databaseForSource(declared), session.getVariables(), schema, physical);
  }

  /** dbt {@code config(...)} is captured at import time; at render it must not leak into SQL. */
  public static String config(Object... ignored) {
    return "";
  }

  public static Object var(Object nameObj, Object... defaultOrEmpty) {
    BvSqlJinjaRenderSession session = requireSession();
    String name = stringArg("var", nameObj);
    String hopValue =
        session.getVariables() != null ? session.getVariables().getVariable(name) : null;
    if (hopValue != null) {
      return hopValue;
    }
    String libraryDefault = session.libraryVar(name);
    if (libraryDefault != null) {
      return libraryDefault;
    }
    if (defaultOrEmpty != null && defaultOrEmpty.length > 0) {
      return unwrapNamed(defaultOrEmpty[0]);
    }
    throw new IllegalArgumentException(
        BaseMessages.getString(PKG, "DbtJinjaBuiltins.Error.MissingVar", name));
  }

  public static boolean is_incremental() {
    return false;
  }

  public static Object run_query(Object... ignored) {
    throw new UnsupportedOperationException(
        BaseMessages.getString(PKG, "DbtJinjaBuiltins.Error.Unsupported", "run_query"));
  }

  private static String firstRest(Object[] rest) {
    if (rest == null || rest.length == 0 || rest[0] == null) {
      return null;
    }
    Object value = unwrapNamed(rest[0]);
    return value != null ? value.toString() : null;
  }

  private static Object unwrapNamed(Object value) {
    if (value instanceof NamedParameter named) {
      return named.getValue();
    }
    return value;
  }

  private static String stringArg(String function, Object value) {
    Object unwrapped = unwrapNamed(value);
    if (unwrapped == null || Utils.isEmpty(unwrapped.toString())) {
      throw new IllegalArgumentException(
          BaseMessages.getString(PKG, "DbtJinjaBuiltins.Error.MissingArg", function));
    }
    return unwrapped.toString();
  }
}
