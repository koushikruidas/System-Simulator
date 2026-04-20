package com.koushik.systemSimulator.application.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RequestSamples {
    private final List<RequestTrace> first;
    private final List<RequestTrace> slowest;
}
