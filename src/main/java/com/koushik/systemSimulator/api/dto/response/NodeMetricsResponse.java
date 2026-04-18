package com.koushik.systemSimulator.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeMetricsResponse {

	private long processedRequests;
	private long droppedRequests;
}
