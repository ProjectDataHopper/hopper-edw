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
 *
 */

package org.apache.hop.datavault.metadata;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlFormatter;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;

/** Formats and writes `.hdv`, `.hbv`, `.hdm`, `.hem`, and `.hsm` XML documents. */
public final class ModelXmlWriteSupport {

  private ModelXmlWriteSupport() {}

  public static IVariables resolveVariables(IVariables variables) {
    return variables != null ? variables : Variables.getADefaultVariableSpace();
  }

  public static String formatModelXml(String xmlTag, Object model, IVariables variables)
      throws HopException {
    return formatModelXml(xmlTag, XmlMetadataUtil.serializeObjectToXml(model), variables);
  }

  public static String formatModelXml(String xmlTag, String innerXml, IVariables variables)
      throws HopException {
    return XmlHandler.getLicenseHeader(resolveVariables(variables))
        + XmlFormatter.format(XmlHandler.aroundTag(xmlTag, innerXml));
  }

  public static void writeToFile(String xml, String filename) throws HopException {
    try (OutputStream out = HopVfs.getOutputStream(filename, false)) {
      out.write(xml.getBytes(StandardCharsets.UTF_8));
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to write model XML to: " + filename, e);
    }
  }

  public static void writeModelXml(
      String xmlTag, Object model, String filename, IVariables variables) throws HopException {
    writeToFile(formatModelXml(xmlTag, model, variables), filename);
  }
}