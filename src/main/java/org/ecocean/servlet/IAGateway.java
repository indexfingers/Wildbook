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
import org.ecocean.Util;
import org.ecocean.RestClient;
import org.ecocean.Annotation;
import org.ecocean.Occurrence;
import org.ecocean.Cluster;
import org.ecocean.Resolver;
import org.ecocean.media.*;
import org.ecocean.identity.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONArray;

import java.net.URL;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import javax.jdo.Query;

import java.io.InputStream;
import java.util.UUID;

public class IAGateway extends HttpServlet {

    private static final int ERROR_CODE_NO_REVIEWS = 410;

    private static final int IDENTIFICATION_REVIEWS_BEFORE_SEND = 2;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
    String getOut = "";

    if (request.getParameter("getJobResult") != null) {
        JSONObject res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
        try {
            res = IBEISIA.getJobResult(request.getParameter("getJobResult"));
        } catch (Exception ex) {
            throw new IOException(ex.toString());
        }
        response.setContentType("text/plain");
        getOut = res.toString();

    } else if (request.getParameter("status") != null) {
        response.setContentType("text/plain");
        getOut = IBEISIA.iaStatus(request).toString();

///////////////
    } else if (request.getParameter("getJobResultFromTaskID") != null) {
        JSONObject res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        String taskID = request.getParameter("getJobResultFromTaskID");


        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, "IBEISIA", myShepherd);
        if ((logs == null) || (logs.size() < 1)) {
            res.put("error", "could not find any record for task ID = " + taskID);

        } else {
            JSONObject last = logs.get(logs.size() - 1).getStatusJson();
            res.put("_debug", last);
// note: jobstatus == completed seems to be the thing we want
            if ("getJobStatus".equals(last.getString("_action")) && "unknown".equals(last.getJSONObject("_response").getJSONObject("response").getString("jobstatus"))) {
                res.put("details", last.get("_response"));
                res.put("error", "final log for task " + taskID + " was an unknown jobstatus, so results were not obtained");

            } else if (last.getString("_action").equals("getJobResult") && (last.optJSONObject("_response") != null)) {
                res = last.getJSONObject("_response");

/*  this gets results live from IA - problematic cuz of reboots and it resets jobs.  :(
            try {
                res = IBEISIA.getJobResult(jobID);
            } catch (Exception ex) {
                throw new IOException(ex.toString());
            }
*/

            if ((res != null) && (res.optJSONObject("response") != null) && (res.getJSONObject("response").optJSONArray("json_result") != null)) {
                JSONObject firstResult = res.getJSONObject("response").getJSONArray("json_result").optJSONObject(0);
                if (firstResult != null) {
System.out.println("firstResult -> " + firstResult.toString());
                    res.put("queryAnnotation", expandAnnotation(IBEISIA.fromFancyUUID(firstResult.optJSONObject("qauuid")), myShepherd, request));
                    JSONArray matches = firstResult.optJSONArray("dauuid_list");
                    JSONArray scores = firstResult.optJSONArray("score_list");
                    JSONArray mout = new JSONArray();
                    if (matches != null) {
                        for (int i = 0 ; i < matches.length() ; i++) {
                            JSONObject aj = expandAnnotation(IBEISIA.fromFancyUUID(matches.optJSONObject(i)), myShepherd, request);
                            if (aj != null) {
                                if (scores != null) aj.put("score", scores.optDouble(i, -1.0));
                                mout.put(aj);
                            }
                        }
                    }
                    res.put("matchAnnotations", mout);
                }
            }
            }

        }

