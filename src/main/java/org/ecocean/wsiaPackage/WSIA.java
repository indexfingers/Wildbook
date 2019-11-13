package org.ecocean.wsiaPackage;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jdo.Query;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Keyword;
import org.ecocean.Occurrence;
import org.ecocean.RestClient;
import org.ecocean.Shepherd;
import org.ecocean.Taxonomy;
import org.ecocean.Util;
import org.ecocean.ia.IA;
import org.ecocean.ia.IAPluginManager;
import org.ecocean.ia.Task;
import org.ecocean.ia.plugin.IAPlugin;
import org.ecocean.ia.plugin.WSIAM;
import org.ecocean.identity.IBEISIA;
import org.ecocean.identity.IdentityServiceLog;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.media.MediaAssetSet;
import org.ecocean.servlet.RestKeyword;
import org.ecocean.servlet.ServletUtilities;
import org.json.JSONObject;
import org.json.JSONArray;

public class WSIA {
  
  private static final Map<String, String[]> speciesMap;
  static {
      speciesMap = new HashMap<String, String[]>();
      speciesMap.put("zebra_plains", new String[]{"Equus","quagga"});
      speciesMap.put("zebra_grevys", new String[]{"Equus","grevyi"});
      speciesMap.put("whale shark", new String[]{"Rhincodon","typus"});
      speciesMap.put("white_shark", new String[]{"Carcharodon", "carcharias"});
  }

  public static final String STATUS_PENDING = "pending"; // pending review
                                                         // (needs action by
                                                         // user)
  public static final String STATUS_COMPLETE = "complete"; // process is done
  public static final String STATUS_PROCESSING = "processing"; // off at IA,
                                                               // awaiting
                                                               // results
  public static final String STATUS_ERROR = "error";
  public static final String IA_UNKNOWN_NAME = "____";
  
  public static final String CAUDAL = "Caudal Fin";
  
  private static String SERVICE_NAME = "IBEISIA";

  public static void doDetect(JSONObject jobj) {
    JSONObject res = new JSONObject("{\"success\": false}");
    res.put("taskId", jobj.getString("taskId"));
    String context = jobj.optString("__context", "context0");
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("IAGateway.processQueueMessage.WSIA.detect");
    myShepherd.beginDBTransaction();
    String baseUrl = jobj.optString("__baseUrl", null);
    try {
      JSONObject rtn = _doDetect(jobj, res, myShepherd, baseUrl);
      System.out.println(
          "INFO: IAGateway.processQueueMessage() 'detect' successful --> "
              + rtn.toString());
      myShepherd.commitDBTransaction();
    } catch (Exception ex) {
      System.out.println(
          "ERROR: IAGateway.processQueueMessage() 'detect' threw exception: "
              + ex.toString());
      myShepherd.rollbackDBTransaction();
    }
    myShepherd.closeDBTransaction();
//      return rtn;
  }

  public static JSONObject _doDetect(JSONObject jin, JSONObject res,
      Shepherd myShepherd, String baseUrl)
      throws ServletException, IOException {
    if (res == null)
      throw new RuntimeException(
          "WSIA._doDetect() called without res passed in");
    String taskId = res.optString("taskId", null);
    if (taskId == null)
      throw new RuntimeException("WSIA._doDetect() has no taskId passed in");
    System.out.println("PRELOADED");
    System.out.println(CommonConfiguration.getServerInfo(myShepherd).toString());
    Task task = Task.load(taskId, myShepherd); // might be null in some cases,
                                               // such as non-queued ... maybe
                                               // FIXME when we dump cruft?
    System.out.println("LOADED???? " + taskId + " --> " + task);
    String context = myShepherd.getContext();
    if (baseUrl == null)
      return res;
    if (jin == null)
      return res;
    JSONObject j = jin.optJSONObject("detect");
    if (j == null)
      return res; // "should never happen"
    System.out.println(jin.toString());
    ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
    List<MediaAsset> needOccurrences = new ArrayList<MediaAsset>();
    ArrayList<String> validIds = new ArrayList<String>();

    if (j.optJSONArray("mediaAssetIds") != null) {
      JSONArray ids = j.getJSONArray("mediaAssetIds");
      for (int i = 0; i < ids.length(); i++) {
        int id = ids.optInt(i, 0);
        if (id < 1)
          continue;
        myShepherd.beginDBTransaction();
        MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
        myShepherd.getPM().refresh(ma);

        if (ma != null) {
          ma.setDetectionStatus(WSIA.STATUS_PROCESSING);
          mas.add(ma);
        }
      }
    } else if (j.optJSONArray("mediaAssetSetIds") != null) {
      JSONArray ids = j.getJSONArray("mediaAssetSetIds");
      for (int i = 0; i < ids.length(); i++) {
        MediaAssetSet set = myShepherd.getMediaAssetSet(ids.optString(i));
        if ((set != null) && (set.getMediaAssets() != null)
            && (set.getMediaAssets().size() > 0))
          mas.addAll(set.getMediaAssets());
      }
    } else {
      res.put("success", false);
      res.put("error", "unknown detect value");
    }
    System.out.println("mas size: " + mas.size());
    if (mas.size() > 0) {
      if (task != null) {
        task.setObjectMediaAssets(mas);
        task.addParameter("wsia.detection", true);
      }
      for (MediaAsset ma : mas) {
        validIds.add(Integer.toString(ma.getId()));
        if (ma.getOccurrence() == null)
          needOccurrences.add(ma);
      }

      boolean success = true;
         
      try {
        res.put("sendMediaAssets", sendMediaAssetsNew(mas, context));
        JSONObject sent = sendDetect(mas, baseUrl, context, taskId);
        System.out.println("Send detect reply: " + sent);
        res.put("sendDetect", sent);
        String jobId = taskId;
        if ((sent.optJSONObject("status") == null) || sent.getJSONObject("status").optBoolean("success", false) == false)
          jobId = null;
//          jobId = sent.optString("response", null);
        res.put("jobId", jobId);
        log(taskId, validIds.toArray(new String[validIds.size()]), jobId, new JSONObject("{\"_action\": \"initDetect\"}"), context);
      } catch (Exception ex) {
        success = false;
        ex.printStackTrace();
        throw new IOException(ex.toString());
      }
      if (!success) {
        for (MediaAsset ma : mas) {
            ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
        }
      }
      res.remove("error");
      res.put("success", true);
    } else {
      res.put("error", "no valid MediaAssets");   
    }
    return res;
  }
  
  
  public static JSONObject sendDetect(ArrayList<MediaAsset> mas, String baseUrl, String context, String taskId) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
    HashMap<String,Object> map = new HashMap<String,Object>();
//    Taxonomy taxy = taxonomyFromMediaAssets(context, mas);
//    String viewpointModelTag = null;
    String detectionAlgo = getLabelerAlgo(context);
    if (detectionAlgo!=null) {
      map.put("detectionAlgo",detectionAlgo);
      System.out.println("[INFO] sendDetect() detectionAlgo set to " + detectionAlgo);
    } else {System.out.println("[INFO] sendDetect() detectionAlgo is null; skipping");}
    map.put("callback_url", callbackUrl(baseUrl));
    System.out.println("sendDetect() baseUrl = " + baseUrl);
    ArrayList<JSONObject> malist = new ArrayList<JSONObject>();
    for (MediaAsset ma : mas) {
      if (ma == null) continue;
      if (ma.getAcmId() == null) {  //usually this means it was not able to be added to IA (e.g. a video etc)
          System.out.println("WARNING: sendDetect() skipping " + ma + " due to missing acmId");
          ma.setDetectionStatus(STATUS_ERROR);  //is this wise?
          continue;
      }
      malist.add(WSIAM.toFancyUUID2(ma.getAcmId()));
    }
    map.put("image_uuid_list", malist);
    
