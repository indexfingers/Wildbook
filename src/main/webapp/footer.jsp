<%@ page
		contentType="text/html; charset=utf-8"
		language="java"
     	import="org.ecocean.CommonConfiguration,
      org.ecocean.ContextConfiguration,
      org.ecocean.ShepherdProperties,
      org.ecocean.servlet.ServletUtilities,
      org.ecocean.Shepherd,
      org.ecocean.User,
      java.util.ArrayList,
      java.util.List,
      java.util.Properties,
      org.apache.commons.lang.WordUtils,
      org.ecocean.security.Collaboration
      "
%>
        <%
				String context="context0";
				context=ServletUtilities.getContext(request);
				String langCode=ServletUtilities.getLanguageCode(request);
				Properties props = new Properties();
				props = ShepherdProperties.getProperties("header.properties", langCode, context);
				Shepherd myShepherd = new Shepherd(context);
				// 'sets serverInfo if necessary
				CommonConfiguration.ensureServerInfo(myShepherd, request);
				System.out.println(CommonConfiguration.getServerInfo(myShepherd).toString());
				String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
				myShepherd.setAction("footer.jsp");
				myShepherd.rollbackAndClose();
        //String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
        %>/

        <!-- footer -->
        <footer class="page-footer">

            <div class="container-fluid">
              <div class="container main-section">

                <div class="row">
                  <p class="col-sm-8" style="margin-top:40px;">
                    <small>This software is distributed under the GPL v2 license and is intended to support mark-recapture field studies.
                  <br> <a href="http://www.wildme.org/wildbook" target="_blank">Wildbook v.<%=ContextConfiguration.getVersion() %></a> </small>
                  </p>
                  <a href="http://www.wildbook.org" class="col-sm-4" title="This site is Powered by Wildbook">
                    <img src="<%=urlLoc %>/images/WildBook_logo_72dpi-01.png" alt=" logo" class="pull-right" style="
											height: 150px;
										"/>



                  </a>
                </div>
              </div>
            </div>

            <script>
				  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
				  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
				  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
				  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

				  ga('create', 'UA-30944767-5', 'auto');
				  ga('send', 'pageview');

			</script>

        </footer>
        <!-- /footer -->
    </body>
</html>