        response.setContentType("text/plain");
        getOut = res.toString();
/////////////

    } else if (request.getParameter("getDetectionReviewHtml") != null) {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        String jobID = request.getParameter("getDetectionReviewHtml");
        int offset = 0;
        if (request.getParameter("offset") != null) {
            try {
                offset = Integer.parseInt(request.getParameter("offset"));
            } catch (NumberFormatException ex) {}
        }
        JSONObject res = null;
        try {
            res = IBEISIA.getJobResultLogged(jobID, myShepherd);
        } catch (Exception ex) {
            throw new IOException(ex.toString());
        }
System.out.println("res(" + jobID + "[" + offset + "]) -> " + res);
        getOut = _detectionHtmlFromResult(res, request, offset, null);
        setErrorCode(response, getOut);

    } else if (request.getParameter("getDetectionReviewHtmlNext") != null) {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        ArrayList<MediaAsset> mas = mineNeedingDetectionReview(request, myShepherd);
        if ((mas == null) || (mas.size() < 1)) {
            response.sendError(ERROR_CODE_NO_REVIEWS, "No detection reviews pending");
            getOut = "<div>no detections needing review</div>";
        } else {
            MediaAsset ma = mas.get((int)(Math.random() * mas.size()));
            ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA", Integer.toString(ma.getId()), myShepherd);
            JSONObject res = null;
            for (IdentityServiceLog log : logs) {
                if ((log.getStatusJson() == null) || !log.getStatusJson().optString("_action", "FAIL").equals("getJobResult")) continue;
                res = log.getStatusJson().optJSONObject("_response");
                if (res != null) break;
            }
            if (res != null) res.put("_mediaAssetId", ma.getId());
    System.out.println("res(" + ma.toString() + ") -> " + res);
            getOut = _detectionHtmlFromResult(res, request, -1, ma.getUUID());
            setErrorCode(response, getOut);
        }

    //ugh, lets standardize on passing taskId, not jobid cuz jobid sucks
    } else if (request.getParameter("getIdentificationReviewHtml") != null) {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        String taskId = request.getParameter("getIdentificationReviewHtml");
        int offset = 0;
        if (request.getParameter("offset") != null) {
            try {
                offset = Integer.parseInt(request.getParameter("offset"));
            } catch (NumberFormatException ex) {}
        }
        JSONObject res = null;
        try {
            res = IBEISIA.getTaskResults(taskId, myShepherd);
        } catch (Exception ex) {
            throw new IOException(ex.toString());
        }
System.out.println("res(" + taskId + "[" + offset + "]) -> " + res);
        IBEISIA.setActiveTaskId(request, taskId);
        getOut = _identificationHtmlFromResult(res, request, taskId, offset, null);
        setErrorCode(response, getOut);

    } else if (request.getParameter("getIdentificationReviewHtmlNext") != null) {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        String taskId = IBEISIA.getActiveTaskId(request);
System.out.println("getIdentificationReviewHtmlNext -> taskId = " + taskId);
        if (taskId == null) {
            ArrayList<Annotation> anns = mineNeedingIdentificationReview(request, myShepherd);
System.out.println("anns -> " + anns);
            if ((anns != null) && (anns.size() > 0)) {
                Annotation ann = anns.get((int)(Math.random() * anns.size()));
System.out.println("INFO: could not find activeTaskId, so finding taskId for " + ann);
                ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA", ann.getId(), myShepherd);
                for (IdentityServiceLog l : logs) {
                    if (l.getTaskID() != null) {
                        taskId = l.getTaskID();
                        break;
                    }
                }
            }
        }
        if (taskId == null) {
            response.sendError(ERROR_CODE_NO_REVIEWS, "No identification reviews pending");
            getOut = "<div class=\"no-identification-reviews\">no identifications needing review</div>";
        } else {
            JSONObject res = null;
            try {
                res = IBEISIA.getTaskResults(taskId, myShepherd);
            } catch (Exception ex) {
                throw new IOException(ex.toString());
            }
System.out.println("Next: res(" + taskId + ") -> " + res);
            IBEISIA.setActiveTaskId(request, taskId);
            getOut = _identificationHtmlFromResult(res, request, taskId, -1, null);
            setErrorCode(response, getOut);
        }
/*
    } else if (request.getParameter("getIdentificationReviewHtmlNextOLD") != null) {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        ArrayList<Annotation> anns = mineNeedingIdentificationReview(request, myShepherd);
        if ((anns == null) || (anns.size() < 1)) {
            getOut = "<div>no identifications needing review</div>";
        } else {
            Annotation ann = anns.get((int)(Math.random() * anns.size()));
            ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA", ann.getId(), myShepherd);
            JSONObject res = null;
            for (IdentityServiceLog log : logs) {
                if ((log.getStatusJson() == null) || !log.getStatusJson().optString("_action", "FAIL").equals("getJobResult")) continue;
                res = log.getStatusJson().optJSONObject("_response");
                if (res != null) break;
            }
            //this is to munge the format into that of getTaskResults() as above non-Next version
            if ((res != null) && (res.optJSONObject("results") == null) && (res.optJSONObject("response") != null))
                res.put("results", res.getJSONObject("response").optJSONObject("json_result"));
    System.out.println("res(" + ann.toString() + ") -> " + res);
            getOut = _identificationHtmlFromResult(res, request, -1, ann.getId());
        }
*/

    } else {
        response.sendError(501, "Unknown command");
        getOut = "Unknown command";
    }

    PrintWriter out = response.getWriter();
    out.println(getOut);
    out.close();
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost

    String qstr = request.getQueryString();
    if ("detectionReviewPost".equals(qstr)) {
        String url = CommonConfiguration.getProperty("IBEISIARestUrlDetectReview", "context0");
        if (url == null) throw new IOException("IBEISIARestUrlDetectReview url not set");
System.out.println("attempting passthru to " + url);
        URL u = new URL(url);
        JSONObject rtn = new JSONObject("{\"success\": false}");
        try {
            rtn = RestClient.postStream(u, request.getInputStream());
        } catch (Exception ex) {
            rtn.put("error", ex.toString());
        }

System.out.println("############################ rtn -> \n" + rtn);
        //this maybe should be broken out into a method?
        if ((rtn.optJSONObject("status") != null) && rtn.getJSONObject("status").optBoolean("success", false) && (rtn.optJSONObject("response") != null)) {
            String context = ServletUtilities.getContext(request);
            Shepherd myShepherd = new Shepherd(context);
            JSONArray slist = rtn.getJSONObject("response").optJSONArray("score_list");
            JSONArray rlist = rtn.getJSONObject("response").optJSONArray("results_list");
            JSONArray ilist = rtn.getJSONObject("response").optJSONArray("image_uuid_list");
            if ((slist != null) && (rlist != null) && (ilist != null)) {
                JSONObject annsMade = new JSONObject();
                FeatureType.initAll(myShepherd);
                for (int i = 0 ; i < slist.length() ; i++) {
                    int stillNeedingReview = rlist.length();
                    if (slist.optDouble(i, -1.0) < IBEISIA.getDetectionCutoffValue()) continue;
                    /* decrementing this here, which may be "unwise" since this can "fail" in a bunch of ways (below); however, most of them
                       are error-like in nature, and should be dealt with in ways which i feel arent handled well yet.  this likely needs
                       to be reconsidered at some point... sigh.  -jon 20160613    TODO */
                    stillNeedingReview--;
                    JSONArray alist = rlist.optJSONArray(i);
                    if ((alist == null) || (alist.length() < 1)) continue;
                    String uuid = IBEISIA.fromFancyUUID(ilist.optJSONObject(i));
                    if (uuid == null) continue;
                    MediaAsset ma = MediaAssetFactory.loadByUuid(uuid, myShepherd);
System.out.println("i=" + i + " r[i] = " + alist.toString() + "; iuuid=" + uuid + " -> ma:" + ma);
                    if (ma == null) continue;
                    JSONArray thisAnns = new JSONArray();
                    for (int a = 0 ; a < alist.length() ; a++) {
                        JSONObject jann = alist.optJSONObject(a);
                        if (jann == null) continue;
                        Annotation ann = IBEISIA.createAnnotationFromIAResult(jann, ma, myShepherd);
                        if (ann == null) continue;
                        myShepherd.getPM().makePersistent(ann);
                        thisAnns.put(ann.getId());
                    }
                    if (thisAnns.length() > 0) annsMade.put(Integer.toString(ma.getId()), thisAnns);
                    if (stillNeedingReview < 1) ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE);
                }
                rtn.put("annotationsMade", annsMade);
            }
        }

        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        out.println(rtn.toString());
        out.close();
        return;
    }


    if ((qstr != null) && (qstr.indexOf("identificationReviewPost") > -1)) {
        String taskId = qstr.substring(25);
        String url = CommonConfiguration.getProperty("IBEISIARestUrlIdentifyReview", "context0"); //note: cant set context above, cuz getContext() messes up postStream()!
        if (url == null) throw new IOException("IBEISIARestUrlIdentifyReview url not set");
System.out.println("[taskId=" + taskId + "] attempting passthru to " + url);
        URL u = new URL(url);
        JSONObject rtn = new JSONObject("{\"success\": false}");
        try {
            rtn = RestClient.postStream(u, request.getInputStream());
        } catch (Exception ex) {
            rtn.put("error", ex.toString());
        }
        String context = ServletUtilities.getContext(request);
        if ((rtn.optJSONObject("status") != null) && rtn.getJSONObject("status").optBoolean("success", false)) {
            JSONArray match = rtn.optJSONArray("response");
            if ((match != null) && (match.optJSONObject(0) != null) && (match.optJSONObject(1) != null)) {
                Shepherd myShepherd = new Shepherd(context);
                String a1 = IBEISIA.fromFancyUUID(match.optJSONObject(0));
                String a2 = IBEISIA.fromFancyUUID(match.optJSONObject(1));
                String state = match.optString(2, "UNKNOWN_MATCH_STATE");
                IBEISIA.setIdentificationMatchingState(a1, a2, state, myShepherd);
                JSONObject jlog = new JSONObject("{\"_action\": \"identificationReviewPost\"}");
                jlog.put("state", new JSONArray(new String[]{a1, a2, state}));
                IBEISIA.log(taskId, a1, null, jlog, context);
                checkIdentificationIterationStatus(a1, taskId, request);
            }
        }
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        out.println(rtn.toString());
        out.close();
        return;
    }


    String context = ServletUtilities.getContext(request);  //note! this *must* be run after postStream stuff above
    Shepherd myShepherd = new Shepherd(context);

    response.setContentType("text/plain");
    PrintWriter out = response.getWriter();

    JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
    JSONObject res = new JSONObject();
    String taskId = Util.generateUUID();
    res.put("taskId", taskId);

    /* proposal: for multi-algorithm support, break out all calls here into their own class (subclassed from identity class or whateve)
        and then access by service-name as a key.  attempting to use this pattern with BenWhiteshark now....  */
    if (j.optJSONObject("BenWhiteshark") != null) {
        res = BenWhiteshark.iaGateway(j.getJSONObject("BenWhiteshark"), request);


    } else if (j.optJSONArray("detect") != null) {
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        List<MediaAsset> needOccurrences = new ArrayList<MediaAsset>();
        JSONArray ids = j.getJSONArray("detect");
        ArrayList<String> validIds = new ArrayList<String>();
        for (int i = 0 ; i < ids.length() ; i++) {
            int id = ids.optInt(i, 0);
System.out.println(id);
            if (id < 1) continue;
            MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
            if (ma != null) {
                ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING);
                mas.add(ma);
                validIds.add(Integer.toString(id));
                if (ma.getOccurrence() == null) needOccurrences.add(ma);
            }
        }
        if (mas.size() > 0) {
            if (needOccurrences.size() > 0) {  //first we make occurrences where needed
                List<Occurrence> occs = Cluster.defaultCluster(needOccurrences, myShepherd);
                res.put("_occurrenceNote", "created " + occs.size() + " Occurrences out of " + mas.size() + " MediaAssets");
            }

            boolean success = true;
            try {
                String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
                res.put("sendMediaAssets", IBEISIA.sendMediaAssets(mas));
                JSONObject sent = IBEISIA.sendDetect(mas, baseUrl);
                res.put("sendDetect", sent);
                String jobId = null;
                if ((sent.optJSONObject("status") != null) && sent.getJSONObject("status").optBoolean("success", false))
                    jobId = sent.optString("response", null);
                res.put("jobId", jobId);
                IBEISIA.log(taskId, validIds.toArray(new String[validIds.size()]), jobId, new JSONObject("{\"_action\": \"initDetect\"}"), context);
            } catch (Exception ex) {
                success = false;
                throw new IOException(ex.toString());
            }
            if (!success) {
                for (MediaAsset ma : mas) {
                    ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                }
            }
        }
        res.put("success", true);

    } else if (j.optJSONObject("identify") != null) {
        ArrayList<Annotation> anns = new ArrayList<Annotation>();  //what we ultimately run on.  occurrences are irrelevant now right?
        ArrayList<String> validIds = new ArrayList<String>();
        int limitTargetSize = j.optInt("limitTargetSize", -1);  //really "only" for debugging/testing, so use if you know what you are doing

        //currently this implies each annotation should be sent one-at-a-time TODO later will be allow clumping (to be sent as multi-annotation
        //  query lists.... *when* that is supported by IA
        JSONArray alist = j.getJSONObject("identify").optJSONArray("annotationIds");
        if ((alist != null) && (alist.length() > 0)) {
            for (int i = 0 ; i < alist.length() ; i++) {
                String aid = alist.optString(i, null);
                if (aid == null) continue;
                Annotation ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, aid), true)));
                if (ann == null) continue;
                anns.add(ann);
                validIds.add(aid);
            }
        }

        //i think that "in the future" co-occurring annotations should be sent together as one set of query list; but since we dont have support for that
        // now, we just send these all in one at a time.  hope that is good enough!   TODO
        JSONArray olist = j.getJSONObject("identify").optJSONArray("occurrenceIds");
        if ((olist != null) && (olist.length() > 0)) {
            for (int i = 0 ; i < olist.length() ; i++) {
                String oid = olist.optString(i, null);
                if (oid == null) continue;
                Occurrence occ = ((Occurrence) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Occurrence.class, oid), true)));
