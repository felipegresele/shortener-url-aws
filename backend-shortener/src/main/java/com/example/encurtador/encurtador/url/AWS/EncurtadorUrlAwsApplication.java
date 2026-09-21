package com.example.encurtador.encurtador.url.AWS;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
public class EncurtadorUrlAwsApplication implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	ObjectMapper objectMapper = new ObjectMapper();

	private final S3Client s3Client = S3Client.builder().build();

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {

		// AJUSTE 1: trata o preflight OPTIONS antes de qualquer outra coisa.
		// Function URL manda o método HTTP em requestContext.http.method
		String httpMethod = extractHttpMethod(input);
		if ("OPTIONS".equalsIgnoreCase(httpMethod)) {
			Map<String, Object> preflightResponse = new HashMap<>();
			preflightResponse.put("statusCode", 200);
			preflightResponse.put("headers", corsHeaders());
			preflightResponse.put("body", "");
			return preflightResponse;
		}

		// AJUSTE 2: todo o processamento agora está dentro de um try/catch geral,
		// pra garantir que QUALQUER erro ainda devolva os headers de CORS
		try {
			String body = input.get("body").toString();

			Map<String, String> bodyMap;
			try {
				bodyMap = objectMapper.readValue(body, Map.class);
			} catch (Exception e) {
				return errorResponse(400, "Error parsing JSON body: " + e.getMessage());
			}

			String originalUrl = bodyMap.get("originalUrl");
			String expirationTime = bodyMap.get("expirationTime");

			if (originalUrl == null || originalUrl.isBlank() || expirationTime == null) {
				return errorResponse(400, "originalUrl e expirationTime são obrigatórios");
			}

			long currentTimeInSeconds = System.currentTimeMillis() / 1000;
			long expirationTimeSeconds;
			try {
				expirationTimeSeconds = currentTimeInSeconds + (Long.parseLong(expirationTime) * 3600);
			} catch (NumberFormatException e) {
				return errorResponse(400, "expirationTime precisa ser um número");
			}

			String shortUrlCode = UUID.randomUUID().toString().substring(0, 8);

			UrlData urlData = new UrlData(originalUrl, expirationTimeSeconds);

			try {
				String urlDataJson = objectMapper.writeValueAsString(urlData);
				PutObjectRequest putRequest = PutObjectRequest.builder()
						.bucket("url-shortner-storage-project")
						.key(shortUrlCode + ".json")
						.build();

				s3Client.putObject(putRequest, RequestBody.fromString(urlDataJson));
			} catch (Exception e) {
				return errorResponse(500, "Error saving URL data to S3: " + e.getMessage());
			}

			Map<String, String> bodyResponse = new HashMap<>();
			bodyResponse.put("code", shortUrlCode);

			Map<String, Object> response = new HashMap<>();
			response.put("statusCode", 200);
			response.put("headers", corsHeaders());
			response.put("body", objectMapper.writeValueAsString(bodyResponse));

			return response;

		} catch (Exception e) {
			// rede de segurança: qualquer exceção não prevista ainda volta com CORS
			return errorResponse(500, "Unexpected error: " + e.getMessage());
		}
	}

	// AJUSTE 3: método auxiliar pra montar erro sempre com CORS
	private Map<String, Object> errorResponse(int statusCode, String message) {
		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", statusCode);
		response.put("headers", corsHeaders());
		response.put("body", "{\"error\":\"" + message.replace("\"", "'") + "\"}");
		return response;
	}

	// AJUSTE 4: centraliza os headers de CORS num único lugar
	private Map<String, String> corsHeaders() {
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Access-Control-Allow-Origin", "*");
		headers.put("Access-Control-Allow-Methods", "POST, OPTIONS");
		headers.put("Access-Control-Allow-Headers", "Content-Type");
		return headers;
	}

	// AJUSTE 5: extrai o método HTTP do payload que a Function URL manda
	@SuppressWarnings("unchecked")
	private String extractHttpMethod(Map<String, Object> input) {
		try {
			Map<String, Object> requestContext = (Map<String, Object>) input.get("requestContext");
			Map<String, Object> http = (Map<String, Object>) requestContext.get("http");
			return (String) http.get("method");
		} catch (Exception e) {
			return "";
		}
	}
}