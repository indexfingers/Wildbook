package org.ecocean.identity.BenWhitesharkPkg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AttachVolumeRequest;
import com.amazonaws.services.ec2.model.CancelSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.CreateSnapshotRequest;
import com.amazonaws.services.ec2.model.CreateSnapshotResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.CreateVolumeResult;
import com.amazonaws.services.ec2.model.DeleteVolumeRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSnapshotsRequest;
import com.amazonaws.services.ec2.model.DescribeSnapshotsResult;
import com.amazonaws.services.ec2.model.DescribeSpotFleetInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeSpotFleetInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsResult;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryRequest;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult;
import com.amazonaws.services.ec2.model.DescribeVolumesRequest;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.DetachVolumeRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.services.ec2.model.ModifySpotFleetRequestRequest;
//import com.amazonaws.services.ec2.model.ModifyVolumeRequest;
import com.amazonaws.services.ec2.model.Placement;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.services.ec2.model.SpotPlacement;
import com.amazonaws.services.ec2.model.SpotPrice;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.Volume;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.model.SendCommandRequest;
import com.google.gson.Gson;

public class Ec2Tools {
  private SendCommandRequest scr;
  //private AWSCredentials credentials;
  private AWSCredentials credentials;
  private boolean isCredentials;
  private AWSSimpleSystemsManagement ssm;
  private AmazonEC2 ec2;
  private Gson gson;
  
  
  