//System.out.println("occ -> " + occ);
                if (occ == null) continue;
                List<MediaAsset> mas = occ.getAssets();
//System.out.println("mas -> " + mas);
                if ((mas == null) || (mas.size() < 1)) continue;
                for (MediaAsset ma : mas) {
                    ArrayList<Annotation> maAnns = ma.getAnnotations();
//System.out.println("maAnns -> " + maAnns);
                    if ((maAnns == null) || (maAnns.size() < 1)) continue;
                    for (Annotation ann : maAnns) {
                        if (validIds.contains(ann.getId())) continue;
                        anns.add(ann);
                        validIds.add(ann.getId());
                    }
                }
            }
        }
System.out.println("anns -> " + anns);

        JSONArray taskList = new JSONArray();
/* currently we are sending annotations one at a time (one per query list) but later we will have to support clumped sets...
   things to consider for that - we probably have to further subdivide by species ... other considerations?   */
        for (Annotation ann : anns) {
            JSONObject taskRes = _sendIdentificationTask(ann, request, null, null, limitTargetSize);
/*
            String species = ann.getSpecies();
            if ((species == null) || (species.equals(""))) throw new IOException("species on Annotation " + ann + " invalid: " + species);
            boolean success = true;
            String annTaskId = Util.generateUUID();
            JSONObject taskRes = new JSONObject();
            taskRes.put("taskId", annTaskId);
            JSONArray jids = new JSONArray();
            jids.put(ann.getId());  //for now there is only one
            taskRes.put("annotationIds", jids);
            try {
                String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
                //TODO we might want to cache this examplars list (per species) yes?
                ArrayList<Annotation> exemplars = Annotation.getExemplars(species, myShepherd);
                if ((exemplars == null) || (exemplars.size() < 10)) throw new IOException("suspiciously empty exemplar set for species " + species);
                if ((limitTargetSize > -1) && (exemplars.size() > limitTargetSize)) {
                    res.put("_limitTargetSize", limitTargetSize);
                    System.out.println("WARNING: limited identification exemplar list size from " + exemplars.size() + " to " + limitTargetSize);
                    exemplars = new ArrayList(exemplars.subList(0, limitTargetSize));
                }
                taskRes.put("exemplarsSize", exemplars.size());
                ArrayList<Annotation> qanns = new ArrayList<Annotation>();
                qanns.add(ann);
                JSONObject sent = IBEISIA.beginIdentifyAnnotations(qanns, exemplars, myShepherd, species, annTaskId, baseUrl, context);
                taskRes.put("beginIdentify", sent);
                String jobId = null;
                if ((sent.optJSONObject("status") != null) && sent.getJSONObject("status").optBoolean("success", false))
                    jobId = sent.optString("response", null);
                taskRes.put("jobId", jobId);
                //validIds.toArray(new String[validIds.size()])
                IBEISIA.log(annTaskId, ann.getId(), jobId, new JSONObject("{\"_action\": \"initIdentify\"}"), context);
            } catch (Exception ex) {
                success = false;
                throw new IOException(ex.toString());
            }
/* TODO ?????????
            if (!success) {
                for (MediaAsset ma : mas) {
                    ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                }
            }
*/
            taskList.put(taskRes);
        }
        if (limitTargetSize > -1) res.put("_limitTargetSize", limitTargetSize);
        res.put("tasks", taskList);
        res.put("success", true);


    } else if (j.optJSONObject("resolver") != null) {
        res = Resolver.processAPIJSONObject(j.getJSONObject("resolver"), myShepherd);

    } else if (j.optString("taskIds", null) != null) {  //pass annotation id
        res.put("taskIds", IBEISIA.findTaskIDsFromObjectID(j.getString("taskIds"), myShepherd));
        res.put("success", true);

    } else if (j.optJSONArray("taskSummary") != null) {  //pass annotation ids
        res.put("taskSummary", taskSummary(j.getJSONArray("taskSummary"), myShepherd));
        res.put("success", true);

    } else {
        res.put("error", "unknown POST command");
        res.put("success", false);
    }

    res.put("_in", j);

    out.println(res.toString());
    out.close();
    //myShepherd.closeDBTransaction();
  }


    private JSONObject _sendIdentificationTask(Annotation ann, HttpServletRequest request, JSONObject queryConfigDict,
                                               JSONObject userConfidence, int limitTargetSize) throws IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        String species = ann.getSpecies();
        if ((species == null) || (species.equals(""))) throw new IOException("species on Annotation " + ann + " invalid: " + species);
        boolean success = true;
        String annTaskId = Util.generateUUID();
        JSONObject taskRes = new JSONObject();
        taskRes.put("taskId", annTaskId);
        JSONArray jids = new JSONArray();
        jids.put(ann.getId());  //for now there is only one
        taskRes.put("annotationIds", jids);
        try {
            String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
            //TODO we might want to cache this examplars list (per species) yes?
            ArrayList<Annotation> exemplars = Annotation.getExemplars(species, myShepherd);
            if ((exemplars == null) || (exemplars.size() < 10)) throw new IOException("suspiciously empty exemplar set for species " + species);
            if ((limitTargetSize > -1) && (exemplars.size() > limitTargetSize)) {
                System.out.println("WARNING: limited identification exemplar list size from " + exemplars.size() + " to " + limitTargetSize);
                exemplars = new ArrayList(exemplars.subList(0, limitTargetSize));
            }
            taskRes.put("exemplarsSize", exemplars.size());
            ArrayList<Annotation> qanns = new ArrayList<Annotation>();
            qanns.add(ann);
            JSONObject sent = IBEISIA.beginIdentifyAnnotations(qanns, exemplars, queryConfigDict, userConfidence,
                                                               myShepherd, species, annTaskId, baseUrl, context);
            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING);
            taskRes.put("beginIdentify", sent);
            String jobId = null;
            if ((sent.optJSONObject("status") != null) && sent.getJSONObject("status").optBoolean("success", false))
                jobId = sent.optString("response", null);
            taskRes.put("jobId", jobId);
            //validIds.toArray(new String[validIds.size()])
            IBEISIA.log(annTaskId, ann.getId(), jobId, new JSONObject("{\"_action\": \"initIdentify\"}"), context);
        } catch (Exception ex) {
            success = false;
            throw new IOException(ex.toString());
        }
