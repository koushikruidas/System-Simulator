# Deterministic Distributed System Simulator

## 1. Project Overview

This project is a deterministic distributed system simulator built in Java with a clean layered architecture.

It models how requests move through a distributed system over virtual time, allowing a user or external client to define a system topology and simulate how that system behaves under load. The current
version supports a simple but meaningful request path through:

- a Load Balancer
- a Service
- a Database

The system is interesting because it focuses on a problem that appears often in system design, backend engineering, and infrastructure planning:

> How does a distributed system behave when requests move through multiple nodes with capacity constraints, queues, and latency?

Instead of testing this with real infrastructure, the simulator provides a controlled environment where behavior can be observed deterministically and repeatedly.

### What problem it solves

It helps answer questions like:

- What happens when request volume increases?
- Where does latency accumulate?
- Which node becomes a bottleneck?
- How many requests complete successfully?
- How many get dropped due to capacity or queue limits?

### Why it is interesting

This project combines several important backend engineering ideas:

- event-driven simulation
- deterministic execution
- clean architecture
- extensible node creation
- production-style REST API design

It is useful both as a technical foundation for a larger simulation platform and as a strong architecture-focused portfolio project.

———

## 2. Key Concepts

## Event-Driven Simulation

The simulator is driven by events.

An event represents something that happens at a specific point in simulation time, such as:

- a request arriving at a load balancer
- a service finishing processing
- a database completing a request

Instead of continuously “running” time, the engine jumps from event to event.

### Example

A request may produce a flow like this:

1. Request arrives at load balancer at time 0
2. Load balancer forwards it to service at time 1
3. Service finishes processing at time 6
4. Database finishes processing at time 16

The simulator processes only those significant moments.

———

## Virtual Time vs Real Time

The simulator uses virtual time, not wall-clock time.

### Real time

Real time depends on the machine running the code:

- CPU speed
- thread scheduling
- system load
- actual elapsed milliseconds

### Virtual time

Virtual time is controlled by the engine:

- it advances only when the next event is processed
- it is deterministic
- it is not affected by machine performance

This makes the simulator stable and reproducible.

### Why this matters

If a request takes 16 units of time in the simulation, that means:

- 16 is part of the modeled system behavior
- not an artifact of runtime or infrastructure noise

———

## Deterministic Execution

Determinism means:

> The same input scenario always produces the same result.

This simulator guarantees deterministic execution by using:

- a single-threaded engine
- a priority queue for event scheduling
- strict event ordering by:
    - timestamp
    - sequenceNumber

### Why this matters

Determinism is essential for:

- debugging
- testing
- comparing scenarios
- trusting simulation outcomes

Without determinism, two runs of the same scenario might produce different results for reasons unrelated to the modeled system.

———

## Request Flow Modeling

A request is treated as a first-class entity.

The simulator tracks how a request moves through the system:

- where it arrived
- where it is currently being processed
- whether it was queued
- whether it completed
- whether it was dropped

This makes the simulator useful for understanding behavior, not just counting events.

———

## Node Types

Version 1 supports three built-in node types:

### Load Balancer

Receives incoming requests and forwards them to a downstream service.

### Service

Processes requests with:

- capacity limits
- queue limits
- processing latency

### Database

Processes requests similarly to a service, but acts as the terminal node in the current flow.

### Example request path

Client -> Load Balancer -> Service -> Database -> Complete

———

## 3. Architecture Overview

The system is organized into three main layers.

simulation/   -> core engine and simulation model
application/  -> orchestration and scenario construction
api/          -> REST API and HTTP concerns

## simulation/ - Core Engine

This is the heart of the simulator.

Responsibilities:

- event loop
- event scheduling
- virtual clock
- node execution
- runtime state
- simulation metrics

Important rule:

- this layer is framework-independent
- it does not depend on Spring

This keeps the core portable, testable, and reusable.

———

## application/ - Orchestration Layer

This layer sits on top of the engine and prepares it for use.

Responsibilities:

- build valid scenarios
- assemble engine dependencies
- create nodes through factories
- run simulations
- translate engine output into application-level reports

This layer is where use-case orchestration lives.

———

## api/ - REST Layer

This is the Spring Boot adapter layer.

Responsibilities:

- accept JSON input
- validate request DTOs
- map HTTP requests to application commands
- call the application service
- return structured JSON responses
- handle API errors consistently

This keeps transport concerns outside the simulator core.

———

## 4. System Flow

The end-to-end flow of a simulation request looks like this:

Client
-> REST Controller
-> Request Mapper
-> Application Service
-> Scenario Builder
-> Simulation Runner
-> Engine Assembly Factory
-> Simulation Engine
-> Report Assembler
-> Response Mapper
-> Client

### Step-by-step