  public void initialiseCredentials() {
    try {
      try {
        this.credentials = new ProfileCredentialsProvider("Ben@saveourseas").getCredentials();
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
  
  public void initialiseCredentials(String userAtOrg) {
    try {
      try {
        this.credentials = new ProfileCredentialsProvider(userAtOrg).getCredentials();
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
  
  
  
  

  
  
  /*public void initialiseCredentials(){
    try {
      this.credentials = new ProfileCredentialsProvider("Ben@saveourseas");
      this.isCredentials=true;
    } catch (Exception e) {
      isCredentials=false;
      throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
          + "Please make sure that your credentials file is at the correct "
          + "location (C:\\Users\\Ben\\.aws\\credentials), and is in valid format.", e);
    }
    gson = new Gson();
  }*/

  public void initialiseSsm() {
    if (this.isCredentials == true) {
      ssm = new AWSSimpleSystemsManagementClient(credentials);
      ssm.setEndpoint("https://ssm.eu-west-1.amazonaws.com");
      Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
      ssm.setRegion(euWest1);
    } else {
      ssm = new AWSSimpleSystemsManagementClient();
      ssm.setEndpoint("https://ssm.eu-west-1.amazonaws.com");
      Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
      ssm.setRegion(euWest1);
    }
  }

  public void initialiseEc2() {
    if (this.isCredentials == true) {
      ec2 = new AmazonEC2Client(credentials);
      ec2.setEndpoint("https://ec2.eu-west-1.amazonaws.com");
      Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
      ec2.setRegion(euWest1);
    } else {
      ec2 = new AmazonEC2Client();
      ec2.setEndpoint("https://ec2.eu-west-1.amazonaws.com");
      Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
      ec2.setRegion(euWest1);
    }
  }
  /*public void initialiseSsm(){
    if(this.isCredentials==true){
      ssm =  AWSSimpleSystemsManagementClientBuilder.standard().withCredentials(credentials).withEndpointConfiguration(new EndpointConfiguration("https://ssm.eu-west-1.amazonaws.com", "eu-west-1")).build();
    } else{
      ssm =  AWSSimpleSystemsManagementClientBuilder.standard().withEndpointConfiguration(new EndpointConfiguration("https://ssm.eu-west-1.amazonaws.com", "eu-west-1")).build();
    }
  }*/
  
  /*public void initialiseEc2() {
    if (this.isCredentials == true) {
      ec2 = AmazonEC2ClientBuilder.standard().withEndpointConfiguration(new EndpointConfiguration("https://ec2.eu-west-1.amazonaws.com", "eu-west-1")).withCredentials(credentials).build();
    } else {
      ec2 = AmazonEC2ClientBuilder.standard().withEndpointConfiguration(new EndpointConfiguration("https://ec2.eu-west-1.amazonaws.com", "eu-west-1")).build();
    }
  }*/
  
  
  public String getInstanceId(String requestId){
    DescribeSpotInstanceRequestsRequest describeRequest = new DescribeSpotInstanceRequestsRequest().withSpotInstanceRequestIds(requestId);
    DescribeSpotInstanceRequestsResult describeResult = ec2.describeSpotInstanceRequests(describeRequest);
    List<SpotInstanceRequest> describeResponses = describeResult.getSpotInstanceRequests();
    String instanceId = describeResponses.get(0).getInstanceId();
    return instanceId;  
  }
  
  public String getSpotInstanceRequestState(String requestId){
    DescribeSpotInstanceRequestsRequest describeRequest = new DescribeSpotInstanceRequestsRequest().withSpotInstanceRequestIds(requestId);
    DescribeSpotInstanceRequestsResult describeResult = ec2.describeSpotInstanceRequests(describeRequest);
    List<SpotInstanceRequest> describeResponses = describeResult.getSpotInstanceRequests();
    String state = describeResponses.get(0).getState();
    return state; 
  }
  
  public void startOdInstance(String instanceId){
    ec2.startInstances(new StartInstancesRequest().withInstanceIds(instanceId));
  }
  
  public void stopOdInstance(String instanceId){
    ec2.stopInstances(new StopInstancesRequest().withInstanceIds(instanceId));
  }
  
  
  
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  public void deleteVolume(String volumeId){
    ec2.deleteVolume(new DeleteVolumeRequest().withVolumeId(volumeId));
  }
  
  
  public void detachVolume(String instanceId, String volumeId){
    DetachVolumeRequest dvr = new DetachVolumeRequest().withInstanceId(instanceId).withVolumeId(volumeId);
    ec2.detachVolume(dvr);
    
  }
  
  public void attachVolume(String instanceId, String volumeId, String device){
    AttachVolumeRequest avr = new AttachVolumeRequest().withInstanceId(instanceId).withVolumeId(volumeId).withDevice(device);
    ec2.attachVolume(avr);
  }
  
//  public void modifyVolumeSize(String volumeId,int targetSize){
//    ec2.modifyVolume(new ModifyVolumeRequest().withVolumeId(volumeId).withSize(targetSize));
//  }
  
  public int getVolumeSize(String volumeId){
    DescribeVolumesResult dvr = new DescribeVolumesResult();
    dvr = ec2.describeVolumes(new DescribeVolumesRequest().withVolumeIds(volumeId));
    List<Volume> vols = dvr.getVolumes();
    int size = vols.get(0).getSize();
    return size;
  }
  

  
  public String getVolumeAvailabilityZone(String volumeId){
    DescribeVolumesResult dvr = new DescribeVolumesResult();
    dvr = ec2.describeVolumes(new DescribeVolumesRequest().withVolumeIds(volumeId));
    List<Volume> vols = dvr.getVolumes();
    String availabilityZone = vols.get(0).getAvailabilityZone();
    return availabilityZone;
  }
  
  
  
  public String createVolume(int size,String availabilityZone){
    CreateVolumeRequest cvr = new CreateVolumeRequest(size, availabilityZone);
    System.out.println("Creating volume of size: " + cvr.getSize());
    CreateVolumeResult requestResult = ec2.createVolume(cvr);
    String volumeID=requestResult.getVolume().getVolumeId();
    System.out.println("New volume ID is: " + volumeID);
    return volumeID;
  }
  
  
  public String createVolume(int size, String availabilityZone, String snapshotId) {
    CreateVolumeRequest cvr = new CreateVolumeRequest(snapshotId, availabilityZone).withSize(size);
    CreateVolumeResult requestResult = ec2.createVolume(cvr);
    String volumeID=requestResult.getVolume().getVolumeId();
    System.out.println("New volume ID is: " + volumeID);
    return volumeID;
  }
  
  
  public String createSnapshot(String volumeId){
    System.out.println("Creating snapshot from volume with ID: " + volumeId);
    CreateSnapshotRequest createSnapshotRequest = new CreateSnapshotRequest().withVolumeId(volumeId).withDescription("Latest models");
    CreateSnapshotResult csr = ec2.createSnapshot(createSnapshotRequest);
    String snapshotId=csr.getSnapshot().getSnapshotId();
    System.out.println("New snapshot ID is: " + snapshotId);
    return snapshotId;
  }
  
  

  
  public String getSnapshotState(String snapshotId){
    DescribeSnapshotsRequest dsr = new DescribeSnapshotsRequest().withSnapshotIds(snapshotId);
    DescribeSnapshotsResult dsrr = ec2.describeSnapshots(dsr);
    return dsrr.getSnapshots().get(0).getState();
    
  }
  
  
  public String bh_describeVolumesWithVolId(String volumeID){
    DescribeVolumesRequest dvr = new DescribeVolumesRequest().withVolumeIds(volumeID);
    DescribeVolumesResult dvrr = ec2.describeVolumes(dvr);
    return gson.toJson(dvrr);
  }

  
  public String getVolumeState(String volumeID){
    DescribeVolumesRequest dvr = new DescribeVolumesRequest().withVolumeIds(volumeID);
    DescribeVolumesResult dvrr = ec2.describeVolumes(dvr);
    return dvrr.getVolumes().get(0).getState(); 
  }
  
  
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  
  public void tagResourceWithName(String resourceId,String keyValue ){
    Tag t = new Tag().withKey("Name").withValue(keyValue);
    CreateTagsRequest ctr = new CreateTagsRequest().withResources(resourceId).withTags(t);
    ec2.createTags(ctr);  
  }
  
  
  
  public String requestOnDemandInstance(String instanceType,String amiId,String securityGroup,String keyname,String iamRole,String userData,String availabilityZone){ 
    RunInstancesResult rirr = ec2.runInstances( makeOnDemandInstanceRequest(instanceType,amiId,securityGroup, keyname, iamRole,userData,availabilityZone));
    String instanceID = rirr.getReservation().getInstances().get(0).getInstanceId();
    System.out.println("New instance ID is: " + instanceID);
    return instanceID;
  }
  
  
  private RunInstancesRequest makeOnDemandInstanceRequest(String instanceType,String amiId,String securityGroup,String keyname,String iamRole,String userData,String availabilityZone) {
    RunInstancesRequest rir = new RunInstancesRequest();
    List<String> sglist = new ArrayList<String>();
    sglist.add(securityGroup);
    rir.setImageId(amiId);
    rir.setInstanceType(instanceType);
    rir.setKeyName(keyname);
    rir.setSecurityGroups(sglist);
    rir.setIamInstanceProfile(new IamInstanceProfileSpecification().withName(iamRole));
    rir.setMaxCount(1);
    rir.setMinCount(1);
    if (userData.equals("null")==false){
      rir.setUserData(userData);
    }
    if (availabilityZone.equals("null")==false){
      rir.setPlacement(new Placement(availabilityZone));
    }
    return rir;
  }
  

  
  public void cancelSpotInstanceRequests(String[] requestIdArr){
    List<String> list = Arrays.asList(requestIdArr); 
    ec2.cancelSpotInstanceRequests(new CancelSpotInstanceRequestsRequest().withSpotInstanceRequestIds(list));
  }
  
  public String getInstanceStatusWithInstanceId(String instanceId) {
    DescribeInstancesResult dir =   ec2.describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceId));
    return dir.getReservations().get(0).getInstances().get(0).getState().getName();
    
  }
  
  
  public String bh_describeInstanceWithInstanceId(String instanceId){
    DescribeInstancesResult dir =   ec2.describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceId));
    return gson.toJson(dir);
  }
  
  public String bh_describeInstances(){
    DescribeInstancesResult dir =   ec2.describeInstances(new DescribeInstancesRequest());
    return gson.toJson(dir);
  }
  
  public String bh_describeInstancesWithTag(String tagValue){
    DescribeInstancesResult dir =   ec2.describeInstances(new DescribeInstancesRequest().withFilters(new Filter().withName("tag-value").withValues(tagValue)));
    return gson.toJson(dir);
  }
  
  
  public String bh_describeSpotInstanceRequests(){
    DescribeSpotInstanceRequestsResult dsirr =  ec2.describeSpotInstanceRequests(new DescribeSpotInstanceRequestsRequest());
    return gson.toJson(dsirr);
  }
  
  public String bh_describeSpotInstanceRequestsWithRequestId(String requestId){
    DescribeSpotInstanceRequestsResult dsirr =  ec2.describeSpotInstanceRequests(new DescribeSpotInstanceRequestsRequest().withSpotInstanceRequestIds(requestId));
    return gson.toJson(dsirr);
  }
  
  public String bh_describeSpotInstanceRequestsWithTag(String tagValue){
    DescribeSpotInstanceRequestsResult dsirr =  ec2.describeSpotInstanceRequests(new DescribeSpotInstanceRequestsRequest().withFilters(new Filter().withName("tag-value").withValues(tagValue)));
    return gson.toJson(dsirr);
  }
  
  public String bh_describeSpotFleetInstances(String spotFleetRequestId){
    DescribeSpotFleetInstancesResult dsfir = new DescribeSpotFleetInstancesResult();
    List<DescribeSpotFleetInstancesResult> allResults = new ArrayList<DescribeSpotFleetInstancesResult>();
    DescribeSpotFleetInstancesRequest request = new DescribeSpotFleetInstancesRequest().withSpotFleetRequestId(spotFleetRequestId);
    dsfir = ec2.describeSpotFleetInstances(request);
    return gson.toJson(dsfir);
    /*System.out.println(gson.toJson(dsfir));
    allResults.add(dsfir);
    //System.out.println(dsfir.getNextToken().toString());
    while (!dsfir.getNextToken().isEmpty()){
      //System.out.println(dsfir.getNextToken());
      request.setNextToken(dsfir.getNextToken());
      dsfir = ec2.describeSpotFleetInstances(request);
      allResults.add(dsfir);
      //sp.addAll(dsphr.getSpotPriceHistory());
      
    }
    return gson.toJson(allResults);*/
  }

  public void modifySpotFleetTerminationPolicy(String policy,String spotFleetRequestId){
    ec2.modifySpotFleetRequest(new ModifySpotFleetRequestRequest().withExcessCapacityTerminationPolicy(policy).withSpotFleetRequestId(spotFleetRequestId));
  }
  
  public void bh_modifySpotFleetCapacity(String spotFleetRequestId, int targetCapacity){
    ec2.modifySpotFleetRequest(new ModifySpotFleetRequestRequest().withSpotFleetRequestId(spotFleetRequestId).withTargetCapacity(targetCapacity));
  }
  
  public String bh_getSpotFleetConfig(String spotFleetRequestId){
    DescribeSpotFleetRequestsResult dsfrr = new DescribeSpotFleetRequestsResult();
    dsfrr = ec2.describeSpotFleetRequests(new DescribeSpotFleetRequestsRequest().withSpotFleetRequestIds(spotFleetRequestId));
    return gson.toJson(dsfrr.getSpotFleetRequestConfigs().get(0).getSpotFleetRequestConfig());
  }
  

  

  
  public void terminateInstances(String[] instanceIdsArr){
    List<String> list = Arrays.asList(instanceIdsArr); 
    TerminateInstancesRequest tir = new TerminateInstancesRequest().withInstanceIds(list);
    ec2.terminateInstances(tir);
  }
  
  

  
  
  
  public String requestSpotInstance(String instanceType,String bid,String amiId,String securityGroup,String keyname,String iamRole,String userData,String availabilityZone){
    RequestSpotInstancesResult requestResult = ec2.requestSpotInstances(makeSpotInstanceRequest(instanceType,bid,amiId,securityGroup, keyname, iamRole,userData,availabilityZone));
    List<SpotInstanceRequest> requestResponses = requestResult.getSpotInstanceRequests();
    String requestId = requestResponses.get(0).getSpotInstanceRequestId();
    return requestId;
  }
  
  private RequestSpotInstancesRequest makeSpotInstanceRequest(String instanceType,String bid,String amiId,String securityGroup,String keyname,String iamRole,String userData,String availabilityZone) {
    RequestSpotInstancesRequest requestRequest = new RequestSpotInstancesRequest();
    requestRequest.setSpotPrice(bid);
    LaunchSpecification launchSpecification = new LaunchSpecification();
    launchSpecification.setImageId(amiId);
    launchSpecification.setInstanceType(instanceType);
    ArrayList<String> securityGroups = new ArrayList<String>();
    securityGroups.add(securityGroup);
    launchSpecification.setSecurityGroups(securityGroups);
    launchSpecification.setKeyName(keyname);
    launchSpecification
        .setIamInstanceProfile(new IamInstanceProfileSpecification().withName(iamRole));
    if (userData.equals("null") == false) {
      launchSpecification.setUserData(userData);
    }
    if (availabilityZone.equals("null")==false){
      launchSpecification.setPlacement(new SpotPlacement(availabilityZone));
    }

    requestRequest.setLaunchSpecification(launchSpecification);
    //requestRequest.setType("persistent");
    
    return requestRequest;
  }
  
  public String getSpotPrices(int numHours){
    Date d = new Date();
    Calendar cal = Calendar.getInstance();
    cal.setTime(d);
    cal.add(Calendar.HOUR, -numHours);
    Date oneHourBack = cal.getTime();
    
    DescribeSpotPriceHistoryRequest describeSpotPriceHistoryRequest = new DescribeSpotPriceHistoryRequest().withEndTime(d);
    describeSpotPriceHistoryRequest.setStartTime(oneHourBack);
    
    DescribeSpotPriceHistoryResult dsphr = new DescribeSpotPriceHistoryResult();
    dsphr = ec2.describeSpotPriceHistory(describeSpotPriceHistoryRequest);
    return gson.toJson(dsphr);
    
  }
  
  public String getSpotPrice(String instanceType, String availabilityZone,int numHours){
    Date d = new Date();
    Calendar cal = Calendar.getInstance();
    cal.setTime(d);
    cal.add(Calendar.HOUR, -numHours);
    Date oneHourBack = cal.getTime();
    

    DescribeSpotPriceHistoryRequest describeSpotPriceHistoryRequest = new DescribeSpotPriceHistoryRequest().withAvailabilityZone(availabilityZone).withEndTime(d).withInstanceTypes(instanceType).withProductDescriptions("Linux/UNIX");
    describeSpotPriceHistoryRequest.setStartTime(oneHourBack);
    //describeSpotPriceHistoryRequest.setMaxResults(1);
    
    DescribeSpotPriceHistoryResult dsphr = new DescribeSpotPriceHistoryResult();
    dsphr = ec2.describeSpotPriceHistory(describeSpotPriceHistoryRequest);
    
    
    List<SpotPrice> sp = new ArrayList<SpotPrice>();

    sp=dsphr.getSpotPriceHistory();
    System.out.println(dsphr.getNextToken());
    System.out.println(dsphr.getNextToken().isEmpty());
    
    while (!dsphr.getNextToken().isEmpty()){
      System.out.println(dsphr.getNextToken());
      describeSpotPriceHistoryRequest.setNextToken(dsphr.getNextToken());
      dsphr = ec2.describeSpotPriceHistory(describeSpotPriceHistoryRequest);
      sp.addAll(dsphr.getSpotPriceHistory());
      
    }
    return gson.toJson(sp);
    
    

    
  }
  

  

  
  public void sendCommand(String instanceId,String[] commands){
    List<String> commandValues = new ArrayList<String>();
    for(int i=0;i<commands.length;i++){
      System.out.println(commands[i]);
      commandValues.add(commands[i]);
    }
    scr = new SendCommandRequest().withInstanceIds(instanceId).withDocumentName("AWS-RunShellScript").addParametersEntry("commands", commandValues);
    ssm.sendCommand(scr);
  }
  
}


/*class Ec2objects {
  static class MyInstance {
    String instanceId,state,imageId,tagKey,tagValue,spotInstanceRequestId,stateReason;
    Boolean isSpot;
  }
  
  static class MySpotRequest{
    String requestId,status,state,spotPrice,availabilityZone,tagKey,tagValue,createTime;
    long createTimeLong;
  }
}*/



