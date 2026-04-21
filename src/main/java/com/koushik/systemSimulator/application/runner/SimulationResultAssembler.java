package com.koushik.systemSimulator.application.runner;

import com.koushik.systemSimulator.application.model.FlowBreakdown;
import com.koushik.systemSimulator.application.model.FlowGroup;
import com.koushik.systemSimulator.application.model.NodeConfig;
import com.koushik.systemSimulator.application.model.NodeLatencyBreakdown;
import com.koushik.systemSimulator.application.model.NodeMetrics;
import com.koushik.systemSimulator.application.model.RequestOutcome;
import com.koushik.systemSimulator.application.model.RequestSamples;
import com.koushik.systemSimulator.application.model.RequestTrace;
import com.koushik.systemSimulator.application.model.SimulationCommand;
import com.koushik.systemSimulator.application.model.SimulationResult;
import com.koushik.systemSimulator.application.model.TimeSeriesPoint;
import com.koushik.systemSimulator.simulation.state.RequestRuntimeState;
import com.koushik.systemSimulator.simulation.engine.SimulationReport;
import com.koushik.systemSimulator.simulation.engine.TimeStepReport;
import com.koushik.systemSimulator.simulation.batch.TickMetrics;
import com.koushik.systemSimulator.simulation.metrics.SimulationMetrics;
import com.koushik.systemSimulator.simulation.model.EventType;
import com.koushik.systemSimulator.simulation.model.SimulationEvent;
import com.koushik.systemSimulator.simulation.state.RequestStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingLong;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

@Component
public class SimulationResultAssembler {

    public SimulationResult assemble(SimulationCommand command, SimulationReport engineReport) {
        SimulationMetrics metrics = engineReport.metrics();

        Map<String, NodeMetrics> nodeMetrics = new LinkedHashMap<>();
        for (NodeConfig nodeConfig : command.getNodes()) {
            nodeMetrics.put(
                    nodeConfig.getNodeId(),
                    NodeMetrics.builder()
                            .processedRequests(metrics.nodeProcessedCounts().getOrDefault(nodeConfig.getNodeId(), 0L))
                            .droppedRequests(metrics.nodeDroppedCounts().getOrDefault(nodeConfig.getNodeId(), 0L))
                            .build()
            );
        }

        Map<String, Long> latencyByNodeId = command.getNodes().stream()
                .collect(Collectors.toMap(NodeConfig::getNodeId, NodeConfig::getLatency));

        Map<String, Map<String, Long>> arrivals = new HashMap<>();
        Map<String, Map<String, Long>> completions = new HashMap<>();
        for (SimulationEvent event : engineReport.processedEvents()) {
            String reqId = event.request().requestId();
            String nodeId = event.targetNodeId();
            if (event.eventType() == EventType.REQUEST_ARRIVED) {
                arrivals.computeIfAbsent(reqId, k -> new HashMap<>()).put(nodeId, event.timestamp());
            } else if (event.eventType() == EventType.PROCESSING_COMPLETED) {
                completions.computeIfAbsent(reqId, k -> new HashMap<>()).put(nodeId, event.timestamp());
            }
        }

        List<RequestTrace> traces = engineReport.requestStates().stream()
                .map(state -> {
                    List<String> path = deduplicate(state.hopHistory());
                    String reqId = state.request().requestId();
                    long totalLatency = state.completedAt() != null
                            ? state.completedAt() - state.request().createdAt() : 0L;
                    List<NodeLatencyBreakdown> breakdown = buildBreakdown(
                            path,
                            arrivals.getOrDefault(reqId, Map.of()),
                            completions.getOrDefault(reqId, Map.of()),
                            latencyByNodeId
                    );
                    return RequestTrace.builder()
                            .requestId(reqId)
                            .path(path)
                            .outcome(state.status() == RequestStatus.COMPLETED
                                    ? RequestOutcome.COMPLETED : RequestOutcome.DROPPED)
                            .totalLatency(totalLatency)
                            .breakdown(breakdown)
                            .build();
                })
                .toList();

        List<FlowGroup> flowGroups = buildFlowGroups(traces);
        Map<Long, Long> latencyDistribution = buildLatencyDistribution(traces);
        RequestSamples samples = buildSamples(traces);
        List<String> nodeIds = command.getNodes().stream().map(NodeConfig::getNodeId).toList();
        List<TimeSeriesPoint> timeSeries = buildTimeSeries(
                engineReport.requestStates(), arrivals, completions, latencyByNodeId, nodeIds);

        return SimulationResult.builder()
                .totalRequests(command.getTotalRequests())
                .successfulRequests((int) metrics.completedRequests())
                .failedRequests((int) metrics.droppedRequests())
                .averageLatency(metrics.averageLatency())
                .nodeMetrics(nodeMetrics)
                .flowGroups(flowGroups)
                .latencyDistribution(latencyDistribution)
                .samples(samples)
                .timeSeries(timeSeries)
                .build();
    }