    //config
    //detection
//    String detectionAlgo = IA.getProperty(context, "detectionAlgo");
//    if (detectionAlgo != null) {
//        System.out.println("[INFO] sendDetect() detectionAlgo set to " + detectionAlgo);
//        map.put("detectionAlgo",detectionAlgo);
//    } else {
//        System.out.println("[INFO] sendDetect() detectionAlgo is null; DEFAULT will be used");
//    }
    String detectionAlgoVersion = IA.getProperty(context, "detectionAlgoVersion");
    if (detectionAlgoVersion != null) {
        System.out.println("[INFO] sendDetect() detectionAlgoVersion set to " + detectionAlgoVersion);
        map.put("detectionAlgoVersion",detectionAlgoVersion);
    } else {
        System.out.println("[INFO] sendDetect() detectionAlgoVersion is null; DEFAULT will be used");
    }
    String sensitivity = IA.getProperty(context, "sensitivity");
    if (sensitivity != null) {
        System.out.println("[INFO] sendDetect() sensitivity set to " + sensitivity);
        map.put("sensitivity", sensitivity);
    } else {
        System.out.println("[INFO] sendDetect() sentivity is null; DEFAULT will be used");
    }
    String nms_thresh = IA.getProperty(context, "nms_thresh");
    if (nms_thresh != null) {
        System.out.println("[INFO] sendDetect() nms_thresh set to " + nms_thresh);
        map.put("nms_thresh", nms_thresh);
    } else {
        System.out.println("[INFO] sendDetect() nms_thresh is null; DEFAULT will be used");
    }
    //edge refinement
    String refineAlgo = IA.getProperty(context, "refineAlgo");
    if (refineAlgo != null) {
        System.out.println("[INFO] sendDetect() refineAlgo set to " + refineAlgo);
        map.put("refineAlgo", refineAlgo);
    } else {
        System.out.println("[INFO] sendDetect() refineAlgo is null; DEFAULT will be used");
    }
    String refineRadius = IA.getProperty(context, "refineRadius");
    if (refineRadius != null) {
        System.out.println("[INFO] sendDetect() refineRadius set to " + refineRadius);
        map.put("refineRadius",refineRadius);
    } else {
        System.out.println("[INFO] sendDetect() refineRadius is null; DEFAULT will be used");
    }
    String refineMax_len = IA.getProperty(context, "refineMax_len");
    if (refineMax_len != null) {
        System.out.println("[INFO] sendDetect() refineMax_len set to " + refineMax_len);
        map.put("refineMax_len",refineMax_len);
    } else {
        System.out.println("[INFO] sendDetect() refineMax_len is null; DEFAULT will be used");
    }
    String refineMin_len = IA.getProperty(context, "refineMin_len");
    if (refineMin_len != null) {
        System.out.println("[INFO] sendDetect() refineMin_len set to " + refineMin_len);
        map.put("refineMin_len",refineMin_len);
    } else {
        System.out.println("[INFO] sendDetect() refineMin_len is null; DEFAULT will be used");
    }
    // side (viewpoint) classification
    String sideClassifierAlgo = IA.getProperty(context, "sideClassifierAlgo");
    if (sideClassifierAlgo != null) {
        System.out.println("[INFO] sendDetect() sideClassifierAlgo set to " + sideClassifierAlgo);
        map.put("sideClassifierAlgo", sideClassifierAlgo);
    } else {
        System.out.println("[INFO] sendDetect() sideClassifierAlgo is null; DEFAULT will be used");
    }
    String sideClassifierAlgoVersion = IA.getProperty(context, "sideClassifierAlgoVersion");
    if (sideClassifierAlgoVersion != null) {
        System.out.println("[INFO] sendDetect() sideClassifierAlgoVersion set to " + sideClassifierAlgoVersion);
        map.put("sideClassifierAlgoVersion", sideClassifierAlgoVersion);
    } else {
        System.out.println("[INFO] sendDetect() sideClassifierAlgoVersion is null; DEFAULT will be used");
    }
    // keypoint detection
    String keypointAlgo = IA.getProperty(context, "keypointAlgo");
    if (keypointAlgo != null) {
        System.out.println("[INFO] sendDetect() keypointAlgo set to " + keypointAlgo);
        map.put("keypointAlgo", keypointAlgo);
    } else {
        System.out.println("[INFO] sendDetect() keypointAlgo is null; DEFAULT will be used");
    }
    String keypointAlgoVersion = IA.getProperty(context, "keypointAlgoVersion");
    if (keypointAlgoVersion  != null) {
        System.out.println("[INFO] sendDetect() keypointAlgoVersion  set to " + keypointAlgoVersion );
        map.put("keypointAlgoVersion ", keypointAlgoVersion );
    } else {
        System.out.println("[INFO] sendDetect() keypointAlgoVersion  is null; DEFAULT will be used");
    }
    