/* TODO ?????????
            if (!success) {
                for (MediaAsset ma : mas) {
                    ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                }
            }
*/
        return taskRes;
    }

    private String _detectionHtmlFromResult(JSONObject res, HttpServletRequest request, int offset, String maUUID) throws IOException {
        String getOut = "";
        if ((res == null) || (res.optJSONObject("response") == null) || (res.getJSONObject("response").optJSONObject("json_result") == null) || (res.getJSONObject("response").getJSONObject("json_result").optJSONArray("results_list") == null) || (res.getJSONObject("response").getJSONObject("json_result").optJSONArray("image_uuid_list") == null)) {
            getOut = "<div error-code=\"557\" class=\"response-error\">unable to obtain detection interface</div>";
            System.out.println("ERROR: invalid res for _detectionHtmlFromResult: " + res);
        } else {
            JSONArray rlist = res.getJSONObject("response").getJSONObject("json_result").getJSONArray("results_list");
            JSONArray ilist = res.getJSONObject("response").getJSONObject("json_result").getJSONArray("image_uuid_list");
            if (maUUID != null) {
                offset = -1;
                for (int i = 0 ; i < ilist.length() ; i++) {
                    if (maUUID.equals(IBEISIA.fromFancyUUID(ilist.getJSONObject(i)))) {
                        offset = i;
                        break;
                    }
                }
                if (offset < 0) {
                    System.out.println("ERROR: could not find uuid " + maUUID + " in res: " + res.toString());
                    return "<div error-code=\"558\" class=\"response-error\">unable to find MediaAsset for detection</div>";
                }
            }
            if ((offset > rlist.length() - 1) || (offset < 0)) offset = 0;
            if (offset > ilist.length() - 1) offset = 0;

            int mediaAssetId = res.optInt("_mediaAssetId", -1);
            if ((mediaAssetId < 0) && (res.optJSONArray("_objectIds") != null)) {
                JSONArray jobj = res.getJSONArray("_objectIds");
                for (int i = 0 ; i < jobj.length() ; i++) {
                    int mid = jobj.optInt(i, -1);
                    if (mid < 0) continue;
                    if (IBEISIA.fromFancyUUID(ilist.getJSONObject(offset)).equals(mediaAssetIdToUUID(mid))) {
                        mediaAssetId = mid;
                        break;
                    }
                }
            }

            String url = CommonConfiguration.getProperty("IBEISIARestUrlDetectReview", "context0");
            if (url == null) throw new IOException("IBEISIARestUrlDetectionReview url not set");
            url += "?image_uuid=" + ilist.getJSONObject(offset).toString() + "&";
            url += "result_list=" + rlist.getJSONArray(offset).toString() + "&";
            try {
                url += "callback_url=" + CommonConfiguration.getServerURL(request, request.getContextPath()) + "/ia%3FdetectionReviewPost&callback_method=POST";
System.out.println("url --> " + url);
                URL u = new URL(url);
                JSONObject rtn = RestClient.get(u);
                if ((rtn.optString("response", null) == null) || (rtn.optJSONObject("status") == null) ||
                    !rtn.getJSONObject("status").optBoolean("success", false)) {
                    getOut = "<div error-code=\"559\" class=\"response-error\">invalid response: <xmp>" + rtn.toString() + "</xmp></div>";
                } else {
                    getOut = rtn.getString("response");
                    if (request.getParameter("test") != null) {
                        getOut = "<html><head><script src=\"https://ajax.googleapis.com/ajax/libs/jquery/2.2.0/jquery.min.js\"></script></head><body>" + getOut + "</body></html>";
                    }
                }
            } catch (Exception ex) {
                getOut = "<div error-code=\"560\" class=\"response-error\">Error: " + ex.toString() + "</div>";
            }

            if (mediaAssetId >= 0) getOut += "<input type=\"hidden\" name=\"mediaasset-id\" value=\"" + mediaAssetId + "\" />";
        }
        return getOut;
    }

    private String _identificationHtmlFromResult(JSONObject res, HttpServletRequest request, String taskId, int offset, String annId) throws IOException {
        String getOut = "";
        if ((res == null) || (res.optJSONObject("results") == null) || (res.getJSONObject("results").optJSONObject("inference_dict") == null) ||
            (res.getJSONObject("results").getJSONObject("inference_dict").optJSONObject("annot_pair_dict") == null) ||
            (res.getJSONObject("results").getJSONObject("inference_dict").getJSONObject("annot_pair_dict").optJSONArray("review_pair_list") == null)) {
                System.out.println("ERROR: (no review_pair_list?) invalid res for _identificationHtmlFromResult: " + res);
                return "<div error-code=\"551\" title=\"error 1\" class=\"response-error\">unable to obtain identification interface</div>";
        }

        JSONArray rlist = res.getJSONObject("results").getJSONObject("inference_dict").getJSONObject("annot_pair_dict").getJSONArray("review_pair_list");
        JSONObject rpair = null;
        if (offset >= 0) {
            if (offset > rlist.length() - 1) offset = 0;
            rpair = rlist.optJSONObject(offset);
        } else {
            String context = ServletUtilities.getContext(request);
            Shepherd myShepherd = new Shepherd(context);
            rpair = getAvailableIdentificationReviewPair(rlist, annId, myShepherd);
System.out.println("getAvailableIdentificationReviewPair(" + annId + ") -> " + rpair);
        }
        if (rpair == null) {
            System.out.println("ERROR: could not determine rpair from " + rlist.toString());
            return "<div error-code=\"552\" class=\"response-error\" title=\"error 2\">unable to obtain identification interface</div>";
        }

        String url = CommonConfiguration.getProperty("IBEISIARestUrlIdentifyReview", "context0");
        if (url == null) throw new IOException("IBEISIARestUrlIdentifyReview url not set");
        url += "?query_config_dict=" + res.getJSONObject("results").optJSONObject("query_config_dict").toString() + "&";
        url += "review_pair=" + rpair.toString() + "&";
        String quuid = IBEISIA.fromFancyUUID(rpair.optJSONObject("annot_uuid_1"));
        if (quuid == null) {
            getOut = "<div error-code=\"553\" class=\"response-error\" title=\"error 3\">unable to obtain identification interface</div>";
            System.out.println("ERROR: could not determine query annotation uuid for _identificationHtmlFromResult: " + res);
            return getOut;
        }
        if ((res.getJSONObject("results").optJSONObject("cm_dict") == null) || (res.getJSONObject("results").getJSONObject("cm_dict").optJSONObject(quuid) == null)) {
            getOut = "<div error-code=\"554\" class=\"response-error\" title=\"error 4\">unable to obtain identification interface</div>";
            System.out.println("ERROR: could not determine cm_dict for quuid=" + quuid + " for _identificationHtmlFromResult: " + res);
            return getOut;
        }
        url += "cm_dict=" + res.getJSONObject("results").getJSONObject("cm_dict").getJSONObject(quuid).toString() + "&";
        url += "view_orientation=horizontal&";  //TODO set how?
        url += "_internal_state=null&";  //"placeholder" according to docs

        try {
            url += "callback_url=" + CommonConfiguration.getServerURL(request, request.getContextPath()) + "/ia%3FidentificationReviewPost%3D" + taskId + "&callback_method=POST";
System.out.println("url --> " + url);
getOut = "(( " + url + " ))";
            URL u = new URL(url);
            JSONObject rtn = RestClient.get(u);
            if ((rtn.optString("response", null) == null) || (rtn.optJSONObject("status") == null) ||
                !rtn.getJSONObject("status").optBoolean("success", false)) {
                getOut = "<div error-code=\"555\" class=\"response-error\">invalid response: <xmp>" + rtn.toString() + "</xmp></div>";
            } else {
                getOut = rtn.getString("response");
                if (request.getParameter("test") != null) {
                    getOut = "<html><head><script src=\"https://ajax.googleapis.com/ajax/libs/jquery/2.2.0/jquery.min.js\"></script></head><body>" + getOut + "</body></html>";
                }
            }
        } catch (Exception ex) {
            getOut = "<div error-code=\"556\" class=\"response-error\">Error: " + ex.toString() + "</div>";
        }

        return getOut;
    }

    //note: if we pass annId==null then we dont really care *which* pair we get, we just want one that is available for review (regardless of qannot)
    private JSONObject getAvailableIdentificationReviewPair(JSONArray rlist, String annId, Shepherd myShepherd) {
        if ((rlist == null) || (rlist.length() < 1)) return null;
        for (int i = 0 ; i < rlist.length() ; i++) {
            JSONObject rp = rlist.optJSONObject(i);
            if (rp == null) continue;
            String a1 = IBEISIA.fromFancyUUID(rp.optJSONObject("annot_uuid_1"));
            if ((annId != null) && !annId.equals(a1)) continue;
            String a2 = IBEISIA.fromFancyUUID(rp.optJSONObject("annot_uuid_2"));
            if (IBEISIA.getIdentificationMatchingState(a1, a2, myShepherd) == null) return rp;
        }
        return null;
    }

    private ArrayList<MediaAsset> mineNeedingDetectionReview(HttpServletRequest request, Shepherd myShepherd) {
        String filter = "SELECT FROM org.ecocean.media.MediaAsset WHERE detectionStatus == \"pending\"";
        String username = ((request.getUserPrincipal() == null) ? null : request.getUserPrincipal().getName());
        if (username != null) {
            filter = "SELECT FROM org.ecocean.media.MediaAsset WHERE accessControl.username == \"" + username + "\" && detectionStatus == \"pending\"";
        }
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        Query query = myShepherd.getPM().newQuery(filter);
        Collection c = (Collection) (query.execute());
        Iterator it = c.iterator();
        while (it.hasNext()) {
            mas.add((MediaAsset)it.next());
        }
        query.closeAll();
        return mas;
    }

    private ArrayList<Annotation> mineNeedingIdentificationReview(HttpServletRequest request, Shepherd myShepherd) {
        String filter = "SELECT FROM org.ecocean.Annotation WHERE identificationStatus == \"pending\"";
/*
        String username = ((request.getUserPrincipal() == null) ? null : request.getUserPrincipal().getName());
        if (username != null) {
            filter = "SELECT FROM org.ecocean.media.MediaAsset WHERE accessControl.username == \"" + username + "\" && detectionStatus == \"pending\"";
        }
*/
        ArrayList<Annotation> anns = new ArrayList<Annotation>();
        Query query = myShepherd.getPM().newQuery(filter);
        Collection c = (Collection) (query.execute());
        Iterator it = c.iterator();
        while (it.hasNext()) {
            anns.add((Annotation)it.next());
        }
        query.closeAll();
        return anns;
    }

