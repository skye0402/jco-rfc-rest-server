package com.sap.apj.gunter;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;

import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.conn.jco.AbapException;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoStructure;
import com.sap.conn.jco.JCoRepository;

import com.sap.cloud.sdk.cloudplatform.security.AuthToken;
import static com.sap.cloud.sdk.cloudplatform.security.AuthTokenAccessor.getCurrentToken;

import com.sap.cloud.security.client.HttpClientException;
import com.sap.cloud.security.client.HttpClientFactory;
import com.sap.cloud.security.config.Environments;
import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import com.sap.cloud.security.token.AccessToken;
import com.sap.cloud.security.token.TokenClaims;
import com.sap.cloud.security.xsuaa.client.DefaultOAuth2TokenService;
import com.sap.cloud.security.xsuaa.client.OAuth2TokenResponse;
import com.sap.cloud.security.xsuaa.client.XsuaaDefaultEndpoints;
import com.sap.cloud.security.xsuaa.tokenflows.XsuaaTokenFlows;
import com.sap.cloud.security.token.*;


/**
 * Sample application that uses the Connectivity service. In particular, it is
 * making use of the capability to invoke a function module in an ABAP system
 * via RFC
 *
 * Note: The JCo APIs are available under <code>com.sap.conn.jco</code>.
 */

@WebServlet("/rfc/*")
@ServletSecurity(@HttpConstraint(rolesAllowed = { "Display" }))
public class PurchaseRequisition extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(PurchaseRequisition.class);
	private static final String btpDestination = "S4HANA2021-RFC";
	
	private static XsuaaTokenFlows tokenFlows;
	
	@Override
	public void init() throws ServletException {
		OAuth2ServiceConfiguration configuration = Environments.getCurrent().getXsuaaConfiguration();

		try {
			tokenFlows = new XsuaaTokenFlows(
					new DefaultOAuth2TokenService(HttpClientFactory.create(configuration.getClientIdentity())),
					new XsuaaDefaultEndpoints(configuration), configuration.getClientIdentity());
		} catch (HttpClientException e) {
			throw new ServletException("Couldn't setup XsuaaTokenFlows");
		}
	}
	
	private JCoFunction getRfcFunction(JCoDestination btpDest, String fM) throws JCoException, RuntimeException {
		// Prepare the call to the function module
		JCoRepository repo = btpDest.getRepository();
		JCoFunction rfcFunction = repo.getFunction(fM);
		if (rfcFunction == null)
			throw new RuntimeException("Function " + fM + " not found in destination system.");
		return rfcFunction;
	}
	
	private JCoParameterList fillParamList(JSONObject jsonObject, JCoParameterList paramList) {
	    jsonObject.keys().forEachRemaining(key -> {
	        Object value = jsonObject.get(key);
	        paramList.setValue(key.toString(), value.toString());
	    });
	    return paramList;
	}
		
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		PrintWriter responseWriter = response.getWriter();
		// Handle POST request
	    // ...
	}	
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		JSONObject jsonResponse = new JSONObject();
		JSONObject jQuery = new JSONObject();
		response.setContentType("application/json");
		
		// Get token
		OAuth2TokenResponse tokenResponse = tokenFlows.clientCredentialsTokenFlow().execute();
		logger.info("Access-Token-Payload:" + tokenResponse.getDecodedAccessToken().getPayload());
		

		// Get request parameters
		String functionName = request.getParameter("fm");
		logger.info("Function module: " + functionName);
		String query = request.getParameter("query");
		logger.info("Query: " + query);
		try {
			jQuery = new JSONObject(query);
		} catch (JSONException e){
			jsonResponse.put("exception", "Query couldn't be converted to JSON.");
			jsonResponse.put("errormessage", e.getMessage());
			response.setStatus(400);
            response.getWriter().write(jsonResponse.toString());
            return;
		}
		
		try {
			// Function module call preparation
			JCoDestination btpDest = JCoDestinationManager.getDestination(btpDestination);	
			JCoFunction rfcFunction = getRfcFunction(btpDest, functionName);
			JCoParameterList imports = rfcFunction.getImportParameterList();
			imports.getMetaData();
			imports = fillParamList(jQuery, imports);
			
			// Execute BAPI call
			logger.info("Executing BAPI query...");
			rfcFunction.execute(btpDest);
			
			// Work with return data from BAPI
			JCoParameterList exports = rfcFunction.getExportParameterList();
			JCoParameterList tables = rfcFunction.getTableParameterList();
			
			// Dynamic extract of exports
			JSONObject jExports = new JSONObject(exports.toJSON());
			jsonResponse.put("exports", jExports);
			JSONObject jTables = new JSONObject(tables.toJSON());
			jsonResponse.put("tables", jTables);
            
			// Success, respond with JSON result
			logger.info("Sending JSON result from BAPI call...");
            response.setContentType("application/json");
            response.getWriter().write(jsonResponse.toString());
            
			response.setStatus(200);
		}
		catch (AbapException e) {
			// Only if the ABAP FM throws exceptions
			jsonResponse.put("exception", "ABAP exception occured in " + functionName + " in destination " + btpDestination + ".");
			jsonResponse.put("errormessage", e.getMessage());
			response.setStatus(400);
            response.getWriter().write(jsonResponse.toString());
            logger.error("ABAP FM exception: " + e.getMessage());
            return;
		}
		catch (JCoException e) {
			jsonResponse.put("exception", "JCo exception occurred while executing " + functionName + " in destination " + btpDestination + ".");
			jsonResponse.put("errormessage", e.getMessage());
			response.setStatus(400);
            response.getWriter().write(jsonResponse.toString());
            logger.error("JCo exception: " + e.getMessage());
            return;
		}
		catch (RuntimeException e) {
			jsonResponse.put("exception", "Runtime exception.");
			jsonResponse.put("errormessage", e.getMessage());
			response.setStatus(400);
            response.getWriter().write(jsonResponse.toString());
            logger.error("Runtime exception: " + e.getMessage());
            return;
		}
	}
	
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		if (request.getMethod().equals("GET")) {
			doGet(request, response);
		} else if (request.getMethod().equals("POST")) {
			doPost(request, response);
		} else {
	      // Other HTTP methods (PUT, DELETE, etc.) can be handled here
	      // ...
    	}
    }	
}