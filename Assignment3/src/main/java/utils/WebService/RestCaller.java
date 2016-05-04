package utils.WebService;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import server.MulticastServer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lwdthe1 on 3/11/2016.
 */
public class RestCaller
{
    private static final String TAG = "SeenRestCaller";
    private static final String REST_API_URL = "https://c445a3.herokuapp.com";

    public static int postLog(MulticastServer server, String logIndex, String log) throws URISyntaxException, HttpException, IOException
    {
        log = URLEncoder.encode(log, "UTF-8");

        // Create a new HttpClient and Post Header
        HttpClient httpClient = new DefaultHttpClient();
        System.out.println("logIndex = " + logIndex);
        String restUri = REST_API_URL + "/logs/" + server.getId() + "/" + logIndex + "/" + log;

        HttpPost httpPost = new HttpPost(restUri);
        httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");

        // Add your data
        ArrayList<NameValuePair> postParameters = new ArrayList<NameValuePair>();
        postParameters.add(new BasicNameValuePair("serverId", server.getId() + ""));
        postParameters.add(new BasicNameValuePair("logIndex", logIndex));
        postParameters.add(new BasicNameValuePair("log", log));

        httpPost.setEntity(new UrlEncodedFormEntity(postParameters, "UTF-8"));

        // Execute HTTP Post Request
        server.consoleMessage("Sending Post request to " + restUri, 2);

        HttpResponse response = httpClient.execute(httpPost);

        String resultJson = EntityUtils.toString(response.getEntity());
        System.out.println(server.getId() + " RESPONSE JSON: " + resultJson);

        JSONObject resultJsonObject = new JSONObject(resultJson);

        int resultantLogIndex;
        try
        {
            resultantLogIndex = Integer.parseInt(String.valueOf(resultJsonObject.has("index") ? resultJsonObject.get("index") : "-1"));
        }
        catch (Exception e)
        {
            resultantLogIndex = -1;
        }
        return resultantLogIndex;
    }

    public static String getLogByIndex(MulticastServer server, String logIndex) throws URISyntaxException, HttpException, IOException
    {

        // Create a new HttpClient and Post Header
        HttpClient httpClient = new DefaultHttpClient();
        String restUri = REST_API_URL + "/logs/" + server.getId() + "/" + logIndex;

        HttpGet httpGet = new HttpGet(restUri);

        // Execute HTTP Post Request
        server.consoleMessage("Sending Get request to " + restUri, 2);

        HttpResponse response = httpClient.execute(httpGet);

        String resultJson = EntityUtils.toString(response.getEntity());
        System.out.println(server.getId() + " RESPONSE JSON: " + resultJson);

        String resultantLogIndex = "";
        try
        {
            if (!resultJson.equals(""))
            {

                System.out.println("resultJson = " + resultJson);
                JSONObject resultJsonObject = new JSONObject(resultJson);

                if (resultJsonObject.has("error"))
                {
                    server.consoleError(resultJsonObject.getString("errorMessage"), 2);
                }
                else
                {
                    resultantLogIndex = String.valueOf(resultJsonObject.has("data") ? resultJsonObject.get("data") : "");
                }
            }
            return resultantLogIndex;
        }
        catch (JSONException e)
        {
            server.consoleError("resultJson = " + resultJson,2);
        }
        return resultantLogIndex;
    }

    public static List<String> getAllLogs(MulticastServer server) throws URISyntaxException, HttpException, IOException
    {
        // Create a new HttpClient and Post Header
        HttpClient httpClient = new DefaultHttpClient();
        String restUri = REST_API_URL + "/logs/" + server.getId();

        HttpGet httpGet = new HttpGet(restUri);

        // Execute HTTP Post Request
        server.consoleMessage("Sending Get request to " + restUri, 2);

        HttpResponse response = httpClient.execute(httpGet);

        String resultJson = EntityUtils.toString(response.getEntity());
        System.out.println(server.getId() + " RESPONSE JSON: " + resultJson);

        JSONObject resultJsonObject = new JSONObject(resultJson);

        JSONArray logs = resultJsonObject.has("logs") ? resultJsonObject.getJSONArray("logs") : null;

        List<String> logEntry = new ArrayList<String>();
        if (logs != null)
        {
            for (int i = 0; i < logs.length(); i++)
            {
                JSONObject current = logs.getJSONObject(i);
                String data = current.getString("data");
                int id = current.getInt("index");
                logEntry.add("|" + id + "|" + data + "|");
            }
        }
        else
        {
            server.consoleError("logs was null", 2);
        }

        return logEntry;
    }

    public static boolean deleteAll(MulticastServer server) throws URISyntaxException, HttpException, IOException
    {
        // Create a new HttpClient and Post Header
        HttpClient httpClient = new DefaultHttpClient();
        String restUri = REST_API_URL + "/logs/" + server.getId();

        HttpDelete httpDelete = new HttpDelete(restUri);

        // Execute HTTP Post Request
        server.consoleMessage("Sending Delete request to " + restUri, 2);

        HttpResponse response = httpClient.execute(httpDelete);

//        String resultJson = EntityUtils.toString(response.getEntity());
//        System.out.println(server.getId() + " RESPONSE JSON: " + resultJson);

        return true;
    }

    public static int getLatestIndexNumber(MulticastServer server) throws URISyntaxException, HttpException, IOException
    {
        // Create a new HttpClient and Get Sequence number
        HttpClient httpClient = new DefaultHttpClient();
        String restUri = REST_API_URL + "/logs/" + server.getId() + "/lastIndex";

        HttpGet httpGet = new HttpGet(restUri);

        // Execute HTTP Post Request
        server.consoleMessage("Sending Get request to " + restUri, 2);

        HttpResponse response = httpClient.execute(httpGet);

        String resultJson = EntityUtils.toString(response.getEntity());
        System.out.println(server.getId() + " RESPONSE JSON: " + resultJson);

        return new JSONObject(resultJson).getInt("seq");
    }


}
