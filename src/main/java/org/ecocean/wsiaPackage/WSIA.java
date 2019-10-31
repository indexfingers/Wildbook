package org.ecocean.wsiaPackage;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;

import org.ecocean.CommonConfiguration;
import org.ecocean.RestClient;
import org.ecocean.Shepherd;
import org.ecocean.Taxonomy;
import org.ecocean.ia.IA;
import org.ecocean.ia.IAPluginManager;
import org.ecocean.ia.Task;
import org.ecocean.ia.plugin.IAPlugin;
import org.ecocean.ia.plugin.WSIAM;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.media.MediaAssetSet;
import org.json.JSONObject;
import org.json.JSONArray;

public class WSIA {

  public static final String STATUS_PENDING = "pending"; // pending review
                                                         // (needs action by
                                                         // user)
  public static final String STATUS_COMPLETE = "complete"; // process is done
  public static final String STATUS_PROCESSING = "processing"; // off at IA,
                                                               // awaiting
                                                               // results
  public static final String STATUS_ERROR = "error";
  public static final String IA_UNKNOWN_NAME = "____";

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
        IBEISIA.log(taskId, validIds.toArray(new String[validIds.size()]), jobId, new JSONObject("{\"_action\": \"initDetect\"}"), context);
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
    String labelerAlgo = getLabelerAlgo(context);
    if (labelerAlgo!=null) {
      map.put("labeler_algo",labelerAlgo);
      System.out.println("[INFO] sendDetect() labeler_algo set to " + labelerAlgo);
    } else {System.out.println("[INFO] sendDetect() labeler_algo is null; skipping");}
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
    String modelTag = IA.getProperty(context, "modelTag");
    if (modelTag != null) {
        System.out.println("[INFO] sendDetect() model_tag set to " + modelTag);
        map.put("model_tag", modelTag);
    } else {
        System.out.println("[INFO] sendDetect() model_tag is null; DEFAULT will be used");
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
    
    String refineEdgeTag = IA.getProperty(context, "refineEdgeTag");
    if (refineEdgeTag != null) {
        System.out.println("[INFO] sendDetect() refineEdgeTag set to " + refineEdgeTag);
        map.put("refineEdgeTag", refineEdgeTag);
    } else {
        System.out.println("[INFO] sendDetect() refineEdgeTag is null; DEFAULT will be used");
    }
    
    String sideClassifierTag = IA.getProperty(context, "sideClassifierTag");
    if (sideClassifierTag != null) {
        System.out.println("[INFO] sendDetect() sideClassifierTag set to " + sideClassifierTag);
        map.put("sideClassifierTag", sideClassifierTag);
    } else {
        System.out.println("[INFO] sendDetect() sideClassifierTag is null; DEFAULT will be used");
    }
    
    String keypointModelTag = IA.getProperty(context, "keypointModelTag");
    if (keypointModelTag != null) {
        System.out.println("[INFO] sendDetect() keypointModelTag set to " + keypointModelTag);
        map.put("keypointModelTag", keypointModelTag);
    } else {
        System.out.println("[INFO] sendDetect() keypointModelTag is null; DEFAULT will be used");
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
    return IA.getProperty(context, "labelerAlgo").trim();
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

}
