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

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionValidationAcknowledgement;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Persists validation issue acknowledgements on catalog record definitions. */
public final class ValidationAcknowledgementSupport {

  private static final Class<?> PKG = ValidationAcknowledgementSupport.class;

  private ValidationAcknowledgementSupport() {}

  public static boolean isAcknowledged(RecordDefinition definition, String issueId) {
    if (definition == null || Utils.isEmpty(issueId)) {
      return false;
    }
    List<RecordDefinitionValidationAcknowledgement> acknowledgements =
        definition.getValidationAcknowledgements();
    if (acknowledgements == null) {
      return false;
    }
    for (RecordDefinitionValidationAcknowledgement acknowledgement : acknowledgements) {
      if (ValidationIssueSupport.matchesAcknowledgement(acknowledgement, issueId)) {
        return true;
      }
    }
    return false;
  }

  public static RecordDefinitionValidationAcknowledgement findAcknowledgement(
      RecordDefinition definition, String issueId) {
    if (definition == null || Utils.isEmpty(issueId)) {
      return null;
    }
    List<RecordDefinitionValidationAcknowledgement> acknowledgements =
        definition.getValidationAcknowledgements();
    if (acknowledgements == null) {
      return null;
    }
    for (RecordDefinitionValidationAcknowledgement acknowledgement : acknowledgements) {
      if (ValidationIssueSupport.matchesAcknowledgement(acknowledgement, issueId)) {
        return acknowledgement;
      }
    }
    return null;
  }

  public static void acknowledge(
      String catalogConnection,
      RecordDefinition definition,
      String issueId,
      String comment,
      String acknowledgedBy,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(catalogConnection) || definition == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "ValidationAcknowledgementSupport.Error.MissingDefinition"));
    }
    if (Utils.isEmpty(issueId)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ValidationAcknowledgementSupport.Error.MissingIssueId"));
    }
    String trimmedComment = comment != null ? comment.trim() : null;
    if (Utils.isEmpty(trimmedComment)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ValidationAcknowledgementSupport.Error.MissingComment"));
    }

    List<RecordDefinitionValidationAcknowledgement> acknowledgements =
        definition.getValidationAcknowledgements();
    if (acknowledgements == null) {
      acknowledgements = new ArrayList<>();
      definition.setValidationAcknowledgements(acknowledgements);
    }

    Iterator<RecordDefinitionValidationAcknowledgement> iterator = acknowledgements.iterator();
    while (iterator.hasNext()) {
      RecordDefinitionValidationAcknowledgement existing = iterator.next();
      if (ValidationIssueSupport.matchesAcknowledgement(existing, issueId)) {
        iterator.remove();
      }
    }

    RecordDefinitionValidationAcknowledgement acknowledgement =
        new RecordDefinitionValidationAcknowledgement();
    acknowledgement.setIssueId(issueId);
    acknowledgement.setComment(trimmedComment);
    acknowledgement.setAcknowledgedAt(new Date());
    acknowledgement.setAcknowledgedBy(acknowledgedBy);
    acknowledgements.add(acknowledgement);

    RecordDefinitionRegistry.getInstance()
        .update(catalogConnection, definition, variables, metadataProvider);
  }

  public static void revoke(
      String catalogConnection,
      RecordDefinition definition,
      String issueId,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(catalogConnection) || definition == null || Utils.isEmpty(issueId)) {
      throw new HopException(
          BaseMessages.getString(PKG, "ValidationAcknowledgementSupport.Error.MissingDefinition"));
    }
    List<RecordDefinitionValidationAcknowledgement> acknowledgements =
        definition.getValidationAcknowledgements();
    if (acknowledgements == null || acknowledgements.isEmpty()) {
      return;
    }
    acknowledgements.removeIf(
        acknowledgement -> ValidationIssueSupport.matchesAcknowledgement(acknowledgement, issueId));
    RecordDefinitionRegistry.getInstance()
        .update(catalogConnection, definition, variables, metadataProvider);
  }

  public static String resolveAcknowledgedBy() {
    String user = System.getProperty("user.name");
    return Utils.isEmpty(user) ? "unknown" : user;
  }
}
