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
package org.apache.hop.datavault.resourcedefinition;

import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;

/**
 * Builds plain-language validation findings: axis, what was compared, what was found, why it
 * matters — before severity/kind labels.
 */
public final class ValidationFindingFormatter {

  private static final Class<?> PKG = ValidationFindingFormatter.class;

  private ValidationFindingFormatter() {}

  public static String format(String axis, String compared, String found, String whyItMatters) {
    StringBuilder body = new StringBuilder();
    appendLine(body, "ValidationFindingFormatter.Axis", axis);
    appendLine(body, "ValidationFindingFormatter.Compared", compared);
    appendLine(body, "ValidationFindingFormatter.Found", found);
    if (!Utils.isEmpty(whyItMatters)) {
      appendLine(body, "ValidationFindingFormatter.Why", whyItMatters);
    }
    return body.toString().trim();
  }

  public static String baselineContractMissing(
      String recordKey, String baselineVersionTag, boolean workingPresent) {
    String baseline =
        Utils.isEmpty(baselineVersionTag)
            ? BaseMessages.getString(PKG, "ValidationFindingFormatter.Baseline.UnnamedVersion")
            : baselineVersionTag.trim();
    String found =
        workingPresent
            ? BaseMessages.getString(
                PKG,
                "ValidationFindingFormatter.BaselineMissing.FoundWorkingPresent",
                recordKey,
                baseline)
            : BaseMessages.getString(
                PKG,
                "ValidationFindingFormatter.BaselineMissing.FoundNeither",
                recordKey,
                baseline);
    return format(
        BaseMessages.getString(PKG, "ValidationFindingFormatter.Axis.BaselineVersion"),
        BaseMessages.getString(
            PKG, "ValidationFindingFormatter.BaselineMissing.Compared", baseline),
        found,
        BaseMessages.getString(PKG, "ValidationFindingFormatter.BaselineMissing.Why", baseline));
  }

  public static String workingContractMissing(String recordKey) {
    return format(
        BaseMessages.getString(PKG, "ValidationFindingFormatter.Axis.WorkingCatalog"),
        BaseMessages.getString(PKG, "ValidationFindingFormatter.WorkingMissing.Compared"),
        BaseMessages.getString(PKG, "ValidationFindingFormatter.WorkingMissing.Found", recordKey),
        BaseMessages.getString(PKG, "ValidationFindingFormatter.WorkingMissing.Why"));
  }

  public static String bothContractsMissing(String recordKey, String baselineVersionTag) {
    String baseline =
        Utils.isEmpty(baselineVersionTag)
            ? BaseMessages.getString(PKG, "ValidationFindingFormatter.Baseline.UnnamedVersion")
            : baselineVersionTag.trim();
    return format(
        BaseMessages.getString(PKG, "ValidationFindingFormatter.Axis.BaselineVersion"),
        BaseMessages.getString(PKG, "ValidationFindingFormatter.BothMissing.Compared", baseline),
        BaseMessages.getString(PKG, "ValidationFindingFormatter.BothMissing.Found", recordKey),
        BaseMessages.getString(PKG, "ValidationFindingFormatter.BothMissing.Why"));
  }

  public static String liveSourceUnavailable(String recordKey, String detail) {
    return format(
        BaseMessages.getString(PKG, "ValidationFindingFormatter.Axis.LiveSource"),
        BaseMessages.getString(
            PKG, "ValidationFindingFormatter.LiveUnavailable.Compared", recordKey),
        Const.NVL(
            detail,
            BaseMessages.getString(PKG, "ValidationFindingFormatter.LiveUnavailable.FoundGeneric")),
        BaseMessages.getString(PKG, "ValidationFindingFormatter.LiveUnavailable.Why"));
  }

  public static String targetDdlRequired(
      String tableName, String modelName, int ddlCount, String preview) {
    return format(
        BaseMessages.getString(PKG, "ValidationFindingFormatter.Axis.TargetDatabase"),
        BaseMessages.getString(
            PKG,
            "ValidationFindingFormatter.TargetDdl.Compared",
            Const.NVL(tableName, "?"),
            Const.NVL(modelName, "?")),
        BaseMessages.getString(
            PKG,
            "ValidationFindingFormatter.TargetDdl.Found",
            Integer.toString(Math.max(0, ddlCount)),
            Const.NVL(preview, "")),
        BaseMessages.getString(PKG, "ValidationFindingFormatter.TargetDdl.Why"));
  }

  public static String targetDdlCheckFailed(String tableName, String errorDetail) {
    return format(
        BaseMessages.getString(PKG, "ValidationFindingFormatter.Axis.TargetDatabase"),
        BaseMessages.getString(
            PKG, "ValidationFindingFormatter.TargetDdlFailed.Compared", Const.NVL(tableName, "?")),
        BaseMessages.getString(
            PKG, "ValidationFindingFormatter.TargetDdlFailed.Found", Const.NVL(errorDetail, "?")),
        BaseMessages.getString(PKG, "ValidationFindingFormatter.TargetDdlFailed.Why"));
  }