    map.put("task_id",taskId);
    
    String u = IA.getProperty(context, "IBEISIARestUrlStartDetectImages");
    
    if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlStartDetectImages is not set");
    URL url = new URL(u);
    return RestClient.post(url, new JSONObject(map));
    
  }
  
  public static String callbackUrl(String baseUrl) {
    return baseUrl + "/ia?callback";
}
  
  public static String getLabelerAlgo(String context) {
    return IA.getProperty(context, "detectionAlgo").trim();
  }

  public static WSIAM getPluginInstance(String context) {
    IAPlugin p = IAPluginManager.getIAPluginInstanceFromClass(WSIAM.class,
        context);
    return (WSIAM) p;
  }

  public static JSONObject sendMediaAssetsNew(ArrayList<MediaAsset> mas,
      String context) throws RuntimeException, MalformedURLException,
      IOException, NoSuchAlgorithmException, InvalidKeyException {
    WSIAM plugin = getPluginInstance(context);
    return plugin.sendMediaAssets(mas, true);
  }
  
  
  public static JSONObject getJobStatus(String jobID, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
    String u = IA.getProperty(context, "IBEISIARestUrlGetJobStatus");
    if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlGetJobStatus is not set");
    URL url = new URL(u + "?jobid=" + jobID);
    return RestClient.get(url);
}
  
  //this is built explicitly for Queue support (to lose dependency on passing request around)
  public static void callbackFromQueue(JSONObject qjob) {
//      System.out.println("INFO: callbackFromQueue() -> " + qjob);
      if (qjob == null) return;
      String context = qjob.optString("context", null);
      String rootDir = qjob.optString("rootDir", null);
      String jobId = qjob.optString("jobId", null);
      JSONObject res = qjob.optJSONObject("dataJson");
      if ((context == null) || (rootDir == null) || (jobId == null)) {  //not requiring res so we can have GET callbacks
          System.out.println("ERROR: callbackFromQueue() has insufficient parameters");
          return;
      }
      System.out.println("callbackFromQueue OK!!!!");

      //from here on has been grafted on from IBEISIAGetJobStatus.jsp
      JSONObject statusResponse = new JSONObject();
      try {
        statusResponse = getJobStatus(jobId, context);
      } catch (Exception ex) {
        System.out.println("except? " + ex.toString());
          statusResponse.put("_error", ex.toString());
      }

      System.out.println(statusResponse.toString());
      JSONObject jlog = new JSONObject();
      jlog.put("jobId", jobId);
      String taskId = findTaskIDFromJobID(jobId, context);
      if (taskId == null) {
          jlog.put("error", "could not determine task ID from job " + jobId);
      } else {
          jlog.put("taskId", taskId);
      }

      jlog.put("_action", "getJobStatus");
      jlog.put("_response", statusResponse);


      log(taskId, jobId, jlog, context);

      JSONObject all = new JSONObject();
      all.put("jobStatus", jlog);
System.out.println(">>>>------[ jobId = " + jobId + " -> taskId = " + taskId + " ]----------------------------------------------------");

      try {
          if ((statusResponse != null) && statusResponse.has("status") &&
              statusResponse.getJSONObject("status").getBoolean("success") &&
              statusResponse.has("response") && statusResponse.getJSONObject("response").has("status") &&
              "ok".equals(statusResponse.getJSONObject("response").getString("status")) &&
              "completed".equals(statusResponse.getJSONObject("response").getString("jobstatus"))) {
System.out.println("HEYYYYYYY i am trying to getJobResult(" + jobId + ")");
              JSONObject resultResponse = getJobResult(jobId, context);
              JSONObject rlog = new JSONObject();
              rlog.put("jobId", jobId);
              rlog.put("_action", "getJobResult");
              rlog.put("_response", resultResponse);
              log(taskId, jobId, rlog, context);
              all.put("jobResult", rlog);

              JSONObject proc = processCallback(taskId, rlog, context, rootDir);
//System.out.println("processCallback returned --> " + proc);
          }
      } catch (Exception ex) {
          System.out.println("whoops got exception: " + ex.toString());
          ex.printStackTrace();
      }

      all.put("_timestamp", System.currentTimeMillis());
System.out.println("-------- >>> all.size() (omitting all.toString() because it's too big!) " + all.length() + "\n##################################################################");
      return;
  }
  
  
  public static JSONObject processCallback(String taskID, JSONObject resp, HttpServletRequest request) {
    String context = ServletUtilities.getContext(request);
    String rootDir = request.getSession().getServletContext().getRealPath("/");
    return processCallback(taskID, resp, context, rootDir);
}
  
  public static JSONObject processCallback(String taskID, JSONObject resp, String context, String rootDir) {
System.out.println("CALLBACK GOT: (taskID " + taskID + ") ");
      JSONObject rtn = new JSONObject("{\"success\": false}");
      rtn.put("taskId", taskID);
      if (taskID == null) return rtn;
      Shepherd myShepherd=new Shepherd(context);
      myShepherd.setAction("IBEISIA.processCallback");
      myShepherd.beginDBTransaction();
      ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, "IBEISIA", myShepherd);
      rtn.put("_logs", logs);
      if ((logs == null) || (logs.size() < 1)) return rtn;

      JSONObject newAnns = null;
      String type = getTaskType(logs);
      System.out.println("**** type ---------------> [" + type + "]");
      if ("detect".equals(type)) {
          rtn.put("success", true);
          JSONObject dres = processCallbackDetect(taskID, logs, resp, myShepherd, context, rootDir);
          rtn.put("processResult", dres);
          /*
              for detection, we have to check if we have generated any Annotations, which we then pass on
              to IA.intake() for identification ... BUT *only after we commit* (below) !! since ident stuff is queue-based
          */
          newAnns = dres.optJSONObject("annotations");
          rtn.put("processResult", dres);
          /*
              for detection, we have to check if we have generated any Annotations, which we then pass on
              to IA.intake() for identification ... BUT *only after we commit* (below) !! since ident stuff is queue-based
          */
          newAnns = dres.optJSONObject("annotations");
      } else {
          rtn.put("error", "unknown task action type " + type);
      }
      myShepherd.commitDBTransaction();
      myShepherd.closeDBTransaction();
          
          // putting return in temporarily - put some code below from ibeisia.processcallback
      
    return rtn;
  }
  
  private static JSONObject processCallbackDetect(String taskID, ArrayList<IdentityServiceLog> logs, JSONObject resp, Shepherd myShepherd, HttpServletRequest request) {
    String context = ServletUtilities.getContext(request);
    String rootDir = request.getSession().getServletContext().getRealPath("/");
    return processCallbackDetect(taskID, logs, resp, myShepherd, context, rootDir);
}
  
  private static JSONObject processCallbackDetect(String taskID, ArrayList<IdentityServiceLog> logs, JSONObject resp, Shepherd myShepherd, String context, String rootDir) {
    JSONObject rtn = new JSONObject("{\"success\": false}");
    Task task = Task.load(taskID, myShepherd);
    String[] ids = IdentityServiceLog.findObjectIDs(logs);
    System.out.println("***** ids = " + ids);
    if (ids == null) {
        rtn.put("error", "could not find any MediaAsset ids from logs");
        return rtn;
    }
    ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
    for (int i = 0 ; i < ids.length ; i++) {
        MediaAsset ma = MediaAssetFactory.load(Integer.parseInt(ids[i]), myShepherd);
        if (ma != null) mas.add(ma);
    }
    int numCreated = 0;
//    System.out.println("RESP ===>>>>>> " + resp.toString(2));
    System.out.println("OKAY- SEEMS LIKE WE'VE MDE IT TO processcallbackdetect ");

    if ((resp.optJSONObject("_response") != null) && (resp.getJSONObject("_response").optJSONObject("response") != null) &&
        (resp.getJSONObject("_response").getJSONObject("response").optJSONObject("json_result") != null)) {
        JSONObject j = resp.getJSONObject("_response").getJSONObject("response").getJSONObject("json_result");
        System.out.println("okay - got our json_result");
        
        //handles ia exceptions
        if ((j.optJSONObject("exceptions") != null) && (j.optJSONObject("exceptions").getJSONArray("image_uuid_list") !=null) 
            && (j.optJSONObject("exceptions").getJSONArray("image_uuid_list").length()>0)) {
          System.out.println("Oh dear, we have exceptions for " + j.optJSONObject("exceptions").getJSONArray("image_uuid_list").length() 
              + " images");
          JSONArray exlist = j.optJSONObject("exceptions").getJSONArray("image_uuid_list");
          JSONArray msgExList = j.optJSONObject("exceptions").optJSONArray("message_list");
          for (int i = 0 ; i < exlist.length() ; i++) {
            String iuuid = exlist.optString(i);
            String msg = "";
            if (iuuid == null) continue;
            if (msgExList != null) msg = msgExList.optString(i, "");
            System.out.println("[IA ERROR] ia exception for image: "+iuuid+ ". Message is: " + msg);
            MediaAsset asset = null;
            for (MediaAsset ma : mas) {
              if (ma.getAcmId() == null) continue;  //was likely an asset rejected (e.g. video)
              if (ma.getAcmId().equals(iuuid)) {
                  asset = ma;
                  break;
              }
            }
            if (asset != null) asset.setDetectionStatus(STATUS_ERROR);      
          }
        }
        
        //handle successes
        JSONArray rlist = null;
        JSONArray ilist = null;
        if (j.optJSONObject("success") != null) {
            rlist = j.getJSONObject("success").optJSONArray("results_list");
            ilist = j.getJSONObject("success").optJSONArray("image_uuid_list");
        }
        System.out.println("our image list is: " + ilist);
        if ((rlist != null) && (rlist.length() > 0) && (ilist != null) && (ilist.length() == rlist.length())) {
          FeatureType.initAll(myShepherd);
          JSONArray needReview = new JSONArray();
          JSONObject amap = new JSONObject();
          List<Annotation> allAnns = new ArrayList<Annotation>();
          // for all images (effectively)
          for (int i = 0 ; i < rlist.length() ; i++) {
              JSONObject jann = rlist.optJSONObject(i);
              if (jann == null) {System.out.println("jann is null - continuing"); continue;}
              JSONArray janns = new JSONArray();
              janns.put(jann);
              String iuuid = ilist.optString(i);
              System.out.println("current image is "+iuuid);
              if (iuuid == null) continue;
              MediaAsset asset = null;
              for (MediaAsset ma : mas) {
                if (ma.getAcmId() == null) continue;  //was likely an asset rejected (e.g. video)
                if (ma.getAcmId().equals(iuuid)) {
                  System.out.println("we have a media asset");
                    asset = ma;
                    break;
                }
              }
              if (asset == null) {
                System.out.println("WARN: could not find MediaAsset for " + iuuid + " in detection results for task " + taskID);
                continue;
              }
              boolean needsReview = false;
              JSONArray newAnns = new JSONArray();
              boolean skipEncounters = asset.hasLabel("_frame");
              System.out.println("skip encounters: our survey says: " + skipEncounters);
              //for all annotations detected for the current image
              for (int a = 0 ; a < janns.length() ; a++) {
                JSONObject jann2 = janns.optJSONObject(a);
                if (jann2 == null) continue;
                JSONArray fts = jann2.optJSONArray("features");
                if ((fts == null) || (fts.length()==0)) {
                  System.out.println("WSIA: processCallbackDetect: no features for " + iuuid + 
                      ". Likely means no fins detected. Saying needs review??" );
                  needsReview = true;
                  continue;     
                }
                JSONObject box = getFeatByType(fts, "box");
                if (box == null) {
                  System.out.println("WSIA: processCallbackDetect: no box for " + iuuid + " saying review.");
                  needsReview = true;
                  continue;
                }
                if (box.optDouble("confidence", -1.0) < getDetectionCutoffValue(context, task)) {
                  needsReview = true;
                  continue;
                } 
                System.out.println("sending to create annotation from result");
                Annotation ann = createAnnotationFromIAResult(jann2, asset, myShepherd, context, rootDir, skipEncounters);
                if (ann == null) {
                    System.out.println("WARNING: IBEISIA detection callback could not create Annotation from " + asset + " and " + jann);
                    continue;
                }
                
                // MAYBE NOT NEEDED - same(?) logic in createAnnotationFromIAResult above ?????   if (!skipEncounters) _tellEncounter(myShepherd, ann);  // ???, context, rootDir);
                allAnns.add(ann);  //this is cumulative over *all MAs*
                newAnns.put(ann.getId());
                ///note: *removed* IA.intake (or IAIntake?) from here, as it needs to be done post-commit,
                ///  so we use 'annotations' in returned JSON to kick that off (since they all would have passed confidence)
                numCreated++;

              }
              if (needsReview) {
                needReview.put(asset.getId());
                asset.setDetectionStatus(STATUS_PENDING);
              } else {
                asset.setDetectionStatus(STATUS_COMPLETE);
              }
              if (newAnns.length() > 0) amap.put(Integer.toString(asset.getId()), newAnns);
          }
          
          rtn.put("_note", "created " + numCreated + " annotations for " + rlist.length() + " images");
          rtn.put("success", true);
          if (amap.length() > 0) rtn.put("annotations", amap);  //needed to kick off ident jobs with return value
        }
    } else {
    }
    return rtn;
  }
  
  
  public static boolean isDuplicateAcmid(MediaAsset asset, String newAnnAcmid) {
    ArrayList<Annotation> anar = asset.getAnnotations();
    if((anar == null)||anar.size()==0) {
      System.out.println("WSIA: createAnnotationFromIaResult: no other annotations on this media asset");
      return false;
    } else {
      for (Annotation an : anar) {
        if ( an.getAcmId() != null) {
          if (an.getAcmId().equals(newAnnAcmid)) {
            System.out.println("WSIA: createAnnotationFromIaResult: found a duplicate annotation. Skiiping this ann.");
            return true;
          }
        }
      }
      System.out.println("WSIA: createAnnotationFromIaResult: no duplicate annotation on this media asset");
    }
    return false;
  }
  
  
  private static Encounter findMeAHome(MediaAsset asset, Shepherd myShepherd, Annotation newAn) {
    System.out.println("WSIA: findMeAHome");
    ArrayList<Annotation> anar = asset.getAnnotations();
    for (Annotation an : anar) {
      try {
        if (an.isTrivial()) {
          an.setMatchAgainst(false);
          Encounter home = an.findEncounter(myShepherd);
          if (home == null) {
            System.out.println("WSIA: findMeAHome: weird - got a null encounter???"); 
            return null;
          }
          System.out.println("WSIA: findMeAHome: found a trivial ann on Enc: " + home.getID() + ". this is my home");
          home.replaceAnnotation(an, newAn);
          return home;
        }
      } catch( Exception exx) {
        System.out.println("WSIA: findMeAHome: whoops got exception: " + exx.toString());
        exx.printStackTrace();
        System.out.println();
        return null;
      }
    }
    // we should not currently get a situation where we don't have a trivial annotation to replace because we're only 
    // allowing one detection per image. In future though, this is where we'd need to create a new encounter...
    // see Annotation.toEncounter() for code and ideas how to handle this.
    
    // one idea more broadly to toy with is to create a new encounter in cases where there's already an annotation
    // with the same feature types on this media asset? Another idea, if we get to replacing existing detections
    // with better ones is to somehow record which annotation we're supposed to be replacing with the new one...
    System.out.println("WSIA: findmeahome: no trivial annotation to replace. Maybe now allowing multiple detections per"
        + "image??? Anyhow, will need to start creating new encounters.... See comments in code..."); 
    return null;
  }
