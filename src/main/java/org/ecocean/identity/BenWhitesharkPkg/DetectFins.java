package org.ecocean.identity.BenWhitesharkPkg;

import java.util.ArrayList;
import java.util.HashMap;


import org.ecocean.Annotation;
import org.ecocean.Util;
import org.json.JSONObject;
import com.google.gson.Gson;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;


public class DetectFins {
  private ArrayList<Annotation> annotations;
  private ArrayList<String> jobIds;
  private JSONObject params;
  private AmazonSQSClient sqs;
  private String returnQueueUrl;
  private String detectQueueUrl = "https://sqs.eu-west-1.amazonaws.com/822200170788/toDetect.fifo";
  private String refineQueueUrl = "https://sqs.eu-west-1.amazonaws.com/822200170788/toRefine.fifo";
  public ArrayList<HashMap<String, Object>> jobs;

  
  public DetectFins(ArrayList<Annotation> annotations, JSONObject params) {
    this.finishConstructing(annotations, params);
  }
  
  public DetectFins(Annotation annotation, JSONObject params) {
    ArrayList<Annotation> anns = new ArrayList<Annotation>();
    anns.add(annotation);
    this.finishConstructing(anns, params);
   }
  
  private void finishConstructing(ArrayList<Annotation> annotations, JSONObject params) {
    this.params = params;
    this.annotations = annotations;
    this.initialiseSqs();
    this.returnQueueUrl = this.createQueue(Util.generateUUID());
    this.jobIds = new ArrayList<String>();
  }
  
  public void receive() {
    this.receiveDetections();
  }
  
  private void receiveDetections() {
    
  }
  
  public void send() {
    this.createJobs();
    this.sendJobs();
  }
  
  private void sendJobs() {
    Gson gson = new Gson();
    for (HashMap<String, Object> job : jobs) {
      sendJob(job,gson);
    }
  }
  
  
  private void sendJob(HashMap<String, Object> job, Gson gson) {
    sqs.sendMessage(new SendMessageRequest(this.detectQueueUrl,  gson.toJson(job)));
  }
  
  
  public void createJobs() {
    this.jobs = new ArrayList<HashMap<String, Object>>();
    if(params.has("splitAnnotations") && params.getBoolean("splitAnnotations")){
      for(Annotation ann : this.annotations) {
        this.jobs.add(packageAnnotations(ann));
      }
    } else {
      this.jobs.add(packageAnnotations(this.annotations));
    }
  }
  
  private HashMap<String, Object> packageAnnotations(Annotation annotation) {
    ArrayList<Annotation> anns = new ArrayList<Annotation>();
    anns.add(annotation);
    return packageAnnotations(anns);
  }
  
  
  
  private HashMap<String, Object> packageAnnotations(ArrayList<Annotation> annotations) {
    // create hashmap with annotations
    HashMap<String, Object> hm = new HashMap<String,Object>();
    hm.put("annotations", annotations);
    
    // give a jobId
    String uuid = Util.generateUUID();
    hm.put("jobId",uuid);
    this.jobIds.add(uuid);
    
    // add queue sequence
    hm.put("refineQueueUrl",this.refineQueueUrl);
    hm.put("returnQueueUrl",this.returnQueueUrl);
    
    return hm;
  }
  
  
  private void initialiseSqs(){
    sqs = new AmazonSQSClient();
    sqs.setEndpoint("https://sqs.eu-west-1.amazonaws.com");
    Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
    sqs.setRegion(euWest1);
  }
  
  
  private String createQueue(String queueName) {
    CreateQueueRequest createQueueRequest = new CreateQueueRequest(queueName).addAttributesEntry("FifoQueue", "true").addAttributesEntry("ContentBasedDeduplication", "true");
    String myQueueUrl = sqs.createQueue(createQueueRequest).getQueueUrl();
    return myQueueUrl;
  }
  


}
