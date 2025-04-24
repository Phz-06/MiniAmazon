package edu.ece568.MiniAmazon.dto.response;

import lombok.Data;

@Data
public class JwtResponse {
    private String token;

    private String type = "Bearer";

    public JwtResponse(String token) {
        this.token = token;
    }
}
