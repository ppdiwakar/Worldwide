package com.worldwide.paymentgateway.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private String code;
    private String message;
    private List<FieldError> fieldErrors;
    private OffsetDateTime timestamp;

    @Data
    @Builder
    public static class FieldError {
        private String field;
        private String message;
    }
}
