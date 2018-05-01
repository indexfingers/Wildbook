<%@ page contentType="text/html; charset=utf-8" language="java"
	import="org.ecocean.*,
            org.ecocean.servlet.ServletUtilities,
			org.ecocean.media.S3AssetStore,
			org.ecocean.media.AssetStoreConfig,
			org.json.JSONObject
			"
%>




<%

String context=ServletUtilities.getContext(request);

Shepherd myShepherd=null;
myShepherd=new Shepherd(context);

JSONObject c = new JSONObject();
c.put("urlAccessible", true);
c.put("bucket","sosf-ws-wildbook");



AssetStoreConfig cfg = new AssetStoreConfig(c.toString());
S3AssetStore as3 = new S3AssetStore("S3 AssetStore", cfg, true);

myShepherd.beginDBTransaction();
myShepherd.getPM().makePersistent(as3);
myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();
myShepherd=null;

out.println("<p>seems to be okay</p>");



%>
