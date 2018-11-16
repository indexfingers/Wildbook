package org.ecocean.identity;

import java.util.List;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import org.ecocean.Annotation;
import org.ecocean.MarkedIndividual;
import org.ecocean.Util;
import org.ecocean.ia.Task;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.CommonConfiguration;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.net.URL;
import org.apache.commons.lang3.StringUtils;

/*
import org.ecocean.ImageAttributes;
import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import java.util.HashMap;
import java.util.Map;
import org.ecocean.media.*;
import org.ecocean.RestClient;
import java.io.File;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import org.joda.time.DateTime;
import org.apache.commons.lang3.StringUtils;
*/
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.io.FileWriter;
import java.io.BufferedWriter;
import javax.jdo.Extent;
import javax.jdo.Query;

import org.ecocean.identity.BenWhitesharkPkg.Ec2Tools;
import org.ecocean.identity.BenWhitesharkPkg.SQStools;

public class BenWhiteshark {
//#BenWhitesharkJobStartDirectory = /efs/job/start
//#BenWhitesharkJobResultsDirectory = /efs/job/results

    public static final String ERROR_KEY = "__ERROR__";
    public static final String SERVICE_NAME = "BenWhiteshark";
    public static final HashMap<Integer,String> JOB_DATA_CACHE = new HashMap<Integer,String>();

    public static boolean enabled() {
        return ((getJobStartDir() != null) && (getJobResultsDir() != null));
    }
    public static JSONObject iaStatus() {
        JSONObject j = new JSONObject();
        j.put("enabled", enabled());
        return j;
    }

    //right now we use .isExemplar on Annotations; but later we may shift to some other logic, including (discussed with ben):
    //  quality keywords on image, features approved/input by manual method (e.g. end-points of fin) etc....
    //  also: the choice to focus on Annotation vs MediaAsset feels a little arbitrary; am choosing Annotation... for now?
    public static List<Annotation> getExemplars(Shepherd myShepherd) {
        Extent all = myShepherd.getPM().getExtent(Annotation.class, true);
        Query qry = myShepherd.getPM().newQuery(all, "isExemplar");
        Collection results = (Collection)qry.execute();
        List<Annotation> rtn = new ArrayList<Annotation>();
        for (Object o : results) {
            Annotation ann = (Annotation)o;
            if (ann.getMediaAsset() != null) rtn.add(ann);
        }
        return rtn;
    }

    public static File getJobStartDir() {
        String d = CommonConfiguration.getProperty("BenWhitesharkJobStartDirectory", "context0");
        if (d == null) return null;
        return new File(d);
    }
    public static File getJobResultsDir() {
        String d = CommonConfiguration.getProperty("BenWhitesharkJobResultsDirectory", "context0");
        if (d == null) return null;
        return new File(d);
    }

    //TODO support taxonomy!
    public static String startJob(List<MediaAsset> queryMAs, Shepherd myShepherd) {
////NOTE: per discussion with ben on 2016/10/24 we now will not pass *any* target MAs... whole set will be assumed to be known by CV
System.out.println("WARNING: currently not passing target MA (exemplar) data to job start file! per discussion on 2016/10/24");
List<MediaAsset> tmas = new ArrayList<MediaAsset>();
/*
        List<Annotation> exs = getExemplars(myShepherd);
        if ((exs == null) || (exs.size() < 1)) throw new RuntimeException("getExemplars() returned no results");
System.out.println("startJob() exemplars size=" + exs.size());
        List<MediaAsset> tmas = new ArrayList<MediaAsset>();
        for (Annotation ann : exs) {
            if (!queryMAs.contains(ann.getMediaAsset())) tmas.add(ann.getMediaAsset());
        }
*/
System.out.println("queryMAs.size = " + queryMAs.size());
        String[] ids = new String[queryMAs.size()];
        for (int i = 0 ; i < queryMAs.size() ; i++) {
            ids[i] = Integer.toString(queryMAs.get(i).getId());
        }
        String taskId = startJob(queryMAs, tmas);
        IdentityServiceLog log = new IdentityServiceLog(taskId, ids, SERVICE_NAME, null, new JSONObject("{\"_action\": \"initIdentify\"}"));
        log.save(myShepherd);
        return taskId;
    }
    //single queryMA convenience method
    public static String startJob(MediaAsset queryMA, Shepherd myShepherd) {
        List<MediaAsset> mas = new ArrayList<MediaAsset>();
        mas.add(queryMA);
        return startJob(mas, myShepherd);
    }
    public static String startJob(List<MediaAsset> queryMAs, List<MediaAsset> targetMAs) {
        String taskId = Util.generateUUID();
System.out.println("startJob() taskId="+taskId);
        String contents = "";
int count = 0;
        for (MediaAsset ma : queryMAs) {
count++;  System.out.println(count + "/" + queryMAs.size() + ": " + ma.getId());
            contents += jobdata(ma);
        }
/*
        contents += "-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\n";   //agreed divider between queryMA(s) and targetMA(s)
count = 0;
        for (MediaAsset ma : targetMAs) {
count++;  System.out.println(count + "/" + targetMAs.size() + ": " + ma.getId());
            contents += jobdata(ma);
        }
*/
        writeFile(taskId, contents);
        return taskId;
    }