/*
{"_action":"getJobResult","_response":{"response":{"json_result":{"query_annot_uuid_list":[{"__UUID__":"637ecc22-7f84-460d-84ab-fe4a9e277dd4"}],"query_config_dict":{},"inference_dict":{"annot_pair_dict":{"review_pair_list":[{"prior_matching_state":{"p_match":0.4737890251600697,"p_nomatch":0.5262109748399303,"p_notcomp":0},"annot_uuid_2":{"__UUID__":"6d328175-3180-4ea9-8160-80e6aee586ec"},"annot_uuid_1":{"__UUID__":"637ecc22-7f84-460d-84ab-fe4a9e277dd4"},"annot_uuid_key":{"__UUID__":"637ecc22-7f84-460d-84ab-fe4a9e277dd4"}},{"prior_matching_state":{"p_match":0.632309908395664,"p_nomatch":0.36769009160433597,"p_notcomp":0},"annot_uuid_2":{"__UUID__":"15d5a7ec-6113-4f6e-a572-8787b5727f5b"},"annot_uuid_1":{"__UUID__":"637ecc22-7f84-460d-84ab-fe4a9e277dd4"},"annot_uuid_key":{"__UUID__":"637ecc22-7f84-460d-84ab-fe4a9e277dd4"}},{"prior_matching_state":{"p_match":0.7422175273580092,"p_nomatch":0.25778247264199083,"p_notcomp":0},"annot_uuid_2":{"__UUID__":"5f46e85e-4f0f-45ba-a713-13aef6a9d48d"},"annot_uuid_1":{"__UUID__":"637ecc22-7f84-460d-84ab-fe4a9e277dd4"},"annot_uuid_key":{"__UUID__":"637ecc22-7f84-460d-84ab-fe4a9e277dd4"}},{"prior_matching_state":{"p_match":0.8296040477990491,"p_nomatch":0.17039595220095094,"p_notcomp":0},"annot_uuid_2":{"__UUID__":"c33706a2-4bab-4a36-8b15-3ba129407855"},"annot_uuid_1":{"__UUID__":"637ecc22-7f84-460d-84ab-fe4a9e277dd4"},"annot_uuid_key":{"__UUID__":"637ecc22-7f84-460d-84ab-fe4a9e277dd4"}}],"confidence_list":[0.002748060808237835,0.07002364743867603,0.23467732223771168,0.43455531330207126]},"_internal_state":null,"cluster_dict":{"exemplar_flag_list":[true],"orig_name_uuid_list":[-2229],"annot_uuid_list":[{"__UUID__":"637ecc22-7f84-460d-84ab-fe4a9e277dd4"}],"error_flag_list":[["merge"]],"new_name_uuid_list":[9001]}},"cm_dict":{"637ecc22-7f84-460d-84ab-fe4a9e277dd4":{"dannot_uuid_list":[{"__UUID__":"6d328175-3180-4ea9-8160-80e6aee586ec"},{"__UUID__":"c33706a2-4bab-4a36-8b15-3ba129407855"},{"__UUID__":"5f46e85e-4f0f-45ba-a713-13aef6a9d48d"},{"__UUID__":"00ae1179-e934-4b11-9353-bb1944f311e2"},{"__UUID__"
*/

    /*
        checked here after every ident review submission form comes back from IA.  essentially: do we kick to outer loop?

        note: kinda feel like taskId is redundant here -- loadMostRecentByObjectID() should only give one task.....
        really the question is: do we want the most recent ident result for this annot? or the result from this task?
        they most(?) often will be the same, yet can not be.  ???
    */
    private void checkIdentificationIterationStatus(String annId, String taskId, HttpServletRequest request) throws IOException {
        if (annId == null) return;
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA", annId, myShepherd);
        if ((logs == null) || (logs.size() < 1)) return;
        Collections.reverse(logs);  //getTaskResultsBase() needs to be timestamp ASC order, but this is not; sigh.
//for (IdentityServiceLog l : logs) { System.out.println(l.toString()); }
        JSONObject res = IBEISIA.getTaskResultsBasic(taskId, logs);
//System.out.println("JSON_RESULT -> " + res.getJSONObject("_response").getJSONObject("response").getJSONObject("json_result").toString());
//System.out.println("JSON_RESULT -> " + res.optJSONObject("_json_result"));
        if ((res == null) || (res.optJSONObject("_json_result") == null) ||
            (res.getJSONObject("_json_result").optJSONObject("inference_dict") == null) ||
            (res.getJSONObject("_json_result").getJSONObject("inference_dict").optJSONObject("annot_pair_dict") == null)) return;
        JSONArray rlist = res.getJSONObject("_json_result").getJSONObject("inference_dict").getJSONObject("annot_pair_dict").optJSONArray("review_pair_list");
        if (rlist == null) return;
System.out.println(">> checkIdentificationIterationStatus(" + annId + ", " + taskId + ") review_pair_list -> " + rlist.toString());
/// now we check that MatchingStates are all done?  and/or which pairs in rlist are still needed? or if N have gone by and we should resend to IA ????
        JSONArray matchingStateList = new JSONArray();
        for (int i = 0 ; i < rlist.length() ; i++) {
            if (rlist.optJSONObject(i) == null) continue;
            String a1 = IBEISIA.fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_1"));
            String a2 = IBEISIA.fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_2"));
            String state = IBEISIA.getIdentificationMatchingState(a1, a2, myShepherd);
System.out.println(" - state(" + a1 + ", " + a2 + ") -> " + state);
            if (state != null) matchingStateList.put(new JSONArray(new String[]{a1, a2, state}));
        }
        //if ((numReviewed >= rlist.length()) || (numReviewed % IDENTIFICATION_REVIEWS_BEFORE_SEND == 0)) {
        if (matchingStateList.length() >= rlist.length()) {
System.out.println("((((( once more thru the outer loop )))))");
            Annotation ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, annId), true)));
            if (ann == null) return;
            JSONObject rtn = _sendIdentificationTask(ann, request, null, null, -1);
            /////// at this point, we can consider this current task done
            IBEISIA.setActiveTaskId(request, null);  //reset it so it can discovered when results come back
            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING);
