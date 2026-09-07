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
package org.hopper.edw.datavault.documentation.render;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.vfs.HopVfs;

/** HopVfs helpers for the documentation generator. */
public final class DocumentationIo {

  private DocumentationIo() {}

  /**
   * Native filename Hop loaders can reopen through {@link HopVfs#getFileObject(String)}.
   *
   * <p>{@link org.apache.commons.vfs2.FileName#getPath()} is not usable on Windows: the VFS path
   * omits the drive letter ({@code /Users/...} instead of {@code C:\Users\...}). Hop then treats
   * that as current-drive relative, the file is missing, and project documentation only writes
   * metadata pages (issue #158).
   */
  public static String filenameForLoad(FileObject file) {
    if (file == null) {
      return "";
    }
    return HopVfs.getFilename(file);
  }

  public static void writeUtf8(FileObject file, String content) throws HopException {
    try {
      FileObject parent = file.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
      try (OutputStream out = HopVfs.getOutputStream(file, false)) {
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.flush();
      }
    } catch (Exception e) {
      throw new HopException("Unable to write " + file, e);
    }
  }

  public static void writeUtf8(String filename, String content) throws HopException {
    try {
      writeUtf8(HopVfs.getFileObject(filename), content);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to write " + filename, e);
    }
  }

  public static void writeBytes(FileObject file, byte[] bytes) throws HopException {
    try {
      FileObject parent = file.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
      try (OutputStream out = HopVfs.getOutputStream(file, false)) {
        out.write(bytes);
        out.flush();
      }
    } catch (Exception e) {
      throw new HopException("Unable to write " + file, e);
    }
  }

  public static byte[] readClasspath(String resource) throws HopException {
    try (InputStream in = DocumentationIo.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new HopException("Classpath resource not found: " + resource);
      }
      return in.readAllBytes();
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to read classpath resource " + resource, e);
    }
  }

  public static FileObject child(FileObject root, String relative) throws HopException {
    try {
      String path = root.getName().getURI();
      if (!path.endsWith("/")) {
        path = path + "/";
      }
      return HopVfs.getFileObject(path + relative.replace('\\', '/'));
    } catch (Exception e) {
      throw new HopException("Unable to resolve " + relative, e);
    }
  }
}