    static String jobdata(MediaAsset ma) {
        if (ma == null) return "# null MediaAsset passed\n";
        if (JOB_DATA_CACHE.get(ma.getId()) != null) return JOB_DATA_CACHE.get(ma.getId());
        Shepherd myShepherd = new Shepherd("context0");
        //i guess technically we only need encounter to get individual... which maybe we dont need?
        Encounter enc = null;
        for (Annotation ann : ma.getAnnotations()) {
            enc = Encounter.findByAnnotation(ann, myShepherd);
            if (enc != null) break;
        }
        if (enc == null) return "#unable to find Encounter for " + ma.toString() + "; skipping\n";
        //yup, this assumes LocalAssetStore, but thats our magic here
	//String filePathString = ma.localPath().toString();
      //  String pathReplaceRegex = CommonConfiguration.getProperty("BenWhitesharkMediaAssetPathReplaceRegex", "context0");
      //  String pathReplaceValue = CommonConfiguration.getProperty("BenWhitesharkMediaAssetPathReplaceValue", "context0");
	//if ((pathReplaceRegex != null) && (pathReplaceValue != null)) {
	//	filePathString = filePathString.replace(pathReplaceRegex, pathReplaceValue);
	//}

  //BH: now making 'filePathString' a url - should work with S3 or LocalAssetStore
        String filePathString = ma.webURLString();

        String jd = ma.getUUID() + "\t" + filePathString + "\t" + (enc.hasMarkedIndividual() ? enc.getIndividualID() : "-1") + "\t" + enc.getCatalogNumber() +
                    "\t-1\t-1\t-1\t-1\n";    // this is holding the place of the potential two fin end points x1,y1 x2,y2 (via user input)
        JOB_DATA_CACHE.put(ma.getId(), jd);
        return jd;
    }

    static void writeFile(String taskId, String contents) {
        File dir = getJobStartDir();
        if (dir == null) throw new RuntimeException("no defined BenWhitesharkJobStartDirectory");
        File ftmp = new File(dir, taskId + ".tmp");  //dissuade race condition of reading before done
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(ftmp));
            writer.write(contents);
            writer.close();
            ftmp.renameTo(new File(dir, taskId));
        } catch (java.io.IOException ex) {
            ex.printStackTrace();
        }
    }