//  
  public static Annotation createAnnotationFromIAResult(JSONObject jann, MediaAsset asset, Shepherd myShepherd, String context, String rootDir, boolean skipEncounter) {
    // check for duplicate annotations
    String newAnnAcmid = jann.optString("uuid", null);
    if (newAnnAcmid == null) return null;
    if (isDuplicateAcmid(asset, newAnnAcmid)) return null;
    Annotation ann = convertAnnotation(asset, jann, myShepherd, context, rootDir);
//    System.out.println("number of annotation fetures" + ann.getFeatures().size());
    if (ann == null) return null;
    if (skipEncounter) {
      myShepherd.getPM().makePersistent(ann);
      System.out.println("* createAnnotationFromIAResult() CREATED " + ann + " [with no Encounter!]");
      return ann;
    }
    System.out.println("[INFO] WSIA: createAnnotationFromIaResult: maybe set ann matchagainst here"
        + "based on best n anns in an encounter");
    
    Encounter enc = findMeAHome(asset, myShepherd, ann);
    if (enc==null) {
      System.out.println("WSIA: createAnnotationFromIaResult: failed to find an encounter for the new Ann. "
          + "This shouldn't happen!!! Anyhow, skipping... ");
      return null;
    }

//    DONT DELETE THE BELOW - KEEP FOR REFERENCE. Now replaced with findMeAHome()
//    Encounter enc = ann.toEncounter(myShepherd);  //this does the magic of making a new Encounter if needed etc.  good luck!
    System.out.println("the encounter we're adding our ann to is " + enc.getID());
    Occurrence occ = asset.getOccurrence();
    if (occ != null) {
        enc.setOccurrenceID(occ.getOccurrenceID());
        occ.addEncounter(enc);
    }
    enc.detectedAnnotation(myShepherd, ann);  //this is a stub presently, so meh?
    System.out.println("making "+ ann +" persistent...");
    myShepherd.getPM().makePersistent(ann);
    if (ann.getFeatures() != null) {
        for (Feature ft : ann.getFeatures()) {
            myShepherd.getPM().makePersistent(ft);
        }
    }
    
//    ArrayList<Annotation> matchAgainst = getTopNAnnotationsByQuality(enc, 5);
//    for (Annotation a: matchAgainst) {
//      a.setMatchAgainst(true);
//      myShepherd.getPM().makePersistent(a);
//    }
//    
    
    myShepherd.getPM().makePersistent(enc);
    if (occ != null) myShepherd.getPM().makePersistent(occ);
    System.out.println("* createAnnotationFromIAResult() CREATED " + ann + " on Encounter " + enc.getCatalogNumber());
  
    return ann;
  }
  
  
  public static Annotation convertAnnotation(MediaAsset ma, JSONObject iaResult, Shepherd myShepherd, String context, String rootDir) {
//    if (iaResult == null||duplicateDetection(ma, iaResult)) return null;
    JSONArray fts = iaResult.optJSONArray("features");
    if ((fts == null) || (fts.length()==0)) return null;

    String iaClass = "white_shark";

    ArrayList<Feature> ftArray = new ArrayList<Feature>();
    //do features one at a time
    
    
    // do caudal
    String finKw = null;
    JSONObject caudal =  getFeatByType(fts, "class_caudal");
    if (caudal != null) {
      double score = caudal.optDouble("scores");
      if (score<0.0) { finKw = CAUDAL; caudal.put("isCaudal", true);} else {caudal.put("isCaudal", false);}
      ftArray.add(new Feature("org.sosf.ws.classCaudal", caudal));
    }
    if (finKw!=null) {
          Keyword kw = myShepherd.getOrCreateKeyword(finKw);
          ma.addKeyword(kw);
          System.out.println("[INFO] convertAnnotation got caudal fin and made kwName "+finKw);
    }
    
    //do viewpoint
    String viewpoint = null;
    JSONObject fin_side = getFeatByType(fts, "class_fin_side");
    if (fin_side != null) {
      double score = fin_side.optDouble("scores");
      viewpoint = "right";
      if (score<0.0) viewpoint = "left";
      ftArray.add(new Feature("org.sosf.ws.classFinSide", fin_side));
    }
    
    if (finKw==null) {// no keyword - therefore, assume dorsal
      if (Util.stringExists(viewpoint)) {
        String kwName = RestKeyword.getKwNameFromIaViewpoint(viewpoint);
        if (kwName!=null) {
            Keyword kw = myShepherd.getOrCreateKeyword(kwName);
            ma.addKeyword(kw);
            System.out.println("[INFO] convertAnnotation viewpoint got ia viewpoint "+viewpoint+" mapped to kwName "+kwName+" and is adding kw "+kw);
        }
      } else {
        System.out.println("[INFO] convertAnnotation viewpoint got no viewpoint from IA");
      }
    }
    
    // do box
    JSONObject box = getFeatByType(fts, "box");
    if (box != null) {
      JSONObject fparams = new JSONObject();
      fparams.put("detectionConfidence", box.optDouble("confidence", -2.0));
      fparams.put("theta", 0.0);
      if (viewpoint!=null) fparams.put("viewpoint", viewpoint);
      Feature boxFt = ma.generateFeatureFromBbox(box.optDouble("w", 0), box.optDouble("h", 0),
          iaResult.optDouble("tlx", 0), box.optDouble("tly", 0), fparams, "org.sosf.boundingBox");
      System.out.println("convertAnnotation() generated ft = " + boxFt + "; params = " + boxFt.getParameters());
      ftArray.add(boxFt);
    }
    
    // do boundary
    JSONObject boundary = getFeatByType(fts, "refined_fin_boundary");
    if (boundary != null) {
      JSONObject fparams = new JSONObject();
      fparams.put("x", boundary.optJSONArray("x"));
      fparams.put("y", boundary.optJSONArray("y"));
      fparams.put("keypoints", boundary.optJSONArray("keypoints"));
      fparams.put("note", boundary.optString("note", null));
      ftArray.add(new Feature("org.sosf.ws.dorsalfin.edgeSpots", fparams));
    }   
    

    
    // mask feature
    JSONObject mask =  getFeatByType(fts, "detection_soft_mask");
    if (mask != null) {
      ftArray.add(new Feature("org.sosf.boundingBoxSoftMask", mask));
    }
    
    
    // do quality
    double defaultQVal = -999999.0;
    JSONObject quality = getFeatByType(fts, "quality");
    double qualScore = defaultQVal;
    if (quality!=null) {
      qualScore = quality.optDouble("scores", defaultQVal);
    }
    
    
    Annotation ann = new Annotation(convertSpeciesToString(iaClass), ftArray, iaClass);
    ann.setAcmId(iaResult.optString("uuid", null));
    ann.setViewpoint(viewpoint);
    if (qualScore!=defaultQVal) ann.setQuality(qualScore);
    
  //  // todo replace this with something more sophisticated - maybe above - make the best n annotations as matchagainst
//    if((qualScore>0) & (finKw == null)) {
//      System.out.println("[INFO] WSIA: convertAnnotation: setting annotation (acmId) "+ann.getAcmId()+" to match against based on quality and"
//          + "not caudal only. Replace with better system..??");
//      ann.setMatchAgainst(true);
//    }
    
    return ann;
    
  }
  
  
  public static String convertSpeciesToString(String iaClassLabel) {
    String[] s = convertSpecies(iaClassLabel);
    if (s == null) return null;
    return StringUtils.join(s, " ");
  }
  public static String[] convertSpecies(String iaClassLabel) {
    if (iaClassLabel == null) return null;
    if (speciesMap.containsKey(iaClassLabel)) return speciesMap.get(iaClassLabel);
    return null;  //we FAIL now if no explicit mapping.... sorry
  }
  
  public static void initiateIndexCreation(String context) throws InvalidKeyException, NoSuchAlgorithmException, RuntimeException, IOException{
    //likely will become more sophisticated, but for now:
    //
    // 1. get the config from ia.properties:
    // {"indexId":id,"matchingSetParams"{"species";param,"location":param2, "etc":p3},"encodingParams":{}}
    // for all indexes we want to build. Perhaps later we can have args which indicate / override 
    // params from ia.properties? NB we'll save the encoding / matching params in ia with the index 
    // so we can check the params for consistency with those provided during identification 
    //
    // 2. get the matching set
    //
    // 3. (optional) sendMediaAssetsNew(), sendAnnotationsNew()
    //
    // 4. Create a (uuid) index version id
    //
    // 5. Send the index creation task to ia
    //
    // 6. (do later) provide callback url (?) and persist the index creation process so we can track it
    Shepherd myShepherd = new Shepherd(context);
    JSONObject matchingSet = getMatchAgainstSetJson(myShepherd);
    HashMap<String,Object> map = new HashMap<String,Object>();
    map.put("matchset",matchingSet);
    map.put("matchset_tag", "SAWS");
    map.put("taskid",Util.generateUUID());
    map.put("encoding_config", "default");
    
    String u = IA.getProperty(context, "WSIARestUrlCreateIndex");
    if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlStartIdentifyAnnotations is not set");
    URL url = new URL(u);
    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject2(map));
    return;
    
    
    
    
  }
  
  
  public static boolean validForIdentification(Annotation ann) {
    if (ann.getQuality()==null) return false;
    if (getFeatByType(ann.getFeatures(),"org.sosf.ws.dorsalfin.edgeSpots")==null) return false;
    MediaAsset asset = ann.getMediaAsset();
    if (asset==null) return false;
    if (!asset.getDetectionStatus().equals(STATUS_COMPLETE)) return false;
    return true;
  }
  
  static public ArrayList<Annotation> getMatchingSetForFilter(Shepherd myShepherd, String filter) {
    if (filter == null) return null;
    long t = System.currentTimeMillis();
    System.out.println("INFO: getMatchingSetForFilter filter = " + filter);
    Query query = myShepherd.getPM().newQuery(filter);
    Collection c = (Collection)query.execute();
    Iterator it = c.iterator();
    ArrayList<Annotation> anns = new ArrayList<Annotation>();
    while (it.hasNext()) {
        Annotation ann = (Annotation)it.next();
        if (!validForIdentification(ann)) continue;
        anns.add(ann);
    }
    query.closeAll();
    System.out.println("INFO: getMatchingSetForFilter found " + anns.size() + " annots (" + (System.currentTimeMillis() - t) + "ms)");
    return anns;
  }
  
  static public ArrayList<Annotation> getMatchingSetAllSpecies(Shepherd myShepherd) {
    return getMatchingSetForFilter(myShepherd, "SELECT FROM org.ecocean.Annotation WHERE matchAgainst && acmId != null");
  }
  
  
  public static JSONObject getMatchAgainstSetJson(Shepherd myShepherd) {
    ArrayList<Annotation> allAnns = getMatchAgainstSet(myShepherd);
    ArrayList<String> acmList = new ArrayList<String>();
    ArrayList<Double> qualityList = new ArrayList<Double>();
    ArrayList<List<String>> kwList = new ArrayList<List<String>>();
    ArrayList<String> encounterIdList = new ArrayList<String>();
    // gonna need a bunch of catches and conditions in here (if returns null, length=0 etc
    // conditions about acmid and matchagainst = true handled in query string. Correct fetaures, quality
    // and media asset, asset detection status handled in validforidentification(). Here we just check 
    // the encounter...
    for (Annotation a : allAnns) {
      Encounter enc = a.findEncounter(myShepherd);
      if (enc == null) continue;
      String catNo = enc.getCatalogNumber();
      if (catNo == null) continue;
      encounterIdList.add(catNo);  
      acmList.add(a.getAcmId());
      qualityList.add(a.getQuality());
      MediaAsset asset = a.getMediaAsset();
      kwList.add(asset.getKeywordNames());
      // maybe put date in here if we want to at some point...
//    e.g.  enc.getDateInMilliseconds();
    }
    
    JSONObject set = new JSONObject();
    set.put("acmList", acmList);
    set.put("qualityList", qualityList);
    set.put("kwList", kwList);
    set.put("encounterIdList", encounterIdList);
    return set;
  }
  
  
  
  
  public static ArrayList<Annotation> getMatchAgainstSet(Shepherd myShepherd){
    return getMatchingSetAllSpecies(myShepherd);
  }
  