1. A client sends POST /simulate with a scenario definition in JSON.
2. The REST controller validates the request shape.
3. The request mapper converts the DTO into an application command.
4. The application service builds a validated SimulationScenario.
5. The simulation runner assembles the core engine and dependencies.
6. Seed events are generated.
7. The core engine runs the scenario deterministically.
8. The engine produces a core report.
9. The application layer converts that into a summary report.
10. The API layer serializes the response to JSON.

———

## 5. Core Engine Design (High Level)

The core engine is intentionally simple and focused.

## SimulationEngine

SimulationEngine owns the main event loop.

Responsibilities:

- sort and seed initial events
- poll the next event
- advance virtual time
- resolve the target node
- execute node behavior
- apply state mutations
- collect metrics
- enqueue emitted events

It is the orchestrator of the simulation, not a business-logic container.

———

## EventScheduler

EventScheduler manages future events.

In the current implementation:

- it uses a priority queue
- events are ordered by:
    - timestamp
    - sequenceNumber

This guarantees stable ordering.

———

## VirtualClock

VirtualClock tracks the current simulation time.

Responsibilities:

- expose current time
- advance only forward
- prevent time from moving backward

This is the basis of virtual-time execution.

———

## Node Model

Nodes implement a common SimNode interface.

A node receives:

- the current event
- a narrow execution context

A node returns:

- emitted events
- state mutations
- metric signals

This design keeps node behavior explicit and avoids broad mutable shared context.

———

## NodeResult

NodeResult is the output of node execution.

It contains:

- emittedEvents
- stateMutations
- metricSignals

This allows nodes to describe what should happen next without directly controlling the engine.

———

## StateMutation

StateMutation represents an explicit runtime change.

Examples:

- increment in-flight request count
- enqueue request
- mark request completed
- mark request dropped

This keeps state changes controlled, testable, and deterministic.

———

## 6. Application Layer

The application layer turns the core engine into a usable simulation service.

## ScenarioBuilder

ScenarioBuilder provides a fluent DSL for constructing a valid simulation scenario.

Responsibilities:

- define nodes
- define connections
- set request count
- validate topology rules
- produce an application-level SimulationScenario

Why it exists:

- keeps scenario construction readable
- centralizes semantic validation
- avoids pushing topology rules into the controller

———

## SimulationRunner

SimulationRunner is the application service responsible for execution.

Responsibilities:

- assemble a fresh engine instance
- generate seed events
- execute the scenario
- convert engine output into an application report

Why it exists:

- separates orchestration from both API and core engine
- makes simulation execution reusable from non-HTTP entrypoints later

———

## EngineAssemblyFactory

EngineAssemblyFactory creates the runtime dependencies required for a simulation run.

Responsibilities:

- create scheduler
- create clock
- create runtime state store
- create metrics collector
- build node registry with node factories
- create SimulationEngine

Why it exists:

- centralizes assembly logic
- keeps the runner focused on orchestration rather than object graph creation

———

## ReportAssembler

ReportAssembler converts the engine-level report into an application-level summary report.

Why it exists:

- prevents REST responses from depending directly on engine internals
- makes the output stable even if engine reporting evolves

———

## 7. Node Creation Design

A major design goal was to avoid hard-coded creation logic such as:

switch (nodeType) {
...
}

That approach works initially, but it scales poorly. Every new node type requires modifying existing orchestration code.

## Why switch-case was avoided

Problems with switch-based creation:

- violates Open/Closed Principle
- centralizes too much knowledge
- grows over time
- increases regression risk when adding new node types

———

## Factory Pattern

Each supported node type has its own factory implementing a common interface.

Example concept:

public interface SimNodeFactory {
NodeType supportedType();
SimNode create(NodeDefinition definition);
}

Benefits:

- node creation is encapsulated
- new node types are added by introducing new factories
- orchestration code stays unchanged

———

## Registry Pattern

A factory registry maps node types to factories.

Responsibilities:

- register factories
- resolve the correct factory for a node type
- fail fast for duplicates or unsupported types

Benefits:

- removes branching from the runner
- centralizes factory lookup
- supports clean extension

———

## How to add a new node type

To add a new node type in the future:

1. Add a new NodeType
2. Implement a new core SimNode
3. Add a SimNodeFactory for that type
4. Register the factory as a Spring component
5. Add a scenario configurer if the API/DSL should support it

The runner and engine orchestration should not need to change.

———

## 8. REST API Design

## Endpoint

POST /simulate

This endpoint accepts a scenario definition and returns a simulation report.

———

## Request Format

Example JSON:

{
"requestCount": 100,
"nodes": [
{ "id": "lb", "type": "LOAD_BALANCER", "latency": 1 },
{ "id": "service", "type": "SERVICE", "capacity": 10, "queueLimit": 20, "latency": 100 },
{ "id": "db", "type": "DATABASE", "capacity": 5, "queueLimit": 10, "latency": 200 }
],
"connections": [
{ "sourceNodeId": "lb", "targetNodeId": "service" },
{ "sourceNodeId": "service", "targetNodeId": "db" }
]
}

