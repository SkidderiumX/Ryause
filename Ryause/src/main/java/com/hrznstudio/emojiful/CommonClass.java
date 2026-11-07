package com.hrznstudio.emojiful;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public class CommonClass {

    public static String readStringFromURL(String requestURL) {
        try {
            URLConnection connection = new URL(requestURL).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Ryause");
            try (InputStream stream = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load emoji metadata from " + requestURL, e);
        }
    }

    @SuppressWarnings("deprecation")
    public static JsonElement readJsonFromUrl(String url) {
        String jsonText = readStringFromURL(url);
        return new JsonParser().parse(jsonText);
    }

}
