package org.ecocean.ia.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletContextEvent;

import org.ecocean.Annotation;
import org.ecocean.RestClient;
import org.ecocean.Shepherd;
import org.ecocean.acm.AcmUtil;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.S3AssetStore;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class WSIAM extends IAPlugin {
    private String context = null;

    public WSIAM() {
        super();
    }
    
    public WSIAM(String context) {
        super(context);
        this.context = context;
    }

    @Override
    public boolean isEnabled() {
        return true;  //FIXME
    }

    @Override
    public boolean init(String context) {
        this.context = context;
        IA.log("WSIAM init() called on context " + context);
        return true;
    }

    @Override
    public void startup(ServletContextEvent sce) {
    	prime();
    	
//    	System.out.println("WSIAM startup() called: nothing to do...");
        //TODO genericize this to be under .ia (with startup hooks for *any* IA plugin)
        //if we dont need identificaiton, no need to prime
        //boolean skipIdent = Util.booleanNotFalse(IA.getProperty(context, "IBEISIADisableIdentification"));
        //if (!skipIdent && !org.ecocean.StartupWildbook.skipInit(sce, "PRIMEIA")) prime();
    }
    
    public void prime() {
        IA.log("INFO: WSIAM.prime(" + this.context + ") called");
        System.out.println("WSIAM.prime() called on context " + context + " nothing to do??? setting IA primed to true...");
        IBEISIA.setIAPrimed(true);
        try {
          sayHello();
        } catch(Exception ex) {
          IA.log("ERROR: WildbookIAM.prime() failed due to " + ex.toString());
          ex.printStackTrace();
        }
    }
    
    public void sayHello() throws MalformedURLException, IOException, InvalidKeyException, NoSuchAlgorithmException{
      String u = IA.getProperty(context, "WSIARestUrlSayHello");
      if (u == null) throw new MalformedURLException("WSIAM configuration value IBEISIARestUrlAddAnnotations is not set");
      URL url = new URL(u);
      JSONObject rtn = RestClient.get(url);
      IA.log("INFO: we said hello; WSIA said " + rtn.toString());
    }
    
    public boolean isPrimed() {
        return IBEISIA.isIAPrimed();
    }
    
    @Override
    public Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
        Task parentTask) {
      // TODO Auto-generated method stub
      return null;
    }
    @Override
    public Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
        Task parentTask) {
      // TODO Auto-generated method stub
      return null;
    }

    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean b) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
      System.out.println("BY SOME MIRACLE WEVE ARRIVED IN SEND MEDIAASSETS");
      String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
      if (u == null) throw new MalformedURLException("WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
      URL url = new URL(u);
      int batchSize = 30;
      int numBatches = Math.round(mas.size() / batchSize + 1);
      
      List<String> iaImageIds = new ArrayList<String>();
      // gonna be expensive to grab all images from ia database everytime a new image is added to wildbook?
//      if (checkFirst) iaImageIds = iaImageIds();
      //initial initialization(!)
      HashMap<String,ArrayList> map = new HashMap<String,ArrayList>();
      map.put("image_uri_list", new ArrayList<JSONObject>());
      map.put("image_unixtime_list", new ArrayList<Integer>());
      map.put("image_gps_lat_list", new ArrayList<Double>());
      map.put("image_gps_lon_list", new ArrayList<Double>());
      List<MediaAsset> acmList = new ArrayList<MediaAsset>(); //for rectifyMediaAssetIds below
      int batchCt = 1;
      JSONObject allRtn = new JSONObject();
      allRtn.put("_batchSize", batchSize);
      allRtn.put("_totalSize", mas.size());
      JSONArray bres = new JSONArray();
      for (int i = 0 ; i < mas.size() ; i++) {
        MediaAsset ma = mas.get(i);
        if (iaImageIds.contains(ma.getAcmId())) continue;
        if (ma.isValidImageForIA()!=null&&!ma.isValidImageForIA()) {
            IA.log("WARNING: WildbookIAM.sendMediaAssets() found a corrupt or otherwise invalid MediaAsset with Id: " + ma.getId());
            continue;
        }
        if (!validMediaAsset(ma)) {
            IA.log("WARNING: WildbookIAM.sendMediaAssets() skipping invalid " + ma);
            continue;
        }
        acmList.add(ma);
        map.get("image_uri_list").add(mediaAssetToUri(ma));
        map.get("image_gps_lat_list").add(ma.getLatitude());
        map.get("image_gps_lon_list").add(ma.getLongitude());
        DateTime t = ma.getDateTime();
        if (t == null) {
            map.get("image_unixtime_list").add(null);
        } else {
            map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000));  //IA wants seconds since epoch
        }

        if ( (i == (mas.size() - 1))  ||  ((i > 0) && (i % batchSize == 0)) ) {   //end of all; or end of a batch
            if (acmList.size() > 0) {
                IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + acmList.size() + " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches + " batches)");
                JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
                System.out.println(batchCt + "]  sendMediaAssets() -> " + rtn);
                List<String> acmIds = acmIdsFromResponse(rtn);
                if (acmIds == null) {
                    IA.log("WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: " + rtn);
                } else {
                    int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
                    IA.log("INFO: WildbookIAM.sendMediaAssets() updated " + numChanged + " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
                }
                bres.put(rtn);
                //initialize for next batch (if any)
                map.put("image_uri_list", new ArrayList<JSONObject>());
                map.put("image_unixtime_list", new ArrayList<Integer>());
                map.put("image_gps_lat_list", new ArrayList<Double>());
                map.put("image_gps_lon_list", new ArrayList<Double>());
                acmList = new ArrayList<MediaAsset>();
            } else {
                bres.put("EMPTY BATCH");
            }
            batchCt++;
        }
    }
    allRtn.put("batchResults", bres);
    return allRtn;
  
    }
    
    public static String fromFancyUUID2(JSONObject u) {
      if (u == null) return null;
      return u.optString("uuid", null);
  }
  public static JSONObject toFancyUUID2(String u) {
      JSONObject j = new JSONObject();
      j.put("uuid", u);
      return j;
  }
    
    public static String fromFancyUUID(JSONObject u) {
      if (u == null) return null;
      return u.optString("__UUID__", null);
  }
  public static JSONObject toFancyUUID(String u) {
      JSONObject j = new JSONObject();
      j.put("__UUID__", u);
      return j;
  }
  
