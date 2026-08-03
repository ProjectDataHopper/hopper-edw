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
package org.apache.hop.quality.engine;

import lombok.Builder;
import lombok.Getter;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.quality.model.QualityEvaluationMode;
import org.apache.hop.quality.model.QualityLifecycle;
import org.apache.hop.quality.profile.DataProfileSnapshot;

/** Context for evaluating rules after a profile has been collected. */
@Getter
@Builder
public class QualityEvaluationContext {

  private final String subjectKey;
  private final DataProfileSnapshot profile;
  private final QualityEvaluationMode mode;
  private final QualityLifecycle lifecycle;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final ILogChannel log;
}
