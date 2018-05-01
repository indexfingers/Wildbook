<%@ page contentType="text/html; charset=utf-8"
	language="java"
	import="org.ecocean.servlet.ServletUtilities,javax.jdo.Query,com.drew.imaging.jpeg.JpegMetadataReader,com.drew.metadata.Metadata, com.drew.metadata.Tag, org.ecocean.mmutil.MediaUtilities,org.ecocean.*,java.io.File, java.util.*,org.ecocean.security.Collaboration, java.io.FileInputStream, javax.jdo.Extent" %>


	<%

	String context="context0";
	context=ServletUtilities.getContext(request);

	//setup data dir
	String rootWebappPath = getServletContext().getRealPath("/");
	File webappsDir = new File(rootWebappPath).getParentFile();
	File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
	//if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
	File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
	//if(!encountersDir.exists()){encountersDir.mkdirs();}


	int startNum = 0;
	int endNum = 45;

	try {

		if (request.getParameter("startNum") != null) {
			startNum = (new Integer(request.getParameter("startNum"))).intValue();
		}
		if (request.getParameter("endNum") != null) {
			endNum = (new Integer(request.getParameter("endNum"))).intValue();
		}

	} catch (NumberFormatException nfe) {
		startNum = 0;
		endNum = 45;
	}


	//let's load thumbnailSearch.properties
	//String langCode = "en";
	String langCode=ServletUtilities.getLanguageCode(request);


	Properties encprops = new Properties();
	//encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/thumbnailSearchResults.properties"));
	encprops = ShepherdProperties.getProperties("thumbnailSearchResults.properties", langCode, context);

	Shepherd myShepherd = new Shepherd(context);

	List<SinglePhotoVideo> rEncounters = new ArrayList<SinglePhotoVideo>();

	myShepherd.beginDBTransaction();
	//EncounterQueryResult queryResult = new EncounterQueryResult(new Vector<Encounter>(), "", "");

	StringBuffer prettyPrint=new StringBuffer("");
	Map<String,Object> paramMap = new HashMap<String, Object>();

	/**
	String filter="";
	if (request.getParameter("noQuery") == null) {
	filter="SELECT from org.ecocean.SinglePhotoVideo WHERE ("+EncounterQueryProcessor.queryStringBuilder(request, prettyPrint, paramMap).replaceAll("SELECT FROM", "SELECT DISTINCT catalogNumber FROM")+").contains(this.correspondingEncounterNumber)";
}
else {

filter="SELECT from org.ecocean.SinglePhotoVideo";

}
*/

String[] keywords = request.getParameterValues("keyword");
if (keywords == null) {
	keywords = new String[0];
}

List<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);


//if (request.getParameter("noQuery") == null) {


String jdoqlQueryString=EncounterQueryProcessor.queryStringBuilder(request, prettyPrint, paramMap);
Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
Query query = myShepherd.getPM().newQuery(jdoqlQueryString);
//query.setFilter("SELECT "+jdoqlQueryString);
query.setResult("catalogNumber");
Collection c = (Collection) (query.execute());
ArrayList<String> enclist = new ArrayList<String>(c);
query.closeAll();

//queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, "year descending, month descending, day descending");

rEncounters=myShepherd.getThumbnails(myShepherd, request, enclist, startNum, endNum, keywords);
/*
}
else{
Query allQuery=myShepherd.getPM().newQuery("SELECT from org.ecocean.Annotation WHERE id != null");
allQuery.setRange(startNum, endNum);
ArrayList<Annotation> al=new ArrayList<Annotation>((Collection<Annotation>)allQuery.execute());
rEncounters
}
*/

%>
<jsp:include page="../header.jsp" flush="true"/>

<!--
1 ) Reference to the files containing the JavaScript and CSS.
These files must be located on your server.
-->

<script type="text/javascript" src="../highslide/highslide/highslide-with-gallery.js"></script>
<link rel="stylesheet" type="text/css" href="../highslide/highslide/highslide.css"/>

<!--
2) Optionally override the settings defined at the top
of the highslide.js file. The parameter hs.graphicsDir is important!
-->

<script type="text/javascript">
hs.graphicsDir = '../highslide/highslide/graphics/';
hs.align = 'auto';
hs.showCredits = false;
hs.anchor = 'top';

//transition behavior
hs.transitions = ['expand', 'crossfade'];
hs.outlineType = 'rounded-white';
hs.fadeInOut = true;
hs.transitionDuration = 0;
hs.expandDuration = 0;
hs.restoreDuration = 0;
hs.numberOfImagesToPreload = 15;
hs.dimmingDuration = 0;

