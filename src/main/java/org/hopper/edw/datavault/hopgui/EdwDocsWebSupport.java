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
package org.hopper.edw.datavault.hopgui;

import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileType;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.ui.util.EnvironmentUtils;

/**
 * Serves plugin {@code docs/} over a RAP service handler so Hop Web can open HTML that lives on the
 * Tomcat host, not on the browser machine.
 */
public final class EdwDocsWebSupport {

  static final String SERVICE_ID = "hopperEdwDocs";
  static final String FILE_PARAM = "file";

  private static final Pattern HTML_REF =
      Pattern.compile("(?i)(\\s(?:href|src)\\s*=\\s*)(['\"])([^'\"]+)\\2");
  private static final Pattern CSS_URL =
      Pattern.compile("(?i)(url\\(\\s*)(['\"]?)([^'\")]+)\\2(\\s*\\))");
  private static final Set<String> ALLOWED_EXTENSIONS =
      Set.of(
          "html", "htm", "css", "js", "png", "jpg", "jpeg", "gif", "svg", "ico", "webp", "woff",
          "woff2", "ttf", "eot", "map", "txt", "md", "json", "xml");

  private static final Object LOCK = new Object();
  private static volatile boolean handlerRegistered;
  private static volatile Path docsRoot;

  private EdwDocsWebSupport() {}

  /**
   * Browser URL for a plugin docs file. On desktop this is the {@code file:} URI. On Hop Web this
   * is a RAP service-handler URL that streams the same file (and relative assets) from the server.
   */
  public static String browserUrl(Path htmlFile) {
    if (htmlFile == null) {
      return null;
    }
    Path absolute = htmlFile.toAbsolutePath().normalize();
    if (!EnvironmentUtils.getInstance().isWeb()) {
      return absolute.toUri().toString();
    }
    Path root = docsDirectory(absolute);
    String relative = relativeDocFile(root, absolute);
    if (root == null || relative == null) {
      return null;
    }
    docsRoot = root;
    String handlerUrl = registerAndHandlerUrl();
    if (Utils.isEmpty(handlerUrl)) {
      return null;
    }
    return appendFileParam(handlerUrl, relative);
  }

  static Path docsDirectory(Path htmlFile) {
    if (htmlFile == null) {
      return null;
    }
    Path current = htmlFile.toAbsolutePath().normalize();
    for (Path dir = isDirectory(current) ? current : current.getParent();
        dir != null;
        dir = dir.getParent()) {
      if (dir.getFileName() != null && "docs".equals(dir.getFileName().toString())) {
        return dir;
      }
    }
    return current.getParent();
  }

  static String relativeDocFile(Path docsDirectory, Path htmlFile) {
    if (docsDirectory == null || htmlFile == null) {
      return null;
    }
    try {
      Path relative =
          docsDirectory
              .toAbsolutePath()
              .normalize()
              .relativize(htmlFile.toAbsolutePath().normalize());
      String asString = relative.toString().replace('\\', '/');
      if (asString.startsWith("..") || asString.startsWith("/")) {
        return null;
      }
      return asString;
    } catch (Exception ignored) {
      return null;
    }
  }

