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
package org.hopper.edw.databases.hopsourcemodel;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.BaseDatabaseMeta;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.database.DatabaseMetaPlugin;
import org.apache.hop.core.database.IDatabase;
import org.apache.hop.core.encryption.Encr;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.IGuiPluginCompositeWidgetsListener;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;

/**
 * Hop connection type for the thin hop-hsm JDBC driver (free SQL against Hop Server source model
 * services).
 *
 * <p>Connection fields map to {@code jdbc:hop-hsm://host:port/[service]}:
 *
 * <ul>
 *   <li><strong>Host</strong> — Hop Server hostname
 *   <li><strong>Port</strong> — Hop Server HTTP port (default {@value #DEFAULT_PORT})
 *   <li><strong>Database name</strong> — optional default Source model service (JDBC schema)
 *   <li><strong>Username / password</strong> — Hop Server credentials (passed as JDBC properties)
 * </ul>
 *
 * <p>Read-only virtual views over named source model services; DDL and writes are not supported.
 */
@DatabaseMetaPlugin(
    type = "HOPSOURCEMODEL",
    typeDescription = "Apache Hop Source Model",
    image = "source-model.svg",
    documentationUrl =
        "https://github.com/ProjectDataHopper/hopper-edw/blob/main/hop-hsm-jdbc/README.md")
