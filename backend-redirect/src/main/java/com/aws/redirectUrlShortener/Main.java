package com.aws.redirectUrlShortener;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Main implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final S3Client s3Client = S3Client.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {

        String pathParameters = input.get("rawPath").toString();
        String shortUrlCode = pathParameters.replace("/", "");

        if (shortUrlCode == null || shortUrlCode.isEmpty()) {
            throw new RuntimeException("Invalid input: 'shortUrlCode' is required");
        }

        GetObjectRequest get = GetObjectRequest.builder()
                .bucket("url-shortner-storage-project")
                .key(shortUrlCode + ".json" )
                .build();

        InputStream s3ObjectStream;

        try {
            s3ObjectStream = s3Client.getObject(get);
        } catch (Exception e) {
            // AJUSTE: antes isso estourava RuntimeException e virava erro 500 genérico.
            // Melhor devolver 404 tratado pro front conseguir mostrar "link não encontrado".
            Map<String, Object> notFound = new HashMap<>();
            notFound.put("statusCode", 404);
            notFound.put("headers", corsHeaders());
            notFound.put("body", "Short URL not found.");
            return notFound;
        }

        UrlData urlDataResponse;

        try {
            urlDataResponse = objectMapper.readValue(s3ObjectStream, UrlData.class);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing URL data: " + e.getMessage(), e);
        }

        long currentTimeInSeconds = System.currentTimeMillis() / 1000;

        Map<String, Object> response = new HashMap<>();

        if (urlDataResponse.getExpirationTime() < currentTimeInSeconds ) {
            response.put("statusCode", 410);
            response.put("headers", corsHeaders()); // AJUSTE: CORS aqui também
            response.put("body", "This URL has expired.");
            return response;
        }

        response.put("statusCode", 302);
        Map<String, String> headers = corsHeaders();
        headers.put("Location", urlDataResponse.getOriginalUrl());
        response.put("headers", headers);

        return response;
    }

    // AJUSTE: método auxiliar pra não repetir os headers em cada retorno
    private Map<String, String> corsHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, OPTIONS");
        return headers;
    }
}