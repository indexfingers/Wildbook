package org.ecocean.identity.BenWhitesharkPkg;



import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.ListQueuesRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.SetQueueAttributesResult;
import com.google.gson.Gson;


public class SQStools {
  private AWSCredentials credentials;
  private boolean isCredentials;
  private AmazonSQS sqs;
  private Gson gson;
  
  
  
  public void initialiseCredentials(String userAtAccount) {
    try {
      try {
        this.credentials = new ProfileCredentialsProvider(userAtAccount).getCredentials();
        this.isCredentials = true;
      } catch (Exception e) {
        isCredentials = false;
        throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
            + "Please make sure that your credentials file is at the correct "
            + "location (C:\\Users\\Ben\\.aws\\credentials), and is in valid format.", e);
      }
    } catch (Exception e2) {
      isCredentials = false;
    }
    gson = new Gson();
  }

  public void initialiseCredentials() {
    try {
      try {
        this.credentials = new ProfileCredentialsProvider("Ben@mantatrust").getCredentials();
        this.isCredentials = true;
      } catch (Exception e) {
        isCredentials = false;
        throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
            + "Please make sure that your credentials file is at the correct "
            + "location (C:\\Users\\Ben\\.aws\\credentials), and is in valid format.", e);
      }
    } catch (Exception e2) {
      isCredentials = false;

    }

    gson = new Gson();
  }

  public void initialiseSqs(){
    if(this.isCredentials==true){
      sqs = new AmazonSQSClient(credentials);
      sqs.setEndpoint("https://sqs.eu-west-1.amazonaws.com");
          Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
          sqs.setRegion(euWest1);
    } else{
      sqs = new AmazonSQSClient();
      sqs.setEndpoint("https://sqs.eu-west-1.amazonaws.com");
          Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
          sqs.setRegion(euWest1);
    }
  }
  
  public void changeSqsRegion(String region) {
    String endpoint = "none";
    Region reg = Region.getRegion(Regions.EU_WEST_1);
    switch (region) {
    case "eu_west_1":
      reg = Region.getRegion(Regions.EU_WEST_1);
      endpoint = "https://sqs.eu-west-1.amazonaws.com";
      break;
    case "us_east_2":
      reg = Region.getRegion(Regions.US_EAST_2);
      endpoint = "https://sqs.us-east-2.amazonaws.com";
      break;
    }
    if (endpoint.equals("none") == false) {
      sqs.setEndpoint(endpoint);
      sqs.setRegion(reg);
    }
  }

  
  
  public String createQueue(String queueName) {
    CreateQueueRequest createQueueRequest = new CreateQueueRequest(queueName).addAttributesEntry("FifoQueue", "true").addAttributesEntry("ContentBasedDeduplication", "true");
    String myQueueUrl = sqs.createQueue(createQueueRequest).getQueueUrl();
    return myQueueUrl;
  }
  
  public void purgeQueue(String qurl){
    sqs.purgeQueue(new PurgeQueueRequest(qurl));
  }
  
  public void deleteQueue(String qurl){
    sqs.deleteQueue(qurl);    
  }
  
  public String[] listQueues(){
    ListQueuesResult lq_result = sqs.listQueues();
    String[] urls = lq_result.getQueueUrls().toArray(new String [lq_result.getQueueUrls().size()]);
    return urls;    
  }
  
  public String[] listQueues(String prefix){
    ListQueuesResult lq_result = sqs.listQueues(new ListQueuesRequest(prefix));
    String[] urls = lq_result.getQueueUrls().toArray(new String [lq_result.getQueueUrls().size()]);
    return urls;    
  }
  
  public void setQueueAttribute(String qurl,String attribute, String value){
    SetQueueAttributesResult sqar = new SetQueueAttributesResult();
    Map<String,String> myMap = new HashMap<String,String>();
    myMap.put(attribute, value);  
    sqar=sqs.setQueueAttributes(new SetQueueAttributesRequest().withQueueUrl(qurl).withAttributes(myMap));
  }
  
  
  public String getQueueAttribute(String qurl,String key){
        GetQueueAttributesResult gqar = new  GetQueueAttributesResult();
        gqar=sqs.getQueueAttributes(new GetQueueAttributesRequest().withAttributeNames(key).withQueueUrl(qurl));
        Map<String,String> myMap = gqar.getAttributes();
        return myMap.get(key);
  }
  
  public String receiveMessageAndDelete(String qurl, int waitTime) {
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(qurl).withMaxNumberOfMessages(1)
        .withWaitTimeSeconds(waitTime);
    List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
    String rtn;
    if (messages.isEmpty() == true) {
      rtn = null;
    } else {
      rtn = messages.get(0).getBody();
      sqs.deleteMessage(qurl, messages.get(0).getReceiptHandle());
      
    }
    return rtn;
  }
  
  
  public String receiveMessage(String qurl, int waitTime){
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(qurl).withMaxNumberOfMessages(1)
        .withWaitTimeSeconds(waitTime);
    List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
    return gson.toJson(messages); 
  }
  
  
  public String receiveMessage(String qurl, int waitTime,int maxNoMessages){
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(qurl).withMaxNumberOfMessages(maxNoMessages)
        .withWaitTimeSeconds(waitTime);
    List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
    return gson.toJson(messages); 
  }
  
  public void deleteMessage(String qurl, String receiptHandle){
    sqs.deleteMessage(qurl,receiptHandle);
  }
  

  
  public void sendMessage(String qurl,String messageText,String messageGroupdId){
    sqs.sendMessage(new SendMessageRequest(qurl,  messageText).withMessageGroupId(messageGroupdId));
  }
  
  
  

}