//  public static ArrayList<Annotation> getTopNAnnotationsByQuality(Encounter enc, int N) {
//    ArrayList<Annotation> qualifyingAnnotations = new ArrayList<Annotation>();
//    ArrayList<Annotation> matchAgainstSet = new ArrayList<Annotation>();
//    for (Annotation a : enc.getAnnotations()) {
//      if (a.isTrivial()) continue;
//      MediaAsset asset = a.getMediaAsset();
//      if (asset == null) continue;
//      if (! asset.getDetectionStatus().equals(org.ecocean.wsiaPackage.WSIA.STATUS_COMPLETE)) continue;
//      if ( asset.getKeywordNames().contains("Caudal")) continue;
//      Feature boundary = org.ecocean.wsiaPackage.WSIA.getFeatByType(asset.getFeatures(),"org.sosf.ws.dorsalfin.edgeSpots");
//      if (boundary == null) continue;
//      if (asset.getKeywordNames().contains("Exemplar")) {
//        matchAgainstSet.add(a);
//      } else {
//        qualifyingAnnotations.add(a);  
//      }
//    }
//     if (matchAgainstSet.size()>=N) {
//       return (ArrayList<Annotation>) matchAgainstSet.subList(0, N-1);
//     }
//    
//    if (qualifyingAnnotations.size()==0) return matchAgainstSet;
//    ArrayList<Double> quals = new ArrayList<Double>();
//    for (Annotation a : qualifyingAnnotations) {
//      quals.add(a.getQuality());
//    }
//    Collections.sort(quals);
//    Collections.reverse(quals);
//    
//    int numNeeded = N - matchAgainstSet.size();
//    double thresh = quals.get(numNeeded).doubleValue();
//    for (Annotation a : qualifyingAnnotations) {
//      if (a.getQuality().doubleValue()>thresh) matchAgainstSet.add(a);
//    }
//    return matchAgainstSet;  
//  }
  
  
//  private static boolean duplicateDetection2(MediaAsset ma, JSONObject iaResult) {
//    System.out.println("-- Verifying that we do not have a feature for this detection already...");
//    String result_acmid = iaResult.optString("uuid", null);
//    if (result_acmid == null) return false;
//    JSONArray fts = iaResult.optJSONArray("features");
//    JSONObject box = getFeatByType(fts, "box");
//    if (box == null) return false;
//    if (ma.getFeatures()!=null&&ma.getFeatures().size()>0) {
//      ArrayList<Feature> ftrs = ma.getFeatures();
//      for (Feature ft  : ftrs) {
//        if (ft.getType() == null) continue;
//        if (! ft.getType().toString().equals("org.sosf.boundingBox")) continue;
//        
//        
//      }    
//    }
//  }
  
