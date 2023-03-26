package com.sap.apj.gunter;

import java.io.BufferedReader;
import java.io.IOException;

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
import com.sap.conn.jco.JCoContext;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoField;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoFunctionTemplate;
import com.sap.conn.jco.JCoListMetaData;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoRecordMetaData;
import com.sap.conn.jco.JCoRepository;
import com.sap.conn.jco.JCoStructure;
import com.sap.conn.jco.JCoTable;
import com.sap.cloud.sdk.cloudplatform.security.RolesAllowed;
import com.sap.cloud.security.client.HttpClientException;
import com.sap.cloud.security.client.HttpClientFactory;
import com.sap.cloud.security.config.Environments;
import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import com.sap.cloud.security.xsuaa.client.DefaultOAuth2TokenService;
import com.sap.cloud.security.xsuaa.client.XsuaaDefaultEndpoints;
import com.sap.cloud.security.xsuaa.tokenflows.XsuaaTokenFlows;


/**
 * Sample application that uses the Connectivity service. In particular, it is
 * making use of the capability to invoke a function module in an ABAP system
 * via RFC
 *
 * Note: The JCo APIs are available under <code>com.sap.conn.jco</code>.
 */

@WebServlet("/rfc/*")
@ServletSecurity(@HttpConstraint(rolesAllowed = { "Display", "Modify" }))
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
	
	private JSONObject convertToJson(JSONObject jInput, String bapiParam) {
		JSONObject jData = new JSONObject();
		try {
			jData = jInput.getJSONObject(bapiParam);
		} catch (JSONException e){
			logger.warn("No BAPI parameters for " + bapiParam + ".");
			return null;
		}
		return jData;
	}
	
	private void handleRequest(String fm, String jsonData, boolean luw, HttpServletResponse response) 
			throws IOException
			 {
		JSONObject jsonResponse = new JSONObject();
	    JSONObject jData = new JSONObject();
	    
		try {
	    	jData = new JSONObject(jsonData);
		} catch (JSONException e){
			jsonResponse.put("exception", "Body couldn't be converted to JSON.");
			jsonResponse.put("errormessage", e.getMessage());
			response.setStatus(400);
            response.getWriter().write(jsonResponse.toString());
            return;
		}
	    
	    // Is null if no data
	    JSONObject jImports = convertToJson(jData, "imports");
	    JSONObject jTables = convertToJson(jData, "tables");		
		
	    try {
			// Function module call preparation
			JCoDestination btpDest = JCoDestinationManager.getDestination(btpDestination);
			if (luw) {
				JCoContext.begin(btpDest); // A bracket around the LUW
			}
			JCoFunction rfcFunction = getRfcFunction(btpDest, fm);
			JCoParameterList bapiImports = rfcFunction.getImportParameterList();
			JCoParameterList bapiTables = rfcFunction.getTableParameterList();
			
			// Fill imports structures and tables (if applicable)
			logger.info("Getting function module metadata...");
			bapiImports.getMetaData();
			bapiTables.getMetaData();
			
			if (jImports != null) {
				logger.info("Filling BAPI import: " + jImports.toString());
				bapiImports.fromJSON(jImports.toString());
			}
			if (jTables != null) {
				logger.info("Filling BAPI tables: " + jTables.toString());
				bapiTables.fromJSON(jTables.toString());
			}
			// Execute BAPI call
			logger.info("Executing BAPI call...");
			rfcFunction.execute(btpDest);
			
			if (luw) {
				// Commit work of BAPI
				JCoFunction commitFunction = btpDest.getRepository().getFunction("BAPI_TRANSACTION_COMMIT");
				commitFunction.execute(btpDest);			
				logger.info("Committed BAPI LUW.");
				JCoContext.end(btpDest); // Ending the context here
			}
			
			// Work with return data from BAPI
			JCoParameterList bapiExports = rfcFunction.getExportParameterList();
			bapiTables = rfcFunction.getTableParameterList();
			
			// Dynamic extract of exports
			JSONObject jExports = new JSONObject(bapiExports.toJSON());
			jsonResponse.put("exports", jExports);
			jTables = new JSONObject(bapiTables.toJSON());
			jsonResponse.put("tables", jTables);
            
			// Success, respond with JSON result
			logger.info("Sending JSON result from BAPI call...");
            response.getWriter().write(jsonResponse.toString());            
			response.setStatus(200);			
	    }
		catch (AbapException e) {
			// Only if the ABAP FM throws exceptions
			jsonResponse.put("exception", "ABAP exception occured in " + fm + " in destination " + btpDestination + ".");
			jsonResponse.put("errormessage", e.getMessage());
			response.setStatus(400);
            response.getWriter().write(jsonResponse.toString());
            logger.error("ABAP FM exception: " + e.getMessage());
            return;
		}
		catch (JCoException e) {
			jsonResponse.put("exception", "JCo exception occurred while executing " + fm + " in destination " + btpDestination + ".");
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
	//@RolesAllowed({"Modify"})
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {	
		
		// Get request parameters
		String functionName = request.getParameter("fm");
		logger.info("Function module: " + functionName);
		StringBuilder sbBody = new StringBuilder();
		BufferedReader brReqBody = request.getReader();
		String line;
	    while ((line = brReqBody.readLine()) != null) {
	    	sbBody.append(line);
	    }
	    String requestBody = sbBody.toString();	 
	    
	    // Submit to BAPI or RFC FM - Commit work when success
	    handleRequest(functionName, requestBody, true, response);
	}
	
	@Override
	@RolesAllowed({"Display", "Modify"})
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		
		// Get request parameters
		String functionName = request.getParameter("fm");
		logger.info("Function module: " + functionName);
		String query = request.getParameter("query");
		if (query == null) {
			query = "{}";
		}
		logger.info("Query: " + query);		
		String metadata = request.getParameter("metadata");
		
		if (metadata == null) { // Regular call of RFC
			// Submit to BAPI or RFC FM - No commit
			handleRequest(functionName, query, false, response);
		} else { // Deliver the structure back to the caller (like $metadata)
//			// TODO
		}
	}
					
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("application/json");
		logger.info("Request method " + request.getMethod() + " received.");
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