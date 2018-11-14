package org.ecocean.identity.BenWhitesharkPkg;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.MultipleFileDownload;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.google.gson.Gson;

public class S3tools {
  private AmazonS3 s3Client;
  private AWSCredentials credentials;
  private ObjectListing listing = null;
  private long jobDate = -1;
  private boolean isCredentials;
  private Gson gson;

  /*public void initialise(){
    System.out.println("hello from java");
    try {
      try {
        this.credentials = new ProfileCredentialsProvider("Ben@saveourseas").getCredentials();
      } catch (Exception e) {
        throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
            + "Please make sure that your credentials file is at the correct "
            + "location (C:\\Users\\Ben\\.aws\\credentials), and is in valid format.", e);
      }
      
      s3Client = AmazonS3ClientBuilder.standard().withCredentials(credentials).withRegion("eu-west-1").build();
      System.out.println("found credentials and seems okay");
      //s3Client = new AmazonS3Client(credentials);
    } catch (Exception e2) {
      //s3Client = new AmazonS3Client();
      s3Client = AmazonS3ClientBuilder.standard().withRegion("eu-west-1").build();
    }
    
  }*/
  
  
  public void initialise(String accessKey, String secretKey) {
    s3Client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey));
    gson = new Gson();
  }
  
  public void initialise(String userAtAccount) {
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
    
    
    if (this.isCredentials == true) {
      s3Client = new AmazonS3Client(credentials);
      //s3Client.setEndpoint("https://ec2.eu-west-1.amazonaws.com");
      //Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
      //s3Client.setRegion(euWest1);
    } else {
      s3Client = new AmazonS3Client();
      //s3Client.setEndpoint("https://ec2.eu-west-1.amazonaws.com");
      //Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
      //s3Client.setRegion(euWest1);
    }

    gson = new Gson();
  }
  
  
  public void initialise() {
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
    
    
    if (this.isCredentials == true) {
      s3Client = new AmazonS3Client(credentials);
      //s3Client.setEndpoint("https://ec2.eu-west-1.amazonaws.com");
      //Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
      //s3Client.setRegion(euWest1);
    } else {
      s3Client = new AmazonS3Client();
      //s3Client.setEndpoint("https://ec2.eu-west-1.amazonaws.com");
      //Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
      //s3Client.setRegion(euWest1);
    }

    gson = new Gson();
  }
  
  
  
  public void bh_downloadDirectory(){
    TransferManager tm  = new TransferManager(this.s3Client);
    MultipleFileDownload download = tm.downloadDirectory("mt-karey-kumli-asia", "Hello_Karey", new File("F:\\Dropbox\\idTheManta"));
    try {
      download.waitForCompletion();
    } catch (AmazonServiceException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (AmazonClientException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    System.out.println("download complete");
  }
  
  
  public boolean doesBucketExist(String bucketname){
    return s3Client.doesBucketExist(bucketname);
  }
  
  public boolean doesObjectExist(String bucketname,String objectName){
    return s3Client.doesObjectExist(bucketname, objectName);
  }
  
  public boolean isBucketMine(String bucketname){
    boolean ismine;
    try{
      this.doesObjectExist(bucketname, "abcd");
      ismine = true;
    } catch (AmazonS3Exception ase){
      ismine = false;
      
    }
    return ismine;
  }
  
  public void moveObject(String srcbucketname,String srckey, String dstbucket, String dstkey){
    s3Client.copyObject(srcbucketname, srckey, dstbucket, dstkey);
    this.deleteFile(srcbucketname, srckey);
  }
  
  public String checkBucket(String bucketname){
    String str;
    if(this.doesBucketExist(bucketname)){
      if(this.isBucketMine(bucketname)){
        str="yours";
      }else{
        str="notyours";
      }
    }else{
      str="notexist";
    }
    return str;
  }
  
  
  public void createBucket(String bucketname){
    s3Client.createBucket(bucketname);
  }
  
  public void resetListing(){
    this.listing=null;
    this.jobDate=-1;
  }
  
  public void uploadFile(String filename, String bucketname, String keyname){
        File file = new File(filename);
        System.out.println("Uploading a new object to S3 from a file\n");
        s3Client.putObject(new PutObjectRequest(
                             bucketname, keyname, file));
  }
  
  public JSONObject downloadJsonFile(String bucket, String key) throws IOException {
    S3Object fullObject = s3Client.getObject(new GetObjectRequest(bucket, key));
    StringBuilder sb = new StringBuilder();
    BufferedReader reader = new BufferedReader(new InputStreamReader(fullObject.getObjectContent()));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
        return new JSONObject(sb.toString());
  }
  
  public void downloadFile(String filename, String bucketname, String keyname){
    System.out.println("Downloading an object from S3 to a file\n");
    File localFile = new File(filename);
        s3Client.getObject(new GetObjectRequest(bucketname, keyname), localFile);
  }
  
  public void deleteFile(String bucketname, String keyname){
    s3Client.deleteObject(bucketname, keyname);
  }
  
  public long getDateOfJob(){
    return this.jobDate;
  }
  
  
  public String getKeyByAge(String bucketname,String prefix,String age){
    String str;
    if(this.listing==null){
       str = "no listing";
    } else {
      List<S3ObjectSummary> summaryList = this.listing.getObjectSummaries();
      if (summaryList.isEmpty()==true){
        str = "empty list";
        return str;
      }
      long best = summaryList.get(0).getLastModified().getTime();
      str = summaryList.get(0).getKey();
      for (S3ObjectSummary su : summaryList) {
        long t = su.getLastModified().getTime();
        if (age.equals("newest")) {
          if (t > best) {
            str = su.getKey();
            this.jobDate=su.getLastModified().getTime();
            best = t;
          }

        } else {
          if (t < best) {
            str = su.getKey();
            this.jobDate=su.getLastModified().getTime();
            best = t;
          }
        }
      }
    }
    return str;
  }
  
  
  public void listObjects(String bucketname,String prefix){
    this.listing = s3Client.listObjects(bucketname,
                prefix);  
  }
  
  public String[] getList(){
    List<S3ObjectSummary> summaryList = this.listing.getObjectSummaries();
    List<String> strs = new ArrayList<String>();
    for (S3ObjectSummary su : summaryList) {
      strs.add(su.getKey());
    }
    String[] strs2 = new String[strs.size()];
    strs2 = strs.toArray(strs2);
    return strs2;
  }
  
  public Long[] getObjectDates(){
    List<S3ObjectSummary> summaryList = this.listing.getObjectSummaries();
    List<Long> strs = new ArrayList<Long>();
    for (S3ObjectSummary su : summaryList) {
      strs.add(su.getLastModified().getTime());
    }
    Long[] strs2 = new Long[strs.size()];
    strs2 = strs.toArray(strs2);
    return strs2;
  }

}