//  private static boolean duplicateDetection(MediaAsset ma, JSONObject iaResult ) {
//    // jann is iaResult
//    System.out.println("-- Verifying that we do not have a feature for this detection already...");
//    if (ma.getFeatures()!=null&&ma.getFeatures().size()>0) {
//      
//      JSONArray fts = iaResult.optJSONArray("features");
//      JSONObject box = getFeatByType(fts, "box");
//        double width = box.optDouble("w", 0);
//        double height = box.optDouble("h", 0);
//        double xtl = box.optDouble("tlx", 0);
//        double ytl = box.optDouble("tly", 0);
//        ArrayList<Feature> ftrs = ma.getFeatures();
//        System.out.println(ftrs.size());
//        for (Feature ft  : ftrs) {
//          if (ft.getType() == null) continue;
//          if (! ft.getType().toString().equals("org.sosf.boundingBox")) continue;
//            try {
//                JSONObject params = ft.getParameters();
//                if (params!=null) {
//                    Double ftWidth = params.optDouble("width", 0);
//                    Double ftHeight = params.optDouble("height", 0);
//                    Double ftXtl = params.optDouble("x", 0);
//                    Double ftYtl = params.optDouble("y", 0);
//                    // yikes!
//                    if (ftHeight==0||ftHeight==0||height==0||width==0) {continue;}
//                    if ((width==ftWidth)&&(height==ftHeight)&&(ytl==ftYtl)&&(xtl==ftXtl)) {
//                        System.out.println("We have an Identicle detection feature! Skip this ann.");
//                        return true;
//                    }
//                }
//            } catch (NullPointerException npe) {continue;}
//        }
//    }
//    System.out.println("---- Did not find an identicle feature.");
//    return false;
//}
  
  public static double getDetectionCutoffValue(String context) {
    return getDetectionCutoffValue(context, null);
}