    public SimulationResult assembleFromTimeStep(SimulationCommand command, TimeStepReport report) {
        Map<String, NodeMetrics> nodeMetrics = new LinkedHashMap<>();
        for (NodeConfig nodeConfig : command.getNodes()) {
            String id = nodeConfig.getNodeId();
            nodeMetrics.put(id, NodeMetrics.builder()
                    .processedRequests(report.nodeProcessedCounts().getOrDefault(id, 0L))
                    .droppedRequests(report.nodeDroppedCounts().getOrDefault(id, 0L))
                    .build());
        }

        int duration = command.getSimulationDuration();
        int arrivalRate = command.getArrivalRate();
        List<TimeSeriesPoint> timeSeries = new ArrayList<>();
        for (TickMetrics tm : report.ticks()) {
            int incoming = tm.tick() < duration ? arrivalRate : 0;
            timeSeries.add(TimeSeriesPoint.builder()
                    .time((int) tm.tick())
                    .incoming(incoming)
                    .processed(tm.completed())
                    .dropped(tm.dropped())
                    .queues(Map.copyOf(tm.queueDepths()))
                    .avgLatency(tm.avgLatency())
                    .build());
        }

        double averageLatency = report.totalCompleted() > 0
                ? (double) report.totalLatencySum() / report.totalCompleted() : 0.0;

        return SimulationResult.builder()
                .totalRequests((int) report.totalInjected())
                .successfulRequests((int) report.totalCompleted())
                .failedRequests((int) report.totalDropped())
                .averageLatency(averageLatency)
                .nodeMetrics(nodeMetrics)
                .flowGroups(List.of())
                .latencyDistribution(Map.of())
                .samples(RequestSamples.builder().first(List.of()).slowest(List.of()).build())
                .timeSeries(timeSeries)
                .build();
    }

    private List<FlowGroup> buildFlowGroups(List<RequestTrace> traces) {
        Map<String, List<RequestTrace>> byFlow = traces.stream()
                .collect(groupingBy(t -> String.join(",", t.getPath()) + ":" + t.getOutcome().name()));

        return byFlow.values().stream()
                .sorted(Comparator.comparingInt(g -> -g.size()))
                .map(group -> FlowGroup.builder()
                        .path(group.get(0).getPath())
                        .outcome(group.get(0).getOutcome().name())
                        .count(group.size())
                        .avgLatency(group.stream().mapToLong(RequestTrace::getTotalLatency).average().orElse(0))
                        .breakdown(buildFlowBreakdown(group))
                        .build())
                .toList();
    }

    private List<FlowBreakdown> buildFlowBreakdown(List<RequestTrace> group) {
        List<String> path = group.get(0).getPath();
        return path.stream()
                .map(nodeId -> {
                    double avgQueue = group.stream()
                            .flatMap(t -> t.getBreakdown().stream())
                            .filter(b -> b.getNodeId().equals(nodeId))
                            .mapToLong(NodeLatencyBreakdown::getQueueTime)
                            .average().orElse(0);
                    double avgProc = group.stream()
                            .flatMap(t -> t.getBreakdown().stream())
                            .filter(b -> b.getNodeId().equals(nodeId))
                            .mapToLong(NodeLatencyBreakdown::getProcessingTime)
                            .average().orElse(0);
                    return FlowBreakdown.builder()
                            .nodeId(nodeId)
                            .avgQueueTime(avgQueue)
                            .avgProcessingTime(avgProc)
                            .build();
                })
                .toList();
    }

    private Map<Long, Long> buildLatencyDistribution(List<RequestTrace> traces) {
        return traces.stream().collect(groupingBy(RequestTrace::getTotalLatency, counting()));
    }

