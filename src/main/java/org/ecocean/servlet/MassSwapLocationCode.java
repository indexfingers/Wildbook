/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.jdo.Extent;
import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;


//handles operations to sharks. possible operations include, create new, add encounter, remove encounter from
public class MassSwapLocationCode extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    boolean madeChanges = false;
    int count = 0;

    String newLocCode = "", oldLocCode = "";
    oldLocCode = request.getParameter("oldLocCode");
    newLocCode = request.getParameter("newLocCode");
    Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
    Query query = myShepherd.getPM().newQuery(encClass);

    if ((oldLocCode != null) && (oldLocCode != null) && (!newLocCode.equals("")) && (!newLocCode.equals(""))) {
      myShepherd.beginDBTransaction();
      try {
        Iterator it = myShepherd.getAllEncounters(query);

        while (it.hasNext()) {
          Encounter tempEnc = (Encounter) it.next();
          if (tempEnc.getLocationCode().equals(oldLocCode)) {
            tempEnc.setLocationCode(newLocCode);
            madeChanges = true;
            count++;
          }
        } //end while
      } catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      if (!madeChanges) {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      //success!!!!!!!!

      else if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println(("<strong>Success!</strong> I have successfully changed the location code " + oldLocCode + " to " + newLocCode + " for " + count + " encounters."));
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
      }
      //failure due to exception
      else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> An encounter is currently being modified by another user. Please wait a few seconds before trying to remove this data file again.");
        out.println(ServletUtilities.getFooter(context));
      }
      query.closeAll();
    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to set the location code as requested due to missing parameter values.");
      out.println(ServletUtilities.getFooter(context));
    }
    out.close();
  }

}
