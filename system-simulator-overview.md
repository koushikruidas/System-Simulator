# System Simulator

## 1. Project Overview

**System Simulator** is a deterministic, event-driven distributed systems simulator built in Java with a layered architecture.

The project is designed to model how requests move through a distributed system over **virtual time**, allowing developers to reason about throughput, latency, queueing, and node behavior without relying
on real infrastructure.

### What problem it solves

When designing distributed systems, it is often difficult to answer questions like:

- What happens when request volume increases?
- Where does latency build up?
- Which node becomes the bottleneck?
- How does queueing affect downstream systems?
- What is the impact of capacity limits on request completion and failure?

This simulator provides a controlled environment to answer those questions.

### Why it is interesting

This project is interesting because it combines:

- **distributed systems thinking**
- **event-driven simulation**
- **deterministic execution**
- **clean layered architecture**
- **extensible design for future growth**

It is not just a CRUD application or a REST wrapper. It is a small simulation platform with a reusable engine, an application orchestration layer, and an API layer that can later support UI-driven system
design and experimentation.

  ---

## 2. Key Concepts

## Event-Driven Simulation

The simulator works by processing **events** in order.

An event represents something that happens at a specific virtual time, for example:

- a request arriving at a load balancer
- a service finishing processing
- a database completing a request

Instead of continuously updating the system like a real-time loop, the simulator jumps from event to event.

### Example

A request might produce these events:

1. `REQUEST_ARRIVED` at load balancer at time `0`
2. `REQUEST_ARRIVED` at service at time `1`
3. `PROCESSING_COMPLETED` at service at time `6`
4. `REQUEST_ARRIVED` at database at time `6`
5. `PROCESSING_COMPLETED` at database at time `16`

The engine processes those events in order and updates the system state accordingly.

  ---

## Virtual Time vs Real Time

The simulator uses **virtual time**, not wall-clock time.

### Real time
Real time depends on the actual clock on the machine. If a task takes 10 ms on one machine and 30 ms on another, behavior may vary.

### Virtual time
Virtual time is a simulated timeline controlled entirely by the engine. Time advances only when the engine processes the next event.

### Why this matters

Using virtual time makes the simulator:

- predictable
- fast
- reproducible
- independent of machine speed

If the same inputs are used, the same outputs are produced every time.

  ---

## Deterministic Execution

Determinism means:

> the same scenario and the same inputs always produce the same result.

This simulator enforces determinism by:

- processing one event at a time
- using a priority queue
- ordering events by:
    - `timestamp`
    - `sequenceNumber`
- avoiding concurrency in version 1

This is critical because simulation results must be trustworthy and repeatable.

  ---

## Request Flow Modeling

The simulator models how a **request** flows through the system.

A request is the logical business object being simulated. It may:

- arrive at a load balancer
- be routed to a service
- wait in a queue
- be processed
- be forwarded downstream
- complete successfully
- be dropped if capacity is exhausted

This lets the simulator answer questions about:

- request latency
- throughput
- queueing behavior
- failure/drop behavior

  ---

## Node Types

Version 1 supports three node types:

### Load Balancer
Routes incoming requests to a downstream service.

### Service
Processes requests with:

- capacity constraints
- queueing behavior
- processing latency

### Database
Acts as a downstream processing node with its own:

- capacity
- queue
- latency

These nodes are enough to simulate a simple request path:

`Client -> Load Balancer -> Service -> Database`

  ---

## 3. Architecture Overview

The project is organized into three main layers.

## `simulation/` - Core Engine

This is the deterministic simulation core.

Responsibilities:

- event loop
- event scheduling
- virtual clock
- node execution
- runtime state management
- core metrics collection

Important rule:

> This layer is framework-independent and must remain free from Spring dependencies.

  ---

## `application/` - Orchestration Layer

This layer sits on top of the simulation core.

Responsibilities:

- build valid simulation scenarios
- validate business-level topology rules
- assemble engine dependencies
- run simulations
- transform engine output into application-level reports

This layer is where the system becomes usable as an application rather than just a raw engine.

  ---

## `api/` - REST Layer

This is the Spring Boot adapter layer.

Responsibilities:

- expose HTTP endpoints
- validate JSON requests
- map DTOs to application commands
- return structured responses
- handle API errors consistently

This layer should remain thin and should never contain core simulation logic.

  ---

## 4. System Flow

