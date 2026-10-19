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
package org.hopper.edw.datavault.hopgui.ai;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.ui.hopgui.HopGui;
import org.w3c.dom.Document;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.IHopPerspective;
import org.apache.hop.ui.hopgui.perspective.TabItemHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;

/**
 * Resolves the model a question is about. A saved AI session keeps the file name and drops the live
 * object, and AI Help can miss the graph after another toolbar unregisters the shared instance id.
 * The open editor is the model to read and to edit.
 */
public final class EdwAiBoundModel {

  private EdwAiBoundModel() {}

  /**
   * The model for this request when this class loader can see it. A model owned by another loader
   * is left for {@link #structureOf(Object)}, which does not open files.
   */
  public static <T> T bind(AiAdvisorRequest request, Class<T> type) {
    if (type == null) {
      return null;
    }
    Object artifact = request == null ? null : request.getArtifact();
    if (type.isInstance(artifact)) {
      return type.cast(artifact);
    }
    if (isForeignModel(artifact, type)) {
      // The modeler's class is a different copy. Do not read its file or reflect through Hop VFS
      // on the UI thread; the advisor builds a short summary from getters that are already loaded.
      return null;
    }
    HopGui hopGui = hopGui();
    T selected =
        select(artifact, activeExplorerSubject(hopGui), openSubjects(hopGui), type);
    if (selected != null) {
      if (request != null && request.getArtifact() != selected) {
        request.setArtifact(selected);
      }
      return selected;
    }
    return null;
  }

  /**
   * Table names from a model whose class was loaded elsewhere. Only calls getters that already
   * exist on the open object. Does not follow alias filenames or open another file.
   */
  public static String structureOf(Object model) {
    StringBuilder json = new StringBuilder();
    json.append("{\"name\":").append(jsonString(invoke(model, "getName")));
    json.append(",\"tables\":[");
    Object tables = invoke(model, "getTables");
    if (tables instanceof Iterable<?> iterable) {
      int count = 0;
      for (Object table : iterable) {
        if (table == null) {
          continue;
        }
        if (count >= 500) {
          break;
        }
        if (count > 0) {
          json.append(',');
        }
        count++;
        json.append("{\"name\":").append(jsonString(invoke(table, "getName")));
        json.append(",\"type\":").append(jsonString(invoke(table, "getTableType")));
        json.append(",\"targetTable\":").append(jsonString(invoke(table, "getTableName")));
        Object referenced = invoke(table, "getReferencedDimensionName");
        if (referenced != null) {
          json.append(",\"referencedDimension\":").append(jsonString(referenced));
          json.append(",\"referencedModelFilename\":")
              .append(jsonString(invoke(table, "getReferencedModelFilename")));
        }
        json.append('}');
      }
    }
    json.append("]}");
    return json.toString();
  }

