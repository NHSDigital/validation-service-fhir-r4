package com.example.fhirvalidator.shared;

import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.crypto.MacProvider;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Key;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;


public class AuthorisationClient implements IClientInterceptor {

    private String accessToken;
    private String refreshToken;
    private Date expires;

    private String clientId;
    private String clientSecret;

    public AuthorisationClient(String clientId, String clientSecret) throws Exception {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        String targetURL="https://ontology.nhs.uk/authorisation/auth/realms/nhs-digital-terminology/protocol/openid-connect/token";
        String urlParameters="grant_type=client_credentials&client_id="+clientId+"&client_secret="+clientSecret;
        performAuth(targetURL,urlParameters);
    }

    private void setClaims() {
        String[] split_string = accessToken.split("\\.");

        String base64EncodedBody = split_string[1];

        Base64.Decoder decoder = Base64.getDecoder();
        //String header = new String(decoder.decode(base64EncodedHeader));

        String body = new String(decoder.decode(base64EncodedBody));
        JSONObject obj = new JSONObject(body);
        Integer expire = obj.getInt("exp");
        expires = Date.from(Instant.ofEpochSecond(expire));

    }

    private Boolean isExpired() {
        if (new Date().before(expires)) return false;
        return true;
    }

    private void doRefresh() throws Exception {
        String targetURL="https://ontology.nhs.uk/authorisation/auth/realms/nhs-digital-terminology/protocol/openid-connect/token";

        String urlParameters="grant_type=client_credentials&client_id="+clientId+"&client_secret="+clientSecret+"&refresh_token="+refreshToken;
        performAuth(targetURL,urlParameters);
    }

    private void performAuth(String targetURL, String urlParameters) throws Exception {
        HttpURLConnection connection = null;
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
            this.setClaims();
            refreshToken = obj.getString("refresh_token");
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void interceptRequest(IHttpRequest theRequest)  {
        if (isExpired()) {
            try {
                doRefresh();
            }
            catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }
        theRequest.addHeader("Authorization", "Bearer " + this.accessToken);
    }

    public void interceptResponse(IHttpResponse theResponse) {
    }
}
