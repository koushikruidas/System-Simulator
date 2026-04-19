import dagre from '@dagrejs/dagre'

export const NODE_W = 130
export const NODE_H = 80

/**
 * Computes left-to-right dagre layout positions.
 * Returns a map of nodeId → { x, y } (top-left corner for React Flow).
 */
export function computeDagreLayout(nodes, connections) {
  const g = new dagre.graphlib.Graph()
  g.setDefaultEdgeLabel(() => ({}))
  g.setGraph({
    rankdir: 'LR',
    nodesep: 48,    // vertical gap between nodes in same rank
    ranksep: 110,   // horizontal gap between ranks
    marginx: 30,
    marginy: 30,
  })

  nodes.forEach(n => g.setNode(n.id, { width: NODE_W, height: NODE_H }))

  connections.forEach(c => {
    if (g.hasNode(c.sourceNodeId) && g.hasNode(c.targetNodeId)) {
      g.setEdge(c.sourceNodeId, c.targetNodeId)
    }
  })

  dagre.layout(g)

  const positions = {}
  nodes.forEach(n => {
    const pos = g.node(n.id)
    positions[n.id] = pos
      ? { x: pos.x - NODE_W / 2, y: pos.y - NODE_H / 2 }
      : { x: 0, y: 0 }
  })

  return positions
}