  static Path resolveSafe(Path docsDirectory, String relativeFile) {
    if (docsDirectory == null || Utils.isEmpty(relativeFile)) {
      return null;
    }
    String name = relativeFile.trim().replace('\\', '/');
    while (name.startsWith("./")) {
      name = name.substring(2);
    }
    if (name.isEmpty() || name.startsWith("/") || name.contains("..")) {
      return null;
    }
    int slash = name.lastIndexOf('/');
    String fileName = slash >= 0 ? name.substring(slash + 1) : name;
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return null;
    }
    String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      return null;
    }
    Path root = docsDirectory.toAbsolutePath().normalize();
    Path resolved = root.resolve(name).normalize();
    if (!resolved.startsWith(root)) {
      return null;
    }
    return resolved;
  }

  static String appendFileParam(String handlerUrl, String relativeFile) {
    if (Utils.isEmpty(handlerUrl) || Utils.isEmpty(relativeFile)) {
      return handlerUrl;
    }
    String encoded = URLEncoder.encode(relativeFile, StandardCharsets.UTF_8).replace("+", "%20");
    String separator = handlerUrl.contains("?") ? "&" : "?";
    return handlerUrl + separator + FILE_PARAM + "=" + encoded;
  }

  static String rewriteRelativeUrls(String content, String handlerUrl, String currentFile) {
    if (Utils.isEmpty(content) || Utils.isEmpty(handlerUrl) || Utils.isEmpty(currentFile)) {
      return content;
    }
    Matcher html = HTML_REF.matcher(content);
    StringBuffer htmlOut = new StringBuffer();
    while (html.find()) {
      String rewritten = rewriteRef(handlerUrl, currentFile, html.group(3));
      html.appendReplacement(
          htmlOut,
          Matcher.quoteReplacement(html.group(1) + html.group(2) + rewritten + html.group(2)));
    }
    html.appendTail(htmlOut);

    Matcher css = CSS_URL.matcher(htmlOut.toString());
    StringBuffer cssOut = new StringBuffer();
    while (css.find()) {
      String rewritten = rewriteRef(handlerUrl, currentFile, css.group(3).trim());
      css.appendReplacement(
          cssOut,
          Matcher.quoteReplacement(
              css.group(1) + css.group(2) + rewritten + css.group(2) + css.group(4)));
    }
    css.appendTail(cssOut);
    return cssOut.toString();
  }

  static String rewriteRef(String handlerUrl, String currentFile, String ref) {
    if (Utils.isEmpty(ref)) {
      return ref;
    }
    String path = ref.trim();
    String fragment = "";
    int hash = path.indexOf('#');
    if (hash >= 0) {
      fragment = path.substring(hash);
      path = path.substring(0, hash);
    }
    if (path.isEmpty() || isAbsoluteRef(path)) {
      return ref;
    }
    String resolved = resolveAgainst(currentFile, path);
    if (resolved == null) {
      return ref;
    }
    return appendFileParam(handlerUrl, resolved) + fragment;
  }

  static boolean isAbsoluteRef(String path) {
    String value = path.toLowerCase(Locale.ROOT);
    return value.startsWith("http://")
        || value.startsWith("https://")
        || value.startsWith("mailto:")
        || value.startsWith("data:")
        || value.startsWith("javascript:")
        || value.startsWith("//")
        || value.startsWith("/");
  }

  static String resolveAgainst(String currentFile, String relative) {
    String base = currentFile.replace('\\', '/');
    int slash = base.lastIndexOf('/');
    String dir = slash >= 0 ? base.substring(0, slash + 1) : "";
    Path resolved = Path.of(".").resolve(dir).resolve(relative).normalize();
    String asString = resolved.toString().replace('\\', '/');
    while (asString.startsWith("./")) {
      asString = asString.substring(2);
    }
    if (asString.startsWith("../") || asString.equals("..") || asString.startsWith("/")) {
      return null;
    }
    return asString;
  }

  static String contentType(String fileName) {
    if (Utils.isEmpty(fileName)) {
      return "application/octet-stream";
    }
    String name = fileName.toLowerCase(Locale.ROOT);
    if (name.endsWith(".html") || name.endsWith(".htm")) {
      return "text/html; charset=UTF-8";
    }
    if (name.endsWith(".css")) {
      return "text/css; charset=UTF-8";
    }
    if (name.endsWith(".js")) {
      return "application/javascript; charset=UTF-8";
    }
    if (name.endsWith(".svg")) {
      return "image/svg+xml";
    }
    if (name.endsWith(".png")) {
      return "image/png";
    }
    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (name.endsWith(".gif")) {
      return "image/gif";
    }
    if (name.endsWith(".webp")) {
      return "image/webp";
    }
    if (name.endsWith(".ico")) {
      return "image/x-icon";
    }
    if (name.endsWith(".json")) {
      return "application/json; charset=UTF-8";
    }
    if (name.endsWith(".xml")) {
      return "application/xml; charset=UTF-8";
    }
    if (name.endsWith(".md") || name.endsWith(".txt")) {
      return "text/plain; charset=UTF-8";
    }
    return "application/octet-stream";
  }

  private static boolean isDirectory(Path path) {
    return path != null && java.nio.file.Files.isDirectory(path);
  }

  private static String registerAndHandlerUrl() {
    synchronized (LOCK) {
      try {
        Class<?> rwtClass = Class.forName("org.eclipse.rap.rwt.RWT");
        Object serviceManager = rwtClass.getMethod("getServiceManager").invoke(null);
        if (!handlerRegistered) {
          Class<?> handlerType = Class.forName("org.eclipse.rap.rwt.service.ServiceHandler");
          Object handler =
              Proxy.newProxyInstance(
                  handlerType.getClassLoader(), new Class<?>[] {handlerType}, new DocsHandler());
          try {
            serviceManager
                .getClass()
                .getMethod("registerServiceHandler", String.class, handlerType)
                .invoke(serviceManager, SERVICE_ID, handler);
            handlerRegistered = true;
          } catch (Exception registerError) {
            // Already registered in this session is fine.
          }
        }
        Object url =
            serviceManager
                .getClass()
                .getMethod("getServiceHandlerUrl", String.class)
                .invoke(serviceManager, SERVICE_ID);
        if (url != null) {
          handlerRegistered = true;
          return url.toString();
        }
        return null;
      } catch (Exception e) {
        LogChannel.UI.logError("Unable to register EDW documentation handler for Hop Web", e);
        return null;
      }
    }
  }

  static void serve(Object request, Object response) throws Exception {
    String relative =
        (String)
            request.getClass().getMethod("getParameter", String.class).invoke(request, FILE_PARAM);
    Path root = docsRoot;
    Path file = resolveSafe(root, relative);
    if (file == null) {
      sendError(response, 404, "Documentation page not found");
      return;
    }
    FileObject fileObject = HopVfs.getFileObject(file.toString());
    if (!fileObject.exists() || fileObject.getType() != FileType.FILE) {
      sendError(response, 404, "Documentation page not found");
      return;
    }
    String type = contentType(file.getFileName().toString());
    byte[] body;
    try (InputStream in = HopVfs.getInputStream(fileObject)) {
      body = in.readAllBytes();
    }
    String handlerUrl = currentHandlerUrl(request);
    if (type.startsWith("text/html") || type.startsWith("text/css")) {
      String text = new String(body, StandardCharsets.UTF_8);
      String relativeFile = relativeDocFile(root, file);
      if (relativeFile != null && !Utils.isEmpty(handlerUrl)) {
        text = rewriteRelativeUrls(text, handlerUrl, relativeFile);
      }
      body = text.getBytes(StandardCharsets.UTF_8);
    }
    write(response, 200, type, body);
  }

  private static String currentHandlerUrl(Object request) {
    try {
      Class<?> rwtClass = Class.forName("org.eclipse.rap.rwt.RWT");
      Object serviceManager = rwtClass.getMethod("getServiceManager").invoke(null);
      Object url =
          serviceManager
              .getClass()
              .getMethod("getServiceHandlerUrl", String.class)
              .invoke(serviceManager, SERVICE_ID);
      if (url != null) {
        return url.toString();
      }
    } catch (Exception ignored) {
      // Reconstruct from the request URL below.
    }
    try {
      Object requestUrl = request.getClass().getMethod("getRequestURL").invoke(request);
      Object query = request.getClass().getMethod("getQueryString").invoke(request);
      String url = requestUrl != null ? requestUrl.toString() : "";
      if (query != null && !query.toString().isBlank()) {
        url = url + "?" + stripFileParam(query.toString());
      }
      return url;
    } catch (Exception ignored) {
      return null;
    }
  }

  static String stripFileParam(String query) {
    if (Utils.isEmpty(query)) {
      return query;
    }
    StringBuilder kept = new StringBuilder();
    for (String part : query.split("&")) {
      if (part.isEmpty() || part.startsWith(FILE_PARAM + "=")) {
        continue;
      }
      if (!kept.isEmpty()) {
        kept.append('&');
      }
      kept.append(part);
    }
    return kept.toString();
  }

  private static void sendError(Object response, int status, String message) throws Exception {
    try {
      response
          .getClass()
          .getMethod("sendError", int.class, String.class)
          .invoke(response, status, message);
    } catch (NoSuchMethodException ignored) {
      write(
          response, status, "text/plain; charset=UTF-8", message.getBytes(StandardCharsets.UTF_8));
    }
  }

  private static void write(Object response, int status, String contentType, byte[] body)
      throws Exception {
    response.getClass().getMethod("setStatus", int.class).invoke(response, status);
    response.getClass().getMethod("setContentType", String.class).invoke(response, contentType);
    response
        .getClass()
        .getMethod("setCharacterEncoding", String.class)
        .invoke(response, StandardCharsets.UTF_8.name());
    response.getClass().getMethod("setContentLength", int.class).invoke(response, body.length);
    Object output = response.getClass().getMethod("getOutputStream").invoke(response);
    output.getClass().getMethod("write", byte[].class).invoke(output, body);
  }

  private static final class DocsHandler implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String name = method.getName();
      if ("service".equals(name) && args != null && args.length == 2) {
        try {
          serve(args[0], args[1]);
        } catch (Exception e) {
          try {
            sendError(args[1], 500, "Unable to serve documentation");
          } catch (Exception ignored) {
            throw e;
          }
        }
        return null;
      }
      if ("equals".equals(name) && args != null && args.length == 1) {
        return proxy == args[0];
      }
      if ("hashCode".equals(name)) {
        return System.identityHashCode(proxy);
      }
      if ("toString".equals(name)) {
        return "EdwDocsWebSupport.DocsHandler";
      }
      return null;
    }
  }
}