@GuiPlugin(id = "GUI-HopSourceModelDatabaseMeta")
public class HopSourceModelDatabaseMeta extends BaseDatabaseMeta
    implements IDatabase, IGuiPluginCompositeWidgetsListener {

  /** Common Hop Server listen port used in tutorials and hop-server defaults. */
  public static final int DEFAULT_PORT = 8080;

  public static final String DRIVER_CLASS = "org.hopper.edw.hsm.jdbc.HopHsmJdbcDriver";

  public static final String JDBC_PREFIX = "jdbc:hop-hsm://";

  public static final String ID_AUTHENTICATION_TYPE = "authenticationType";
  public static final String ID_OAUTH_TOKEN_URL = "oauthTokenUrl";
  public static final String ID_OAUTH_GRANT = "oauthGrant";
  public static final String ID_OAUTH_CLIENT_ID = "oauthClientId";
  public static final String ID_OAUTH_CLIENT_SECRET = "oauthClientSecret";
  public static final String ID_OAUTH_SCOPE = "oauthScope";
  public static final String ID_OAUTH_REFRESH_TOKEN = "oauthRefreshToken";

  public static final String PROP_AUTH_TYPE = "authType";
  public static final String PROP_OAUTH_TOKEN_URL = "oauthTokenUrl";
  public static final String PROP_OAUTH_GRANT = "oauthGrant";
  public static final String PROP_OAUTH_CLIENT_ID = "oauthClientId";
  public static final String PROP_OAUTH_CLIENT_SECRET = "oauthClientSecret";
  public static final String PROP_OAUTH_SCOPE = "oauthScope";
  public static final String PROP_OAUTH_REFRESH_TOKEN = "oauthRefreshToken";

  @Getter
  @Setter
  @GuiWidgetElement(
      id = ID_AUTHENTICATION_TYPE,
      order = "10",
      parentId = DatabaseMeta.GUI_PLUGIN_ELEMENT_PARENT_ID,
      type = GuiElementType.COMBO,
      variables = false,
      comboValuesMethod = "getAuthenticationTypeNames",
      label = "i18n::HopSourceModelDatabaseMeta.label.AuthenticationType",
      toolTip = "i18n::HopSourceModelDatabaseMeta.tooltip.AuthenticationType")
  @HopMetadataProperty(enumNameWhenNotFound = "BASIC")
  private HopHsmAuthType authenticationType = HopHsmAuthType.BASIC;

  @Getter
  @Setter
  @GuiWidgetElement(
      id = ID_OAUTH_TOKEN_URL,
      order = "20",
      parentId = DatabaseMeta.GUI_PLUGIN_ELEMENT_PARENT_ID,
      type = GuiElementType.TEXT,
      label = "i18n::HopSourceModelDatabaseMeta.label.OauthTokenUrl",
      toolTip = "i18n::HopSourceModelDatabaseMeta.tooltip.OauthTokenUrl")
  @HopMetadataProperty
  private String oauthTokenUrl;

  @Getter
  @Setter
  @GuiWidgetElement(
      id = ID_OAUTH_GRANT,
      order = "30",
      parentId = DatabaseMeta.GUI_PLUGIN_ELEMENT_PARENT_ID,
      type = GuiElementType.COMBO,
      variables = false,
      comboValuesMethod = "getOauthGrantNames",
      label = "i18n::HopSourceModelDatabaseMeta.label.OauthGrant",
      toolTip = "i18n::HopSourceModelDatabaseMeta.tooltip.OauthGrant")
  @HopMetadataProperty
  private String oauthGrant = "client_credentials";

  @Getter
  @Setter
  @GuiWidgetElement(
      id = ID_OAUTH_CLIENT_ID,
      order = "40",
      parentId = DatabaseMeta.GUI_PLUGIN_ELEMENT_PARENT_ID,
      type = GuiElementType.TEXT,
      label = "i18n::HopSourceModelDatabaseMeta.label.OauthClientId",
      toolTip = "i18n::HopSourceModelDatabaseMeta.tooltip.OauthClientId")
  @HopMetadataProperty
  private String oauthClientId;

  @Getter
  @Setter
  @GuiWidgetElement(
      id = ID_OAUTH_CLIENT_SECRET,
      order = "50",
      parentId = DatabaseMeta.GUI_PLUGIN_ELEMENT_PARENT_ID,
      type = GuiElementType.TEXT,
      password = true,
      label = "i18n::HopSourceModelDatabaseMeta.label.OauthClientSecret",
      toolTip = "i18n::HopSourceModelDatabaseMeta.tooltip.OauthClientSecret")
  @HopMetadataProperty(password = true)
  private String oauthClientSecret;

  @Getter
  @Setter
  @GuiWidgetElement(
      id = ID_OAUTH_SCOPE,
      order = "60",
      parentId = DatabaseMeta.GUI_PLUGIN_ELEMENT_PARENT_ID,
      type = GuiElementType.TEXT,
      label = "i18n::HopSourceModelDatabaseMeta.label.OauthScope",
      toolTip = "i18n::HopSourceModelDatabaseMeta.tooltip.OauthScope")
  @HopMetadataProperty
  private String oauthScope;

  @Getter
  @Setter
  @GuiWidgetElement(
      id = ID_OAUTH_REFRESH_TOKEN,
      order = "70",
      parentId = DatabaseMeta.GUI_PLUGIN_ELEMENT_PARENT_ID,
      type = GuiElementType.TEXT,
      password = true,
      label = "i18n::HopSourceModelDatabaseMeta.label.OauthRefreshToken",
      toolTip = "i18n::HopSourceModelDatabaseMeta.tooltip.OauthRefreshToken")
  @HopMetadataProperty(password = true)
  private String oauthRefreshToken;

  @Override
  public int[] getAccessTypeList() {
    return new int[] {DatabaseMeta.TYPE_ACCESS_NATIVE};
  }

  @Override
  public int getDefaultDatabasePort() {
    if (getAccessType() == DatabaseMeta.TYPE_ACCESS_NATIVE) {
      return DEFAULT_PORT;
    }
    return -1;
  }

  @Override
  public String getDriverClass() {
    return DRIVER_CLASS;
  }

  @Override
  public String getURL(String hostname, String port, String databaseName) {
    StringBuilder url = new StringBuilder(JDBC_PREFIX);
    url.append(Utils.isEmpty(hostname) ? "localhost" : hostname);
    if (!Utils.isEmpty(port)) {
      url.append(':').append(port);
    }
    // Optional default Source model service (= JDBC schema)
    if (!Utils.isEmpty(databaseName)) {
      url.append('/').append(databaseName);
    }
    return url.toString();
  }

  /**
   * Auth settings go in JDBC properties, not the URL: tokens are large and would leak in logs and
   * the connection string shown on the dialog.
   */
  @Override
  public Properties getConnectionProperties(IVariables variables) {
    Properties properties = new Properties();
    HopHsmAuthType type = authenticationType != null ? authenticationType : HopHsmAuthType.BASIC;
    properties.put(PROP_AUTH_TYPE, type.jdbcValue());
    if (type.isOauth2()) {
      putIfFilled(properties, PROP_OAUTH_TOKEN_URL, resolve(variables, oauthTokenUrl));
      putIfFilled(
          properties,
          PROP_OAUTH_GRANT,
          resolve(variables, Utils.isEmpty(oauthGrant) ? "client_credentials" : oauthGrant));
      putIfFilled(properties, PROP_OAUTH_CLIENT_ID, resolve(variables, oauthClientId));
      putIfFilled(properties, PROP_OAUTH_CLIENT_SECRET, decrypt(variables, oauthClientSecret));
      putIfFilled(properties, PROP_OAUTH_SCOPE, resolve(variables, oauthScope));
      putIfFilled(properties, PROP_OAUTH_REFRESH_TOKEN, decrypt(variables, oauthRefreshToken));
    }
    return properties;
  }

  /**
   * Combo values for {@link #authenticationType}. Must return {@code List} — {@code
   * GuiCompositeWidgets} casts the reflected result.
   */
  public List<String> getAuthenticationTypeNames(
      ILogChannel log, IHopMetadataProvider metadataProvider) {
    return List.of(
        HopHsmAuthType.BASIC.name(), HopHsmAuthType.BEARER.name(), HopHsmAuthType.OAUTH2.name());
  }

  /**
   * Combo values for {@link #oauthGrant}. Must return {@code List} — {@code GuiCompositeWidgets}
   * casts the reflected result.
   */
  public List<String> getOauthGrantNames(ILogChannel log, IHopMetadataProvider metadataProvider) {
    return List.of("client_credentials", "refresh_token");
  }

  @Override
  public void widgetsCreated(GuiCompositeWidgets compositeWidgets) {
    // Values are not set yet.
  }

  @Override
  public void widgetsPopulated(GuiCompositeWidgets compositeWidgets) {
    hideFieldsThatDoNotApply(compositeWidgets);
  }

  @Override
  public void widgetModified(
      GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
    hideFieldsThatDoNotApply(compositeWidgets);
  }

  @Override
  public void persistContents(GuiCompositeWidgets compositeWidgets) {
    // Dialog reads widgets back itself.
  }

  private void hideFieldsThatDoNotApply(GuiCompositeWidgets compositeWidgets) {
    HopHsmAuthType type = readAuthenticationType(compositeWidgets);
    Set<String> hidden = new HashSet<>();
    if (!type.isOauth2()) {
      hidden.add(ID_OAUTH_TOKEN_URL);
      hidden.add(ID_OAUTH_GRANT);
      hidden.add(ID_OAUTH_CLIENT_ID);
      hidden.add(ID_OAUTH_CLIENT_SECRET);
      hidden.add(ID_OAUTH_SCOPE);
      hidden.add(ID_OAUTH_REFRESH_TOKEN);
    }
    compositeWidgets.setWidgetsHidden(this, hidden);
  }

  private HopHsmAuthType readAuthenticationType(GuiCompositeWidgets compositeWidgets) {
    Control control = compositeWidgets.getWidgetsMap().get(ID_AUTHENTICATION_TYPE);
    if (control instanceof Combo combo) {
      try {
        return HopHsmAuthType.valueOf(combo.getText());
      } catch (IllegalArgumentException e) {
        // nothing selected yet
      }
    }
    return authenticationType != null ? authenticationType : HopHsmAuthType.BASIC;
  }

  private void putIfFilled(Properties properties, String name, String value) {
    if (!Utils.isEmpty(value)) {
      properties.put(name, value.trim());
    }
  }

  private String resolve(IVariables variables, String value) {
    if (Utils.isEmpty(value)) {
      return value;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private String decrypt(IVariables variables, String password) {
    if (Utils.isEmpty(password)) {
      return password;
    }
    String resolved = resolve(variables, password);
    return Encr.decryptPasswordOptionallyEncrypted(resolved);
  }

  @Override
  public String getExtraOptionIndicator() {
    return "?";
  }

  @Override
  public String getExtraOptionSeparator() {
    return "&";
  }

  @Override
  public String getExtraOptionValueSeparator() {
    return "=";
  }

  @Override
  public String getExtraOptionsHelpText() {
    return "https://github.com/ProjectDataHopper/hopper-edw/blob/main/hop-hsm-jdbc/README.md";
  }

  @Override
  public void addDefaultOptions() {
    setSupportsBooleanDataType(true);
    setSupportsTimestampDataType(true);
  }

  @Override
  public boolean isSupportsBooleanDataType() {
    return true;
  }

  @Override
  public boolean isSupportsTimestampDataType() {
    return true;
  }

  @Override
  public boolean isSupportsSchemas() {
    // Each Source model service is exposed as a JDBC schema.
    return true;
  }

  @Override
  public boolean isSupportsCatalogs() {
    return false;
  }

  @Override
  public boolean isSupportsViews() {
    // JSON / pipeline feeds appear as VIEW in DatabaseMetaData.
    return true;
  }

  @Override
  public boolean isSupportsSynonyms() {
    return false;
  }

  @Override
  public boolean isSupportsTransactions() {
    return false;
  }

  @Override
  public boolean isSupportsBitmapIndex() {
    return false;
  }

  @Override
  public boolean isSupportsSequences() {
    return false;
  }

  @Override
  public boolean isSupportsAutoInc() {
    return false;
  }

  @Override
  public boolean isSupportsAutoGeneratedKeys() {
    return false;
  }

  @Override
  public boolean isFetchSizeSupported() {
    return false;
  }

  @Override
  public boolean isRequiringTransactionsOnQueries() {
    return false;
  }

  @Override
  public boolean isSupportsGetBlob() {
    return false;
  }

  @Override
  public boolean isSupportsSetCharacterStream() {
    return false;
  }

  @Override
  public boolean isSupportsBatchUpdates() {
    return false;
  }

  @Override
  public boolean IsSupportsErrorHandlingOnBatchUpdates() {
    return false;
  }

  @Override
  public String getStartQuote() {
    return "\"";
  }

  @Override
  public String getEndQuote() {
    return "\"";
  }

  @Override
  public String getLimitClause(int nrRows) {
    return " LIMIT " + nrRows;
  }

  @Override
  public boolean isSupportsPreparedStatementMetadataRetrieval() {
    // hop-hsm PreparedStatement.getMetaData() is null; Hop would cache an empty layout.
    return false;
  }

  @Override
  public String getSqlQueryFields(String tableName) {
    // LIMIT 0 yields no pipeline rows, so query JSON has no column metadata for tables.
    return "SELECT * FROM " + tableName + " LIMIT 1";
  }

  @Override
  public String getSqlTableExists(String tableName) {
    return getSqlQueryFields(tableName);
  }

  @Override
  public String getSqlColumnExists(String columnname, String tableName) {
    return "SELECT " + columnname + " FROM " + tableName + " LIMIT 1";
  }

  @Override
  public String getAddColumnStatement(
      String tableName,
      IValueMeta v,
      String tk,
      boolean useAutoIncrement,
      String pk,
      boolean semicolon) {
    // Read-only virtualization over Hop Server source models.
    return null;
  }

  @Override
  public String getModifyColumnStatement(
      String tableName,
      IValueMeta v,
      String tk,
      boolean useAutoIncrement,
      String pk,
      boolean semicolon) {
    return null;
  }

  @Override
  public String getDropColumnStatement(
      String tableName,
      IValueMeta v,
      String tk,
      boolean useAutoIncrement,
      String pk,
      boolean semicolon) {
    return null;
  }

  @Override
  public String getFieldDefinition(
      IValueMeta v,
      String tk,
      String pk,
      boolean useAutoIncrement,
      boolean addFieldName,
      boolean addCr) {
    // DDL is not used against hop-hsm; keep a reasonable Calcite-style mapping for tooling.
    StringBuilder retval = new StringBuilder();
    String fieldname = v.getName();
    if (addFieldName) {
      retval.append(fieldname).append(' ');
    }

    switch (v.getType()) {
      case IValueMeta.TYPE_TIMESTAMP, IValueMeta.TYPE_DATE -> retval.append("TIMESTAMP");
      case IValueMeta.TYPE_BOOLEAN -> retval.append("BOOLEAN");
      case IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_BIGNUMBER -> {
        int length = v.getLength();
        int precision = v.getPrecision();
        if (length > 0) {
          if (precision > 0) {
            retval.append("DECIMAL(").append(length).append(',').append(precision).append(')');
          } else {
            retval.append("DECIMAL(").append(length).append(')');
          }
        } else {
          retval.append("DOUBLE");
        }
      }
      case IValueMeta.TYPE_INTEGER -> {
        int length = v.getLength();
        if (length > 0 && length <= 9) {
          retval.append("INTEGER");
        } else {
          retval.append("BIGINT");
        }
      }
      case IValueMeta.TYPE_STRING -> {
        int length = v.getLength();
        if (length < 1 || length >= DatabaseMeta.CLOB_LENGTH) {
          retval.append("VARCHAR");
        } else {
          retval.append("VARCHAR(").append(length).append(')');
        }
      }
      case IValueMeta.TYPE_BINARY -> retval.append("VARBINARY");
      default -> retval.append("VARCHAR");
    }

    if (addCr) {
      retval.append(Const.CR);
    }
    return retval.toString();
  }

  @Override
  public String[] getReservedWords() {
    return new String[] {
      "ALL",
      "AND",
      "AS",
      "ASC",
      "BETWEEN",
      "BY",
      "CASE",
      "CAST",
      "CROSS",
      "DESC",
      "DISTINCT",
      "ELSE",
      "END",
      "EXCEPT",
      "EXISTS",
      "FALSE",
      "FROM",
      "FULL",
      "GROUP",
      "HAVING",
      "IN",
      "INNER",
      "INTERSECT",
      "IS",
      "JOIN",
      "LEFT",
      "LIKE",
      "LIMIT",
      "NOT",
      "NULL",
      "ON",
      "OR",
      "ORDER",
      "OUTER",
      "RIGHT",
      "SELECT",
      "THEN",
      "TRUE",
      "UNION",
      "USING",
      "WHEN",
      "WHERE",
      "WITH"
    };
  }
}
