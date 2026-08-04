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
package org.apache.hop.datavault.hopgui.file.modelgraph;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.GuiBusySupport;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.ui.core.dialog.CheckResultDialog;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Clones warehouse models for non-destructive table dialog validation. */
public final class ModelDialogValidationSupport {

  private ModelDialogValidationSupport() {}

  /** Callable that produces check remarks and may throw checked exceptions. */
  @FunctionalInterface
  public interface CheckWork {
    List<ICheckResult> run() throws Exception;
  }

  /**
   * Runs model/table validation with the wait cursor, then returns the remarks. Exceptions from
   * {@code work} are rethrown to the caller.
   */
  public static List<ICheckResult> runChecksWithBusyCursor(Control control, CheckWork work)
      throws Exception {
    if (work == null) {
      return Collections.emptyList();
    }
    AtomicReference<List<ICheckResult>> remarks = new AtomicReference<>(Collections.emptyList());
    AtomicReference<Exception> error = new AtomicReference<>();
    GuiBusySupport.showWhile(
        control,
        () -> {
          try {
            List<ICheckResult> result = work.run();
            remarks.set(result != null ? result : Collections.emptyList());
          } catch (Exception e) {
            error.set(e);
          }
        });
    if (error.get() != null) {
      throw error.get();
    }
    return remarks.get();
  }

  public static DataVaultModel cloneDataVaultModel(
      DataVaultModel model, IHopMetadataProvider metadataProvider) throws HopException {
    return cloneModel(
        model,
        DataVaultModel.class,
        HopVaultFileType.XML_TAG,
        DataVaultModel::new,
        metadataProvider);
  }

  public static BusinessVaultModel cloneBusinessVaultModel(
      BusinessVaultModel model, IHopMetadataProvider metadataProvider) throws HopException {
    return cloneModel(
        model,
        BusinessVaultModel.class,
        HopBusinessVaultFileType.XML_TAG,
        BusinessVaultModel::new,
        metadataProvider);
  }

  public static DimensionalModel cloneDimensionalModel(
      DimensionalModel model, IHopMetadataProvider metadataProvider) throws HopException {
    return cloneModel(
        model,
        DimensionalModel.class,
        HopDimensionalFileType.XML_TAG,
        DimensionalModel::new,
        metadataProvider);
  }

  public static void showCheckResults(Shell shell, List<ICheckResult> remarks) {
    if (shell == null || shell.isDisposed()) {
      return;
    }
    CheckResultDialog dialog = new CheckResultDialog(shell, remarks);
    dialog.open();
  }

  private static <M> M cloneModel(
      M model,
      Class<M> modelClass,
      String xmlRootTag,
      Supplier<M> modelFactory,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null) {
      throw new HopException("Cannot clone a null model");
    }
    try {
      String xml = XmlHandler.aroundTag(xmlRootTag, XmlMetadataUtil.serializeObjectToXml(model));
      Document document = XmlHandler.loadXmlString(xml);
      Node rootNode = XmlHandler.getSubNode(document, xmlRootTag);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      M clone = modelFactory.get();
      XmlMetadataUtil.deSerializeFromXml(rootNode, modelClass, clone, metadataProvider);
      preserveFilename(model, clone);
      return clone;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Error cloning model for validation", e);
    }
  }

  private static void preserveFilename(Object source, Object clone) {
    if (source instanceof DataVaultModel sourceDv && clone instanceof DataVaultModel cloneDv) {
      cloneDv.setFilename(sourceDv.getFilename());
    } else if (source instanceof BusinessVaultModel sourceBv
        && clone instanceof BusinessVaultModel cloneBv) {
      cloneBv.setFilename(sourceBv.getFilename());
    } else if (source instanceof DimensionalModel sourceDm
        && clone instanceof DimensionalModel cloneDm) {
      cloneDm.setFilename(sourceDm.getFilename());
    }
  }
}
