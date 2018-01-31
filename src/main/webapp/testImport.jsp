<%@ page
	import="java.util.*,java.io.IOException,java.nio.file.Files,java.nio.file.Paths,com.google.gson.Gson,
java.nio.charset.Charset,org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
java.util.Collection,
java.util.List,
java.util.ArrayList,
org.json.JSONObject,
javax.jdo.Query,
javax.jdo.Extent,
java.util.HashMap,
org.ecocean.Annotation,
org.ecocean.media.*"%>


<%!public class ReadBenEncounterData {

		private List<BenEncounter> encs;
		private Gson gson = new Gson();

		// populate encs using json string read from file
		public void fillEncsUsingFile(String pathToFile) {
			String json = doFileRead(pathToFile);
			BenEncounter[] beArray = gson.fromJson(json, BenEncounter[].class);
			encs = new ArrayList<>(Arrays.asList(beArray));
		}

		public List<BenEncounter> getBenEncs() {
			return encs;
		}

		private String doFileRead(String pathToFile) {
			final String EoL = System.getProperty("line.separator");
			String out = null;
			try {
				List<String> lines = Files.readAllLines(Paths.get(pathToFile), Charset.defaultCharset());
				StringBuilder sb = new StringBuilder();
				for (String line : lines) {
					sb.append(line).append(EoL);
				}
				out = sb.toString();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return out;

		}

	}

	public class BenEncounter {
		public String encounterId, encDate, sex, submitterId, locationId, individualId, photographer, species, alternateEncounterId;
		public List<String> paths, comments;
	}%>


<html>
<body>


<%
	// read my json encounters file and deserialise into array of tmep encounter objects (called BenEncounter)
	String pathToFile = "/home/ubuntu/benTestImportFiles/testMantaImportData.json";
	ReadBenEncounterData rbed = new ReadBenEncounterData();
	rbed.fillEncsUsingFile(pathToFile);

	boolean reuseMediaAssets = true; //probably want false
	String grouping = "A";
	Shepherd myShepherd = null;
	String context = "context0";
	myShepherd = new Shepherd(context);
	myShepherd.beginDBTransaction();
	AssetStore astore = AssetStore.getDefault(myShepherd);
	FeatureType.initAll(myShepherd);

	for (BenEncounter benc : rbed.getBenEncs()) {
		Encounter encExists = myShepherd.getEncounter(benc.encounterId);
		if (encExists != null) {
			out.println("<p>" + benc.encounterId + " already exists; skipping</p>");
			continue;
		}

		// create / get media assets for encounter and stick in 'mas'
		boolean maExisted = false;
		List<MediaAsset> mas = new ArrayList<MediaAsset>();

		for (String p : benc.paths) {
			File f = new File(p);
			if (!f.exists()) {
				out.println(f.toString() + " does not exists; skipping");
				continue;
			}

			JSONObject sp = astore.createParameters(f, grouping + "/" + f.toString().hashCode());

			//checks to see if media asset already exists
			MediaAsset ma = astore.find(sp, myShepherd);

			//if does exist
			if (ma != null) {
				if (reuseMediaAssets) {
					// add ma to this list (to be added to this encounter)
					mas.add(ma);
				} else {
					maExisted = true;
					continue;
				}
			} else {
				// if doesnt already exist, try to create ma, or at least copy necessary data to database...
				try {
					ma = astore.copyIn(f, sp);
				} catch (IOException ioe) {
					System.out.println("failed copyIn of " + f + ": " + ioe.toString());
				}
				// if ma created okay, do add label (?) then add ma to list for enc
				if (ma != null) {
					ma.addLabel("_original");
					mas.add(ma);
				}
			}
		}

		// create (html) list of comments
		String comments = "";
		if (benc.comments != null) {
			for (String c : benc.comments) {
				comments += "<li>" + c + "</li>";
			}
		}

		myShepherd.beginDBTransaction();

		// now bung mas into anns (assuming here that no features so one ma is one ann)
		ArrayList<Annotation> anns = new ArrayList<Annotation>();
		for (MediaAsset ma : mas) {
			out.println("<p>" + ma.toString() + "</p>");
			// have to check what updateMetaData does
			try {
				ma.updateMetadata();
			} catch (IOException ioe) {
				//we dont care (well sorta) ... since IOException usually means we couldnt open file or some nonsense that we cant recover from
				System.out.println("could not updateMetadata() on " + ma);
			}
			// now creating media asset in database (maybe as opposed to just data as above..with copyIn())
			MediaAssetFactory.save(ma, myShepherd);

			// creates children (downsampled images etc(?))
			ma.updateStandardChildren(myShepherd);
			out.println("<p>created <a target=\"_new\" title=\"" + ma.toString()
					+ "\" href=\"obrowse.jsp?type=MediaAsset&id=" + ma.getId() + "\">" + ma.getId()
					+ "</a></p>");

			Annotation ann = new Annotation(benc.species, ma);
			anns.add(ann);
		}

		Encounter enc = new Encounter(anns);
		//-- leaving this out - wanna keep uuids for now...
		//enc.setCatalogNumber(fields[0]);

		// perhaps though try out setOtherCatalogNumbers (why plural when takes single string, unless perhaps wants html list?)
		if (benc.encounterId != null)
			enc.setOtherCatalogNumbers(benc.encounterId);

		if (benc.alternateEncounterId != null)
			enc.setAlternateCatalogNumber(benc.alternateEncounterId);

		if (benc.encDate != null) {
			enc.setYear(Integer.parseInt(benc.encDate.substring(0, 4)));
			enc.setMonth(Integer.parseInt(benc.encDate.substring(4, 6)));
			enc.setDay(Integer.parseInt(benc.encDate.substring(6, 8)));
		}

		//not worrying about time for now
		//if (!fields[2].equals("NA")) {
		//	enc.setHour(Integer.parseInt(fields[2].substring(0,2)));
		//	enc.setMinutes(fields[2].substring(2,4));
		//}

		String sex = null;
		if (benc.sex != null)
			sex = benc.sex.toLowerCase();
		enc.setSex(sex);

		// note will need to look at method to get submitter details from org.ecocean.User so stored with encounter (?)
		if (benc.submitterId != null) {
			enc.setRecordedBy(benc.submitterId);
			enc.setSubmitterID(benc.submitterId);
		}

		if (benc.photographer != null)
			enc.setPhotographerName(benc.photographer);
		if (benc.locationId != null)
			enc.setLocationID(benc.locationId);
		// ignoring verbatim locality for now - return to this
		//if (!fields[6].equals("NA"))enc.setVerbatimLocality(fields[6]);

		// ignoring these for now
		//if (!fields[7].equals("NA")) enc.setMatchedBy(fields[7]);
		//if (!fields[9].equals("NA")) enc.setDecimalLatitude(Double.parseDouble(fields[9]));
		//if (!fields[10].equals("NA")) enc.setDecimalLongitude(Double.parseDouble(fields[10]));


		if (!comments.equals("")) enc.setComments("<ul>" + comments + "</ul>");

		if (benc.individualId != null){
			MarkedIndividual indiv = myShepherd.getMarkedIndividualQuiet(benc.individualId);
			//if new individual
			if (indiv == null) {
				indiv = new MarkedIndividual(benc.individualId, enc);
				myShepherd.storeNewMarkedIndividual(indiv);
				System.out.println("+ created new " + indiv);
			} else {
				indiv.addEncounter(enc, context);
			}
			enc.setIndividualID(benc.individualId);
			enc.setState("approved");
		} else {
			enc.setState("unapproved");
		}

		Date d = new Date();
		enc.setDWCDateAdded(new Long(d.getTime()));
		//out.println(new Long(d.getTime()));


		myShepherd.storeNewEncounter(enc, enc.getCatalogNumber());
		System.out.println("+ created " + enc);
		out.println("<p>created <a target=\"_new\" title=\"" + enc.toString() + "\" href=\"obrowse.jsp?type=Encounter&id=" + enc.getCatalogNumber() + "\">" + enc.getCatalogNumber() + "</a></p>");
		myShepherd.commitDBTransaction();

	}
	myShepherd.commitDBTransaction();
	myShepherd.closeDBTransaction();
%>




</body>
</html>
