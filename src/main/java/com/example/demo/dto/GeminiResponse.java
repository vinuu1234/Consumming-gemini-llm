package com.example.demo.dto;


import java.util.List;

import lombok.Data;

@Data
public class GeminiResponse {
    private List<Candidate> candidates;
}