// define the restraining box
hs.useBox = true;
hs.width = 810;
hs.height=250;

//block right-click user copying if no permissions available
<%
if(request.getUserPrincipal()==null){
	%>
	hs.blockRightClick = true;
	<%
}
%>

// Add the controlbar
hs.addSlideshow({
	//slideshowGroup: 'group1',
	interval: 5000,
	repeat: false,
	useControls: true,
	fixedControls: 'fit',
	overlayOptions: {
		opacity: 0.75,
		position: 'bottom center',
		hideOnMouseOut: true
	}
});

</script>
</head>
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

div.scroll {
	height: 200px;
	overflow: auto;
	border: 1px solid #666;
	background-color: #ccc;
	padding: 8px;
}
</style>


<div class="container maincontent">

	<%
	String rq = "";
	if (request.getQueryString() != null) {
		rq = request.getQueryString();
	}
	if (request.getParameter("noQuery") == null) {
		%>

		<table width="810px" border="0" cellspacing="0" cellpadding="0">
			<tr>
				<td>
					<p>

						<h1 class="intro"><%=encprops.getProperty("title")%>
					</h1>
				</p>

			</td>
		</tr>
	</table>

	<ul id="tabmenu">

		<li><a
			href="searchResults.jsp?<%=rq.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("table")%>
		</a></li>
		<li><a class="active"><%=encprops.getProperty("matchingImages")%>
	</a></li>
	<li><a
		href="mappedSearchResults.jsp?<%=rq.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("mappedResults")%>
	</a></li>
	<li><a
		href="../xcalendar/calendar2.jsp?<%=rq.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("resultsCalendar")%>
	</a></li>
	<li><a
		href="searchResultsAnalysis.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("analysis")%>
	</a></li>
	<li><a
		href="exportSearchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("export")%>
	</a></li>
</ul>
<%
}
%>

<p><%=encprops.getProperty("belowMatches")%> <%=startNum%>
- <%=endNum%> <%=encprops.getProperty("thatMatched")%>
</p>

<%
String qString = rq;
int startNumIndex = qString.indexOf("&startNum");
if (startNumIndex > -1) {
	qString = qString.substring(0, startNumIndex);
}

%>
<table width="810px">
	<tr>
		<%
		if ((startNum) > 1) {%>
		<td align="left">
			<p><a
				href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-45)%>&endNum=<%=(startNum-1)%>"><img
				src="../images/Black_Arrow_left.png" width="28" height="28" border="0" align="absmiddle"
				title="<%=encprops.getProperty("seePreviousResults")%>"/></a> <a
				href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-45)%>&endNum=<%=(startNum-1)%>"><%=(startNum - 45)%>
				- <%=(startNum - 1)%>
			</a></p>
		</td>
		<%
	}
	%>
	<td align="right">
		<p><a
			href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum+45)%>&endNum=<%=(endNum+45)%>"><%=(startNum + 45)%>
			- <%=(endNum + 45)%>
		</a> <a
		href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum+45)%>&endNum=<%=(endNum+45)%>"><img
		src="../images/Black_Arrow_right.png" border="0" align="absmiddle"
		title="<%=encprops.getProperty("seeNextResults")%>"/></a></p>
	</td>
</tr>
</table>


