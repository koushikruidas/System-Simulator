package com.koushik.systemSimulator.simulation.engine;

import com.koushik.systemSimulator.simulation.batch.BatchNodeState;
import com.koushik.systemSimulator.simulation.batch.ForwardedBatch;
import com.koushik.systemSimulator.simulation.batch.InFlightBatch;
import com.koushik.systemSimulator.simulation.batch.NodeTickResult;
import com.koushik.systemSimulator.simulation.batch.RequestBatch;
import com.koushik.systemSimulator.simulation.batch.TickMetrics;
import com.koushik.systemSimulator.simulation.model.NodeType;
import com.koushik.systemSimulator.simulation.scenario.NodeDefinition;
import com.koushik.systemSimulator.simulation.scenario.Topology;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TimeStepSimulationEngine {

    private static final long HIT_RATE_SCALE = 1_000_000L;

    private final Topology topology;
    private final List<String> entryNodeIds;
    private final double requestsPerTick;
    private final int durationTicks;
    private double fractionalAccumulator = 0.0;
    private final Map<String, BatchNodeState> stateByNodeId;
    private final Map<String, NodeDefinition> defByNodeId;
    private final List<NodeDefinition> nodeDefinitions;

    public TimeStepSimulationEngine(
            Topology topology,
            String entryNodeId,
            double requestsPerTick,
            int durationTicks
    ) {
        this(topology, List.of(entryNodeId), requestsPerTick, durationTicks);
    }

    public TimeStepSimulationEngine(
            Topology topology,
            List<String> entryNodeIds,
            double requestsPerTick,
            int durationTicks
    ) {
        this.topology = topology;
        this.entryNodeIds = List.copyOf(entryNodeIds);
        this.requestsPerTick = requestsPerTick;
        this.durationTicks = durationTicks;

        this.nodeDefinitions = List.copyOf(topology.nodeDefinitions());
        this.stateByNodeId = new LinkedHashMap<>();
        this.defByNodeId   = new LinkedHashMap<>();
        for (NodeDefinition def : nodeDefinitions) {
            stateByNodeId.put(def.nodeId(), new BatchNodeState());
            defByNodeId.put(def.nodeId(), def);
        }
    }

    public TimeStepReport run() {
        long terminationCap = computeTerminationCap();

        List<TickMetrics> allTicks = new ArrayList<>();
        List<ForwardedBatch> pendingDeliveries = new ArrayList<>();

        long totalInjected   = 0L;
        long totalCompleted  = 0L;
        long totalDropped    = 0L;
        long totalLatencySum = 0L;
        Map<String, Long> nodeProcessedCounts = new LinkedHashMap<>();
        Map<String, Long> nodeDroppedCounts   = new LinkedHashMap<>();
        for (NodeDefinition def : nodeDefinitions) {
            nodeProcessedCounts.put(def.nodeId(), 0L);
            nodeDroppedCounts.put(def.nodeId(), 0L);
        }

        for (long tick = 0; tick < terminationCap; tick++) {

            // ── 1. PROCESS existing work ──────────────────────────────────────
            final long currentTick = tick;
            List<NodeTickResult> results = nodeDefinitions.stream()
                    .map(def -> processNode(def, stateByNodeId.get(def.nodeId()), currentTick))
                    .toList();

            // ── 2. DELIVER to downstream ──────────────────────────────────────
            Map<String, Long> deliverDrops = new LinkedHashMap<>();
            for (NodeTickResult result : results) {
                for (ForwardedBatch fwd : result.forwardedBatches()) {
                    if (fwd.deliveryTick() <= tick + 1) {
                        int d = admitToQueue(fwd.targetNodeId(), fwd.size(), fwd.arrivalTick());
                        if (d > 0) deliverDrops.merge(fwd.targetNodeId(), (long) d, Long::sum);
                    } else {
                        pendingDeliveries.add(fwd);
                    }
                }
            }
            Map<String, Long> pendingDrops = deliverPendingBatches(pendingDeliveries, tick);
            pendingDrops.forEach((k, v) -> deliverDrops.merge(k, v, Long::sum));

            // ── 3. INJECT new arrivals ────────────────────────────────────────
            int tickInjected = 0;
            if (tick < durationTicks) {
                fractionalAccumulator += requestsPerTick;
                int toInject = (int) fractionalAccumulator;
                fractionalAccumulator -= toInject;
                if (toInject > 0) {
                    int n = entryNodeIds.size(), base = toInject / n, rem = toInject % n;
                    for (int i = 0; i < n; i++) {
                        int share = base + (i < rem ? 1 : 0);
                        if (share > 0) {
                            stateByNodeId.get(entryNodeIds.get(i)).inputQueue
                                    .addLast(new RequestBatch(share, tick));
                        }
                    }
                    totalInjected += toInject;
                    tickInjected = toInject;
                }
            }

            // ── 4. SNAPSHOT metrics ───────────────────────────────────────────
            snapshotLoads();

            int tickCompleted   = 0;
            int tickDropped     = 0;
            int tickProcessed   = 0;
            long tickLatencySum = 0L;

            for (NodeTickResult result : results) {
                tickCompleted   += result.completedCount();
                tickDropped     += result.droppedCount();
                tickProcessed   += result.processedCount();
                tickLatencySum  += result.latencySum();

                nodeProcessedCounts.merge(result.nodeId(), (long) result.processedCount(), Long::sum);
                nodeDroppedCounts.merge(result.nodeId(), (long) result.droppedCount(), Long::sum);
            }

            // Drops from DELIVER enforcement (occur after PROCESS, so tracked separately)
            for (Map.Entry<String, Long> e : deliverDrops.entrySet()) {
                tickDropped += e.getValue();
                nodeDroppedCounts.merge(e.getKey(), e.getValue(), Long::sum);
            }

            // Queue depths read from live state AFTER process + deliver + inject
            Map<String, Integer> queueDepths = new LinkedHashMap<>();
            for (NodeDefinition def : nodeDefinitions) {
                BatchNodeState state = stateByNodeId.get(def.nodeId());
                int queued = state.inputQueue.stream().mapToInt(RequestBatch::size).sum();
                int qd = queued + state.inFlight.size()
                        + state.hitInFlight.size() + state.missInFlight.size();
                queueDepths.put(def.nodeId(), qd);
            }

            System.out.printf("[tick %d] processed=%d completed=%d dropped=%d%n",
                    tick, tickProcessed, tickCompleted, tickDropped);

            totalCompleted  += tickCompleted;
            totalDropped    += tickDropped;
            totalLatencySum += tickLatencySum;

            double avgLatency = tickCompleted > 0
                    ? (double) tickLatencySum / tickCompleted : 0.0;
            allTicks.add(new TickMetrics(
                    tick, tickCompleted, tickDropped, Map.copyOf(queueDepths), avgLatency,
                    tickInjected, tickLatencySum));

            validateTickInvariants(tick, totalInjected, totalCompleted, totalDropped, pendingDeliveries);

            if (tick >= durationTicks - 1 && pendingDeliveries.isEmpty() && isIdle()) {
                break;
            }
        }

        long unfinishedRequests = totalInjected - totalCompleted - totalDropped;
        if (unfinishedRequests != 0) {
            System.err.printf(
                    "[TimeStepEngine] WARNING: %d request(s) still in-flight/queued at termination" +
                    " — injected=%d completed=%d dropped=%d%n",
                    unfinishedRequests, totalInjected, totalCompleted, totalDropped);
        }

        return new TimeStepReport(
                totalCompleted, totalDropped, totalLatencySum,
                totalInjected, unfinishedRequests,
                Map.copyOf(nodeProcessedCounts), Map.copyOf(nodeDroppedCounts),
                List.copyOf(allTicks));
    }

    // ── Per-node dispatch ─────────────────────────────────────────────────────

    private NodeTickResult processNode(NodeDefinition def, BatchNodeState state, long tick) {
        return switch (def.nodeType()) {
            case CACHE -> processCacheNode(def, state, tick);
            case LOAD_BALANCER,
                 ROUND_ROBIN_LOAD_BALANCER,
                 LEAST_CONNECTIONS_LOAD_BALANCER -> processLbNode(def, state, tick);
            default -> processProcessingNode(def, state, tick);
        };
    }

    // ── Processing node (SERVICE, DATABASE, PROCESSING_LOAD_BALANCER) ─────────

    private NodeTickResult processProcessingNode(NodeDefinition def, BatchNodeState state, long tick) {
        List<ForwardedBatch> forwarded = new ArrayList<>();
        int completedCount = 0;
        int processedThisTick = 0;
        long latencySum = 0L;

        List<String> downstreams = topology.getDownstreams(def.nodeId());
        boolean isTerminal = def.nodeType() == NodeType.DATABASE || downstreams.isEmpty();

        // ADMIT first so completionTick=tick batches are visible to the drain loop below
        int slotsAvailable = def.capacityPerTick() - state.inFlight.size();
        while (slotsAvailable > 0 && !state.inputQueue.isEmpty()) {
            RequestBatch queued = state.inputQueue.peekFirst();
            int accept = Math.min(queued.size(), slotsAvailable);
            if (accept < queued.size()) {
                state.inputQueue.pollFirst();
                state.inputQueue.addFirst(
                        new RequestBatch(queued.size() - accept, queued.arrivalTick()));
            } else {
                state.inputQueue.pollFirst();
            }
            state.inFlight.add(new InFlightBatch(accept, queued.arrivalTick(), tick));
            slotsAvailable -= accept;
        }

        // DRAIN: completionTick=tick satisfies tick<=tick, so newly admitted batches drain same tick
        Iterator<InFlightBatch> it = state.inFlight.iterator();
        while (it.hasNext()) {
            InFlightBatch batch = it.next();
            if (batch.completionTick() <= tick) {
                it.remove();
                state.processedCount += batch.size();
                processedThisTick += batch.size();

                if (isTerminal) {
                    completedCount += batch.size();
                    latencySum += (long) batch.size() * (tick - batch.arrivalTick());
                } else {
                    forwarded.add(new ForwardedBatch(
                            batch.size(), batch.arrivalTick(), downstreams.get(0),
                            batch.completionTick() + def.processingLatencyTicks()));
                }
            }
        }

        // Drop excess requests until total queued request count <= queueLimit.
        // Split the tail batch if only a partial drop is needed to exactly honour the limit.
        int droppedCount = 0;
        int totalQueued = state.inputQueue.stream().mapToInt(RequestBatch::size).sum();
        while (totalQueued > def.queueLimit()) {
            RequestBatch last = state.inputQueue.peekLast();
            if (last == null) break;
            int excess = totalQueued - def.queueLimit();
            if (excess >= last.size()) {
                state.inputQueue.pollLast();
                droppedCount += last.size();
                state.droppedCount += last.size();
                totalQueued -= last.size();
            } else {
                state.inputQueue.pollLast();
                state.inputQueue.addLast(new RequestBatch(last.size() - excess, last.arrivalTick()));
                droppedCount += excess;
                state.droppedCount += excess;
                totalQueued -= excess;
            }
        }

        int queueDepth = totalQueued + state.inFlight.size();
        return new NodeTickResult(
                def.nodeId(), completedCount, processedThisTick, droppedCount, latencySum, forwarded, queueDepth);
    }

    // ── Cache node ────────────────────────────────────────────────────────────

    private NodeTickResult processCacheNode(NodeDefinition def, BatchNodeState state, long tick) {
        long hitRateScaled = Math.round(def.hitRate() * HIT_RATE_SCALE);
        List<ForwardedBatch> forwarded = new ArrayList<>();
        int completedCount = 0;
        int processedThisTick = 0;
        long latencySum = 0L;

        List<String> downstreams = topology.getDownstreams(def.nodeId());

        // ADMIT first so completionTick=tick batches are visible to the drain loops below
        int totalInFlight = state.hitInFlight.size() + state.missInFlight.size();
        int slotsAvailable = def.capacityPerTick() - totalInFlight;
        while (slotsAvailable > 0 && !state.inputQueue.isEmpty()) {
            RequestBatch queued = state.inputQueue.peekFirst();
            int accept = Math.min(queued.size(), slotsAvailable);
            if (accept < queued.size()) {
                state.inputQueue.pollFirst();
                state.inputQueue.addFirst(
                        new RequestBatch(queued.size() - accept, queued.arrivalTick()));
            } else {
                state.inputQueue.pollFirst();
            }

            state.hitsOwedScaled += hitRateScaled * accept;
            int hitCount = (int) (state.hitsOwedScaled / HIT_RATE_SCALE);
            state.hitsOwedScaled -= (long) hitCount * HIT_RATE_SCALE;
            int missCount = accept - hitCount;

            if (hitCount > 0) {
                state.hitInFlight.add(new InFlightBatch(hitCount, queued.arrivalTick(), tick));
            }
            if (missCount > 0) {
                state.missInFlight.add(new InFlightBatch(missCount, queued.arrivalTick(), tick));
            }
            slotsAvailable -= accept;
        }

        // DRAIN hits: completionTick=tick satisfies tick<=tick, so drain same tick as admit
        Iterator<InFlightBatch> hitIt = state.hitInFlight.iterator();
        while (hitIt.hasNext()) {
            InFlightBatch batch = hitIt.next();
            if (batch.completionTick() <= tick) {
                hitIt.remove();
                completedCount += batch.size();
                processedThisTick += batch.size();
                latencySum += (long) batch.size() * (batch.completionTick() + def.hitLatencyTicks() - batch.arrivalTick());
                state.processedCount += batch.size();
            }
        }

        // DRAIN misses
        Iterator<InFlightBatch> missIt = state.missInFlight.iterator();
        while (missIt.hasNext()) {
            InFlightBatch batch = missIt.next();
            if (batch.completionTick() <= tick) {
                missIt.remove();
                state.processedCount += batch.size();
                processedThisTick += batch.size();
                if (!downstreams.isEmpty()) {
                    forwarded.add(new ForwardedBatch(
                            batch.size(), batch.arrivalTick(), downstreams.get(0),
                            batch.completionTick() + def.processingLatencyTicks()));
                } else {
                    state.droppedCount += batch.size();
                }
            }
        }

        int droppedCount = 0;
        int totalQueued = state.inputQueue.stream().mapToInt(RequestBatch::size).sum();
        while (totalQueued > def.queueLimit()) {
            RequestBatch last = state.inputQueue.peekLast();
            if (last == null) break;
            int excess = totalQueued - def.queueLimit();
            if (excess >= last.size()) {
                state.inputQueue.pollLast();
                droppedCount += last.size();
                state.droppedCount += last.size();
                totalQueued -= last.size();
            } else {
                state.inputQueue.pollLast();
                state.inputQueue.addLast(new RequestBatch(last.size() - excess, last.arrivalTick()));
                droppedCount += excess;
                state.droppedCount += excess;
                totalQueued -= excess;
            }
        }

        int queueDepth = totalQueued + state.hitInFlight.size() + state.missInFlight.size();
        return new NodeTickResult(
                def.nodeId(), completedCount, processedThisTick, droppedCount, latencySum, forwarded, queueDepth);
    }

    // ── Load balancer node ────────────────────────────────────────────────────

    private NodeTickResult processLbNode(NodeDefinition def, BatchNodeState state, long tick) {
        List<ForwardedBatch> forwarded = new ArrayList<>();
        List<String> downstreams = topology.getDownstreams(def.nodeId());
        int processedThisTick = 0;
        int droppedThisTick = 0;

        // capacity=0 means unlimited (backward compat); capacity>0 throttles throughput per tick
        int capacityRemaining = def.capacityPerTick() > 0 ? def.capacityPerTick() : Integer.MAX_VALUE;

        while (!state.inputQueue.isEmpty() && capacityRemaining > 0) {
            RequestBatch batch = state.inputQueue.peekFirst();
            int accept = Math.min(batch.size(), capacityRemaining);

            if (accept < batch.size()) {
                state.inputQueue.pollFirst();
                state.inputQueue.addFirst(new RequestBatch(batch.size() - accept, batch.arrivalTick()));
                batch = new RequestBatch(accept, batch.arrivalTick());
            } else {
                state.inputQueue.pollFirst();
            }

            state.processedCount += accept;
            processedThisTick += accept;
            capacityRemaining -= accept;
            long deliveryTick = tick + def.processingLatencyTicks();

            if (downstreams.isEmpty()) {
                state.droppedCount += accept;
                droppedThisTick += accept;
                continue;
            }

            switch (def.nodeType()) {
                case ROUND_ROBIN_LOAD_BALANCER -> forwarded.addAll(
                        roundRobinForward(batch, downstreams, state, deliveryTick));
                case LEAST_CONNECTIONS_LOAD_BALANCER -> forwarded.add(
                        leastConnectionsForward(batch, downstreams, deliveryTick));
                default -> forwarded.add(
                        new ForwardedBatch(accept, batch.arrivalTick(),
                                downstreams.get(0), deliveryTick));
            }
        }

        // Trim queue to queueLimit; queueLimit=0 means unlimited (no drops)
        if (def.queueLimit() > 0) {
            int totalQueued = state.inputQueue.stream().mapToInt(RequestBatch::size).sum();
            while (totalQueued > def.queueLimit()) {
                RequestBatch last = state.inputQueue.peekLast();
                if (last == null) break;
                int excess = totalQueued - def.queueLimit();
                if (excess >= last.size()) {
                    state.inputQueue.pollLast();
                    droppedThisTick += last.size();
                    state.droppedCount += last.size();
                    totalQueued -= last.size();
                } else {
                    state.inputQueue.pollLast();
                    state.inputQueue.addLast(new RequestBatch(last.size() - excess, last.arrivalTick()));
                    droppedThisTick += excess;
                    state.droppedCount += excess;
                    totalQueued -= excess;
                }
            }
        }

        int queueDepth = state.inputQueue.stream().mapToInt(RequestBatch::size).sum();
        return new NodeTickResult(def.nodeId(), 0, processedThisTick, droppedThisTick, 0L, forwarded, queueDepth);
    }

    private List<ForwardedBatch> roundRobinForward(
            RequestBatch batch, List<String> downstreams,
            BatchNodeState state, long deliveryTick) {

        int n = downstreams.size();
        int base = batch.size() / n;
        int remainder = batch.size() % n;
        List<ForwardedBatch> result = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            int targetIndex = (state.rrIndex + i) % n;
            int alloc = base + (i < remainder ? 1 : 0);
            if (alloc > 0) {
                result.add(new ForwardedBatch(
                        alloc, batch.arrivalTick(), downstreams.get(targetIndex), deliveryTick));
            }
        }
        state.rrIndex = (state.rrIndex + 1) % n;
        return result;
    }

    private ForwardedBatch leastConnectionsForward(
            RequestBatch batch, List<String> downstreams, long deliveryTick) {

        String best = downstreams.stream()
                .min(Comparator.comparingInt(id -> stateByNodeId.get(id).loadSnapshot))
                .orElse(downstreams.get(0));
        return new ForwardedBatch(batch.size(), batch.arrivalTick(), best, deliveryTick);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Long> deliverPendingBatches(List<ForwardedBatch> pending, long tick) {
        Map<String, Long> drops = new LinkedHashMap<>();
        Iterator<ForwardedBatch> it = pending.iterator();
        while (it.hasNext()) {
            ForwardedBatch fwd = it.next();
            if (fwd.deliveryTick() <= tick) {
                int d = admitToQueue(fwd.targetNodeId(), fwd.size(), fwd.arrivalTick());
                if (d > 0) drops.merge(fwd.targetNodeId(), (long) d, Long::sum);
                it.remove();
            }
        }
        return drops;
    }

    /**
     * Admits only as many requests as fit within the node's queueLimit; drops the rest.
     * Enforced before insertion — queue never exceeds queueLimit.
     * Returns the number dropped.
     */
    private int admitToQueue(String nodeId, int size, long arrivalTick) {
        NodeDefinition def   = defByNodeId.get(nodeId);
        BatchNodeState state = stateByNodeId.get(nodeId);
        int currentQueued = state.inputQueue.stream().mapToInt(RequestBatch::size).sum();
        int available = def.queueLimit() - currentQueued;
        if (available <= 0) {
            state.droppedCount += size;
            return size;
        }
        int admit   = Math.min(size, available);
        int dropped = size - admit;
        state.inputQueue.addLast(new RequestBatch(admit, arrivalTick));
        if (dropped > 0) {
            state.droppedCount += dropped;
        }
        return dropped;
    }

    private void snapshotLoads() {
        for (BatchNodeState state : stateByNodeId.values()) {
            // Use request count (not batch count) so LC routing distinguishes queue sizes correctly
            state.loadSnapshot = state.inFlight.stream().mapToInt(InFlightBatch::size).sum()
                    + state.hitInFlight.stream().mapToInt(InFlightBatch::size).sum()
                    + state.missInFlight.stream().mapToInt(InFlightBatch::size).sum()
                    + state.inputQueue.stream().mapToInt(RequestBatch::size).sum();
        }
    }

    private boolean isIdle() {
        for (BatchNodeState state : stateByNodeId.values()) {
            if (!state.inputQueue.isEmpty()
                    || !state.inFlight.isEmpty()
                    || !state.hitInFlight.isEmpty()
                    || !state.missInFlight.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ── Runtime invariant checks ──────────────────────────────────────────────

    private long computeTerminationCap() {
        long latencySum = nodeDefinitions.stream()
                .mapToLong(NodeDefinition::processingLatencyTicks)
                .sum();
        long maxLatency = nodeDefinitions.stream()
                .mapToLong(NodeDefinition::processingLatencyTicks)
                .max().orElse(1L);
        long maxHitLatency = nodeDefinitions.stream()
                .mapToLong(NodeDefinition::hitLatencyTicks)
                .max().orElse(0L);
        // Each forwarding hop adds +1 tick delivery delay.
        long hopBuffer = nodeDefinitions.size();
        // Worst-case drain: all injected requests queue at the slowest node and drain one at a time.
        // In practice the isIdle() check exits the loop long before this cap is reached.
        long worstCase = (long) Math.ceil(requestsPerTick * durationTicks);
        long drainBuffer = worstCase * Math.max(maxLatency, maxHitLatency + 1);
        return durationTicks + drainBuffer + latencySum + hopBuffer + 1;
    }

    private void validateTickInvariants(
            long tick,
            long injected,
            long completed,
            long dropped,
            List<ForwardedBatch> pending
    ) {
        // ─────────────────────────────────────────────
        // (1) Basic conservation sanity (always true)
        // ─────────────────────────────────────────────
        if (injected < completed + dropped) {
            logInvariant(tick, "Conservation broken: injected=%d < completed+dropped=%d",
                    injected, completed + dropped);
        }

        // ─────────────────────────────────────────────
        // (2) Per-node safety checks
        // ─────────────────────────────────────────────
        for (NodeDefinition def : nodeDefinitions) {
            BatchNodeState state = stateByNodeId.get(def.nodeId());

            if (state.processedCount < 0) {
                logInvariant(tick, "Node %s negative processedCount=%d",
                        def.nodeId(), state.processedCount);
            }

            if (state.droppedCount < 0) {
                logInvariant(tick, "Node %s negative droppedCount=%d",
                        def.nodeId(), state.droppedCount);
            }

            int totalQueued = state.inputQueue.stream().mapToInt(RequestBatch::size).sum();

            if (totalQueued < 0) {
                logInvariant(tick, "Node %s negative queue size=%d",
                        def.nodeId(), totalQueued);
            }

            // ✅ STRICT queue enforcement (no exceptions)
            if (def.queueLimit() > 0 && totalQueued > def.queueLimit()) {
                logInvariant(tick,
                        "Node %s queue overflow: queue=%d > limit=%d",
                        def.nodeId(), totalQueued, def.queueLimit());
            }
        }

        // ─────────────────────────────────────────────
        // (3) Full conservation (with tolerance)
        // ─────────────────────────────────────────────
        long inSystem = 0;

        for (BatchNodeState state : stateByNodeId.values()) {
            inSystem += state.inputQueue.stream().mapToLong(RequestBatch::size).sum();
            inSystem += state.inFlight.stream().mapToLong(InFlightBatch::size).sum();
            inSystem += state.hitInFlight.stream().mapToLong(InFlightBatch::size).sum();
            inSystem += state.missInFlight.stream().mapToLong(InFlightBatch::size).sum();
        }

        inSystem += pending.stream().mapToLong(ForwardedBatch::size).sum();

        long diff = injected - (completed + dropped + inSystem);

        // allow small tolerance due to tick timing
        if (Math.abs(diff) > 0) {
            logInvariant(tick,
                    "Conservation mismatch: injected=%d, completed=%d, dropped=%d, inSystem=%d, diff=%d",
                    injected, completed, dropped, inSystem, diff);
        }
    }

    private void logInvariant(long tick, String message, Object... args) {
        System.err.printf("[INVARIANT][tick=%d] %s%n", tick, String.format(message, args));
    }
}
