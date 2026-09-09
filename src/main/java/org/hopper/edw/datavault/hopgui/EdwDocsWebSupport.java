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
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileType;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.ui.util.EnvironmentUtils;

/**
 * Serves HTML documentation over a RAP service handler so Hop Web can open pages that live on the
 * Tomcat host, not on the browser machine.
 *
 * <p>Plugin-shipped {@code docs/} use the default root. Generated project documentation registers
 * its output folder as an extra root ({@code root=} query parameter) so CSS, images, and in-page
 * links resolve in both a new browser tab and the explorer {@code Browser} widget.
 */
public final class EdwDocsWebSupport {

  static final String SERVICE_ID = "hopperEdwDocs";
  static final String FILE_PARAM = "file";
  static final String ROOT_PARAM = "root";
  static final String DEFAULT_ROOT_ID = "edw";
  static final String HOP_DOC_CSS = "assets/css/hop-doc.css";

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
  private static final Map<String, Path> extraRoots = new ConcurrentHashMap<>();
  private static final Map<String, Path> siteRootCache = new ConcurrentHashMap<>();

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
    return handlerUrlFor(root, absolute, DEFAULT_ROOT_ID);
  }

  /**
   * Browser URL for a file under an explicit documentation site root (generated project docs). On
   * desktop this is the {@code file:} URI.
   */
  public static String browserUrl(Path htmlFile, Path siteRoot) {
    if (htmlFile == null) {
      return null;
    }
    Path absolute = htmlFile.toAbsolutePath().normalize();
    if (!EnvironmentUtils.getInstance().isWeb()) {
      return absolute.toUri().toString();
    }
    if (siteRoot == null) {
      return null;
    }
    Path root = siteRoot.toAbsolutePath().normalize();
    return handlerUrlFor(root, absolute, registerSiteRoot(root));
  }

  /**
   * Same-origin HTTP URL suitable for RAP {@code Browser.setUrl}. Relative handler URLs are
   * resolved against the current request so the explorer iframe can load CSS and follow links.
   */
  public static String absoluteBrowserUrl(Path htmlFile, Path siteRoot) {
    String handlerUrl = browserUrl(htmlFile, siteRoot);
    return toAbsoluteUrl(handlerUrl, currentRequestUrl());
  }

  /**
   * RAP browser URL for a documentation file identified by a Hop VFS filename (explorer tree path).
   */
  public static String absoluteBrowserUrl(String htmlFilename) {
    Path root = serveRoot(htmlFilename);
    if (root == null || Utils.isEmpty(htmlFilename)) {
      return null;
    }
    String relative = relativeFromRoot(root, htmlFilename);
    if (relative == null) {
      return null;
    }
    return toAbsoluteUrl(
        handlerUrlFor(root, relative, registerSiteRoot(root)), currentRequestUrl());
  }

  /**
   * Open generated documentation in the real browser, same pattern as plugin EDW docs ({@code
   * servicehandler=hopperEdwDocs&file=...}). Relative handler URLs resolve against the current Hop
   * Web entry ({@code /ui-dark}, {@code /ui}, …).
   */
  public static void openInBrowser(String htmlFilename) throws HopException {
    if (Utils.isEmpty(htmlFilename)) {
      throw new HopException("Documentation file name is required");
    }
    Path root = serveRoot(htmlFilename);
    String relative = relativeFromRoot(root, htmlFilename);
    String handlerUrl = handlerUrlFor(root, relative, registerSiteRoot(root));
    if (Utils.isEmpty(handlerUrl)) {
      throw new HopException("Unable to register a documentation handler for " + htmlFilename);
    }
    String url = toAbsoluteUrl(handlerUrl, currentRequestUrl());
    if (Utils.isEmpty(url)) {
      url = handlerUrl;
    }
    EnvironmentUtils.getInstance().openUrl(url);
  }

  /**
   * HTML with {@code href}/{@code src} rewritten to RAP handler URLs so {@code Browser.setText()}
   * can still load CSS when the explorer iframe has no document base.
   */
  public static String rewrittenPageHtml(String htmlFilename) {
    if (Utils.isEmpty(htmlFilename) || !isHtmlPath(htmlFilename)) {
      return null;
    }
    Path root = serveRoot(htmlFilename);
    if (root == null) {
      return null;
    }
    String relative = relativeFromRoot(root, htmlFilename);
    String handlerUrl = handlerUrlFor(root, relative, registerSiteRoot(root));
    String absolute = toAbsoluteUrl(handlerUrl, currentRequestUrl());
    if (relative == null || Utils.isEmpty(absolute)) {
      return null;
    }
    try {
      FileObject fileObject = HopVfs.getFileObject(htmlFilename);
      if (!fileObject.exists() || fileObject.getType() != FileType.FILE) {
        return null;
      }
      byte[] body;
      try (InputStream in = HopVfs.getInputStream(fileObject)) {
        body = in.readAllBytes();
      }
      return rewriteRelativeUrls(new String(body, StandardCharsets.UTF_8), absolute, relative);
    } catch (Exception ignored) {
      return null;
    }
  }

  /** Hop-doc site root when present, otherwise the HTML file's parent folder. */
  public static Path serveRoot(String htmlFilename) {
    Path site = findSiteRoot(htmlFilename);
    return site != null ? site : parentDirectory(htmlFilename);
  }

  static Path parentDirectory(String filename) {
    if (Utils.isEmpty(filename)) {
      return null;
    }
    try {
      FileObject file = HopVfs.getFileObject(filename);
      FileObject parent =
          file.exists() && file.getType() == FileType.FILE ? file.getParent() : file;
      if (parent == null) {
        return null;
      }
      return Path.of(HopVfs.getFilename(parent));
    } catch (Exception ignored) {
      try {
        Path path = Path.of(filename);
        Path parent = Files.isRegularFile(path) ? path.getParent() : path;
        return parent == null ? null : parent.toAbsolutePath().normalize();
      } catch (Exception e) {
        return null;
      }
    }
  }

  /** {@code true} when {@code path} looks like an HTML file. */
  public static boolean isHtmlPath(String path) {
    if (Utils.isEmpty(path)) {
      return false;
    }
    String name = path.toLowerCase(Locale.ROOT);
    int query = name.indexOf('?');
    if (query >= 0) {
      name = name.substring(0, query);
    }
    return name.endsWith(".html") || name.endsWith(".htm");
  }

  /**
   * Directory that contains {@code assets/css/hop-doc.css}, walking up from {@code htmlFile}. Used
   * to recognise generated project documentation.
   */
  public static Path findSiteRoot(Path htmlFile) {
    return htmlFile == null ? null : findSiteRoot(htmlFile.toString());
  }

  /** Same as {@link #findSiteRoot(Path)} for a Hop VFS filename. */
  public static Path findSiteRoot(String filename) {
    if (Utils.isEmpty(filename)) {
      return null;
    }
    Path fromVfs = findSiteRootViaVfs(filename);
    if (fromVfs != null) {
      return fromVfs;
    }
    try {
      return findSiteRootNio(Path.of(filename));
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Path findSiteRootViaVfs(String filename) {
    try {
      FileObject current = HopVfs.getFileObject(filename);
      if (current.exists() && current.getType() == FileType.FILE) {
        current = current.getParent();
      }
      String startKey = current == null ? null : HopVfs.getFilename(current);
      if (startKey != null) {
        Path cached = siteRootCache.get(startKey);
        if (cached != null) {
          return cached;
        }
      }
      while (current != null) {
        FileObject css = current.resolveFile(HOP_DOC_CSS);
        if (css.exists() && css.getType() == FileType.FILE) {
          Path root = Path.of(HopVfs.getFilename(current));
          if (startKey != null) {
            siteRootCache.put(startKey, root);
          }
          siteRootCache.put(root.toString(), root);
          return root;
        }
        current = current.getParent();
      }
    } catch (Exception ignored) {
      // Local Path walk below.
    }
    return null;
  }

  private static Path findSiteRootNio(Path htmlFile) {
    Path current = htmlFile.toAbsolutePath().normalize();
    if (Files.isRegularFile(current)) {
      current = current.getParent();
    }
    Path start = current;
    if (start != null) {
      Path cached = siteRootCache.get(start.toString());
      if (cached != null) {
        return cached;
      }
    }
    for (; current != null; current = current.getParent()) {
      Path css = current.resolve(HOP_DOC_CSS);
      if (Files.isRegularFile(css)) {
        if (start != null) {
          siteRootCache.put(start.toString(), current);
        }
        siteRootCache.put(current.toString(), current);
        return current;
      }
    }
    return null;
  }

  static String registerSiteRoot(Path siteRoot) {
    if (siteRoot == null) {
      return null;
    }
    Path root = siteRoot.toAbsolutePath().normalize();
    String id = Integer.toUnsignedString(root.toString().hashCode(), 36);
    extraRoots.put(id, root);
    return id;
  }

  static String handlerUrlFor(Path root, Path htmlFile, String rootId) {
    return handlerUrlFor(root, relativeDocFile(root, htmlFile), rootId);
  }

  static String handlerUrlFor(Path root, String relative, String rootId) {
    if (root == null || relative == null) {
      return null;
    }
    if (DEFAULT_ROOT_ID.equals(rootId) || Utils.isEmpty(rootId)) {
      docsRoot = root;
    } else {
      extraRoots.put(rootId, root);
    }
    String handlerUrl = registerAndHandlerUrl();
    if (Utils.isEmpty(handlerUrl)) {
      return null;
    }
    if (!Utils.isEmpty(rootId) && !DEFAULT_ROOT_ID.equals(rootId)) {
      handlerUrl = appendQueryParam(handlerUrl, ROOT_PARAM, rootId);
    }
    return appendFileParam(handlerUrl, relative);
  }

  static String relativeFromRoot(Path root, String htmlFilename) {
    if (root == null || Utils.isEmpty(htmlFilename)) {
      return null;
    }
    try {
      FileObject rootObject = HopVfs.getFileObject(root.toString());
      FileObject fileObject = HopVfs.getFileObject(htmlFilename);
      String relative = rootObject.getName().getRelativeName(fileObject.getName());
      relative = relative.replace('\\', '/');
      while (relative.startsWith("./")) {
        relative = relative.substring(2);
      }
      while (relative.startsWith("/")) {
        relative = relative.substring(1);
      }
      if (relative.isEmpty() || relative.startsWith("..")) {
        return relativeDocFile(root, Path.of(htmlFilename));
      }
      return relative;
    } catch (Exception ignored) {
      try {
        return relativeDocFile(root, Path.of(htmlFilename));
      } catch (Exception e) {
        return null;
      }
    }
  }

  static String toAbsoluteUrl(String handlerUrl, String requestUrl) {
    if (Utils.isEmpty(handlerUrl)) {
      return handlerUrl;
    }
    String trimmed = handlerUrl.trim();
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
      return trimmed;
    }
    if (Utils.isEmpty(requestUrl)) {
      return trimmed;
    }
    int queryAt = trimmed.indexOf('?');
    if (queryAt >= 0) {
      return requestUrl + trimmed.substring(queryAt);
    }
    try {
      return URI.create(requestUrl).resolve(trimmed).toString();
    } catch (Exception ignored) {
      return trimmed;
    }
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
    return appendQueryParam(handlerUrl, FILE_PARAM, relativeFile);
  }

  static String appendQueryParam(String handlerUrl, String name, String value) {
    if (Utils.isEmpty(handlerUrl) || Utils.isEmpty(name) || Utils.isEmpty(value)) {
      return handlerUrl;
    }
    String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    String separator = handlerUrl.contains("?") ? "&" : "?";
    return handlerUrl + separator + name + "=" + encoded;
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
    String rootId =
        (String)
            request.getClass().getMethod("getParameter", String.class).invoke(request, ROOT_PARAM);
    Path root = rootForId(rootId);
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
    String handlerUrl = currentHandlerUrl(request, rootId);
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

  static Path rootForId(String rootId) {
    if (Utils.isEmpty(rootId) || DEFAULT_ROOT_ID.equals(rootId)) {
      return docsRoot;
    }
    Path extra = extraRoots.get(rootId);
    return extra != null ? extra : docsRoot;
  }

  private static String currentRequestUrl() {
    try {
      Class<?> rwtClass = Class.forName("org.eclipse.rap.rwt.RWT");
      Object request = rwtClass.getMethod("getRequest").invoke(null);
      Object requestUrl = request.getClass().getMethod("getRequestURL").invoke(request);
      return requestUrl != null ? requestUrl.toString() : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String currentHandlerUrl(Object request, String rootId) {
    String url = null;
    try {
      Class<?> rwtClass = Class.forName("org.eclipse.rap.rwt.RWT");
      Object serviceManager = rwtClass.getMethod("getServiceManager").invoke(null);
      Object handlerUrl =
          serviceManager
              .getClass()
              .getMethod("getServiceHandlerUrl", String.class)
              .invoke(serviceManager, SERVICE_ID);
      if (handlerUrl != null) {
        url = handlerUrl.toString();
      }
    } catch (Exception ignored) {
      // Reconstruct from the request URL below.
    }
    if (Utils.isEmpty(url)) {
      try {
        Object requestUrl = request.getClass().getMethod("getRequestURL").invoke(request);
        Object query = request.getClass().getMethod("getQueryString").invoke(request);
        url = requestUrl != null ? requestUrl.toString() : "";
        if (query != null && !query.toString().isBlank()) {
          url = url + "?" + stripFileParam(query.toString());
        }
      } catch (Exception ignored) {
        return null;
      }
    }
    if (!Utils.isEmpty(url)
        && !Utils.isEmpty(rootId)
        && !DEFAULT_ROOT_ID.equals(rootId)
        && !url.contains(ROOT_PARAM + "=")) {
      url = appendQueryParam(url, ROOT_PARAM, rootId);
    }
    return url;
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
