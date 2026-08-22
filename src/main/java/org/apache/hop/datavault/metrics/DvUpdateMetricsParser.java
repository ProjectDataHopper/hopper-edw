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
package org.apache.hop.datavault.metrics;

import java.util.Optional;
import org.apache.hop.core.util.Utils;

/** Parses generated Data Vault update pipeline names into table type, table name, and source. */
public final class DvUpdateMetricsParser {

  private static final String[] TABLE_TYPE_PREFIXES = {"hub-", "link-", "sat-", "sts-"};

  private static final String[][] BUSINESS_VAULT_TYPE_PREFIXES = {
    {"bv-scd2-", "scd2"},
    {"bv-pit-", "pit"},
    {"bv-biz-", "biz"},
  };

  private static final String[][] DIMENSIONAL_TYPE_PREFIXES = {
    {"dm-dim-", "dimension"},
    {"dm-junk-", "junk_dimension"},
    {"dm-bridge-", "bridge"},
    {"dm-fact-", "fact"},
  };

  private DvUpdateMetricsParser() {}

  public static Optional<ParsedPipeline> parse(String pipelineName) {
    if (Utils.isEmpty(pipelineName)) {
      return Optional.empty();
    }
    if (pipelineName.startsWith(DvUpdateMetricsConstants.ORCHESTRATOR_NAME_PREFIX)) {
      return Optional.empty();
    }

    Optional<ParsedPipeline> dimensional = parseDimensionalPipeline(pipelineName);
    if (dimensional.isPresent()) {
      return dimensional;
    }

    Optional<ParsedPipeline> businessVault = parseBusinessVaultPipeline(pipelineName);
    if (businessVault.isPresent()) {
      return businessVault;
    }

    String tableType = null;
    String remainder = pipelineName;
    for (String prefix : TABLE_TYPE_PREFIXES) {
      if (pipelineName.startsWith(prefix)) {
        tableType = prefix.substring(0, prefix.length() - 1);
        remainder = pipelineName.substring(prefix.length());
        break;
      }
    }
    if (tableType == null) {
      return Optional.empty();
    }

    int separator = remainder.indexOf('-');
    if (separator <= 0 || separator >= remainder.length() - 1) {
      return Optional.empty();
    }

    String tableName = remainder.substring(0, separator);
    String sourcePart = remainder.substring(separator + 1);
    String sourceName = resolveSourceName(sourcePart);

    return Optional.of(new ParsedPipeline(tableType, tableName, sourceName));
  }

  private static Optional<ParsedPipeline> parseBusinessVaultPipeline(String pipelineName) {
    for (String[] prefixAndType : BUSINESS_VAULT_TYPE_PREFIXES) {
      String prefix = prefixAndType[0];
      String tableType = prefixAndType[1];
      if (pipelineName.startsWith(prefix)) {
        String tableName = pipelineName.substring(prefix.length());
        if (Utils.isEmpty(tableName)) {
          return Optional.empty();
        }
        return Optional.of(new ParsedPipeline(tableType, tableName, ""));
      }
    }
    return Optional.empty();
  }

  private static Optional<ParsedPipeline> parseDimensionalPipeline(String pipelineName) {
    for (String[] prefixAndType : DIMENSIONAL_TYPE_PREFIXES) {
      String prefix = prefixAndType[0];
      String tableType = prefixAndType[1];
      if (pipelineName.startsWith(prefix)) {
        String tableName = pipelineName.substring(prefix.length());
        if (Utils.isEmpty(tableName)) {
          return Optional.empty();
        }
        return Optional.of(new ParsedPipeline(tableType, tableName, ""));
      }
    }
    return Optional.empty();
  }

  private static String resolveSourceName(String sourcePart) {
    int underscore = sourcePart.lastIndexOf('_');
    if (underscore > 0 && underscore < sourcePart.length() - 1) {
      return sourcePart.substring(underscore + 1);
    }
    return sourcePart;
  }

  public record ParsedPipeline(String tableType, String tableName, String sourceName) {}
}
