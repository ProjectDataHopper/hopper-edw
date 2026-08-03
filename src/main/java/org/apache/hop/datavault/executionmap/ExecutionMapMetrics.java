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
package org.apache.hop.datavault.executionmap;

/** Shared layout and paint metrics for execution map graphs. */
public final class ExecutionMapMetrics {

  public static final int MIN_NODE_WIDTH = 160;
  public static final int MIN_NODE_HEIGHT = 56;

  /**
   * @deprecated use measured card width via {@link ExecutionMapNodeCardMetrics}
   */
  @Deprecated public static final int NODE_WIDTH = MIN_NODE_WIDTH;

  /**
   * @deprecated use measured card height via {@link ExecutionMapNodeCardMetrics}
   */
  @Deprecated public static final int NODE_HEIGHT = MIN_NODE_HEIGHT;

  public static final int CONTAINER_PADDING = 16;
  public static final int HUB_GUTTER = 48;
  public static final int BUS_LANE_WIDTH = 48;
  public static final int CHILD_VERTICAL_SPACING = 32;
  public static final int PIPELINE_ROW_SPACING = 32;
  public static final int ORPHAN_GRID_COLUMNS = 3;

  private ExecutionMapMetrics() {}
}