<table id="results" border="0" width="100%">
	<%


	int countMe=0;
	List<SinglePhotoVideo> thumbLocs=new ArrayList<SinglePhotoVideo>();

	try {
		//thumbLocs=myShepherd.getThumbnails(request, rEncounters.iterator(), startNum, endNum, keywords);
		thumbLocs=rEncounters;
		//thumbLocs = SinglePhotoVideo.notBlocked(thumbLocs , request, myShepherd);
		//System.out.println("thumLocs.size="+thumbLocs.size());
		for(int rows=0;rows<15;rows++) {		%>

			<tr valign="top">

			<%
			for(int columns=0;columns<3;columns++){
				if(countMe<thumbLocs.size()) {
					Encounter thisEnc = myShepherd.getEncounter(thumbLocs.get(countMe).getCorrespondingEncounterNumber());
					boolean visible = thisEnc.canUserAccess(request);
					if(!visible){
						continue;
					}
					//String encUrlDir = "/" + CommonConfiguration.getDataDirectoryName(context) + "/" + enc.dir("encounters");
					String encSubdir = thisEnc.subdir();

					String thumbLink="";
					boolean video=true;
					if(!myShepherd.isAcceptableVideoFile(thumbLocs.get(countMe).getFilename())){
						//thumbLink="/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/"+ encSubdir +"/"+thumbLocs.get(countMe).getFilename();
						thumbLink=thumbLocs.get(countMe).getWebURL();
						video=false;
					}
					else{
						thumbLink="http://"+CommonConfiguration.getURLLocation(request)+"/images/video.jpg";

					}
					//String link="/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/"+ encSubdir +"/"+thumbLocs.get(countMe).getFilename();
					String link = thumbLocs.get(countMe).getWebURL();
					%>

					<td>
					<table class="<%= (visible ? "" : " no-access") %>">
					<tr>
					<td valign="top">
					<% if (visible) { %>
						<a href="<%=link%>"

						<%
						if(!thumbLink.endsWith("video.jpg")){
							%>
							class="highslide" onclick="return hs.expand(this)"
							<%
						}
						%>

						>
						<% } else { %><a><% } %>
						<img width="250px" height="*" class="lazyload" src="http://<%=CommonConfiguration.getURLLocation(request) %>/cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="<%=thumbLink%>" alt="photo" border="1" title="<%= (visible ? encprops.getProperty("clickEnlarge") : "") %>" /></a>
						<div
						<%
						if(!thumbLink.endsWith("video.jpg")){
							%>
							class="highslide-caption"
							<%
						}
						%>
						>

						<%
						if ((request.getParameter("referenceImageName") != null)&&(!thumbLink.endsWith("video.jpg"))) {
							if(myShepherd.isSinglePhotoVideo(request.getParameter("referenceImageName"))){

								SinglePhotoVideo mySPV=myShepherd.getSinglePhotoVideo(request.getParameter("referenceImageName"));
								//int slashPosition=request.getParameter("referenceImageName").indexOf("/");
								String encNum=mySPV.getCorrespondingEncounterNumber();
								Encounter refImageEnc = myShepherd.getEncounter(encNum);
								%>
								<h4>Reference Image</h4>
								<table id="table<%=(countMe+startNum) %>">
								<tr>
								<td>

								<img width="790px"

								<%
								if(!thumbLink.endsWith("video.jpg")){
									%>
									class="highslide-image"
									<%
								}
								%>

								id="refImage<%=(countMe+startNum) %>"
								src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/encounters/<%=refImageEnc.subdir(refImageEnc.getCatalogNumber()) %>/<%=mySPV.getFilename() %>"/>
								//src="<%=CommonConfiguration.getDataDirectoryName(context) %>/encounters/<%=refImageEnc.subdir(refImageEnc.getCatalogNumber()) %>/<%=mySPV.getFilename() %>"/>
								</td>
								</tr>
								</table>
								<%
							}
						}
						%>

						<%
						if(!thumbLink.endsWith("video.jpg")){
							%>
							<h4><%=encprops.getProperty("imageMetadata") %>
							</h4>
							<%
						}
						%>


						<table>
						<tr>
						<td align="left" valign="top">



						<table>
						<%

						int kwLength = keywords.length;
						//Encounter thisEnc = myShepherd.getEncounter(thumbLocs.get(countMe).getCorrespondingEncounterNumber());
						%>

						<%
						if(!thumbLink.endsWith("video.jpg")){
							%>
							<tr>
							<td><span class="caption"><em><%=(countMe + startNum) %>
							</em></span></td>
							</tr>
							<tr>
							<td>
							<span class="caption"><%=encprops.getProperty("location") %>:
							<%
							try{
								if(thisEnc.getLocation()!=null){
									%>
									<em><%=thisEnc.getLocation() %></em>
									<%
								}
							}
							catch(Exception e){}
								%>
								</span>
								</td>
								</tr>
								<tr>
								<td><span
								class="caption"><%=encprops.getProperty("locationID") %>:
								<%
								try{
									if(thisEnc.getLocationID()!=null){
										%>
										<em><%=thisEnc.getLocationID() %></em>
										<%
									}
								}
								catch(Exception e){}
									%>
									</span>
									</td>
									</tr>
									<tr>
									<td><span class="caption">
									<%=encprops.getProperty("date") %>:
									<%
									try{
										if(thisEnc.getDate()!=null){
											%>
											<%=thisEnc.getDate() %>
											<%
										}
									}
									catch(Exception e){}
										%>
										</span>
										</td>
										</tr>
										<tr>
										<td><span class="caption"><%=encprops.getProperty("individualID") %>:
										<%
										try{
											if((thisEnc.getIndividualID()!=null)&&(!thisEnc.getIndividualID().equals("Unassigned"))){
												%>
												<a href="../individuals.jsp?number=<%=thisEnc.getIndividualID() %>" target="_blank">

												<%=thisEnc.getIndividualID() %>

												</a>
												<%
											}
										}
										catch(Exception e){}
											%>

											</span></td>
											</tr>
											<%
											if(CommonConfiguration.showProperty("showTaxonomy",context)){
												%>
												<tr>
												<td>
												<span class="caption">
												<em><%=encprops.getProperty("taxonomy") %>:
												<%
												try{
													if((thisEnc.getGenus()!=null)&&(thisEnc.getSpecificEpithet()!=null)){

														%>
														<%=(thisEnc.getGenus()+" "+thisEnc.getSpecificEpithet())%>
														<%
													}
												}
												catch(Exception e){}
													%>
													</em>
													</span>
													</td>
													</tr>
													<%

												}
												%>
												<tr>
												<td><span class="caption"><%=encprops.getProperty("catalogNumber") %>:
												<%
												try{
													if(thisEnc.getCatalogNumber()!=null){
														%>
														<a href="encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>" target="_blank">
														<%=thisEnc.getCatalogNumber() %>
														</a>
														<%
													}
												}
												catch(Exception e){}
													%>
													</span></td>
													</tr>
													<%
													try{
														if (thisEnc.getVerbatimEventDate() != null) {
															%>
															<tr>

															<td><span
															class="caption"><%=encprops.getProperty("verbatimEventDate") %>: <%=thisEnc.getVerbatimEventDate() %></span>
															</td>
															</tr>
															<%

														}
													}
													catch(Exception e){}

														if (request.getParameter("keyword") != null) {
															%>


															<tr>
															<td><span class="caption">
															<%=encprops.getProperty("matchingKeywords") %>
															<%

															List<Keyword> myWords = thumbLocs.get(countMe).getKeywords();
															if(myWords!=null){
																int myWordsSize=myWords.size();
																for (int kwIter = 0; kwIter<myWordsSize; kwIter++) {

																	%>
																	<br/><%=myWords.get(kwIter).getReadableName()%>
																	<%
																}
															}




															//    }
															// }
														}

														%>
														</span></td>
														</tr>
														<%
													}
													%>

													</table>

													<%
													if(!thumbLink.endsWith("video.jpg")){
														%>
														<br/>
														<%
													}
													%>

													<%
													if (CommonConfiguration.showEXIFData(context)&&!thumbLink.endsWith("video.jpg")) {
														%>

														<p><strong>EXIF</strong></p>

														<span class="caption">
														<div class="scroll">
														<span class="caption">
														<%
														if ((thumbLocs.get(countMe).getFilename().toLowerCase().endsWith("jpg")) || (thumbLocs.get(countMe).getFilename().toLowerCase().endsWith("jpeg"))) {
															FileInputStream jin=null;
															try{
																//File exifImage = new File(encountersDir.getAbsolutePath() + "/" + thisEnc.getCatalogNumber() + "/" + thumbLocs.get(countMe).getFilename());
																File exifImage = new File(encountersDir.getAbsolutePath() + "/" + thisEnc.subdir() + "/" + thumbLocs.get(countMe).getFilename());
																jin=new FileInputStream(exifImage);

																if(exifImage.exists()){
																	Metadata metadata = JpegMetadataReader.readMetadata(jin);
																	// iterate through metadata directories
																	for (Tag tag : MediaUtilities.extractMetadataTags(metadata)) {
																		%>
																		<%=tag.toString() %><br/>
																		<%
																	}
																} //end if
																else{
																	%>
																	<p>File not found on file system. No EXIF data available.</p>
																	<p>I looked for the file at: <%=exifImage.getAbsolutePath()%></p>
																	<%
																}
															} //end try
															catch(Exception e){
																%>
																<p>Cannot read metadata for this file.</p>
																<%
																e.printStackTrace();
															}
															finally{
																if(jin!=null){jin.close();}
															}
														}
														%>
														</span>
														</div>
														</span>


														</td>
														<%
													}
													%>
													</tr>
													</table>
													</div>
													</div>
													</td>
													</tr>


													<tr>
													<td>
													<span class="caption">
													<%
													if (!visible) out.println("<div class=\"lock-right\">" + thisEnc.collaborationLockHtml(collabs) + "</div>");
													%>
													<%=encprops.getProperty("location") %>:
													<%
													try{
														if(thisEnc.getLocation()!=null){
															%>
															<em><%=thisEnc.getLocation() %></em>
															<%
														}
													}
													catch(Exception e){}
														%>
														</span>
														</td>
														</tr>
														<tr>
														<td><span
														class="caption"><%=encprops.getProperty("locationID") %>:
														<%
														try{
															if(thisEnc.getLocationID()!=null){
																%>
																<em><%=thisEnc.getLocationID() %></em>
																<%
															}
														}
														catch(Exception e){}
															%>
															</span>
															</td>
															</tr>
															<tr>
															<td>
															<span class="caption"><%=encprops.getProperty("date") %>:
															<%
															try{
																if(thisEnc.getDate()!=null){
																	%>
																	<%=thisEnc.getDate() %>
																	<%
																}
															}
															catch(Exception e){}
																%>
																</span>
																</td>
																</tr>
																<tr>
																<td><span class="caption"><%=encprops.getProperty("individualID") %>:
																<%
																try{
																	if((thisEnc.getIndividualID()!=null)&&(!thisEnc.getIndividualID().equals("Unassigned"))){
																		%>
																		<a href="../individuals.jsp?number=<%=thisEnc.getIndividualID() %>" target="_blank">

																		<%=thisEnc.getIndividualID() %>

																		</a>
																		<%
																	}
																}
																catch(Exception e){}
																	%>
																	</span></td>
																	</tr>
																	<%
																	if(CommonConfiguration.showProperty("showTaxonomy",context)){
																		try{
																			if((thisEnc.getGenus()!=null)&&(thisEnc.getSpecificEpithet()!=null)){
																				%>
																				<tr>
																				<td>
																				<span class="caption">
																				<em><%=encprops.getProperty("taxonomy") %>: <%=(thisEnc.getGenus()+" "+thisEnc.getSpecificEpithet())%></em>
																				</span>
																				</td>
																				</tr>
																				<%
																			}
																		}
																		catch(Exception e){}
																		}
																		%>
																		<tr>
																		<td><span class="caption"><%=encprops.getProperty("catalogNumber") %>:
																		<%
																		try{
																			if(thisEnc.getCatalogNumber()!=null){
																				%>
																				<a href="encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>" target="_blank"><%=thisEnc.getCatalogNumber() %>
																				</a>
																				<%
																			}
																		}
																		catch(Exception e){}
																			%>
																			</span></td>
																			</tr>
																			<tr>
																			<td><span class="caption">
																			<%=encprops.getProperty("matchingKeywords") %>
																			<%
																			List<Keyword> myWords = thumbLocs.get(countMe).getKeywords();
																			if(myWords!=null){
																				int myWordsSize=myWords.size();
																				for (int kwIter = 0; kwIter<myWordsSize; kwIter++) {

																					%>
																					<br/><%=myWords.get(kwIter).getReadableName() %>
																					<%
																				}
																			}




																			//    }
																			// }

																			%>
																			</span></td>
																			</tr>

																			</table>
																			</td>
																			<%

																			countMe++;
																		} //end if
																	} //endFor
																	%>
																	</tr>
																	<%
																} //endFor

															} catch (Exception e) {
																e.printStackTrace();
																%>
																<tr>
																<td>
																<p><%=encprops.getProperty("error")%>
																</p>.</p>
																</td>
																</tr>
																<%
															}
															%>

															</table>

															<%


															startNum = startNum + 45;
															endNum = endNum + 45;

															%>

															<table width="810px">
															<tr>
															<%
															if ((startNum - 45) > 1) {%>
																<td align="left">
																<p><a
																href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-90)%>&endNum=<%=(startNum-46)%>"><img
																src="../images/Black_Arrow_left.png" width="28" height="28" border="0" align="absmiddle"
																title="<%=encprops.getProperty("seePreviousResults")%>"/></a> <a
																href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-90)%>&endNum=<%=(startNum-46)%>"><%=(startNum - 90)%>
																- <%=(startNum - 46)%>
																</a></p>
																</td>
																<%
															}
															%>
															<td align="right">
															<p><a
															href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=startNum%>&endNum=<%=endNum%>"><%=startNum%>
															- <%=endNum%>
															</a> <a
															href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=startNum%>&endNum=<%=endNum%>"><img
															src="../images/Black_Arrow_right.png" border="0" align="absmiddle"
															title="<%=encprops.getProperty("seeNextResults")%>"/></a></p>
															</td>
															</tr>
															</table>
															<%
															myShepherd.rollbackDBTransaction();
															myShepherd.closeDBTransaction();


															%>

															</div>
															<jsp:include page="../footer.jsp" flush="true"/>
