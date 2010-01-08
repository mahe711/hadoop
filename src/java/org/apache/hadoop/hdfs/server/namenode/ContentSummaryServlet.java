/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.namenode;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.hdfs.protocol.ClientProtocol;
import org.apache.hadoop.ipc.RemoteException;
import org.apache.hadoop.security.UnixUserGroupInformation;
import org.znerd.xmlenc.XMLOutputter;

/** Servlets for file checksum */
public class ContentSummaryServlet extends DfsServlet {
  /** For java.io.Serializable */
  private static final long serialVersionUID = 1L;
  
  /** {@inheritDoc} */
  public void doGet(HttpServletRequest request, HttpServletResponse response
      ) throws ServletException, IOException {
    final UnixUserGroupInformation ugi = getUGI(request);
    final String path = request.getPathInfo();

    final PrintWriter out = response.getWriter();
    final XMLOutputter xml = new XMLOutputter(out, "UTF-8");
    xml.declaration();
    try {
      //get content summary
      final ClientProtocol nnproxy = createNameNodeProxy(ugi);
      final ContentSummary cs = nnproxy.getContentSummary(path);

      //write xml
      xml.startTag(ContentSummary.class.getName());
      if (cs != null) {
        xml.attribute("length"        , "" + cs.getLength());
        xml.attribute("fileCount"     , "" + cs.getFileCount());
        xml.attribute("directoryCount", "" + cs.getDirectoryCount());
        xml.attribute("quota"         , "" + cs.getQuota());
        xml.attribute("spaceConsumed" , "" + cs.getSpaceConsumed());
        xml.attribute("spaceQuota"    , "" + cs.getSpaceQuota());
      }
      xml.endTag();
    } catch(IOException ioe) {
      new RemoteException(ioe.getClass().getName(), ioe.getMessage()
          ).writeXml(path, xml);
    }
    xml.endDocument();
  }
}
