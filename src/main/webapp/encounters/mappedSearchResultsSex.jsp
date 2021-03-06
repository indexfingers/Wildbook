<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,java.util.Vector,java.util.Properties,org.ecocean.genetics.*,java.util.*,java.net.URI, org.ecocean.*" %>


  <%
  String context="context0";
  context=ServletUtilities.getContext(request);

    //let's load encounterSearch.properties
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    
    Properties encprops = new Properties();
    //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/mappedSearchResults.properties"));
    encprops=ShepherdProperties.getProperties("mappedSearchResults.properties", langCode, context);

    
    
    //Properties map_props = new Properties();
    //map_props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/mappedSearchResults.properties"));
    //map_props=ShepherdProperties.getProperties("mappedSearchResults.properties", langCode);


    
    //get our Shepherd
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("mappedSearchResultsSex.jsp");





    int numResults = 0;

    //set up the vector for matching encounters
    Vector rEncounters = new Vector();

    //kick off the transaction
    myShepherd.beginDBTransaction();

    //start the query and get the results
    String order = "";
    request.setAttribute("gpsOnly", "yes");
    EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, order);
    rEncounters = queryResult.getResult();
    
    //let's prep the HashTable for the pie chart
    List<String> allHaplos2=myShepherd.getAllHaplotypes(); 
    int numHaplos2 = allHaplos2.size();
    Hashtable<String,Integer> pieHashtable = new Hashtable<String,Integer>();
 	for(int gg=0;gg<numHaplos2;gg++){
 		String thisHaplo=allHaplos2.get(gg);
 		pieHashtable.put(thisHaplo, new Integer(0));
 	}
    		
    		
  %>

  

<style type="text/css">
  #tabmenu {
    color: #000;
    border-bottom: 1px solid #CDCDCD;
    margin: 12px 0px 0px 0px;
    padding: 0px;
    z-index: 1;
    padding-left: 10px
  }

  #tabmenu li {
    display: inline;
    overflow: hidden;
    list-style-type: none;
    
  }

  #tabmenu a, a.active {
    color: #000;
    background: #E6EEEE;
     
    border: 1px solid #CDCDCD;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #8DBDD8;
    color: #000000;
    border-bottom: 1px solid #8DBDD8;
  }

  #tabmenu a:hover {
    color: #000;
    background: #8DBDD8;
  }

  #tabmenu a:visited {
    
  }

  #tabmenu a.active:hover {
    color: #000;
    border-bottom: 1px solid #8DBDD8;
  }
  
</style>
  
      <script>
        function getQueryParameter(name) {
          name = name.replace(/[\[]/, "\\\[").replace(/[\]]/, "\\\]");
          var regexS = "[\\?&]" + name + "=([^&#]*)";
          var regex = new RegExp(regexS);
          var results = regex.exec(window.location.href);
          if (results == null)
            return "";
          else
            return results[1];
        }
  </script>
  
    <jsp:include page="../header.jsp" flush="true"/>

    <script src="//maps.google.com/maps/api/js?sensor=false"></script>


    <script type="text/javascript">
      function initialize() {
        var center = new google.maps.LatLng(0,0);
        var mapZoom = 3;
    	if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}
    	var bounds = new google.maps.LatLngBounds();
        
        var map = new google.maps.Map(document.getElementById('map_canvas'), {
          zoom: mapZoom,
          center: center,
          mapTypeId: google.maps.MapTypeId.HYBRID,
          fullscreenControl: true
        });


        
        var markers = [];
 
 
        
        <%

//now remove encounters this user cannot see
for (int i = rEncounters.size() - 1 ; i >= 0 ; i--) {
	Encounter enc = (Encounter)rEncounters.get(i);
	if (!enc.canUserAccess(request)) rEncounters.remove(i);
}

        //Vector haveGPSData = new Vector();
        int rEncountersSize=rEncounters.size();
        int count = 0;

          
      
        
      
if(rEncounters.size()>0){
	int havegpsSize=rEncounters.size();
 for(int y=0;y<havegpsSize;y++){
	 Encounter thisEnc=(Encounter)rEncounters.get(y);
		String encSubdir = thisEnc.subdir();
	 

 %>
          
          var latLng = new google.maps.LatLng(<%=thisEnc.getDecimalLatitude()%>, <%=thisEnc.getDecimalLongitude()%>);
          bounds.extend(latLng);
           <%

           
           //currently unused programatically
           String markerText="";
           
           String haploColor="CC0000";
           if((encprops.getProperty("defaultMarkerColor")!=null)&&(!encprops.getProperty("defaultMarkerColor").trim().equals(""))){
        	   haploColor=encprops.getProperty("defaultMarkerColor");
           }
		   
           //map by sex
           if(thisEnc.getSex()!=null){
           	if(thisEnc.getSex().equals("male")){haploColor="0000FF";}
           	else if(thisEnc.getSex().equals("female")){haploColor="FF00FF";}
           }
           
           %>
           var marker = new google.maps.Marker({
        	   icon: 'https://chart.googleapis.com/chart?chst=d_map_pin_letter&chld=|<%=haploColor%>',
        	   position:latLng,
        	   map:map
        	   });
	    
			google.maps.event.addListener(marker,'click', function() {
            
      	<%
    	String individualLinkString="";
    	//if this is a MarkedIndividual, provide a link to it
    	if((thisEnc.getIndividualID()!=null)&&(!thisEnc.getIndividualID().toLowerCase().equals("unassigned"))){
    		individualLinkString="<strong><a target=\"_blank\" href=\"//"+CommonConfiguration.getURLLocation(request)+"/individuals.jsp?number="+thisEnc.getIndividualID()+"\">"+thisEnc.getIndividualID()+"</a></strong><br />";
    	}
    	%>
    	(new google.maps.InfoWindow({content: '<%=individualLinkString %><table><tr><td><img align=\"top\" border=\"1\" src=\"/<%=CommonConfiguration.getDataDirectoryName(context)%>/encounters/<%=encSubdir%>/thumb.jpg\"></td><td>Date: <%=thisEnc.getDate()%><%if(thisEnc.getSex()!=null){%><br />Sex: <%=thisEnc.getSex()%><%}%><%if(thisEnc.getSizeAsDouble()!=null){%><br />Size: <%=thisEnc.getSize()%> m<%}%><br /><br /><a target=\"_blank\" href=\"//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=thisEnc.getEncounterNumber()%>\" >Go to encounter</a></td></tr></table>'})).open(map, this);

          
			});
 
	
          markers.push(marker);
          map.fitBounds(bounds); 
 
 <%
 
	 }
} 

