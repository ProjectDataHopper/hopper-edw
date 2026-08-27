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
package org.hopper.edw.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.SatelliteAttribute;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2FieldMapping;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2SatelliteConfig;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmSourceType;
import org.junit.jupiter.api.Test;

class RemediationTargetResolutionSupportTest {

  @Test
  void resolvesBvMappingAndDmViaBvSqlStar() {
    DataVaultModel dv = new DataVaultModel();
    dv.setName("retail-360");
    dv.setFilename("models/retail-360.hdv");
    DvSatellite sat = new DvSatellite();
    sat.setName("sat_customer_address");
    sat.setRecordSourceName("E2E-customer-address");
    SatelliteAttribute attr = new SatelliteAttribute("address_line1");
    attr.setLength("50");
    sat.getAttributes().add(attr);
    dv.getTables().add(sat);

    BusinessVaultModel bv = new BusinessVaultModel();
    bv.setName("retail-360-bv");
    bv.setFilename("models/retail-360.hbv");
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("customer_360_bv");
    scd2.setTableName("customer_360_bv");
    scd2.getSatelliteConfigs().add(new BvScd2SatelliteConfig("sat_customer_address"));
    scd2.getFieldMappings()
        .add(new BvScd2FieldMapping("sat_customer_address", "address_line1", "cust_address"));
    bv.getTables().add(scd2);

    DimensionalModel dm = new DimensionalModel();
    dm.setName("retail-conformed-dims");
    dm.setFilename("models/retail-conformed-dims.hdm");
    DmDimension dimension = new DmDimension();
    dimension.setName("d_customer");
    dimension.setTableName("d_customer");
    dimension.getSourceOrDefault().setSourceType(DmSourceType.SQL);
    dimension
        .getSourceOrDefault()
        .setSourceSql(
            """
            SELECT hc.customer_id, sc.*
            FROM hub_customer hc
            JOIN customer_360_bv sc ON hc.customer_hk = sc.customer_hk
            """);
    dimension.getAttributes().add(new DmDimensionAttribute("cust_address"));
    dm.getTables().add(dimension);

    ValidationModels models =
        new ValidationModels(
            new ResourceDefinitionGroupMeta(),
            List.of(new ValidationModels.LoadedDataVaultModel(dv, "local-catalog")),
            List.of(new ValidationModels.LoadedBusinessVaultModel(bv, dv, "local-catalog")),
            List.of(new ValidationModels.LoadedDimensionalModel(dm, "local-catalog")));

    List<RemediationTargetColumn> targets =
        RemediationTargetResolutionSupport.resolveDownstreamTargets(
            models, "address_line1", "75", Set.of("sat_customer_address"), new Variables());

    assertTrue(
        targets.stream()
            .anyMatch(
                t ->
                    RemediationTargetColumn.LAYER_BV.equals(t.layer())
                        && "cust_address".equals(t.targetFieldName())
                        && RemediationTargetColumn.CONFIDENCE_EXPLICIT_MAP.equals(t.confidence())),
        targets.toString());
    assertTrue(
        targets.stream()
            .anyMatch(
                t ->
                    RemediationTargetColumn.LAYER_DM.equals(t.layer())
                        && "d_customer".equals(t.tableElementName())
                        && "cust_address".equals(t.targetFieldName())
                        && RemediationTargetColumn.CONFIDENCE_DERIVED_VIA_BV.equals(
                            t.confidence())),
        targets.toString());
    assertEquals("75", targets.getFirst().catalogLength());
  }
}
