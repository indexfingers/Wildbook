<%@ page contentType="text/html; charset=utf-8" language="java"
	import="org.ecocean.*,
      org.ecocean.servlet.ServletUtilities,
      org.ecocean.security.Collaboration
			"
%>


<%

String context = ServletUtilities.getContext(request);
out.println("<p>context = " + context + "</p>");


String currentUsername = "Dylan";
String username = "benAdmin";


Shepherd myShepherd=null;
myShepherd = new Shepherd(context);

Collaboration collab = Collaboration.collaborationBetweenUsers(context, currentUsername, username);
if(collab == null){

  out.println("<p> collab is null <p>");
  collab = Collaboration.create(currentUsername, username);
  out.println("<p>collab state = " + collab.getState() +  "<p>");
  boolean result = myShepherd.storeNewCollaboration(collab);
  out.println("<p>collab new create result = " + result +  "<p>");

} else {
  out.println("<p>collab state = " + collab.getState() +  "<p>");
  myShepherd.beginDBTransaction();
  collab.setState(Collaboration.STATE_APPROVED);
  myShepherd.commitDBTransaction();
  out.println("<p>collab state = " + collab.getState() +  "<p>");
}

myShepherd.closeDBTransaction();
myShepherd=null;

/*
myShepherd.beginDBTransaction();
collab.setState(Collaboration.STATE_APPROVED);
myShepherd.commitDBTransaction();


collab = Collaboration.create(currentUsername, username);

out.println("<p>collab state = " + collab.getState() +  "<p>");

boolean result = myShepherd.storeNewCollaboration(collab);

out.println("<p>collab new create result = " + result +  "<p>");

/*







collab = Collaboration.create(currentUsername, username);
myShepherd.storeNewCollaboration(collab);


myShepherd.beginDBTransaction();
collab.setState(Collaboration.STATE_APPROVED);
myShepherd.commitDBTransaction();*/
%>
