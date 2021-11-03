package com.example.fhirvalidator.shared;

import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class AuthorisationClient implements IClientInterceptor {

    private String accessToken;
    private String refreshToken;

    public AuthorisationClient(String clientId, String clientSecret) throws Exception {
        String targetURL="https://ontology.nhs.uk/authorisation/auth/realms/nhs-digital-terminology/protocol/openid-connect/token";
        HttpURLConnection connection = null;
        String urlParameters="grant_type=client_credentials&client_id="+clientId+"&client_secret="+clientSecret;
        try {
            //Create connection
            URL url = new URL(targetURL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");

            connection.setRequestProperty("Content-Length",
                    Integer.toString(urlParameters.getBytes().length));
            connection.setRequestProperty("Content-Language", "en-US");

            connection.setUseCaches(false);
            connection.setDoOutput(true);

            //Send request
            DataOutputStream wr = new DataOutputStream (
                    connection.getOutputStream());
            wr.writeBytes(urlParameters);
            wr.close();

            //Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
            String line;
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();
            JSONObject obj = new JSONObject(response.toString());
            accessToken = obj.getString("access_token");
            refreshToken = obj.getString("refresh_token");
            System.out.println(accessToken);
            System.out.println(refreshToken);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void interceptRequest(IHttpRequest theRequest) {

        theRequest.addHeader("Authorization", "Bearer " + this.accessToken);
    }

    public void interceptResponse(IHttpResponse theResponse) {
    }
}
