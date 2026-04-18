package com.koushik.systemSimulator.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

	private String code;
	private String message;
	private String path;
	private Instant timestamp;
	private List<FieldViolationResponse> fieldErrors;
}