The end-to-end flow is:

  ```text
  Client
    -> POST /simulate
    -> REST Controller
    -> Request Mapper
    -> Application Service
    -> Scenario Builder
    -> Simulation Runner
    -> Engine Assembly
    -> Simulation Engine
    -> Engine Report
    -> Report Assembler
    -> Response Mapper
    -> HTTP Response

  ### Step-by-step

  1. The client sends a simulation request as JSON.
  2. The API layer validates the request shape.
  3. The request is mapped into application-level command objects.
  4. The application service builds a valid SimulationScenario.
  5. The simulation runner assembles the engine and related collaborators.
  6. The engine runs the scenario deterministically.
  7. The engine produces a raw simulation report.
  8. The application layer transforms that into a UI/API-friendly summary report.
  9. The API layer maps that report into JSON and returns it.

  ———

  ## 5. Core Engine Design (High Level)

  The core engine already exists and remains unchanged.

  ## SimulationEngine

  SimulationEngine is the orchestrator of the simulation.

  Responsibilities:

  - accept seed events
  - sort and schedule them
  - process events one at a time
  - advance virtual time
  - resolve the target node
  - collect emitted events
  - apply state mutations
  - record metrics
  - return a final report

  It is intentionally generic and does not contain node-specific business rules.

  ———

  ## EventScheduler

  The scheduler manages future events.

  In version 1, it is implemented as a priority queue ordered by:

  1. event timestamp
  2. sequence number

  This guarantees deterministic processing order.

  ———

  ## VirtualClock

  VirtualClock stores the current simulation time.

  It moves only forward. Time does not pass continuously. It advances only when the next event is processed.

  ———

  ## Node Model

  Each node implements the SimNode contract.

  A node receives:

  - the current event
  - a narrow execution context

  A node returns:

  - follow-up events
  - state mutations
  - metric signals

  This design keeps node behavior isolated from engine orchestration.

  ———

  ## NodeResult

  NodeResult is the output of node execution.

  It contains:

  - emittedEvents
  - stateMutations
  - metricSignals

  This is important because nodes do not directly manipulate the engine. They describe what should happen next, and the engine applies those results in order.

  ———

  ## StateMutation

  A StateMutation is an explicit state change to apply to runtime state.

  Examples include:

  - increment in-flight count
  - enqueue a request
  - dequeue a request
  - mark a request completed
  - mark a request dropped

  This keeps state changes explicit and deterministic.

  ———

  ## 6. Application Layer

  The application layer turns the engine into a usable simulation service.

  ## ScenarioBuilder

  ScenarioBuilder provides a fluent DSL for creating valid simulation scenarios.

  Example intent:

  SimulationScenario scenario = ScenarioBuilder.create()
      .addLoadBalancer("lb", 1)
      .addService("service", 10, 20, 100)
      .addDatabase("db", 5, 10, 200)
      .connect("lb", "service")
      .connect("service", "db")
      .withRequestCount(100)
      .build();

  Why it exists:

  - encapsulates topology construction
  - centralizes business validation
  - keeps Spring out of scenario creation
  - gives developers a readable API for building simulations

  ———

  ## SimulationRunner

  SimulationRunner is the application-level orchestrator for executing a scenario.

  Responsibilities:

  - generate deterministic seed requests
  - assemble a fresh engine and its collaborators
  - execute the simulation
  - delegate report transformation

  Why it exists:

  - keeps engine wiring out of controllers
  - creates a clean application boundary
  - allows future async execution or job-based orchestration without changing the core engine

  ———

  ## EngineAssemblyFactory

  This factory creates the runtime components needed for one simulation execution.

  It assembles:

  - event scheduler
  - virtual clock
  - runtime state store
  - metrics collector
  - node registry
  - simulation engine

  Why it exists:

  - keeps creation logic centralized
  - avoids wiring duplication
  - supports clean separation between orchestration and object construction

  ———

  ## ReportAssembler

  The engine produces a core report based on engine internals.

  The application layer should not expose that raw report directly.

  ReportAssembler converts the engine report into an application-level summary that is:

  - stable
  - cleaner for clients
  - not tightly coupled to engine internals

  ———

  ## 7. Node Creation Design

  A key design goal is extensibility.

  ## Why switch-case was avoided

  Using a switch on node type for creation would introduce a growth problem:

  - every new node type would require editing central orchestration code
  - this violates the Open/Closed Principle
  - it increases coupling between the runner and node implementations

  Instead, node creation uses Factory + Registry.

  ———

  ## Factory Pattern

  Each supported node type has its own factory.

  Examples:

  - LoadBalancerNodeFactory
  - ServiceNodeFactory
  - DatabaseNodeFactory

  Each factory knows how to create one node type.

  ———

  ## Registry Pattern

  SimNodeFactoryRegistry stores all registered factories, keyed by node type.

  The runner asks the registry:

  > create a node for this NodeDefinition

  The registry delegates that request to the correct factory.

  ### Benefits

  - no central switch-case
  - easy to extend
  - clearer ownership of creation logic
  - better testability

  ———

  ## Adding a new node type

  To add a new node type in the future:

  1. Add a new core SimNode implementation in simulation/
  2. Add a new NodeType
  3. Add a matching SimNodeFactory
  4. Register the factory as a Spring component
  5. Add a scenario configurer if the API/application layer must support it

  No central runner logic needs to be modified.

  ———

  ## 8. REST API Design

  ## Endpoint

  POST /simulate

  This endpoint accepts a scenario definition and returns a simulation summary.

  ———

  ## Request Format

  Example request:

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

  Example validation/business error response:

  {
    "code": "INVALID_SIMULATION_REQUEST",
    "message": "Node service must define a downstream connection",
    "path": "/simulate",
    "timestamp": "2026-04-18T12:34:56Z",
    "fieldErrors": []
  }

  Example DTO validation failure:

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

  ## Validation flow

  Validation happens in two stages:

  1. DTO validation
      - verifies JSON shape and basic field constraints
  2. Scenario builder validation
      - verifies business rules and topology correctness

  This split keeps concerns clear and avoids pushing business rules into the controller.

  ———

  ## 9. Validation Strategy

  Validation is intentionally split across layers.

  ## DTO Validation

  Performed in the API layer using bean validation annotations such as:

  - @Valid
  - @NotBlank
  - @NotNull
  - @NotEmpty
  - @Positive
  - @PositiveOrZero

  This validates:

  - required fields
  - nullability
  - simple numeric constraints
  - nested request structure

  ### Why this belongs in the API layer

  Because these rules concern request shape and basic input integrity.

  ———

  ## Builder Validation

  Performed in the application layer inside ScenarioBuilder.

  This validates:

  - unique node ids
  - valid node references in connections
  - exactly one load balancer
  - at least one service and one database
  - valid type-to-type connections
  - single downstream per node
  - required downstream links for non-terminal nodes

  ### Why this belongs in the application layer

  Because these are business rules, not just JSON field rules.

  ———

  ## Why split validation

  If all validation lived in DTOs:

  - business rules would leak into the controller layer
  - validation would become harder to reuse outside REST

  If all validation lived in the builder:

  - malformed requests would not get clean field-level API errors

  The split gives the best of both approaches.

  ———

  ## 10. Simulation Report

  The application exposes a summary-oriented simulation report.

  It includes:

  - total requests
  - successful requests
  - failed requests
  - average latency
  - per-node processed request counts
  - per-node dropped request counts

  ### Why this shape

  This format is:

  - useful for UI dashboards
  - easy to consume from REST
  - stable as a public contract
  - decoupled from engine internals

  ### Why full trace is not exposed in V1

  The core engine does have richer data internally, including processed events and request state.

  That full trace is not exposed in version 1 because:

  - it would tightly couple the API to engine internals
  - it increases payload size significantly
  - it complicates the public contract too early
  - the initial goal is summary-level system insight, not deep debugging output

  Trace exposure can be added later through a dedicated endpoint or advanced report mode.

  ———

  ## 11. Constraints (Version 1)

  Version 1 intentionally has several constraints.

  - Single downstream per node
      - the current core NodeDefinition supports one downstreamNodeId
  - Synchronous execution
      - POST /simulate runs inline and returns immediately
  - Summary-only report
      - event traces and per-request details are not exposed
  - Limited node types
      - load balancer, service, and database only
  - No async processing
      - no job submission, status polling, or background runs
  - No advanced routing strategies
      - routing is simple and fixed in v1
  - No UI yet
      - the simulator is currently exposed via API and builder APIs

  These constraints are deliberate. They keep version 1 correct, understandable, and stable.

  ———

  ## 12. Future Roadmap (Version 2+)

  There are several natural extensions for future versions.

  ## Multiple downstream nodes

  Allow nodes to route to multiple downstream targets instead of exactly one.

  This would enable:

  - service fan-out
  - multiple database replicas
  - load-balancer strategies across multiple services

  ———

  ## Routing strategies

  Introduce configurable routing policies such as:

  - round robin
  - random
  - weighted routing
  - least loaded

  This would likely use a Strategy Pattern.

  ———

  ## Async execution

  Move from synchronous request/response execution to job-based simulation.

  Example future model:

  - POST /simulate creates a simulation job
  - GET /simulate/{id} returns status and results

  This would support longer or more complex runs.

  ———

  ## Richer simulation report

  Expose richer output such as:

  - event traces
  - per-request lifecycles
  - queue-depth timelines
  - latency distributions

  This would help with debugging and UI visualization.

  ———

  ## UI visualization

  A future frontend could allow users to:

  - design topologies visually
  - run simulations interactively
  - view bottlenecks and metrics graphically
  - compare scenarios

  This is one of the most compelling long-term directions for the project.

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

  Then execute it through the application runner:

  SimulationSummaryReport report = simulationRunner.run(scenario);

  ———

  ## Example REST request

  POST /simulate
  Content-Type: application/json

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

  ## Example response

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

  ## 14. Design Decisions

  ## Deterministic simulation

  The simulator is deterministic because simulation systems must produce reproducible results. This makes the engine suitable for debugging, testing, and architectural reasoning.

  ———

  ## Event-driven model

  An event-driven model matches the nature of distributed systems well. Requests move through nodes over time, and the engine reacts to discrete state transitions.

  ———

  ## Factory and registry patterns

  These were chosen to keep node creation extensible and avoid central switch-case growth. They help preserve the Open/Closed Principle.

  ———

  ## Layered architecture

  The layered design was chosen to keep concerns isolated:

  - engine remains reusable and framework-free
  - application layer manages orchestration
  - API layer handles transport concerns only

  This makes the system easier to evolve and easier to reason about.

  ———

  ## 15. Extensibility Guide

  ## Adding a new node type

  To add a new node type:

  1. Add a new NodeType
  2. Implement a new core SimNode
  3. Add a SimNodeFactory for creation
  4. Register it in the Spring context
  5. Add a node scenario configurer for the application layer
  6. Extend request DTO support if the API should expose it

  Because the design uses factories and registries, orchestration code should not need to change.

  ———

  ## Extending simulation behavior

  Examples of future extension points:

  - routing strategies
  - failure strategies
  - richer node configuration
  - advanced report generation
  - async execution model

  These extensions should be added in the layer where they belong:

  - engine behavior in simulation/
  - orchestration/reporting in application/
  - transport concerns in api/

  ———

  ## Where to plug new logic

  - core execution logic: simulation/
  - scenario rules and orchestration: application/
  - HTTP contract and validation: api/

  Keeping that separation is important. It prevents framework concerns from leaking into the engine.

  ———

  ## 16. Testing Strategy

  The project uses multiple levels of testing.

  ## Unit tests

  ### Builder tests

  Verify scenario-building rules such as:

  - duplicate node rejection
  - missing downstream validation
  - valid topology construction

  ### Runner tests

  Verify:

  - deterministic seeding
  - correct execution path
  - correct summary report mapping

  ### Factory/registry tests

  Verify:

  - correct factory resolution
  - duplicate factory registration failure

  ———

  ## Integration tests

  The API layer is tested end-to-end through Spring:

  - valid request -> 200 OK
  - invalid request -> structured 400
  - request flows through controller -> service -> runner -> engine

  ———

  ## Deterministic testing

  The engine design makes deterministic testing straightforward because:

  - event ordering is stable
  - time is virtual
  - execution is single-threaded
  - the same inputs always give the same outputs

  This is one of the biggest strengths of the project from a testing perspective.

  ———

  ## 17. Project Structure

  Example structure:

  src/main/java/com/koushik/systemSimulator/
  ├── simulation/
  │   ├── engine/
  │   ├── model/
  │   ├── node/
  │   ├── scenario/
  │   ├── scheduler/
  │   ├── state/
  │   └── metrics/
  ├── application/
  │   ├── builder/
  │   ├── factory/
  │   ├── model/
  │   ├── runner/
  │   └── service/
  └── api/
      ├── controller/
      ├── dto/
      │   ├── request/
      │   └── response/
      ├── mapper/
      └── exception/

  ### Layer responsibilities recap

  - simulation/
      - deterministic engine and runtime model
  - application/
      - scenario construction, orchestration, report shaping
  - api/
      - HTTP contract, validation, mapping, error handling

  ———

  ## Closing Note

  System Simulator is a strong foundation for a larger distributed systems design platform.

  Even in its current version, it demonstrates:

  - deterministic event-driven simulation
  - clean architecture
  - framework isolation
  - extensibility through patterns
  - production-grade API design

  It is already useful as:

  - a technical learning project
  - a portfolio piece
  - a foundation for future visualization and experimentation tools

  As the system evolves, the current architecture provides a solid base for richer node types, more advanced routing, asynchronous execution, and interactive system design.

