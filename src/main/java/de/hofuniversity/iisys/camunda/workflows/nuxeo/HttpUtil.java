package de.hofuniversity.iisys.camunda.workflows.nuxeo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpUtil
{
    public static String getText(URL url, String user, String password)
        throws IOException
    {
        final StringBuffer buffer = new StringBuffer();
        
        HttpURLConnection connection = (HttpURLConnection)
            url.openConnection();
        
        
        //authentication
        String userpass = user + ":" + password;
        String basicAuth = "Basic " +
            javax.xml.bind.DatatypeConverter.printBase64Binary(
                userpass.getBytes());

        connection.setRequestProperty ("Authorization", basicAuth);
        
        
        

        //read response
        final BufferedReader reader = new BufferedReader(new InputStreamReader(
            connection.getInputStream()));

        String line = reader.readLine();
        while (line != null)
        {
            buffer.append(line);
            line = reader.readLine();
        }

        reader.close();
        
        return buffer.toString();
    }
    
    public static String sendJson(URL url, String method, String data)
        throws IOException
    {
        final StringBuffer buffer = new StringBuffer();
        
        HttpURLConnection connection = (HttpURLConnection)
            url.openConnection();

        //send json data
        connection.setRequestMethod(method);
        
        if(data != null)
        {
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length",
                String.valueOf(data.getBytes().length));

            OutputStreamWriter writer = new OutputStreamWriter(
                connection.getOutputStream());
            writer.write(data);
            writer.flush();
            writer.close();
        }

        //read response
        final BufferedReader reader = new BufferedReader(new InputStreamReader(
            connection.getInputStream()));

        String line = reader.readLine();
        while (line != null)
        {
            buffer.append(line);
            line = reader.readLine();
        }

        reader.close();
        
        return buffer.toString();
    }
}