myShepherd.rollbackDBTransaction();
 %>
 
 //markerClusterer = new MarkerClusterer(map, markers, {gridSize: 10});

      }
      
      
      google.maps.event.addDomListener(window, 'load', initialize);
    </script>
<div class="container maincontent">

      <h1 class="intro"><%=encprops.getProperty("title")%></h1>
      
      
      
 
 <ul id="tabmenu">
 
   <li><a href="searchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("table")%>
   </a></li>
   <li><a href="thumbnailSearchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("matchingImages")%>
   </a></li>
   <li><a class="active"><%=encprops.getProperty("mappedResults") %>
   </a></li>
   <li><a href="../xcalendar/calendar2.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("resultsCalendar")%>
   </a></li>
         <li><a href="searchResultsAnalysis.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("analysis")%>
   </a></li>
      <li><a
     href="exportSearchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("export")%>
   </a></li>
 
 </ul>

 
 
 
 
 <br />
 
 
 

 <%
 
 //read from the encprops property file the value determining how many entries to map. Thousands can cause map delay or failure from Google.
 int numberResultsToMap = -1;

 %>

 <p><%=encprops.getProperty("aspects") %>:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
  <%
  boolean hasMoreProps=true;
  int propsNum=0;
  while(hasMoreProps){
	if((encprops.getProperty("displayAspectName"+propsNum)!=null)&&(encprops.getProperty("displayAspectFile"+propsNum)!=null)){
		%>
		<a href="<%=encprops.getProperty("displayAspectFile"+propsNum)%>?<%=request.getQueryString()%>"><%=encprops.getProperty("displayAspectName"+propsNum) %></a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
		
		<%
		propsNum++;
	}
	else{hasMoreProps=false;}
  }
  %>
</p>
 <%
   if (rEncounters.size() > 0) {
     myShepherd.beginDBTransaction();
     try {
 %>
 
<p><%=encprops.getProperty("mapNote")%></p>
 
 <div id="map-container">
 

 <table width="100%">
 <tr>
 <td valign="top" width="90%">
  <div id="map_canvas" style="width: 100%; height: 500px; "></div>
 </td>
 <td valign="top" width="10%">
 <table>
 <tr><th>Color Key</th></tr>
<%
String haploColor="CC0000";
if((encprops.getProperty("defaultMarkerColor")!=null)&&(!encprops.getProperty("defaultMarkerColor").trim().equals(""))){
	   haploColor=encprops.getProperty("defaultMarkerColor");
}
%>
	<tr bgcolor="#0000FF"><td><strong>Male</strong></td></tr>
	<tr bgcolor="#FF00FF"><td><strong>Female</strong></td></tr>
	<tr bgcolor="#<%=haploColor%>"><td><strong>Unknown</strong></td></tr>
 </table>
 </td>
 </tr>
 </table>

 

 <div id="chart_div"></div>

 </div>
 

 
 <%
 
     } 
     catch (Exception e) {
       e.printStackTrace();
     }
 
   }
 else {
 %>
 <p><%=encprops.getProperty("noGPS")%></p>
 <%
 }  

 
 
   myShepherd.rollbackDBTransaction();
   myShepherd.closeDBTransaction();
   rEncounters = null;
   //haveGPSData = null;
 
%>
 <table>
  <tr>
    <td align="left">

      <p><strong><%=encprops.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=encprops.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=queryResult.getQueryPrettyPrint().replaceAll("locationField", encprops.getProperty("location")).replaceAll("locationCodeField", encprops.getProperty("locationID")).replaceAll("verbatimEventDateField", encprops.getProperty("verbatimEventDate")).replaceAll("alternateIDField", encprops.getProperty("alternateID")).replaceAll("behaviorField", encprops.getProperty("behavior")).replaceAll("Sex", encprops.getProperty("sex")).replaceAll("nameField", encprops.getProperty("nameField")).replaceAll("selectLength", encprops.getProperty("selectLength")).replaceAll("numResights", encprops.getProperty("numResights")).replaceAll("vesselField", encprops.getProperty("vesselField"))%>
      </p>

      <p class="caption"><strong><%=encprops.getProperty("jdoql")%>
      </strong><br/>
        <%=queryResult.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>

</div>

 
 <jsp:include page="../footer.jsp" flush="true"/>