  public static String fieldDiff(String fieldName, String details, String axisHint) {
    return format(
        Utils.isEmpty(axisHint)
            ? BaseMessages.getString(PKG, "ValidationFindingFormatter.Axis.SourceContract")
            : axisHint,
        BaseMessages.getString(
            PKG, "ValidationFindingFormatter.FieldDiff.Compared", Const.NVL(fieldName, "?")),
        BaseMessages.getString(
            PKG,
            "ValidationFindingFormatter.FieldDiff.Found",
            Const.NVL(fieldName, "?"),
            Const.NVL(details, "")),
        BaseMessages.getString(PKG, "ValidationFindingFormatter.FieldDiff.Why"));
  }

  /** One-line summary for list rows / compact log (Found: line when present). */
  public static String shortTitle(String structuredMessage) {
    StructuredFinding parsed = parseStructured(structuredMessage);
    if (!Utils.isEmpty(parsed.found())) {
      return parsed.found();
    }
    if (Utils.isEmpty(structuredMessage)) {
      return "";
    }
    int nl = structuredMessage.indexOf('\n');
    return nl < 0 ? structuredMessage.trim() : structuredMessage.substring(0, nl).trim();
  }

  /**
   * Parses Axis/Compared/Found/Why structured finding bodies produced by {@link #format}. Labels
   * are matched in English for report layout; unknown messages fall back to the raw body as {@code
   * found}.
   */
  public static StructuredFinding parseStructured(String structuredMessage) {
    if (Utils.isEmpty(structuredMessage)) {
      return StructuredFinding.empty();
    }
    String axis = "";
    String compared = "";
    String found = "";
    String why = "";
    String section = null;
    StringBuilder buffer = new StringBuilder();
    for (String rawLine : structuredMessage.split("\\R", -1)) {
      String trimmed = rawLine != null ? rawLine.trim() : "";
      int colon = trimmed.indexOf(':');
      String label = colon > 0 ? trimmed.substring(0, colon).trim() : "";
      String value = colon > 0 ? trimmed.substring(colon + 1).trim() : "";
      String nextSection = null;
      if (equalsIgnoreCaseAny(label, "Axis", "What we checked")) {
        nextSection = "axis";
      } else if (equalsIgnoreCaseAny(label, "Compared")) {
        nextSection = "compared";
      } else if (equalsIgnoreCaseAny(label, "Found")) {
        nextSection = "found";
      } else if (equalsIgnoreCaseAny(label, "Why it matters", "Why", "What to do")) {
        nextSection = "why";
      }
      if (nextSection != null) {
        if (section != null) {
          String text = buffer.toString().trim();
          switch (section) {
            case "axis" -> axis = text;
            case "compared" -> compared = text;
            case "found" -> found = text;
            case "why" -> why = text;
            default -> {
              /* ignore */
            }
          }
        }
        section = nextSection;
        buffer = new StringBuilder(value);
      } else if (section != null) {
        if (!buffer.isEmpty() && !trimmed.isEmpty()) {
          buffer.append(' ');
        }
        buffer.append(trimmed);
      }
    }
    if (section != null) {
      String text = buffer.toString().trim();
      switch (section) {
        case "axis" -> axis = text;
        case "compared" -> compared = text;
        case "found" -> found = text;
        case "why" -> why = text;
        default -> {
          /* ignore */
        }
      }
    }
    if (Utils.isEmpty(found)
        && Utils.isEmpty(why)
        && Utils.isEmpty(axis)
        && Utils.isEmpty(compared)) {
      return new StructuredFinding("", "", structuredMessage.trim(), "");
    }
    return new StructuredFinding(axis, compared, found, why);
  }

  private static boolean equalsIgnoreCaseAny(String value, String... options) {
    if (value == null || options == null) {
      return false;
    }
    for (String option : options) {
      if (option != null && value.equalsIgnoreCase(option)) {
        return true;
      }
    }
    return false;
  }

  /** Human report title for an issue kind (no internal jargon). */
  public static String humanTitle(ValidationReport.IssueKind kind) {
    if (kind == null) {
      return BaseMessages.getString(PKG, "ValidationFindingFormatter.Kind.Unknown");
    }
    String key = "ValidationFindingFormatter.Kind." + kind.name();
    String translated = BaseMessages.getString(PKG, key);
    // BaseMessages returns the key itself when missing in some setups; fall back to enum name.
    if (Utils.isEmpty(translated) || translated.equals(key)) {
      return kind.name().replace('_', ' ').toLowerCase();
    }
    return translated;
  }