/*  results files look like:
ids	scores
59	0.242
64	0.043
50	0.039
48	0.03
40	0.024
15	0.022
1	0.02
89	0.018
*/
    //null means we dont have any
    // results are keyed off of MediaAsset id, which points to a LinkedHashMap of IndividualID/scores (from file)
    // if a key __ERROR__ exists, there was an error from the IA algorithm, which take the form of .err files, with the filename prefix
    // being either (a) the MediaAsset id [for specific error], or (b) taskId [for general]... notably this special key can exist in either map level
    public static HashMap<String,List<String[]>> getJobResultsRaw(String taskId) {
        File dir = new File(getJobResultsDir(), taskId);
        if (!dir.exists()) return null;
        HashMap<String,List<String[]>> rtn = new HashMap<String,List<String[]>>();
        //first lets check for a general error
        File gerr = new File(dir, taskId + ".err");
        if (gerr.exists()) {
            //we need to create a "results" LinkedHashMap to point to, so this is a little wonky
            List<String[]> m = new ArrayList<String[]>();
            m.add(new String[]{StringUtils.join(Util.readAllLines(gerr), "")});
            rtn.put(ERROR_KEY, m);  //thus, to find error msg for general case, need to use ERROR_KEY twice
            return rtn;  //is our work really done here?  seems like implies fail
        }
        for (final File f : dir.listFiles()) {
            if (f.isDirectory()) continue;  //"should never happen"
            List<String[]> m = new ArrayList<String[]>();
            String id = f.getName();  //will get truncated
            if (id.indexOf(".err") > -1) {
                id = id.substring(0, id.length() - 4);
                m.add(new String[]{ERROR_KEY, StringUtils.join(Util.readAllLines(f), ""), null});
                rtn.put(id, m);
            } else if (id.indexOf(".txt") > -1) {
                id = id.substring(0, id.length() - 4);
                for (String line : Util.readAllLines(f)) {
                    if (line.indexOf("ids\t") == 0) continue;  //skip header
/*
                    String[] data = StringUtils.split(line, "\t");
                    if (data.length < 3) continue;  //no?
                    Double score = -1.0;  //if parsing fails?
                    try {
                        score = Double.parseDouble(data[1]);
                    } catch (NumberFormatException ex) { };  //meh
                    m.put(data[0], score);
*/
                    m.add(StringUtils.split(line, "\t"));
                }
                rtn.put(id, m);
            }
        }
        return rtn;
    }

    //grabs from ident log if we have it, otherwise it will attempt to grab raw job results
    public static JSONObject getTaskResults(String taskId, Shepherd myShepherd) {
        return getTaskResults(taskId, IdentityServiceLog.loadByTaskID(taskId, SERVICE_NAME, myShepherd), myShepherd);
    }

    public static JSONObject getTaskResults(String taskId, ArrayList<IdentityServiceLog> logs, Shepherd myShepherd) {
        if ((logs != null) && (logs.size() > 0)) {
            for (IdentityServiceLog log : logs) {
                if ((log.getStatusJson() != null) && (log.getStatusJson().optJSONObject("results") != null))
                    return log.getStatusJson().getJSONObject("results");
            }
        }
        System.out.println("NOTE: getTaskResults(" + taskId + ") fell thru, trying getJobResultsRaw()");
        HashMap<String,List<String[]>> raw = getJobResultsRaw(taskId);
        if (raw == null) return null;
        String[] ids = null;
        if (!raw.containsKey(ERROR_KEY)) {
            ids = new String[raw.keySet().size()];
            ids = raw.keySet().toArray(ids);
        }
        JSONObject jlog = new JSONObject();
        jlog.put("results", resultsAsJSONObject(raw));
        IdentityServiceLog log = new IdentityServiceLog(taskId, ids, SERVICE_NAME, null, jlog);
        log.save(myShepherd);
        return resultsAsJSONObject(raw);
    }

    public static JSONObject resultsAsJSONObject(HashMap<String,List<String[]>> resMap) {
        if (resMap == null) return null;
        JSONObject matches = new JSONObject();
        if (resMap.containsKey(ERROR_KEY)) {
            matches.put("error", resMap.get(ERROR_KEY).get(0)[0]);
            return matches;
        }
        for (String mid : resMap.keySet()) {
            if ((resMap.get(mid).size() > 0) && (resMap.get(mid).get(0).length > 0) && ERROR_KEY.equals(resMap.get(mid).get(0)[0])) {
                matches.put(mid, resMap.get(mid).get(0)[1]);
                continue;
            }
            JSONArray marr = new JSONArray();
            for (int i = 0 ; i < resMap.get(mid).size() ; i++) {
                marr.put(new JSONArray(resMap.get(mid).get(i)));
            }
/*
            for (String iid : resMap.get(mid).keySet()) {
                JSONObject jscore = new JSONObject();
                jscore.put(iid, resMap.get(mid).get(iid));
                marr.put(jscore);
            }
*/
            matches.put(mid, marr);
        }
        return matches;
    }
    
    public static JSONObject resultsAsJSONObject(JSONObject idRes) {
      // here we want to output a jsonobject.
      // each 'key' is the query media asset uuid
      // each 'value' is a jsonarray. Each element of the array is a string array
      // of length 3 with values [<id> <score> <refMAUuid>]
      
      // if there is a general failure, the 'key' is 'error' and the value is whatever (an error message)
      
      // if there is a failure for specific query ma, the key is the query ma uuid, and the value is a 
      // string -  any old string perhaps, but we can make it an error message...
      // the question is, downstream, how do we know this string represents an error? I think this is because
      // when there's not an error, the value associated to this key is a jsonarray, not a string
      if (idRes == null) return null;
      JSONObject matches = new JSONObject();
      if (!idRes.optString("status").equals("success")) {
          matches.put("error", idRes.optString("errorMessage"));
          return matches;
      }
      // if json object, (one result), wrap as array
      JSONArray resArr = new JSONArray();
      if (idRes.optJSONObject("results")!=null) {
        resArr.put(idRes.optJSONObject("results"));
      } else if (idRes.optJSONArray("results")==null){
        matches.put("error", "malformed request");
        return matches;
      } else {
        resArr = idRes.optJSONArray("results");
      }
      // r is a set of matches for one query image
      JSONObject r = new JSONObject();
      for (int i = 0 ; i < resArr.length() ; i++) {
        r = resArr.optJSONObject(i);
        // rowArr is an array to store the matches for current query
        JSONArray rowArr = new JSONArray();
        // can't do anything if no result or no query image... shouldn't happen
        if ((r == null) || (r.optString("queryMediaAssetUuid")==null)) {
          continue;
        }
        if (r.optString("status")==null) {
          matches.put(r.optString("queryMediaAssetUuid"), "error - no status");
          continue;
        }
        if (!r.optString("status").equals("success")) {
          matches.put(r.optString("queryMediaAssetUuid"), "error - " + r.optString("errorMessage"));
          continue;
        }
        // a malformed result - put error for this query
        if ((r.optJSONArray("ids") == null) || (r.optJSONArray("scores") == null) || (r.optJSONArray("referenceMediaAssetUuids"))==null) {
          // special case where one image returned for this query
          if (r.optString("ids")!=null) {
              // if no scores or reference images - put error for this query
              if ( (r.optString("scores") == null) || (r.optString("referenceMediaAssetUuids"))==null ) {
                matches.put(r.optString("queryMediaAssetUuid"), "error - either no scores or reference mas for this query");
                continue;
              }
              JSONArray row  = new JSONArray(); row.put(0, r.optString("ids"));row.put(1, r.optString("scores"));
              row.put(2, r.optString("referenceMediaAssetUuids"));
              rowArr.put(row);
              matches.put(r.optString("queryMediaAssetUuid"), rowArr);
              continue;
            }
          matches.put(r.optString("queryMediaAssetUuid"), "error - malformed result for this query");
          continue;
        }
        for (int j = 0 ; j < r.optJSONArray("ids").length() ; j++) {
          JSONArray row  = new JSONArray(); row.put(0, r.optJSONArray("ids").getString(j));row.put(1, r.optJSONArray("scores").get(j).toString());
          row.put(2, r.optJSONArray("referenceMediaAssetUuids").getString(j));
          rowArr.put(j, row);
        }
        matches.put(r.optString("queryMediaAssetUuid"), rowArr);
      }
      return matches;
  }
    
    

    public static JSONObject iaGateway(JSONObject arg, HttpServletRequest request) {
 
        JSONObject res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        if (arg.optInt("identify", -1) > 0) {  //right now, start identify with {identify: maId}
            int mid = arg.getInt("identify");
            MediaAsset ma = MediaAssetFactory.load(mid, myShepherd);
            if (ma == null) {
                res.put("error", "unknown MediaAsset id=" + mid);
                return res;
            }
            String taskId = startJob(ma, myShepherd);
            res.put("success", true);
            res.remove("error");
            res.put("taskId", taskId);

        } else if (arg.optString("taskResults", null) != null) {
            String taskId = arg.getString("taskResults");
            res.put("taskId", taskId);
            ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskId, SERVICE_NAME, myShepherd);
            if ((logs == null) || (logs.size() < 1)) {
                res.put("error", "unknown taskId=" + taskId);
                return res;
            }
            String[] ids = null;
            for (IdentityServiceLog log : logs) {
                if ((log.getObjectIDs() != null) && (log.getObjectIDs().length > 0)) {
                    ids = log.getObjectIDs();
                    break;
                }
            }
            if (ids != null) {
                JSONArray qobjs = new JSONArray();
                for (int i = 0 ; i < ids.length ; i++) {
                    JSONObject j = new JSONObject();
                    int mid = -1;
                    try { mid = Integer.parseInt(ids[i]); } catch (Exception ex) {}
                    MediaAsset ma = MediaAssetFactory.load(mid, myShepherd);
                    if (ma != null) {
                        try {
                            j.put("asset", Util.toggleJSONObject(ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject())));
                        } catch (Exception ex) {}
                        Encounter enc = Encounter.findByMediaAsset(ma, myShepherd);
                        if (enc != null) j.put("encounterId", enc.getCatalogNumber());
                        qobjs.put(j);
                    }
                }
                if (qobjs.length() > 0) res.put("queryObjects", qobjs);
            }
            JSONObject tres = getTaskResults(taskId, logs, myShepherd);
            if (tres == null) {
                res.put("error", "no results for taskId=" + taskId);
                return res;
            }
            if (tres.opt("error") != null) {  //general failure!
                res.put("error", tres.get("error"));
                return res;
            }
            res.put("success", true);  //well, at least partially?
            res.remove("error");
            Iterator kit = tres.keys();
            while (kit.hasNext()) {
                String key = (String)kit.next();
                if (tres.optJSONArray(key) == null) continue;
                for (int i = 0 ; i < tres.getJSONArray(key).length() ; i++) {
                    JSONArray row = tres.getJSONArray(key).optJSONArray(i);
                    if (row == null) continue;
                    if (row.optString(2, null) != null) {
                        String url = getUrlForMediaAssetUUID(row.getString(2), myShepherd);
                        if (url != null) {
                            row.put(url);
                            tres.getJSONArray(key).put(i, row);
                        }
                    }
                }
            }
            res.put("matches", tres);

