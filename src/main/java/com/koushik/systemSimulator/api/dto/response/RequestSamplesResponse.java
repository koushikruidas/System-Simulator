package com.koushik.systemSimulator.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RequestSamplesResponse {
    private List<RequestTraceResponse> first;
    private List<RequestTraceResponse> slowest;
}
