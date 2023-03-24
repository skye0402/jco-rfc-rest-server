package com.sap.apj.gunter;

import java.io.IOException;
import java.io.PrintWriter; 

import javax.servlet.ServletException;
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


/**
 * Sample application that uses the Connectivity service. In particular, it is
 * making use of the capability to invoke a function module in an ABAP system
 * via RFC
 *
 * Note: The JCo APIs are available under <code>com.sap.conn.jco</code>.
 */

@WebServlet("/rfc/*")
public class PurchaseRequisition extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(PurchaseRequisition.class);
	private static final String btpDestination = "S4HANA2021-RFC";
	
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
		
		// Function module call preparation
		try {
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
			
			JCoStructure prHeader = exports.getStructure("PRHEADER");
            String prNumber = prHeader.getString("PREQ_NO");        
            String prType = prHeader.getString("PR_TYPE");       
            
            // Prepare JSON response
            jsonResponse.put("prNumber", prNumber);
            jsonResponse.put("prType", prType);
            
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
            return;
		}
		catch (JCoException e) {
			jsonResponse.put("exception", "JCo exception occurred while executing " + functionName + " in destination " + btpDestination + ".");
			jsonResponse.put("errormessage", e.getMessage());
			response.setStatus(400);
            response.getWriter().write(jsonResponse.toString());
            return;
		}
		catch (RuntimeException e) {
			jsonResponse.put("exception", "Runtime exception.");
			jsonResponse.put("errormessage", e.getMessage());
			response.setStatus(400);
            response.getWriter().write(jsonResponse.toString());
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