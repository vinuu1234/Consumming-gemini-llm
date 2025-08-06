package com.example.demo.service;

import java.time.Duration;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.Exceptions.GeminiApiException;
import com.example.demo.Exceptions.GeminiContentException;
import com.example.demo.Exceptions.GeminiSafetyException;
import com.example.demo.Exceptions.InvalidPromptException;
import com.example.demo.dto.Candidate;
import com.example.demo.dto.Content;
import com.example.demo.dto.GeminiRequest;
import com.example.demo.dto.GeminiResponse;
import com.example.demo.dto.Part;

@Service
public class GeminiService {
    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    
    private final String apiUrl;
    private final RestTemplate restTemplate;
    
    private String apiKey;

    public GeminiService(
        @Value("${gemini.api.key}") String apiKey,
        RestTemplateBuilder builder
    ) {
        this.apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey;
        this.restTemplate = builder
                        .build();
    }

    public String getGeminiResponse(String prompt) {
        validatePrompt(prompt); // Validate prompt first
        
        GeminiRequest request = buildRequest(prompt);
        HttpEntity<GeminiRequest> entity = new HttpEntity<>(request);
        
        try {
            ResponseEntity<GeminiResponse> response = restTemplate.postForEntity(
                apiUrl, entity, GeminiResponse.class
            );
            validateApiResponse(response);
            return extractResponseText(response.getBody());
            
        } catch (RestClientException e) {
            String errorMsg = "API communication failed: " + e.getMessage();
            log.error(errorMsg, e);
            throw new GeminiApiException(errorMsg, e);
        }
    }

    private void validatePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            String errorMsg = "Prompt cannot be empty";
            log.warn(errorMsg);
            throw new InvalidPromptException(errorMsg);
        }
    }

    private GeminiRequest buildRequest(String prompt) {
        GeminiRequest request = new GeminiRequest();
        Content content = new Content();
        content.setRole("user");
        
        Part part = new Part();
        part.setText(prompt);
        content.setParts(Collections.singletonList(part));
        
        request.setContents(Collections.singletonList(content));
        return request;
    }

    private void validateApiResponse(ResponseEntity<GeminiResponse> response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            String errorMsg = "API returned HTTP " + response.getStatusCode();
            log.error(errorMsg);
            throw new GeminiApiException(errorMsg);
        }
        if (response.getBody() == null) {
            String errorMsg = "No response body from API";
            log.error(errorMsg);
            throw new GeminiApiException(errorMsg);
        }
    }

    private String extractResponseText(GeminiResponse response) {
        if (response.getCandidates() == null || response.getCandidates().isEmpty()) {
            String errorMsg = "No response candidates";
            log.warn(errorMsg);
            throw new GeminiContentException(errorMsg);
        }

        Candidate candidate = response.getCandidates().get(0);
        if (candidate == null) {
            String errorMsg = "Empty candidate data";
            log.warn(errorMsg);
            throw new GeminiContentException(errorMsg);
        }

        if ("SAFETY".equals(candidate.getFinishReason())) {
            String errorMsg = "Response blocked by safety filters";
            log.warn(errorMsg);
            throw new GeminiSafetyException(errorMsg);
        }

        Content content = candidate.getContent();
        if (content == null) {
            String errorMsg = "Missing content in candidate";
            log.warn(errorMsg);
            throw new GeminiContentException(errorMsg);
        }

        if (content.getParts() == null || content.getParts().isEmpty()) {
            String errorMsg = "No content parts available";
            log.warn(errorMsg);
            throw new GeminiContentException(errorMsg);
        }

        Part part = content.getParts().get(0);
        if (part == null || part.getText() == null) {
            String errorMsg = "Missing text in response";
            log.warn(errorMsg);
            throw new GeminiContentException(errorMsg);
        }

        return part.getText();
    }
}