———

## Response Format

Example success response:

{
"totalRequests": 100,
"successfulRequests": 95,
"failedRequests": 5,
"averageLatency": 287.5,
"nodeMetrics": {
"lb": {
"processedRequests": 100,
"droppedRequests": 0
},
"service": {
"processedRequests": 95,
"droppedRequests": 5
},
"db": {
"processedRequests": 95,
"droppedRequests": 0
}
}
}

———

## Error Format

Example structured validation/business error:

{
"code": "INVALID_SIMULATION_REQUEST",
"message": "Node service must define a downstream connection",
"path": "/simulate",
"timestamp": "2026-04-18T12:34:56Z",
"fieldErrors": []
}

Example field validation error:

{
"code": "INVALID_SIMULATION_REQUEST",
"message": "Request validation failed",
"path": "/simulate",
"timestamp": "2026-04-18T12:34:56Z",
"fieldErrors": [
{
"field": "requestCount",
"message": "must be greater than 0"
}
]
}

———

## Validation Flow

Validation happens in two stages:

1. DTO validation
    - ensures request shape is valid
    - handled by bean validation
2. Scenario validation
    - ensures business rules are valid
    - handled by the scenario builder

This separation keeps the API contract clean and keeps business rules out of the controller.

———

## 9. Validation Strategy

Validation is intentionally split across layers.

## DTO Validation

Handled in the REST layer using bean validation annotations such as:

- @Valid
- @NotNull
- @NotBlank
- @NotEmpty
- @Positive
- @PositiveOrZero

Purpose:

- validate incoming JSON structure
- reject malformed requests early
- keep controller logic thin

Examples:

- request count must be positive
- node list must not be empty
- connection ids must not be blank

———

## Builder Validation

Handled in the application layer by ScenarioBuilder.

Purpose:

- enforce domain and topology rules
- validate semantic correctness

Examples:

- duplicate node ids
- missing referenced node
- invalid connection types
- missing downstream connection
- exactly one load balancer
- at least one service and one database

———

## Why validation is split

Because the two concerns are different:

- DTO validation answers:
    - “Is the request structurally valid JSON for this API?”
- Builder validation answers:
    - “Does this represent a valid simulation scenario?”

This produces cleaner code and better error handling.

———

## 10. Simulation Report

The V1 simulation report is intentionally summary-oriented.

It includes:

- total requests
- successful requests
- failed requests
- average latency
- per-node processed counts
- per-node dropped counts

### Why this shape

This report is:

- useful for UI summary views
- stable for API consumers
- not tightly coupled to engine internals

### Why full trace is not exposed in V1

The engine internally knows more:

- processed events
- request runtime states
- detailed lifecycle information

That is not exposed in V1 because:

- it would make the API heavier
- it would expose core internal structures too early
- summary metrics are enough for the initial use case

This is a deliberate design boundary, not a limitation of the engine itself.

———

## 11. Constraints (Version 1)

The first version intentionally keeps scope tight.

### Current constraints

- single downstream per node
- synchronous execution only
- summary-only report
- limited built-in node types:
    - load balancer
    - service
    - database
- no async processing
- no fan-out routing
- no multiple downstream strategies
- no UI visualization yet
- no exposed request trace API

These constraints help keep the system deterministic, testable, and extensible without premature complexity.

———

## 12. Future Roadmap (Version 2+)

The current design leaves room for meaningful expansion.

## Multiple downstream nodes

Support richer topologies such as:

- one load balancer to many services
- service fan-out
- multi-hop routing graphs

This will require evolving the topology model beyond a single downstreamNodeId.

———

## Routing strategies

Introduce configurable routing strategies such as:

- round robin
- weighted routing
- least loaded
- random routing

This is a natural place to apply the Strategy Pattern.

———

## Async execution

Introduce a job-based API model:

- POST /simulate submits a simulation job
- GET /simulate/{id} retrieves status/result

This is useful for long-running or large-scale scenarios.

———

## Richer simulation reports

Future reports may include:

- per-request outcomes
- latency distributions
- request trace history
- full processed event trace

———

## UI visualization

A UI can later use the API to:

- design a topology visually
- execute simulations
- inspect bottlenecks
- compare scenarios
- visualize request flow and failures

———

## 13. How to Use

## Example using ScenarioBuilder

SimulationScenario scenario = ScenarioBuilder.create()
.addLoadBalancer("lb", 1)
.addService("service", 10, 20, 100)
.addDatabase("db", 5, 10, 200)
.connect("lb", "service")
.connect("service", "db")
.withRequestCount(100)
.build();

Then pass the scenario to the application runner:

SimulationSummaryReport report = simulationRunner.run(scenario);