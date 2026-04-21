package com.koushik.systemSimulator.simulation.scenario;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Topology {

	private final Map<String, NodeDefinition> nodesById;
	private final List<LinkDefinition> links;

	public Topology(Collection<NodeDefinition> nodes, Collection<LinkDefinition> links) {
		Objects.requireNonNull(nodes, "nodes must not be null");
		Objects.requireNonNull(links, "links must not be null");
		this.nodesById = nodes.stream()
				.collect(Collectors.toUnmodifiableMap(NodeDefinition::nodeId, Function.identity()));
		this.links = List.copyOf(links);
	}

	public NodeDefinition getNodeDefinition(String nodeId) {
		NodeDefinition definition = nodesById.get(nodeId);
		if (definition == null) {
			throw new IllegalArgumentException("No node definition found for nodeId=" + nodeId);
		}
		return definition;
	}

	public Collection<NodeDefinition> nodeDefinitions() {
		return nodesById.values();
	}

	public List<LinkDefinition> links() {
		return links;
	}

	public List<String> getDownstreams(String nodeId) {
		return links.stream()
				.filter(link -> link.sourceNodeId().equals(nodeId))
				.map(LinkDefinition::targetNodeId)
				.toList();
	}

	/** Returns node IDs that have no incoming links (in-degree = 0). */
	public List<String> getEntryNodes() {
		java.util.Set<String> hasIncoming = links.stream()
				.map(LinkDefinition::targetNodeId)
				.collect(Collectors.toUnmodifiableSet());
		return nodesById.keySet().stream()
				.filter(id -> !hasIncoming.contains(id))
				.sorted()
				.toList();
	}
}