/*
            JSONObject tarr = new JSONObject();
            Iterator kit = tres.keys();
            while (kit.hasNext()) {
                String key = (String)kit.next();
                if (tres.optJSONArray(key) == null) continue;
                for (int i = 0 ; i < tres.getJSONArray(key).length() ; i++) {
                    //String indivId = (String)tres.getJSONArray(key).getJSONObject(i).keys().next();
                    String indivId = tres.getJSONArray(key).getJSONArray(i).optString(0, null);
System.out.println("[" + key + "] indivId ==> " + indivId);
                    if ((indivId == null) || indivId.equals(ERROR_KEY)) continue;
                    if (tarr.opt(indivId) == null) {
                        MediaAsset ma = null;
                        MarkedIndividual indiv = myShepherd.getMarkedIndividualQuiet(indivId);
                        if (indiv != null) {
                            for (Object obj : indiv.getEncounters()) {
                                Encounter enc = (Encounter)obj;
                                for (Annotation ann : enc.getAnnotations()) {
                                    if ((ma == null) || ann.getIsExemplar()) ma = ann.getMediaAsset();
                                }
                            }
                        }
                        if (ma != null) tarr.put(indivId, ma.webURL().toString());
                    }
                }
            }
            res.put("matchImages", tarr);
            
*/
        } else if (arg.optJSONObject("saveIdentificationResults") != null) {
          JSONObject idResults = arg.optJSONObject("saveIdentificationResults");
          if ((idResults.optString("taskId") == null) || (idResults.optString("status") == null)) {
            res.put("error", "malformed request - either no taskId or status");
            return res;
          } 
          String taskId = idResults.getString("taskId");
          res.put("taskId", taskId);
          ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskId, SERVICE_NAME, myShepherd);
          if ((logs == null) || (logs.size() < 1)) {
              res.put("error", "unknown taskId=" + taskId);
              return res;
          }
          JSONObject matches = resultsAsJSONObject(idResults);
          boolean isError = false;
          if (matches == null) {
            res.put("error", "no results for taskId=" + taskId);
            isError = true;
          }
          if (matches.opt("error") != null) {  //general failure!
             res.put("error", matches.get("error"));
             isError = true;
          }

          String[] ids = null;
          if (isError == false) {
            res.put("success", true);  
            res.remove("error");
            ArrayList<String> keyArr = new ArrayList<String>();
            Iterator kit = matches.keys();
            while (kit.hasNext()) {
              keyArr.add((String)kit.next());
            }
            ids = new String[keyArr.size()];
            keyArr.toArray(ids);
          }

          JSONObject jlog = new JSONObject();
          jlog.put("results", matches);
          IdentityServiceLog log = new IdentityServiceLog(taskId, ids, SERVICE_NAME, null, jlog);
          log.save(myShepherd);
          
          return res;
          
          
        //TODO should we have "add" vs "create" ?   for now we are assuming always only one.  replace-if-exists-else-create
        } else if (arg.optJSONArray("createFeatures") != null) {
            JSONArray farr = arg.optJSONArray("createFeatures");
            for (int i = 0 ; i < farr.length() ; i++) {
                if ((farr.optJSONObject(i) == null) || (farr.getJSONObject(i).optInt("mediaAssetId", -1) < 1) || (farr.getJSONObject(i).optJSONObject("parameters") == null)) continue;  //bad array element!
                MediaAsset ma = MediaAssetFactory.load(farr.getJSONObject(i).optInt("mediaAssetId", -1), myShepherd);
                if (ma == null) {
                    res.put("error", "invalid or unknown mediaAssetId passed");
                } else {
                    FeatureType.initAll(myShepherd);
                    String tstring = "com.saveourseas.dorsalEdge";
                    Feature ft = new Feature(tstring, farr.getJSONObject(i).getJSONObject("parameters"));
                    ma.removeFeaturesOfType(tstring);
                    ma.addFeature(ft);
                    MediaAssetFactory.save(ma, myShepherd);
                    res.put("success", true);
                    res.remove("error");
                    res.put("featureId", ft.getId());
                }
            }
            

        } else if (arg.optJSONObject("createFeatures2") != null) {
          
          //TODO - deal with different task types - if detect_identify type, kick off identification...
          
          JSONObject cf = arg.optJSONObject("createFeatures2");
          //JSONArray farr = arg.optJSONArray("createFeatures2");
          JSONArray responseArray = new JSONArray();
          boolean success = true;
          
          if((cf.optString("taskId")==null || cf.optString("taskType") == null))
              res.put("warning", "no task id or no task type");
          
          if((cf.optJSONObject("mediaAssets") == null) && (cf.optJSONArray("mediaAssets")==null)) {
            success = false;
            res.put("error", "no features");
            return res;
          }
          
          // if features for single ma, will have jsonobject, not jsonarray, so wrap jsonobject in array
          if(cf.optJSONObject("mediaAssets") != null) {
            JSONArray benJar = new JSONArray();
            benJar.put(cf.optJSONObject("mediaAssets"));
            cf.remove("mediaAssets");
            cf.put("mediaAssets", benJar);
          }
          
          JSONArray farr = cf.getJSONArray("mediaAssets");
          
          for (int i = 0 ; i < farr.length() ; i++) {
              JSONObject maResponse = new JSONObject();

              if ((farr.optJSONObject(i) == null) || (farr.getJSONObject(i).optString("mediaAssetUuid") == null) || (farr.getJSONObject(i).optString("status") == null)) {
                success = false;
                res.put("error", "bad array element");
                continue;  //bad array element!
              }
              
              maResponse.put("mediaAssetUuid", farr.getJSONObject(i).optString("mediaAssetUuid"));
              maResponse.put("success", true);

              MediaAsset ma = MediaAssetFactory.loadByUuid(farr.getJSONObject(i).optString("mediaAssetUuid"), myShepherd);
              if (ma == null) {
                  maResponse.put("error", "invalid or unknown mediaAssetUuid passed");
                  maResponse.put("success", false);
                  success = false;
                  res.put("error", "invalid or unknown mediaAssetUuid passed");
                  continue;
              }

              if (!farr.getJSONObject(i).optString("status").equals("success")) {
                maResponse.put("IAError", "IA Error");
                maResponse.put("success", false);
                success = false;
                ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                if (farr.getJSONObject(i).optString("errorMessage") != null) ma.setIaDetectionErrorMessage(farr.getJSONObject(i).optString("errorMessage"));
                MediaAssetFactory.save(ma,myShepherd);
                res.put("error", "IA Error");
                continue; // to features for next ma
              }

              // now the set of features for this ma / annotation
              JSONArray features = farr.optJSONObject(i).optJSONArray("features");
              JSONArray featIds = new JSONArray();
              if (features == null) {
                maResponse.put("error", "no features");
                maResponse.put("success", false);
                success = false;
                ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                MediaAssetFactory.save(ma,myShepherd);
                res.put("error", "no feeatures");
                continue; // to features for next ma
              }

              for (int k = 0; k < features.length(); k++) {
                FeatureType.initAll(myShepherd);

                if ((features.optJSONObject(k).optString("type") == null) || (features.optJSONObject(k).optJSONObject("parameters") == null) || (features.optJSONObject(k).optString("revision") == null)) {
                  maResponse.put("error", "feature with no feature type or no parameters or no revision");
                  maResponse.put("success", false);
                  ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                  success = false;
                  res.put("error", "no feature type or parameters or revision");
                  break;
                }
                // now assume we have parameters and feature type for this feature and for this media asset :-)
                // now stick features in ma.. (all gonna be okay now?)
                String tstring = features.optJSONObject(k).optString("type");
                Feature ft = new Feature(tstring, features.optJSONObject(k).optJSONObject("parameters"));
                ft.setRevision(Long.parseLong(features.optJSONObject(k).optString("revision")));

                // store feature id with feature type and add to array of feature ids
                JSONObject featId = new JSONObject();
                featId.put("type", tstring);
                featId.put("id",ft.getId());
                featIds.put(featId);

                ma.removeFeaturesOfType(tstring);
                ma.addFeature(ft);
              }

              // now all features added for this ma so add feature ids and then add response to array
              maResponse.put("featIds", featIds);
              responseArray.put(maResponse);
              if (maResponse.optBoolean("success") == true) {
                ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE);
              } else {
                ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
              }
              MediaAssetFactory.save(ma,myShepherd);
              
              // update annotation.revision
              //Annotation ann = loadAnnotationById(farr.getJSONObject(i).optString("annotationUuid"), myShepherd);
//              if(farr.getJSONObject(i).optString("revision") != null) {
//                Annotation ann = ma.getAnnotations().get(0);
//                ann.setRevision(Long.parseLong(farr.getJSONObject(i).optString("revision")));
//                myShepherd.getPM().makePersistent(ann);
//              }
          }
          

          res.put("success", success);
          if (success == true) res.remove("error");
          res.put("maResponses",responseArray);
        }else {
            res.put("error", "unknown command");
        }
        return res;
    }
    
    