//  public static List<String> acmIdsFromResponse2(JSONObject rtn){
//    if ((rtn == null) || (rtn.optJSONObject("response") == null)) return null;
//    if (rtn.optJSONObject("response").optJSONArray("image_uuid_list") == null) return null;
//    List<String> ids = new ArrayList<String>();
//    for (int i=0; i<rtn.optJSONObject("response").optJSONArray("image_uuid_list").length(); i++) {
//      if rtn.optJSONObject("response").optJSONArray("image_uuid_list")
//    }
//    
//  }
    
    public static List<String> acmIdsFromResponse(JSONObject rtn) {
      if ((rtn == null) || (rtn.optJSONArray("response") == null)) return null;
      List<String> ids = new ArrayList<String>();
      for (int i = 0 ; i < rtn.getJSONArray("response").length() ; i++) {
          if (rtn.getJSONArray("response").optJSONObject(i) == null) {
              //IA returns null when it cant localize/etc, so we need to add this to keep array length the same
              ids.add(null);
          } else {
              ids.add(fromFancyUUID2(rtn.getJSONArray("response").getJSONObject(i)));
          }
      }
      System.out.println("fromResponse ---> " + ids);
      return ids;
  }
    
    
    //basically "should we send to IA?"
    public static boolean validMediaAsset(MediaAsset ma) {
        if (ma == null) return false;
        if (!ma.isMimeTypeMajor("image")) return false;
        if ((ma.getWidth() < 1) || (ma.getHeight() < 1)) return false;
        if (mediaAssetToUri(ma) == null) {
            System.out.println("WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " + ma);
            return false;
        }
        return true;
    }
    
    private static Object mediaAssetToUri(MediaAsset ma) {
      //URL curl = ma.containerURLIfPresent();  //what is this??
      //if (curl == null) curl = ma.webURL();
      URL curl = ma.webURL();
      if (ma.getStore() instanceof LocalAssetStore) {
          if (curl == null) return null;
          return curl.toString();
      } else if (ma.getStore() instanceof S3AssetStore) {
          return ma.getParameters();
      } else {
          if (curl == null) return null;
          return curl.toString();
      }
  }
    


  }