  /** Human description of what this run compared (summary header). Avoids the word "Axis". */
  public static String describeWhatWeCompared(
      SchemaCompareMode mode, String baselineVersion, String catalogVersion) {
    if (mode == SchemaCompareMode.WORKING_VS_VERSION) {
      String tag =
          !Utils.isEmpty(baselineVersion)
              ? baselineVersion.trim()
              : !Utils.isEmpty(catalogVersion) ? catalogVersion.trim() : "?";
      return BaseMessages.getString(
          PKG, "ValidationFindingFormatter.Summary.What.WorkingVsVersion", tag);
    }
    if (mode == SchemaCompareMode.VERSION_VS_VERSION) {
      return BaseMessages.getString(
          PKG,
          "ValidationFindingFormatter.Summary.What.VersionVsVersion",
          Const.NVL(baselineVersion, "?"),
          Const.NVL(catalogVersion, "?"));
    }
    // LIVE_SOURCE
    if (!Utils.isEmpty(catalogVersion) || !Utils.isEmpty(baselineVersion)) {
      String tag = !Utils.isEmpty(catalogVersion) ? catalogVersion.trim() : baselineVersion.trim();
      return BaseMessages.getString(
          PKG, "ValidationFindingFormatter.Summary.What.LiveVsVersion", tag);
    }
    return BaseMessages.getString(PKG, "ValidationFindingFormatter.Summary.What.LiveVsWorking");
  }

  public static String describeExpectedSide(
      SchemaCompareMode mode, String baselineVersion, String catalogVersion) {
    if (mode == SchemaCompareMode.LIVE_SOURCE) {
      if (!Utils.isEmpty(catalogVersion) || !Utils.isEmpty(baselineVersion)) {
        String tag =
            !Utils.isEmpty(catalogVersion) ? catalogVersion.trim() : baselineVersion.trim();
        return BaseMessages.getString(
            PKG, "ValidationFindingFormatter.Summary.Expected.Version", tag);
      }
      return BaseMessages.getString(PKG, "ValidationFindingFormatter.Summary.Expected.Working");
    }
    if (mode == SchemaCompareMode.VERSION_VS_VERSION) {
      return BaseMessages.getString(
          PKG,
          "ValidationFindingFormatter.Summary.Expected.Version",
          Const.NVL(baselineVersion, "?"));
    }
    // WORKING_VS_VERSION
    if (!Utils.isEmpty(baselineVersion) || !Utils.isEmpty(catalogVersion)) {
      String tag = !Utils.isEmpty(baselineVersion) ? baselineVersion.trim() : catalogVersion.trim();
      return BaseMessages.getString(
          PKG, "ValidationFindingFormatter.Summary.Expected.Version", tag);
    }
    return BaseMessages.getString(PKG, "ValidationFindingFormatter.Summary.Expected.Working");
  }

  public static String describeActualSide(
      SchemaCompareMode mode, String baselineVersion, String catalogVersion) {
    if (mode == SchemaCompareMode.LIVE_SOURCE) {
      return BaseMessages.getString(PKG, "ValidationFindingFormatter.Summary.Actual.Live");
    }
    if (mode == SchemaCompareMode.VERSION_VS_VERSION) {
      return BaseMessages.getString(
          PKG, "ValidationFindingFormatter.Summary.Actual.Version", Const.NVL(catalogVersion, "?"));
    }
    return BaseMessages.getString(PKG, "ValidationFindingFormatter.Summary.Actual.Working");
  }

  public static String describeCompareContext(
      SchemaCompareMode mode, String baselineVersion, String catalogVersion) {
    String modeName =
        mode != null
            ? mode.name()
            : BaseMessages.getString(PKG, "ValidationFindingFormatter.Mode.Unknown");
    String baseline;
    if (!Utils.isEmpty(baselineVersion)) {
      baseline =
          BaseMessages.getString(
              PKG, "ValidationFindingFormatter.Context.BaselineTag", baselineVersion.trim());
    } else if (!Utils.isEmpty(catalogVersion) && mode == SchemaCompareMode.LIVE_SOURCE) {
      baseline =
          BaseMessages.getString(
              PKG, "ValidationFindingFormatter.Context.BaselineTag", catalogVersion.trim());
    } else {
      baseline = BaseMessages.getString(PKG, "ValidationFindingFormatter.Context.WorkingCatalog");
    }
    return BaseMessages.getString(
        PKG, "ValidationFindingFormatter.Context.Line", modeName, baseline);
  }

  /**
   * Parsed structured finding for human reports (Axis/Compared are optional context; reports prefer
   * found/why).
   */
  public record StructuredFinding(String axis, String compared, String found, String why) {
    public static StructuredFinding empty() {
      return new StructuredFinding("", "", "", "");
    }
  }

  private static void appendLine(StringBuilder body, String key, String value) {
    if (Utils.isEmpty(value)) {
      return;
    }
    if (!body.isEmpty()) {
      body.append(Const.CR);
    }
    body.append(BaseMessages.getString(PKG, key, value));
  }
}
