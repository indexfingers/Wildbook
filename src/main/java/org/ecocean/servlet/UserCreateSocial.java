package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;


import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Date;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;

import org.pac4j.core.client.*;
import org.pac4j.core.context.*;
import org.pac4j.oauth.*;
import org.pac4j.oauth.client.*;
import org.pac4j.oauth.credentials.*;
import org.pac4j.oauth.profile.facebook.*;

import org.apache.shiro.web.util.WebUtils;
import org.ecocean.*;



/**
 * Uses JSecurity to authenticate a user
 * If user can be authenticated successfully
 * forwards user to /secure/index.jsp
 * 
 * If user cannot be authenticated then forwards
 * user to the /login.jsp which will display
 * an error message
 *
 */
 public class UserCreateSocial extends javax.servlet.http.HttpServlet implements javax.servlet.Servlet {
   static final long serialVersionUID = 1L;
   
    /* (non-Java-doc)
	 * @see javax.servlet.http.HttpServlet#HttpServlet()
	 */
	public UserCreateSocial() {
		super();
	}   	
	
	/* (non-Java-doc)
	 * @see javax.servlet.http.HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		doPost(request, response);
	}  	
	
	/* (non-Java-doc)
	 * @see javax.servlet.http.HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    HttpSession session = request.getSession(true);

/*
----------
*/
    PrintWriter out = response.getWriter();
		String context = "context0";
		Shepherd myShepherd = new Shepherd(context);
		//myShepherd.beginDBTransaction();

		String socialType = request.getParameter("type");

		if (request.getUserPrincipal() != null) {
     	out.println("logout first. you cannot create a user when logged in.");
			return;
		}

		if ("facebook".equals(socialType)) {
			FacebookClient fbclient = new FacebookClient("363791400412043", "719b2c0b21cc5e53bdc9086a283dc589");
			WebContext ctx = new J2EContext(request, response);
			//String callbackUrl = "http://localhost.wildme.org/a/UserCreateSocial?type=facebook";
			String callbackUrl = "http://" + CommonConfiguration.getURLLocation(request) + "/UserCreateSocial?type=facebook";
			fbclient.setCallbackUrl(callbackUrl);

			OAuthCredentials credentials = null;
			try {
				credentials = fbclient.getCredentials(ctx);
			} catch (Exception ex) {
				System.out.println("caught exception on facebook credentials: " + ex.toString());
			}

			if (credentials != null) {
				FacebookProfile facebookProfile = fbclient.getUserProfile(credentials, ctx);
				User fbuser = myShepherd.getUserBySocialId("facebook", facebookProfile.getId());
				System.out.println("getId() = " + facebookProfile.getId() + " -> user = " + fbuser);

				if (fbuser != null) {
					out.println("already a user connected to this facebook");

				} else {
					String username = facebookProfile.getDisplayName().replaceAll(" ", "").toLowerCase();  //TODO handle this better!
System.out.println("username: " + facebookProfile.getUsername());
System.out.println("displayname: " + facebookProfile.getDisplayName());
System.out.println("firstname: " + facebookProfile.getFirstName());
System.out.println("familyname: " + facebookProfile.getFamilyName());
System.out.println("email: " + facebookProfile.getEmail());
//TODO other fields?  --> https://pac4j.github.io/pac4j/apidocs/pac4j/org/pac4j/oauth/profile/facebook/FacebookProfile.html
					fbuser = createUser(username, context);
					fbuser.setSocialFacebook(facebookProfile.getId());
					//myShepherd.getPM().makePersistent(fbuser);
					out.println("account " + fbuser.getUsername() + " created!  [TODO log them in]");
				}
			} else {

System.out.println("*** trying redirect?");
				try {
					fbclient.redirect(ctx, false, false);
				} catch (Exception ex) {
					System.out.println("caught exception on facebook processing: " + ex.toString());
				}
				return;
			}


		} else {
			out.println("invalid type");
			return;
		}


		out.println("ok????");
	}   	  	    


	private User createUser(String username, String context) {
		String salt = ServletUtilities.getSalt().toHex();
		String hashedPassword = ServletUtilities.hashAndSaltPassword("fixme", salt);
		User user = new User(username, hashedPassword, salt);
		Shepherd myShepherd = new Shepherd(context);
		myShepherd.getPM().makePersistent(user);
		Role role = new Role(username, "fromSocial");
		role.setContext(context);
		myShepherd.getPM().makePersistent(role);
		return user;
	}

}