System.out.println(" _sendIdentificationTask ----> " + rtn);
            String newTaskId = rtn.optString("taskId", null);
            JSONObject jlog = new JSONObject("{\"_action\": \"identificationIterate\"}");
            jlog.put("newTaskId", newTaskId);
            jlog.put("previousTaskId", taskId);
            IBEISIA.log(taskId, (String)null, null, jlog, context); //note: do not set annId here, as it will show up newer than ident task started above
        }
    }

    public static JSONObject expandAnnotation(String annID, Shepherd myShepherd, HttpServletRequest request) {
        if (annID == null) return null;
        JSONObject rtn = new JSONObject();
        Annotation ann = null;
        try {
            ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, annID), true)));
        } catch (Exception ex) {}
        if (ann != null) {
            rtn.put("annotationID", annID);
            Encounter enc = Encounter.findByAnnotation(ann, myShepherd);
            if (enc != null) {
                JSONObject jenc = new JSONObject();
                jenc.put("catalogNumber", enc.getCatalogNumber());
                jenc.put("date", enc.getDate());
                jenc.put("sex", enc.getSex());
                jenc.put("verbatimLocality", enc.getVerbatimLocality());
                jenc.put("locationID", enc.getLocationID());
                jenc.put("individualID", enc.getIndividualID());
                jenc.put("otherCatalogNumbers", enc.getOtherCatalogNumbers());
                rtn.put("encounter", jenc);
            }
            MediaAsset ma = ann.getMediaAsset();
            if (ma != null) {
                try {
                    rtn.put("mediaAsset", new JSONObject(ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject()).toString()));
                } catch (Exception ex) {}
            }
        }
        return rtn;
    }

    public static JSONObject taskSummary(JSONArray taskIds, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();
        if ((taskIds == null) || (taskIds.length() < 1)) return rtn;
        for (int i = 0 ; i < taskIds.length() ; i++) {
            String annId = taskIds.optString(i);
            if (annId == null) continue;
            ArrayList<IdentityServiceLog> logs = IdentityServiceLog.summaryForAnnotationId(annId, myShepherd);
            if ((logs != null) && (logs.size() > 0)) {
                JSONObject tasks = new JSONObject();
                for (IdentityServiceLog l : logs) {
                    if (l.getTaskID() == null) continue;
                    JSONObject t = new JSONObject();
                    if (l.getStatus() != null) t.put("status", new JSONObject(l.getStatus()));
                    t.put("timestamp", l.getTimestamp());
                    tasks.put(l.getTaskID(), t);
                }
                rtn.put(annId, tasks);
            }
        }
        return rtn;
    }

    //yeah maybe this should be merged into MediaAsset duh
    private String mediaAssetIdToUUID(int id) {
        byte b1 = (byte)77;
        byte b2 = (byte)97;
        byte[] b = new byte[6];
        b[0] = b1;
        b[1] = b2;
        b[2] = (byte) (id >> 24);
        b[3] = (byte) (id >> 16);
        b[4] = (byte) (id >> 8);
        b[5] = (byte) (id >> 0);
        return UUID.nameUUIDFromBytes(b).toString();
    }

    //parse whether the html returned means we need to adjust the http header return code
    private void setErrorCode(HttpServletResponse response, String html) throws IOException {
        if (html == null) return;
        int code = 500;
        int a = html.indexOf("error-code=");
        int b = html.indexOf("class=\"response-error");
        if ((a < 0) && (b < 0)) return;  //must have at least one
        if (a > -1) {
            try {
                code = Integer.parseInt(html.substring(18,21));
            } catch (NumberFormatException ex) {}
        }
        String msg = "unknown error";
        int m = html.indexOf(">");
        if (m > -1) {
            msg = html.substring(m + 1);
            m = msg.indexOf("<");
            if (m > -1) {
                msg = msg.substring(0, m);
            }
        }
        System.out.println("ERROR: IAGateway.sendError() reporting " + code + ": " + msg);
        response.sendError(code, msg);
    }

}
