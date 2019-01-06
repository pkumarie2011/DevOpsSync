import java.util.ArrayList;
import java.util.Base64;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
public class Main {
	private static final String APPLICATION_NAME = "DevOpsSync";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static HashMap<Integer, Double> hm = new HashMap<Integer, Double>();

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = Main.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        
        // Build flow and trigger user authorization request.
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }
    
    public static void main(String... args) throws IOException, GeneralSecurityException {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        final String spreadsheetId = "<Put your Google Sheet ID here>";
        final String range = "Sheet1!A:D";
        Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) 
        {
            System.out.println("No data found.");
        } 
        else 
        {
        	values.remove(0);
            for (List row : values) 
            {
            	if(row.get(2).toString().equals("1"))
            	{
            		ArrayList<Integer> workItemNumbers= getItemNumbers(StringEscapeUtils.escapeJava(row.get(0).toString()));
            		System.out.println("\n\nworkItemNumbers"+workItemNumbers);
            		for(Integer issueNumber : workItemNumbers)
            		 {
            			remainingWorkUpdate(issueNumber);
            		 } 
            	}
            }
            System.out.println("\n\nUpdate Completed");
        }
    }
    private static ArrayList<Integer> getItemNumbers(String areaPath)
	{
		ArrayList<Integer> workItemNumbers= new ArrayList<Integer>();
		try 
		{
		HttpClient httpClient = HttpClientBuilder.create().build();
		String basicAuth = "Basic " + new String(Base64.getEncoder().encode(":<Basic Auth Token>".getBytes()));	
		HttpPost requestWorkItem = new HttpPost("https://dev.azure.com/domoreexp/MSTeams/_apis/wit/wiql?api-version=4.1");
		requestWorkItem.setHeader("Authorization", basicAuth);
		requestWorkItem.setHeader("content-type", "application/json");
		String jsonWorkItems = "{\"query\": \"Select [System.Id] From WorkItems Where [System.AreaPath] = '"+areaPath+"'\"}";
		StringEntity JSONentity = new StringEntity(jsonWorkItems, "UTF8");
		 
		JSONentity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
		requestWorkItem.setEntity(JSONentity);
		
		HttpResponse response = httpClient.execute(requestWorkItem);
		if(response.getStatusLine().getStatusCode()==200)
			 {
			 HttpEntity resonseEntity = response.getEntity();
			 String responseString = EntityUtils.toString(resonseEntity, "UTF-8");
			 JSONArray workItemsArray = new JSONObject(responseString).getJSONArray("workItems");
			 for(int i=0; i<workItemsArray.length(); i++)
			 	{
				 int workItemID = workItemsArray.getJSONObject(i).getInt("id");
				 workItemNumbers.add(workItemID);
			 	} 
			 }
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		} 
		return workItemNumbers;
	}
	private static double remainingWorkUpdate(int workItemNumber) {
		if(hm.containsKey(workItemNumber))
		{
			System.out.println("Work Item: "+workItemNumber+" already exists in HashMap");
			return(hm.get(workItemNumber));
		}
		double workRemaining = 0.0;
		try {
			 
			 HttpClient httpClient = HttpClientBuilder.create().build();
			 
			 String basicAuth = "Basic " + new String(Base64.getEncoder().encode(":<Basic Auth Token>".getBytes()));
			 
			 HttpResponse response=null;
			 HttpGet requestRelations = new HttpGet("https://dev.azure.com/domoreexp/MSTeams/_apis/wit/workitems/"+workItemNumber+"?api-version=4.1&$expand=all");
			 requestRelations.setHeader("Authorization", basicAuth);
			 requestRelations.setHeader("content-type", "application/json");
			 
			 response = httpClient.execute(requestRelations);
			 
			 HttpEntity resonseEntity = response.getEntity();
			 String responseString = EntityUtils.toString(resonseEntity, "UTF-8");
			 //System.out.println(responseString);
			 JSONObject jsonObject = new JSONObject(responseString);
			 double currentRemainingWork = 0.0;
			 if(jsonObject.getJSONObject("fields").has("Microsoft.VSTS.Scheduling.RemainingWork"))
				 currentRemainingWork=jsonObject.getJSONObject("fields").getDouble("Microsoft.VSTS.Scheduling.RemainingWork");
			 JSONArray relationsArray = jsonObject.optJSONArray("relations");
			 boolean flagNodeHasChild = false;
				 
			 for (int i = 0; relationsArray!=null && i < relationsArray.length(); i++) 
		  		{
		  		    JSONObject jsonnode = relationsArray.getJSONObject(i);
		  		    String rel = jsonnode.getString("rel");
		  		    if(rel.equals("System.LinkTypes.Hierarchy-Forward"))
		  		    {
		  		    flagNodeHasChild=true;
		  		    String url = jsonnode.getString("url");
		  		    int childWorkItemNUmber = Integer.parseInt(url.substring(url.indexOf("workItems")+10));
		  		    workRemaining=workRemaining+remainingWorkUpdate(childWorkItemNUmber);
		  		    }   
		  		}
			 if(flagNodeHasChild==false || currentRemainingWork==workRemaining && workRemaining!=0.0)
				{
				 if(!hm.containsKey(workItemNumber))
					{
						hm.put(workItemNumber, currentRemainingWork);
					}
				 return currentRemainingWork;
				}
				else
				{
					 HttpPatch httpPatch = new HttpPatch("https://dev.azure.com/domoreexp/MSTeams/_apis/wit/workitems/"+workItemNumber+"?api-version=4.1");
					 String jsonPatch = "[{\"op\": \"replace\",\"path\": \"/fields/Microsoft.VSTS.Scheduling.RemainingWork\", \"value\": "+workRemaining+"}]";
						 	//	+"{\"op\": \"add\",\"path\": \"/fields/System.History\", \"value\": \"<div>Updating Remaining Work as "+workRemaining+" (Sum of Remaining work from Child Work Items).</div>\"}]";
					 System.out.println("\n\n\nWorkItemNumber = "+workItemNumber+" JSON Patch = "+jsonPatch);
					 StringEntity JSONPatchentity = new StringEntity(jsonPatch, "UTF8");
					 httpPatch.setHeader("Authorization", basicAuth);
					 httpPatch.setHeader("content-type", "application/json-patch+json");
					 JSONPatchentity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
					 httpPatch.setEntity(JSONPatchentity);
					 HttpResponse patchResponse = httpClient.execute(httpPatch);
					 patchResponse.getEntity().getContent().close();
				}
			 response.getEntity().getContent().close();
			 }     
		  catch (Exception e) {
		     e.printStackTrace();
	      }
		if(!hm.containsKey(workItemNumber))
		{
			hm.put(workItemNumber, workRemaining);
		}
		return workRemaining;
	}	
}