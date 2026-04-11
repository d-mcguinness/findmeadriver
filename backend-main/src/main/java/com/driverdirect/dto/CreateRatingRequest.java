package com.driverdirect.dto;

import lombok.Data;

@Data
public class CreateRatingRequest {
    private Integer score;
    private String comment;
}
