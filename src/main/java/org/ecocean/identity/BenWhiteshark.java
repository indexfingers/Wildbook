package org.ecocean.identity;

import java.util.List;
import java.io.File;
import java.io.IOException;
import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import org.ecocean.Annotation;
import org.ecocean.MarkedIndividual;
import org.ecocean.Util;
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

        } else if (arg.optJSONArray("createFeatures2") != null) {
          
          JSONArray farr = arg.optJSONArray("createFeatures2");
          JSONArray responseArray = new JSONArray();
          boolean success = true;
          
          for (int i = 0 ; i < farr.length() ; i++) {
              JSONObject maResponse = new JSONObject();
              long revision = -1;
              
              if ((farr.optJSONObject(i) == null) || (farr.getJSONObject(i).optString("mediaAssetUuid") == null) || (farr.getJSONObject(i).optString("status") == null)) {
                success = false;
                continue;  //bad array element!
              }
              maResponse.put("mediaAssetUuid", farr.getJSONObject(i).optString("mediaAssetUuid"));
              maResponse.put("success", true);
              
              MediaAsset ma = MediaAssetFactory.loadByUuid(farr.getJSONObject(i).optString("mediaAssetUuid"), myShepherd);
              if (ma == null) {
                  maResponse.put("error", "invalid or unknown mediaAssetUuid passed");
                  maResponse.put("success", false);
                  success = false;
                  continue;
              } 
              
              if (!farr.getJSONObject(i).optString("status").equals("success")) {
                maResponse.put("IAError", "IA Error");
                maResponse.put("success", false);
                success = false;
                ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                if (farr.getJSONObject(i).optString("errorMessage") != null) ma.setIaDetectionErrorMessage(farr.getJSONObject(i).optString("errorMessage"));
                MediaAssetFactory.save(ma,myShepherd);
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
                continue; // to features for next ma
              }
              
              for (int k = 0; k < features.length(); k++) {
                FeatureType.initAll(myShepherd);
                
                if ((features.optJSONObject(k).optString("type") == null) || (features.optJSONObject(k).optJSONObject("parameters") == null)) {
                  maResponse.put("error", "feature with no feature type or parameters");
                  maResponse.put("success", false);
                  ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                  success = false;
                  break;
                }
                // now assume we have parameters and feature type for this feature and for this media asset :-)
                // now stick features in ma.. (all gonna be okay now?)
                String tstring = features.optJSONObject(k).optString("type"); 
                Feature ft = new Feature(tstring, features.optJSONObject(k).optJSONObject("parameters"));
                
                // store feature id with feature type and add to array of feature ids
                JSONObject featId = new JSONObject();
                featId.put("type", tstring);
                featId.put("id",ft.getId());
                featIds.put(featId);
                
                // synchronise revision time across features for given ma
                if (revision == -1) {
                  revision = ft.getRevision();
                  maResponse.put("revision", revision);
                } else {
                  ft.setRevision(revision);
                }
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
          }
          
          res.put("success", success);
          res.remove("error");
          res.put("maResponses",responseArray);
        }else {
            res.put("error", "unknown command");
        }
        return res;
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