  private static Object invoke(Object target, String method) {
    if (target == null) {
      return null;
    }
    try {
      return target.getClass().getMethod(method).invoke(target);
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  private static String jsonString(Object value) {
    if (value == null) {
      return "null";
    }
    String text = String.valueOf(value);
    return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  /**
   * Writes a copied model back onto the live instance the modeler is showing. No-op when the
   * advisor and the modeler already share one instance.
   */
  public static void writeBack(Object copy, AiAdvisorRequest request) throws HopException {
    if (copy == null || request == null) {
      return;
    }
    Object foreign = request.getArtifact();
    if (foreign == null || foreign == copy || foreign.getClass() == copy.getClass()) {
      return;
    }
    if (!foreign.getClass().getName().equals(copy.getClass().getName())) {
      return;
    }
    String inner = XmlMetadataUtil.serializeObjectToXml(copy);
    Document document = XmlHandler.loadXmlString("<model>" + inner + "</model>");
    XmlMetadataUtil.deSerializeFromXml(
        document.getDocumentElement(),
        foreign.getClass(),
        foreign,
        request.getMetadataProvider());
  }

  /**
   * Message when {@link #bind} finds nothing. Keeps the historical sentence when nothing is linked.
   */
  public static String unbound(String kind, AiAdvisorRequest request) {
    Object artifact = request == null ? null : request.getArtifact();
    if (artifact == null) {
      return "No " + kind + " is bound to this session.";
    }
    return "No "
        + kind
        + " is bound to this session (session artifact is "
        + artifact.getClass().getName()
        + ").";
  }

  /**
   * Picks the model without touching Hop GUI. {@code open} empty means the editors are not visible
   * (unit tests, headless), so a model already on the request is kept and nothing else is guessed.
   */
  static <T> T select(Object artifact, Object active, List<?> open, Class<T> type) {
    if (type == null) {
      return null;
    }
    List<T> openOfType = new ArrayList<>();
    if (open != null) {
      for (Object subject : open) {
        if (type.isInstance(subject)) {
          openOfType.add(type.cast(subject));
        }
      }
    }
    boolean sawOpenFiles = open != null && !open.isEmpty();
    if (type.isInstance(artifact)) {
      T bound = type.cast(artifact);
      if (!sawOpenFiles || identityContains(openOfType, bound)) {
        return bound;
      }
      T reopened = uniqueFilenameMatch(bound, openOfType);
      return reopened != null ? reopened : bound;
    }
    if (!sawOpenFiles) {
      return null;
    }
    T byName = uniqueFilenameMatch(artifact, openOfType);
    if (byName != null) {
      return byName;
    }
    if (type.isInstance(active)) {
      return type.cast(active);
    }
    if (openOfType.size() == 1) {
      return openOfType.get(0);
    }
    return null;
  }

  public static boolean isForeignModel(Object artifact, Class<?> modelType) {
    return artifact != null
        && modelType != null
        && !modelType.isInstance(artifact)
        && modelType.getName().equals(artifact.getClass().getName());
  }

  private static HopGui hopGui() {
    try {
      return HopGui.peekInstance();
    } catch (Throwable e) {
      return null;
    }
  }

  private static Object activeExplorerSubject(HopGui hopGui) {
    if (hopGui == null || hopGui.getPerspectiveManager() == null) {
      return null;
    }
    try {
      ExplorerPerspective explorer =
          hopGui.getPerspectiveManager().findPerspective(ExplorerPerspective.class);
      if (explorer == null) {
        return null;
      }
      IHopFileTypeHandler handler = explorer.getActiveFileTypeHandler();
      return handler == null ? null : handler.getSubject();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static List<Object> openSubjects(HopGui hopGui) {
    List<Object> subjects = new ArrayList<>();
    if (hopGui == null || hopGui.getPerspectiveManager() == null) {
      return subjects;
    }
    try {
      for (IHopPerspective perspective : hopGui.getPerspectiveManager().getPerspectives()) {
        if (perspective == null || perspective.getItems() == null) {
          continue;
        }
        for (TabItemHandler item : perspective.getItems()) {
          if (item == null || item.getTypeHandler() == null) {
            continue;
          }
          Object subject = item.getTypeHandler().getSubject();
          if (subject != null) {
            subjects.add(subject);
          }
        }
      }
    } catch (RuntimeException e) {
      return subjects;
    }
    return subjects;
  }

  private static boolean identityContains(List<?> items, Object target) {
    for (Object item : items) {
      if (item == target) {
        return true;
      }
    }
    return false;
  }

  private static <T> T uniqueFilenameMatch(Object artifact, List<T> openOfType) {
    String filename = filename(artifact);
    if (filename == null) {
      return null;
    }
    T match = null;
    for (T candidate : openOfType) {
      if (!filename.equals(filename(candidate))) {
        continue;
      }
      if (match != null && match != candidate) {
        return null;
      }
      match = candidate;
    }
    return match;
  }

  private static String filename(Object subject) {
    if (subject instanceof IHasFilename hasFilename) {
      String name = hasFilename.getFilename();
      if (name != null && !name.isBlank()) {
        return name;
      }
    }
    return null;
  }
}
