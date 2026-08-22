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
package org.hopper.edw.datavault.metadata;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.StringUtil;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.transform.dvhashkey.DvHashKeyLogic;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Ensures unknown and invalid (error) sentinel rows exist in hub and link target tables using
 * {@link Database#insertRow(String, IRowMeta, Object[])}.
 */
public final class DvSpecialRecordSupport {

  private DvSpecialRecordSupport() {}

  public static DataVaultConfiguration loadConfiguration(
      IHopMetadataProvider metadataProvider, DataVaultModel model) throws HopException {
    if (model == null) {
      return new DataVaultConfiguration();
    }
    return model.getConfigurationOrDefault();
  }

  public static DatabaseMeta loadTargetDatabase(
      IHopMetadataProvider metadataProvider, DataVaultConfiguration config) throws HopException {
    String targetDbName = config != null ? config.getTargetDatabase() : null;
    if (Utils.isEmpty(targetDbName) || metadataProvider == null) {
      return null;
    }
    DatabaseMeta targetDatabaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(targetDbName);
    if (targetDatabaseMeta == null) {
      throw new HopException("Target database connection not found in metadata: " + targetDbName);
    }
    return targetDatabaseMeta;
  }

  public static int ensureHubSpecialRecords(
      DvHub hub,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      ILoggingObject loggingObject)
      throws HopException {
    if (hub == null || metadataProvider == null || model == null) {
      return 0;
    }

    DataVaultConfiguration config = loadConfiguration(metadataProvider, model);
    DatabaseMeta targetDatabaseMeta = loadTargetDatabase(metadataProvider, config);
    if (targetDatabaseMeta == null) {
      return 0;
    }

    IRowMeta layout = hub.getTargetTableLayout(metadataProvider, variables, model);
    if (layout == null || layout.isEmpty()) {
      return 0;
    }

    String tableName = !Utils.isEmpty(hub.getTableName()) ? hub.getTableName() : hub.getName();
    int inserted = 0;

    try (Database db = new Database(loggingObject, variables, targetDatabaseMeta)) {
      db.connect();
      if (config.isGenerateUnknownRecord()) {
        inserted +=
            ensureHubRecord(
                db, hub, layout, tableName, config, variables, loadDate, SpecialRecordKind.UNKNOWN);
      }
      if (config.isGenerateInvalidRecord()) {
        inserted +=
            ensureHubRecord(
                db, hub, layout, tableName, config, variables, loadDate, SpecialRecordKind.INVALID);
      }
    } catch (Exception e) {
      throw new HopException("Error ensuring special records for hub '" + hub.getName() + "'", e);
    }
    return inserted;
  }

  public static int ensureLinkSpecialRecords(
      DvLink link,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      ILoggingObject loggingObject)
      throws HopException {
    if (link == null || metadataProvider == null || model == null) {
      return 0;
    }

    DataVaultConfiguration config = loadConfiguration(metadataProvider, model);
    DatabaseMeta targetDatabaseMeta = loadTargetDatabase(metadataProvider, config);
    if (targetDatabaseMeta == null) {
      return 0;
    }

    IRowMeta layout = link.getTargetTableLayout(metadataProvider, variables, model);
    if (layout == null || layout.isEmpty()) {
      return 0;
    }

    String tableName = !Utils.isEmpty(link.getTableName()) ? link.getTableName() : link.getName();
    int inserted = 0;

    try (Database db = new Database(loggingObject, variables, targetDatabaseMeta)) {
      db.connect();
      if (config.isGenerateUnknownRecord()) {
        inserted +=
            ensureLinkRecord(
                db,
                link,
                layout,
                tableName,
                model,
                config,
                variables,
                loadDate,
                SpecialRecordKind.UNKNOWN);
      }
      if (config.isGenerateInvalidRecord()) {
        inserted +=
            ensureLinkRecord(
                db,
                link,
                layout,
                tableName,
                model,
                config,
                variables,
                loadDate,
                SpecialRecordKind.INVALID);
      }
    } catch (Exception e) {
      throw new HopException("Error ensuring special records for link '" + link.getName() + "'", e);
    }
    return inserted;
  }

  private static int ensureHubRecord(
      Database db,
      DvHub hub,
      IRowMeta layout,
      String tableName,
      DataVaultConfiguration config,
      IVariables variables,
      Date loadDate,
      SpecialRecordKind kind)
      throws HopException {
    String hashKeyName = resolveHubHashKeyName(hub);
    IValueMeta hashMeta = layout.getValueMeta(0);
    if (!hashKeyName.equals(hashMeta.getName())) {
      hashMeta = layout.searchValueMeta(hashKeyName);
      if (hashMeta == null) {
        throw new HopException(
            "Hash key column '" + hashKeyName + "' not found in hub layout for " + hub.getName());
      }
    }

    Object hashValue = resolveHubHashValue(hub, config, variables, kind, hashMeta.getType());
    if (hashValue == null) {
      return 0;
    }

    if (specialRecordExists(db, variables, tableName, hashMeta, hashValue)) {
      return 0;
    }

    IRowMeta insertLayout = layoutForInsert(layout, config);
    Object[] row = buildHubRow(hub, insertLayout, config, variables, loadDate, kind, hashValue);
    db.insertRow(tableName, insertLayout, row);
    return 1;
  }

  private static int ensureLinkRecord(
      Database db,
      DvLink link,
      IRowMeta layout,
      String tableName,
      DataVaultModel model,
      DataVaultConfiguration config,
      IVariables variables,
      Date loadDate,
      SpecialRecordKind kind)
      throws HopException {
    String linkHashName = resolveLinkHashKeyName(link);
    IValueMeta linkHashMeta = layout.searchValueMeta(linkHashName);
    if (linkHashMeta == null) {
      throw new HopException(
          "Link hash key column '" + linkHashName + "' not found in layout for " + link.getName());
    }

    List<Object> hubHashValues = new ArrayList<>();
    for (String hubName : link.getHubNames()) {
      DvHub hub = model.findHub(hubName, variables, null);
      if (hub == null) {
        throw new HopException(
            "Hub '" + hubName + "' not found in model for link " + link.getName());
      }
      String hubHashCol =
          DvTableResolutionSupport.resolveParticipatingHubHashColumn(
              model, hubName, variables, null);
      if (Utils.isEmpty(hubHashCol)) {
        hubHashCol = resolveHubHashKeyName(hub);
      }
      if (variables != null) {
        hubHashCol = variables.resolve(hubHashCol);
      }
      IValueMeta hubHashMeta = layout.searchValueMeta(hubHashCol);
      if (hubHashMeta == null) {
        throw new HopException(
            "Hub hash column '" + hubHashCol + "' not found in link layout for " + link.getName());
      }
      Object hubHashValue =
          resolveHubHashValue(hub, config, variables, kind, hubHashMeta.getType());
      if (hubHashValue == null) {
        return 0;
      }
      hubHashValues.add(hubHashValue);
    }

    Object linkHashValue =
        resolveLinkHashValue(link, config, variables, kind, linkHashMeta.getType(), hubHashValues);
    if (linkHashValue == null) {
      return 0;
    }

    if (specialRecordExists(db, variables, tableName, linkHashMeta, linkHashValue)) {
      return 0;
    }

    IRowMeta insertLayout = layoutForInsert(layout, config);
    Object[] row =
        buildLinkRow(
            link,
            insertLayout,
            model,
            config,
            variables,
            loadDate,
            kind,
            linkHashValue,
            hubHashValues);
    db.insertRow(tableName, insertLayout, row);
    return 1;
  }

  /**
   * Returns a row layout suitable for {@link Database#insertRow} where the load date column uses
   * {@link IValueMeta#TYPE_DATE} instead of timestamp. Target tables may still be timestamp columns
   * in the database; DATE binds more reliably across JDBC drivers when passing a {@link Date}.
   */
  private static IRowMeta layoutForInsert(IRowMeta layout, DataVaultConfiguration config)
      throws HopException {
    RowMeta insertLayout = new RowMeta();
    String loadDateField = config.getLoadDateField();
    if (Utils.isEmpty(loadDateField)) {
      loadDateField = "LOAD_DATE";
    }
    for (int i = 0; i < layout.size(); i++) {
      IValueMeta meta = layout.getValueMeta(i);
      try {
        if (loadDateField.equals(meta.getName())) {
          insertLayout.addValueMeta(
              ValueMetaFactory.createValueMeta(meta.getName(), IValueMeta.TYPE_DATE));
        } else {
          insertLayout.addValueMeta(ValueMetaFactory.cloneValueMeta(meta, meta.getType()));
        }
      } catch (Exception e) {
        throw new HopException("Error preparing insert layout for column " + meta.getName(), e);
      }
    }
    return insertLayout;
  }

  private static Object[] buildHubRow(
      DvHub hub,
      IRowMeta layout,
      DataVaultConfiguration config,
      IVariables variables,
      Date loadDate,
      SpecialRecordKind kind,
      Object hashValue)
      throws HopException {
    Object[] row = new Object[layout.size()];
    String hashKeyName = resolveHubHashKeyName(hub);
    String businessKeyValue = resolveBusinessKeyValue(config, variables, kind);
    String recordSource = resolveRecordSource(config, variables, kind);

    for (int i = 0; i < layout.size(); i++) {
      IValueMeta meta = layout.getValueMeta(i);
      String name = meta.getName();
      if (name.equals(hashKeyName)) {
        row[i] = hashValue;
      } else if (isBusinessKeyColumn(hub, name)) {
        row[i] = convertToValueMeta(businessKeyValue, meta);
      } else if (isRecordSourceColumn(hub.getRecordSourceFieldName(), config, variables, name)) {
        row[i] = convertToValueMeta(recordSource, meta);
      } else if (isLoadDateColumn(config, name)) {
        row[i] = loadDate;
      } else if (isLoadCycleColumn(config, variables, name)) {
        row[i] = resolveLoadCycleId(variables);
      }
    }
    return row;
  }

  private static Object[] buildLinkRow(
      DvLink link,
      IRowMeta layout,
      DataVaultModel model,
      DataVaultConfiguration config,
      IVariables variables,
      Date loadDate,
      SpecialRecordKind kind,
      Object linkHashValue,
      List<Object> hubHashValues)
      throws HopException {
    Object[] row = new Object[layout.size()];
    String linkHashName = resolveLinkHashKeyName(link);
    String recordSource = resolveRecordSource(config, variables, kind);
    int hubIndex = 0;

    for (int i = 0; i < layout.size(); i++) {
      IValueMeta meta = layout.getValueMeta(i);
      String name = meta.getName();
      if (name.equals(linkHashName)) {
        row[i] = linkHashValue;
      } else if (isParticipatingHubHashColumn(link, model, name)) {
        row[i] = hubHashValues.get(hubIndex++);
      } else if (isRecordSourceColumn(null, config, variables, name)) {
        row[i] = convertToValueMeta(recordSource, meta);
      } else if (isLoadDateColumn(config, name)) {
        row[i] = loadDate;
      } else if (isLoadCycleColumn(config, variables, name)) {
        row[i] = resolveLoadCycleId(variables);
      }
    }
    return row;
  }

  private static boolean specialRecordExists(
      Database db, IVariables variables, String tableName, IValueMeta hashMeta, Object hashValue)
      throws HopException {
    String quotedTable =
        db.getDatabaseMeta().getQuotedSchemaTableCombination(variables, null, tableName);
    String quotedHash = db.getDatabaseMeta().quoteField(hashMeta.getName());
    String sql = "SELECT " + quotedHash + " FROM " + quotedTable + " WHERE " + quotedHash + " = ?";

    IRowMeta params = new RowMeta();
    IValueMeta paramMeta;
    try {
      paramMeta = ValueMetaFactory.cloneValueMeta(hashMeta, hashMeta.getType());
    } catch (Exception e) {
      throw new HopException("Error cloning hash key metadata for lookup", e);
    }
    params.addValueMeta(paramMeta);

    RowMetaAndData result = db.getOneRow(sql, params, new Object[] {hashValue});
    return result != null && result.getData() != null && result.getData().length > 0;
  }

  static Object resolveHubHashValue(
      DvHub hub,
      DataVaultConfiguration config,
      IVariables variables,
      SpecialRecordKind kind,
      int valueMetaType)
      throws HopException {
    String configured =
        kind == SpecialRecordKind.UNKNOWN
            ? config.getUnknownHashKeyValue()
            : config.getInvalidHashKeyValue();
    String fallbackBk = resolveBusinessKeyValue(config, variables, kind);
    return resolveHashValue(
        configured, fallbackBk, hub.getDistinctBusinessKeys(), config, variables, valueMetaType);
  }

  static Object resolveLinkHashValue(
      DvLink link,
      DataVaultConfiguration config,
      IVariables variables,
      SpecialRecordKind kind,
      int valueMetaType,
      List<Object> hubHashValues)
      throws HopException {
    String configured =
        kind == SpecialRecordKind.UNKNOWN
            ? config.getUnknownLinkHashKeyValue()
            : config.getInvalidLinkHashKeyValue();
    if (!Utils.isEmpty(configured)) {
      return resolveHashValue(configured, null, null, config, variables, valueMetaType);
    }
    return computeLinkHashFromHubHashes(hubHashValues, config, valueMetaType);
  }

  private static Object resolveHashValue(
      String configuredValue,
      String fallbackBusinessKeyValue,
      List<BusinessKey> businessKeys,
      DataVaultConfiguration config,
      IVariables variables,
      int valueMetaType)
      throws HopException {
    String resolved =
        variables != null ? variables.resolve(Const.NVL(configuredValue, "")) : configuredValue;
    if (Utils.isEmpty(resolved) && !Utils.isEmpty(fallbackBusinessKeyValue)) {
      return computeBusinessKeyHash(fallbackBusinessKeyValue, businessKeys, config, valueMetaType);
    }
    if (Utils.isEmpty(resolved)) {
      return null;
    }

    HashKeyDataType hdt = config.resolveHashKeyDataType();

    if (valueMetaType == IValueMeta.TYPE_BINARY || hdt == HashKeyDataType.BINARY) {
      byte[] bytes = parseBinaryValue(resolved, variables);
      return normalizeBinaryLength(bytes, config);
    }
    if (hdt == HashKeyDataType.HEX) {
      byte[] bytes = parseBinaryValue(resolved, variables);
      return bytesToHex(normalizeBinaryLength(bytes, config));
    }
    // STRING format: decimal-dash like DvHashKey STRING result
    byte[] bytes = parseBinaryValue(resolved, variables);
    return DvHashKeyLogic.formatHashResult(
        normalizeBinaryLength(bytes, config), HashKeyDataType.STRING);
  }

  private static byte[] normalizeBinaryLength(byte[] bytes, DataVaultConfiguration config) {
    HashAlgorithm algo = config.resolveHashAlgorithm();
    int expected = algo.getDigestLength();
    if (bytes == null) {
      return new byte[expected];
    }
    if (bytes.length == expected) {
      return bytes;
    }
    byte[] normalized = new byte[expected];
    System.arraycopy(bytes, 0, normalized, 0, Math.min(bytes.length, expected));
    return normalized;
  }

  /**
   * Parses configured values including Hop hexadecimal expressions documented as {@code $[hex]}.
   * Also accepts plain continuous hexadecimal (e.g. an MD5 hex string).
   */
  static byte[] parseBinaryValue(String value, IVariables variables) {
    if (value == null) {
      return new byte[0];
    }
    String resolved = variables != null ? variables.resolve(value) : value;
    if (Utils.isEmpty(resolved)) {
      return new byte[0];
    }

    String withHex = StringUtil.substituteHex(resolved);
    byte[] fromHexSubst = withHex.getBytes(StandardCharsets.ISO_8859_1);
    if (!withHex.equals(resolved)) {
      return fromHexSubst;
    }

    String compact = resolved.trim();
    if (compact.matches("(?i)[0-9A-F]+") && compact.length() % 2 == 0) {
      return hexStringToBytes(compact);
    }

    return resolved.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] hexStringToBytes(String hex) {
    int len = hex.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] =
          (byte)
              ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
    }
    return data;
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private static Object computeBusinessKeyHash(
      String businessKeyValue,
      List<BusinessKey> businessKeys,
      DataVaultConfiguration config,
      int valueMetaType)
      throws HopException {
    List<Object> values = new ArrayList<>();
    List<Boolean> binaryFlags = new ArrayList<>();
    boolean hashComposed = config != null && config.isHashUsesComposedBusinessKey();
    if (businessKeys != null && !businessKeys.isEmpty()) {
      for (BusinessKey bk : businessKeys) {
        if (bk == null) {
          continue;
        }
        // Composite: hash over N part slots (sentinel repeated) unless hash-composed mode.
        int partCount = 1;
        if (bk.isComposite() && !hashComposed) {
          partCount = Math.max(bk.sourcePartCount(), 1);
        }
        for (int i = 0; i < partCount; i++) {
          values.add(businessKeyValue);
          binaryFlags.add(false);
        }
      }
    } else {
      values.add(businessKeyValue);
      binaryFlags.add(false);
    }
    Object hash = DvHashKeyLogic.computeHashFromValues(values, binaryFlags, config, null);
    return coerceHashResult(hash, config, valueMetaType);
  }

  private static Object computeLinkHashFromHubHashes(
      List<Object> hubHashValues, DataVaultConfiguration config, int valueMetaType)
      throws HopException {
    List<Object> values = new ArrayList<>();
    List<Boolean> binaryFlags = new ArrayList<>();
    if (hubHashValues != null) {
      for (Object hubHash : hubHashValues) {
        values.add(hubHash);
        binaryFlags.add(hubHash instanceof byte[]);
      }
    }
    Object hash = DvHashKeyLogic.computeHashFromValues(values, binaryFlags, config, null);
    return coerceHashResult(hash, config, valueMetaType);
  }

  private static Object coerceHashResult(
      Object hash, DataVaultConfiguration config, int valueMetaType) {
    if (hash == null) {
      return null;
    }
    HashKeyDataType hdt = config.resolveHashKeyDataType();
    if (valueMetaType == IValueMeta.TYPE_BINARY || hdt == HashKeyDataType.BINARY) {
      if (hash instanceof byte[] bytes) {
        return bytes;
      }
      return parseBinaryValue(hash.toString(), null);
    }
    return hash;
  }

  private static String resolveBusinessKeyValue(
      DataVaultConfiguration config, IVariables variables, SpecialRecordKind kind) {
    String value =
        kind == SpecialRecordKind.UNKNOWN
            ? config.getUnknownBusinessKeyValue()
            : config.getInvalidBusinessKeyValue();
    return variables != null ? variables.resolve(Const.NVL(value, "")) : Const.NVL(value, "");
  }

  private static String resolveRecordSource(
      DataVaultConfiguration config, IVariables variables, SpecialRecordKind kind) {
    String value =
        kind == SpecialRecordKind.UNKNOWN
            ? config.getUnknownRecordSource()
            : config.getInvalidRecordSource();
    if (Utils.isEmpty(value)) {
      value = kind == SpecialRecordKind.UNKNOWN ? "UNKNOWN" : "INVALID";
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static String resolveHubHashKeyName(DvHub hub) {
    String hashKeyName = hub.getHashKeyFieldName();
    if (Utils.isEmpty(hashKeyName)
        && hub.getBusinessKeys() != null
        && !hub.getBusinessKeys().isEmpty()) {
      hashKeyName = hub.getBusinessKeys().get(0).getName() + "_HK";
    }
    return Const.NVL(hashKeyName, "hashkey");
  }

  private static String resolveLinkHashKeyName(DvLink link) {
    return link.resolveLinkHashKeyFieldName();
  }

  private static boolean isBusinessKeyColumn(DvHub hub, String columnName) {
    if (hub.getBusinessKeys() == null) {
      return false;
    }
    for (BusinessKey bk : hub.getBusinessKeys()) {
      if (columnName.equals(bk.getName())) {
        return true;
      }
      String sourceField = bk.getSourceFieldName();
      if (!Utils.isEmpty(sourceField) && columnName.equals(sourceField)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isParticipatingHubHashColumn(
      DvLink link, DataVaultModel model, String columnName) {
    if (link == null || model == null || Utils.isEmpty(columnName)) {
      return false;
    }
    for (String hubName : link.getHubNames()) {
      String hubHashCol =
          DvTableResolutionSupport.resolveParticipatingHubHashColumn(model, hubName, null, null);
      if (Utils.isEmpty(hubHashCol)) {
        DvHub hub = model.findHub(hubName);
        if (hub != null) {
          hubHashCol = resolveHubHashKeyName(hub);
        }
      }
      if (columnName.equals(hubHashCol)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isRecordSourceColumn(
      String tableRecordSourceFieldName,
      DataVaultConfiguration config,
      IVariables variables,
      String columnName) {
    String rs = tableRecordSourceFieldName;
    if (Utils.isEmpty(rs)) {
      rs = config.getRecordSourceField();
    }
    if (Utils.isEmpty(rs)) {
      rs = "RECORD_SOURCE";
    }
    rs = variables != null ? variables.resolve(rs) : rs;
    return columnName.equals(rs);
  }

  private static boolean isLoadDateColumn(DataVaultConfiguration config, String columnName) {
    String loadDateField = config.getLoadDateField();
    if (Utils.isEmpty(loadDateField)) {
      loadDateField = "LOAD_DATE";
    }
    return columnName.equals(loadDateField);
  }

  private static boolean isLoadCycleColumn(
      DataVaultConfiguration config, IVariables variables, String columnName) {
    if (config == null || !config.isStoreLoadCycleId()) {
      return false;
    }
    return DvLoadCycleSupport.isLoadCycleColumn(
        true, config.getLoadCycleIdField(), variables, columnName);
  }

  private static Long resolveLoadCycleId(IVariables variables) {
    return DvLoadCycleSupport.resolveCycleIdFromVariables(variables);
  }

  private static Object convertToValueMeta(String value, IValueMeta meta) throws HopException {
    if (meta.isString()) {
      return value;
    }
    return null;
  }
}