//    private static Annotation loadAnnotationById(final String uuid, Shepherd myShepherd) {
//      Query query = myShepherd.getPM().newQuery(Annotation.class);
//      query.setFilter("id=='" + uuid + "'");
//      List results = (List)query.execute();
//      //uuid column is constrained unique, so should always get 0 or 1
//      if (results.size() < 1) return null;
//      return (Annotation)results.get(0);
//  }

    
    public static ArrayList<Task> intakeMediaAssets(Shepherd myShepherd, ArrayList<MediaAsset> mas, String taskType, boolean priority) {
      if ((mas == null) || (mas.size() < 1)) return null;
      // try to start detection servers
      if (checkStartOdServer(CommonConfiguration.getProperty("finDetectInstanceId", "context0")) == false) {
        System.out.println("issue starting detection server");
        return null;
      }
      
      if (checkStartOdServer(CommonConfiguration.getProperty("finRefineInstanceId", "context0"),CommonConfiguration.getProperty("spareFinRefineInstanceId", "context0")) == false) {
        System.out.println("issue starting fin refine server");
        return null;
      }
      //if (checkStartOdServer("i-04fbed57be6d86850") == false) return null;
      ArrayList<Task> tasks = new ArrayList<Task>();
      for (MediaAsset ma : mas) {
        tasks.add(intakeMediaAsset(myShepherd, ma, taskType, priority));
      }
      return tasks;
    }
    
    public static ArrayList<Task> intakeMediaAssets(Shepherd myShepherd, ArrayList<MediaAsset> mas, String taskType) {
      return intakeMediaAssets(myShepherd,mas, taskType, false);
    }
    
    
    
    public static boolean checkStartOdServer(String instanceId, String spareInstanceId) {
      boolean success = true;
      Ec2Tools ec2_tools = new Ec2Tools();
      ec2_tools.initialiseCredentials();
      ec2_tools.initialiseEc2();
      String status = ec2_tools.getInstanceStatusWithInstanceId(instanceId);
      if (status.equals("stopping")) {
        return checkStartOdServer(spareInstanceId);
      }
      if ((status.equals("running")) || (status.equals("pending"))) {
        return success;
      }
      String statusSpare = ec2_tools.getInstanceStatusWithInstanceId(spareInstanceId);
      if ((statusSpare.equals("running")) || (statusSpare.equals("pending"))) {
        return success;
      }
      if (status.equals("stopped")) {
        try {
          ec2_tools.startOdInstance(instanceId);
        } catch (Exception ex) {
          return false;
        }
        return success;
      }
      return false;
    }
    
  public static boolean checkStartOdServer(String instanceId) {
    boolean success = true;
    Ec2Tools ec2_tools = new Ec2Tools();
    ec2_tools.initialiseCredentials();
    ec2_tools.initialiseEc2();
    String status = ec2_tools.getInstanceStatusWithInstanceId(instanceId);
    if ((status.equals("running")) || (status.equals("pending"))) {
      return success;
    }
    if (status.equals("stopped")) {
      try {
        ec2_tools.startOdInstance(instanceId);
      } catch (Exception ex) {
        return false;
      }
      return success;
    }
    if (status.equals("stopping")) {
      int count = 0;
      while (status.equals("stopping")) {
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        status = ec2_tools.getInstanceStatusWithInstanceId(instanceId);
        count = count + 1;
        if (count == 20) {
          break;
        }
      }
    }

    if (status.equals("stopped")) {
      try {
        ec2_tools.startOdInstance(instanceId);
      } catch (Exception ex) {
        return false;
      }
      return success;
    }
    return false;
  }

      
      public static Task intakeMediaAsset(Shepherd myShepherd, MediaAsset ma, String taskType, boolean priority) {
        
        // need to add in part where it says if identification job or not - TICK
        // also need to write python code to automatically shutdown server and do status
        Task task = new Task();
        if (ma == null) return task;
        if (checkStartOdServer(CommonConfiguration.getProperty("finDetectInstanceId", "context0")) == false) {
          System.out.println("issue starting detection server");
          return task;
        }
        if (checkStartOdServer(CommonConfiguration.getProperty("finRefineInstanceId", "context0"),CommonConfiguration.getProperty("spareFinRefineInstanceId", "context0")) == false) {
          System.out.println("issue starting fin refine server");
          return task;
        }
        
        ArrayList<MediaAsset> maList = new ArrayList<MediaAsset>();
        maList.add(ma);
        task.setObjectMediaAssets(maList);
        JSONObject qjob = new JSONObject();
        qjob.put("taskType", taskType);
        qjob.put("mediaAssets", convertMaToJson(ma));
        qjob.put("taskId",task.getId());
        qjob.put("__context",myShepherd.getContext());
    
        //qjob.put("__baseUrl", getBaseUrl(context));
        
        ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING);
        MediaAssetFactory.save(ma,myShepherd);
        
        JSONArray destArr = new JSONArray();
        String queueUrl = null;
        if(priority) {
          queueUrl = CommonConfiguration.getProperty("finDetectQueueUrlPriority", "context0");
          destArr.put(makeSqsDest(CommonConfiguration.getProperty("finRefineQueueUrlPriority", "context0")));
        }else {
          queueUrl = CommonConfiguration.getProperty("finDetectQueueUrl", "context0");
          destArr.put(makeSqsDest(CommonConfiguration.getProperty("finRefineQueueUrl", "context0")));
        }
        destArr.put(makePostDest(CommonConfiguration.getProperty("iaPostUrl", "context0")));
        
        qjob.put("destinationSequence", destArr);
        
        SQStools sqs_tools = new SQStools();
        sqs_tools.initialiseCredentials();
        sqs_tools.initialiseSqs();

        sqs_tools.sendMessage(queueUrl, qjob.toString(),
            UUID.randomUUID().toString());
        
        if (checkStartOdServer(CommonConfiguration.getProperty("finDetectInstanceId", "context0")) == false) {
          System.out.println("issue starting detection server");
          return task;
        }
        
        return task;
      }
      
      private static JSONObject makePostDest(String url) {
        JSONObject dest = new JSONObject();
        dest.put("type", "post");
        JSONObject params = new JSONObject();
        params.put("url", url);
        dest.put("params", params);
        return dest;
        
      }
      
      
      private static JSONObject makeSqsDest(String queueUrl) {
        JSONObject dest = new JSONObject();
        dest.put("type", "sqs");
        JSONObject params = new JSONObject();
        params.put("queueUrl", queueUrl);
        dest.put("params", params);
        return dest;
      }
      

      
      private static JSONObject convertMaToJson(MediaAsset ma) {
        JSONObject jma = new JSONObject();
        jma.put("uuid", ma.getUUID());
        jma.put("id", ma.getId());
        jma.put("annotationUuid", ma.getAnnotations().get(0).getUUID());
        
        JSONObject jmaStore = new JSONObject();
        jmaStore.put("type", "url");
        
        JSONObject jmaStoreParams = new JSONObject();
        jmaStoreParams.put("url", ma.webURLString());
        
        jmaStore.put("params",jmaStoreParams);
        jma.put("store", jmaStore);
        return jma;
      }
     

    
    
    private static String getUrlForMediaAssetUUID(String uuid, Shepherd myShepherd) {
        MediaAsset ma = MediaAssetFactory.loadByUuid(uuid, myShepherd);
        if (ma == null) return null;
        URL url = ma.webURL();
        if (url == null) return null;
        if (url.toString().endsWith(".tif")) {  //no tiffs plz
            ArrayList<MediaAsset> mids = ma.findChildrenByLabel(myShepherd, "_mid");
            if ((mids == null) || (mids.size() < 1)) return null;
            url = mids.get(0).webURL();
            if (url == null) return null;
        }
        return url.toString();
    }

}