//scores < these will require human review (otherwise they carry on automatically)
// task is optional, but can have a parameter "detectionCutoffValue"
public static double getDetectionCutoffValue(String context, Task task) {
    if ((task != null) && (task.getParameters() != null) && (task.getParameters().optDouble("detectionCutoffValue", -1) > 0))
        return task.getParameters().getDouble("detectionCutoffValue");
    String c = IA.getProperty(context, "detectionCutoffValue");
    if (c != null) {
        try {
            return Double.parseDouble(c);
        } catch(java.lang.NumberFormatException ex) {}
    }
    return 0.1;  //lowish value cuz we trust detection by default
}
  
  public static Feature getFeatByType(ArrayList<Feature> fts, String type) {
    for (Feature f : fts) {
      if (type.equals(f.getType().getId())) return f;
    }
    return null;
  }
          
  public static JSONObject getFeatByType(JSONArray jarr, String type) {
      JSONObject rtn  = null;
      for (int el =0; el<jarr.length(); el++) {
          rtn = jarr.getJSONObject(el);
          if (rtn.optString("type", "").equals(type)) 
            return rtn;
      }
      return rtn;
  }
  
  public static String getTaskType(ArrayList<IdentityServiceLog> logs) {
    for (IdentityServiceLog l : logs) {
        JSONObject j = l.getStatusJson();
        if ((j == null) || j.optString("_action").equals("")) continue;
        if (j.getString("_action").indexOf("init") == 0) return j.getString("_action").substring(4).toLowerCase();
    }
    return null;
}
  
  
  public static JSONObject getJobResult(String jobID, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
    String u = IA.getProperty(context, "IBEISIARestUrlGetJobResult");
    if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlGetJobResult is not set");
    URL url = new URL(u + "?jobid=" + jobID);
    return RestClient.get(url);
}
  
  //this finds the *most recent* taskID associated with this IBEIS-IA jobID
  public static String findTaskIDFromJobID(String jobID, String context) {
    Shepherd myShepherd=new Shepherd(context);    
    myShepherd.setAction("IBEISIA.findTaskIDFromJobID");
    myShepherd.beginDBTransaction();
    ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByServiceJobID(SERVICE_NAME, jobID, myShepherd);
      if (logs == null) {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return null;
      }
      for (int i = logs.size() - 1 ; i >= 0 ; i--) {
          if (logs.get(i).getTaskID() != null) {
            String id=logs.get(i).getTaskID();
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return id;
          }  //get first one we find. too bad!
      }
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      return null;
  }
  
  public static IdentityServiceLog log(String taskID, String jobID, JSONObject jlog, String context) {
    String[] sa = null;
    return log(taskID, sa, jobID, jlog, context);
}

public static IdentityServiceLog log(String taskID, String objectID, String jobID, JSONObject jlog, String context) {
    String[] sa = new String[1];
    sa[0] = objectID;
    return log(taskID, sa, jobID, jlog, context);
}

public static IdentityServiceLog log(String taskID, String[] objectIDs, String jobID, JSONObject jlog, String context) {
//System.out.println("#LOG: taskID=" + taskID + ", jobID=" + jobID + " --> " + jlog.toString());
    IdentityServiceLog log = new IdentityServiceLog(taskID, objectIDs, SERVICE_NAME, jobID, jlog);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("IBEISIA.log");
    myShepherd.beginDBTransaction();
    try{
      log.save(myShepherd);
    }
    catch(Exception e){e.printStackTrace();}
    finally{
      myShepherd.commitDBTransaction();
      myShepherd.closeDBTransaction();
    }

    return log;
}



}
