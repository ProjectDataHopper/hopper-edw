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
package org.hopper.edw.datavault.metadata.dimensional.service;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataCategory;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.edw.datavault.metadata.sourcemodel.service.SourceModelService;

/**
 * Named Hop Server endpoint that maps a public JDBC schema name to a server-side {@code .hdm}.
 *
 * <p>Listed beside {@link SourceModelService} entries by {@code /hop/sourceModelData}. Clients use
 * {@code jdbc:hop-hsm:} and never see the model path.
 */
@HopMetadata(
    key = "dimensional-model-service",
    name = "i18n::DimensionalModelService.name",
    description = "i18n::DimensionalModelService.description",
    image = "dimensional-model.svg",
    category = HopMetadataCategory.SERVERS,
    documentationUrl = "/dimensional-modeler-overview.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
public class DimensionalModelService extends HopMetadataBase implements IHopMetadata {

  public static final String DEFAULT_AUDIT_TABLE = "edw_dimensional_query_audit";

  @HopMetadataProperty private boolean enabled = true;

  /** Server-side VFS path to the {@code .hdm} file (variables allowed). Never sent to clients. */
  @HopMetadataProperty private String modelFilename;

  @HopMetadataProperty private String description;

  @HopMetadataProperty private int defaultRowLimit = 10_000;

  @HopMetadataProperty private int maxRowLimit = 100_000;

  /** JDBC statement timeout in seconds. {@code 0} leaves the driver default. */
  @HopMetadataProperty private int queryTimeoutSeconds = 60;

  @HopMetadataProperty private boolean allowSchemaMetadata = true;

  /**
   * Hop Server user names allowed to use this schema. Empty means any authenticated user who
   * already has {@code run.execute}.
   */
  @HopMetadataProperty(key = "user", groupKey = "allowed_users")
  private List<String> allowedUsers = new ArrayList<>();

  @HopMetadataProperty(storeWithCode = true)
  private DimensionalMaskingPolicy maskingPolicy = DimensionalMaskingPolicy.SAFE;

  @HopMetadataProperty private boolean auditEnabled;

  /** Hop database connection that receives the audit table. */
  @HopMetadataProperty private String auditConnection;

  @HopMetadataProperty private String auditSchema;

  @HopMetadataProperty private String auditTable = DEFAULT_AUDIT_TABLE;

  /** When false, string, date, and numeric literals in the logged SQL are replaced with {@code ?}. */
  @HopMetadataProperty private boolean storeSqlVerbatim;

  public DimensionalModelService() {
    super();
  }

  public DimensionalModelService(String name) {
    super(name);
  }

  public DimensionalMaskingPolicy getMaskingPolicyOrDefault() {
    return maskingPolicy != null ? maskingPolicy : DimensionalMaskingPolicy.SAFE;
  }

  public String getAuditTableOrDefault() {
    return Utils.isEmpty(auditTable) ? DEFAULT_AUDIT_TABLE : auditTable.trim();
  }

  public List<String> getAllowedUsersOrEmpty() {
    return allowedUsers != null ? allowedUsers : List.of();
  }

  /**
   * Resolves the effective row limit: client request, then the service default, then the hard max.
   *
   * @param requested client {@code rowLimit} ({@code <= 0} means use the default)
   */
  public int resolveRowLimit(int requested) {
    int limit = requested > 0 ? requested : Math.max(0, defaultRowLimit);
    if (maxRowLimit > 0) {
      if (limit <= 0) {
        limit = maxRowLimit;
      } else {
        limit = Math.min(limit, maxRowLimit);
      }
    }
    return Math.max(0, limit);
  }

  /** Empty allow-list accepts every authenticated user. A list rejects a missing principal. */
  public boolean allows(String remoteUser) {
    List<String> allowed = getAllowedUsersOrEmpty();
    boolean restricted = false;
    for (String user : allowed) {
      if (Utils.isEmpty(user)) {
        continue;
      }
      restricted = true;
      if (user.trim().equals(remoteUser)) {
        return true;
      }
    }
    return !restricted;
  }

  /**
   * @return an error when this name is also an enabled source model service, otherwise {@code null}
   */
  public static String sourceNameConflict(String name, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(name) || metadataProvider == null) {
      return null;
    }
    IHopMetadataSerializer<SourceModelService> serializer =
        metadataProvider.getSerializer(SourceModelService.class);
    if (serializer == null) {
      return null;
    }
    for (String existing : serializer.listObjectNames()) {
      if (existing == null || !existing.equalsIgnoreCase(name.trim())) {
        continue;
      }
      SourceModelService source = serializer.load(existing);
      if (source != null && source.isEnabled()) {
        return "JDBC schema '"
            + name.trim()
            + "' is already an enabled Source model service. Choose a different dimensional model"
            + " service name.";
      }
    }
    return null;
  }
}
