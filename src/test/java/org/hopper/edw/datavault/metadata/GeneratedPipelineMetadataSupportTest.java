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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dummy.DummyMeta;
import org.junit.jupiter.api.Test;

class GeneratedPipelineMetadataSupportTest {

  @Test
  void stampsDvHubPipelineAttributes() {
    PipelineMeta pipeline = new PipelineMeta();
    pipeline.setName("hub_customer-CRM-customer");

    DataVaultModel model = new DataVaultModel("retail-360");
    DvHub hub = new DvHub("hub_customer");
    GeneratedPipelineMetadataSupport.stampDvHubPipeline(
        pipeline, model, hub, "hub_customer", "CRM-customer");

    assertEquals("dv", getPipelineAttr(pipeline, GeneratedPipelineMetadataConstants.MODEL_TYPE));
    assertEquals(
        "retail-360", getPipelineAttr(pipeline, GeneratedPipelineMetadataConstants.MODEL_NAME));
    assertEquals("hub", getPipelineAttr(pipeline, GeneratedPipelineMetadataConstants.ELEMENT_TYPE));
    assertEquals(
        "hub_customer", getPipelineAttr(pipeline, GeneratedPipelineMetadataConstants.ELEMENT_NAME));
    assertEquals(
        "CRM-customer", getPipelineAttr(pipeline, GeneratedPipelineMetadataConstants.SOURCE_NAME));
  }

  @Test
  void stampsDvSatelliteAndBvPipelineAttributes() {
    PipelineMeta satellitePipeline = new PipelineMeta();
    satellitePipeline.setName("sat-customer-crm");
    DataVaultModel dvModel = new DataVaultModel("retail-360");
    DvSatellite satellite = new DvSatellite("sat_customer");
    GeneratedPipelineMetadataSupport.stampDvSatellitePipeline(
        satellitePipeline, dvModel, satellite, "sat_customer", "CRM-customer");

    assertEquals(
        "satellite",
        getPipelineAttr(satellitePipeline, GeneratedPipelineMetadataConstants.ELEMENT_TYPE));
    assertEquals(
        "sat_customer",
        getPipelineAttr(satellitePipeline, GeneratedPipelineMetadataConstants.ELEMENT_NAME));

    PipelineMeta bvPipeline = new PipelineMeta();
    bvPipeline.setName("bv-scd2-customer_360");
    org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel bvModel =
        new org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel();
    bvModel.setName("retail-360");
    GeneratedPipelineMetadataSupport.stampBvElementPipeline(
        bvPipeline, bvModel, "scd2", "customer_360", "bv_customer_360");

    assertEquals("bv", getPipelineAttr(bvPipeline, GeneratedPipelineMetadataConstants.MODEL_TYPE));
    assertEquals(
        "scd2", getPipelineAttr(bvPipeline, GeneratedPipelineMetadataConstants.ELEMENT_TYPE));
  }

  @Test
  void stampsDimensionLookupTransformAttributes() {
    TransformMeta lookup =
        new TransformMeta("DimensionLookup", "lookup_d_customer", new DummyMeta());
    GeneratedPipelineMetadataSupport.stampTransform(
        lookup,
        new GeneratedPipelineMetadataSupport.TransformContext(
            GeneratedPipelineMetadataConstants.ROLE_DIMENSION_LOOKUP,
            "dimension",
            "d_customer",
            "f_orders",
            "d_customer",
            "Vault",
            GeneratedPipelineMetadataConstants.LOOKUP_CACHE_DATABASE));

    assertEquals(
        GeneratedPipelineMetadataConstants.ROLE_DIMENSION_LOOKUP,
        getTransformAttr(lookup, GeneratedPipelineMetadataConstants.LOGICAL_ROLE));
    assertEquals(
        "dimension", getTransformAttr(lookup, GeneratedPipelineMetadataConstants.ELEMENT_TYPE));
    assertEquals(
        "d_customer", getTransformAttr(lookup, GeneratedPipelineMetadataConstants.ELEMENT_NAME));
    assertEquals(
        "f_orders",
        getTransformAttr(lookup, GeneratedPipelineMetadataConstants.PARENT_ELEMENT_NAME));
    assertEquals(
        GeneratedPipelineMetadataConstants.LOOKUP_CACHE_DATABASE,
        getTransformAttr(lookup, GeneratedPipelineMetadataConstants.LOOKUP_CACHE_MODE));
  }

  private static String getPipelineAttr(PipelineMeta pipeline, String key) {
    return GeneratedPipelineMetadataSupport.getPipelineAttribute(pipeline, key);
  }

  private static String getTransformAttr(TransformMeta transform, String key) {
    return GeneratedPipelineMetadataSupport.getTransformAttribute(transform, key);
  }
}
