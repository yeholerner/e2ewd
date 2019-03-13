package tests;

import file_ops.ConfigFile;
import logging.Logger;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class SisenseRESTAPIClient{

    private static final Logger logger = Logger.getInstance();
    private static final ConfigFile configFile = ConfigFile.getInstance();
    private HttpClient client;
    private HttpPost post;
    private String uri;
    private boolean isCallSuccessful;
    private String elastiCubeName;

    public SisenseRESTAPIClient(String elastiCubeName) throws JSONException {

        this.elastiCubeName = elastiCubeName;

        setUri();
        initializeClient(createJAQL(elastiCubeName));

    }

    private void initializeClient(JSONObject jaql){

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(configFile.getRequestTimeoutInSeconds() * 1000)
                .setConnectTimeout(configFile.getRequestTimeoutInSeconds() * 1000)
                .build();

        client = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
        post = new HttpPost(uri);
        post.addHeader("authorization", "Bearer " + configFile.getToken());
        post.setEntity(new StringEntity(jaql.toString(), ContentType.APPLICATION_JSON));

    }

    public void exeecuteQuery() throws IOException {

        HttpResponse response = client.execute(post);
        parseResponse(response);

    }

    private void parseResponse(HttpResponse response){

        HttpEntity entity = response.getEntity();
        int responseCode = response.getStatusLine().getStatusCode();

        if (entity != null){

            try(InputStream inputStream = entity.getContent()){

                String res = new BufferedReader(new InputStreamReader(inputStream))
                        .lines()
                        .collect(Collectors.joining("\n"));

                if (responseCode == 200){

                    JSONObject responseObject = new JSONObject(res);
                    JSONObject valuesArray = (JSONObject) responseObject.getJSONArray("values").get(0);
                    int count = valuesArray.getInt("data");

                    // Check if result is larger than 0
                    if (count > 0) {
                        setCallSuccessful(true);
                    }

                    } else {
                    logger.write("[SisenseRESTAPIClient.queryTableIsSuccessful] query failed for " +
                            elastiCubeName + " with code " +
                            responseCode + " ,response: " +
                            res);
                    setCallSuccessful(false);
                    }
                }
            catch (IOException | JSONException e){
                logger.write("[SisenseRESTAPIClient.queryTableIsSuccessful] query failed for " +
                        elastiCubeName + " with code " +
                        responseCode + " , error: " +
                        e.getMessage());
                setCallSuccessful(false);
            }
        }
    }

    private void setCallSuccessful(boolean callSuccessful) {
        isCallSuccessful = callSuccessful;
    }

    public boolean isCallSuccessful() {
        return isCallSuccessful;
    }

    private void setUri() {

        if (configFile.getPort() != 443){
            uri = configFile.getProtocol() +
                    "://" + configFile.getHost() + ":" +
                    configFile.getPort() + "/api/datasources/x/jaql";
        }
        else {
            uri = configFile.getProtocol() +
                    "://" + configFile.getHost() + "/api/datasources/x/jaql";
        }

    }
    private static JSONObject createJAQL(String elastiCube) throws JSONException {

        JSONObject rootObject = new JSONObject();
        JSONArray metadataArray = new JSONArray();

        JSONObject jaqlObject = new JSONObject();

        rootObject.put("datasource", elastiCube);
        jaqlObject.put("formula", "1");
        metadataArray.put(jaqlObject);
        rootObject.put("metadata", metadataArray);

        return rootObject;
    }

}