    private RequestSamples buildSamples(List<RequestTrace> traces) {
        List<RequestTrace> first = traces.stream().limit(5).toList();
        Set<String> firstIds = new HashSet<>(first.stream().map(RequestTrace::getRequestId).toList());
        List<RequestTrace> slowest = traces.stream()
                .filter(t -> !firstIds.contains(t.getRequestId()))
                .sorted(comparingLong(RequestTrace::getTotalLatency).reversed())
                .limit(5)
                .toList();
        return RequestSamples.builder()
                .first(first)
                .slowest(slowest)
                .build();
    }

    private List<NodeLatencyBreakdown> buildBreakdown(
            List<String> path,
            Map<String, Long> nodeArrivals,
            Map<String, Long> nodeCompletions,
            Map<String, Long> latencyByNodeId) {

        return path.stream()
                .map(nodeId -> {
                    long arrivalTime = nodeArrivals.getOrDefault(nodeId, 0L);
                    Long completionTime = nodeCompletions.get(nodeId);
                    long nodeLatency = latencyByNodeId.getOrDefault(nodeId, 0L);

                    long processingStartTime = completionTime != null
                            ? completionTime - nodeLatency : arrivalTime;
                    long queueTime = processingStartTime - arrivalTime;
                    long processingTime = completionTime != null ? nodeLatency : 0L;

                    return NodeLatencyBreakdown.builder()
                            .nodeId(nodeId)
                            .queueTime(queueTime)
                            .processingTime(processingTime)
                            .build();
                })
                .toList();
    }

    private List<String> deduplicate(List<String> hops) {
        List<String> result = new ArrayList<>();
        for (String hop : hops) {
            if (result.isEmpty() || !result.get(result.size() - 1).equals(hop)) {
                result.add(hop);
            }
        }
        return List.copyOf(result);
    }

    private List<TimeSeriesPoint> buildTimeSeries(
            List<RequestRuntimeState> states,
            Map<String, Map<String, Long>> arrivals,
            Map<String, Map<String, Long>> completions,
            Map<String, Long> latencyByNodeId,
            List<String> nodeIds) {

        long maxTime = states.stream()
                .filter(s -> s.completedAt() != null)
                .mapToLong(RequestRuntimeState::completedAt)
                .max().orElse(0);
        if (maxTime == 0) return List.of();

        long bucketSize = Math.max(1, (maxTime + 1) / 100);
        int numBuckets = (int) (maxTime / bucketSize) + 1;
        List<TimeSeriesPoint> series = new ArrayList<>();

        for (int b = 0; b < numBuckets; b++) {
            long bucketStart = (long) b * bucketSize;
            long bucketEnd = bucketStart + bucketSize;

            int incoming = (int) states.stream()
                    .filter(s -> s.request().createdAt() >= bucketStart && s.request().createdAt() < bucketEnd)
                    .count();

            int processed = (int) states.stream()
                    .filter(s -> s.status() == RequestStatus.COMPLETED
                            && s.completedAt() != null
                            && s.completedAt() >= bucketStart && s.completedAt() < bucketEnd)
                    .count();

            int dropped = (int) states.stream()
                    .filter(s -> s.status() == RequestStatus.DROPPED
                            && s.completedAt() != null
                            && s.completedAt() >= bucketStart && s.completedAt() < bucketEnd)
                    .count();

            double avgLatency = states.stream()
                    .filter(s -> s.status() == RequestStatus.COMPLETED
                            && s.completedAt() != null
                            && s.completedAt() >= bucketStart && s.completedAt() < bucketEnd)
                    .mapToLong(s -> s.completedAt() - s.request().createdAt())
                    .average().orElse(0);

            Map<String, Integer> queues = new LinkedHashMap<>();
            for (String nodeId : nodeIds) {
                long nodeLatency = latencyByNodeId.getOrDefault(nodeId, 0L);
                int qSize = (int) states.stream().filter(s -> {
                    Long arrTime = arrivals.getOrDefault(s.request().requestId(), Map.of()).get(nodeId);
                    if (arrTime == null || arrTime > bucketStart) return false;
                    Long compTime = completions.getOrDefault(s.request().requestId(), Map.of()).get(nodeId);
                    if (compTime == null) return true;
                    return (compTime - nodeLatency) > bucketStart;
                }).count();
                queues.put(nodeId, qSize);
            }

            series.add(TimeSeriesPoint.builder()
                    .time(b).incoming(incoming).processed(processed).dropped(dropped)
                    .queues(Map.copyOf(queues)).avgLatency(avgLatency)
                    .build());
        }
        return series;
    }
}
