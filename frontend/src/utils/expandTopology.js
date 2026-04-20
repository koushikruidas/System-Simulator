const TYPE_PREFIX = {
  LOAD_BALANCER: 'lb',
  SERVICE: 'svc',
  DATABASE: 'db',
  CACHE: 'cache',
}

/**
 * Expands topology layers into flat nodes + connections.
 * - Single node of a type gets a bare prefix (lb, svc, db).
 * - Multiple nodes of a type get indexed IDs (lb-1, lb-2, ...).
 * - Connections: every node in layer N → every node in layer N+1.
 */
export function expandTopology(layers) {
  if (!layers || layers.length === 0) {
    return { nodes: [], connections: [], entryNodeId: null }
  }

  // Count total nodes per prefix across all layers for clean ID generation
  const totalByPrefix = {}
  layers.forEach(layer => {
    const p = TYPE_PREFIX[layer.type] ?? layer.type.toLowerCase()
    totalByPrefix[p] = (totalByPrefix[p] ?? 0) + (layer.count || 1)
  })

  const counterByPrefix = {}

  const layerNodes = layers.map(layer => {
    const prefix = TYPE_PREFIX[layer.type] ?? layer.type.toLowerCase()
    const total = totalByPrefix[prefix]
    const count = Math.max(1, layer.count || 1)

    return Array.from({ length: count }, () => {
      counterByPrefix[prefix] = (counterByPrefix[prefix] ?? 0) + 1
      const n = counterByPrefix[prefix]
      const id = total === 1 ? prefix : `${prefix}-${n}`

      return {
        id,
        type: layer.type,
        capacity:   Math.max(1, layer.config?.capacity ?? 1),
        queueLimit: Math.max(0, layer.config?.queueLimit ?? 0),
        latency:    Math.max(0, layer.config?.latency ?? 0),
        strategy:   layer.type === 'LOAD_BALANCER'
          ? (layer.config?.strategy ?? 'ROUND_ROBIN')
          : null,
        hitRate:    layer.type === 'CACHE' ? (layer.config?.hitRate ?? 0.5) : undefined,
        hitLatency: layer.type === 'CACHE' ? (layer.config?.hitLatency ?? 1) : undefined,
      }
    })
  })

  const nodes = layerNodes.flat()

  const connections = []
  for (let i = 0; i < layerNodes.length - 1; i++) {
    const srcLayer = layers[i]
    const srcNodes = layerNodes[i]
    const tgtNodes = layerNodes[i + 1]

    if (srcLayer.type === 'LOAD_BALANCER') {
      // LB can fan out to ALL nodes in the next layer (routing node)
      for (const src of srcNodes) {
        for (const tgt of tgtNodes) {
          connections.push({ sourceNodeId: src.id, targetNodeId: tgt.id })
        }
      }
    } else {
      // SERVICE/DATABASE can only have ONE downstream — round-robin assignment
      srcNodes.forEach((src, idx) => {
        const tgt = tgtNodes[idx % tgtNodes.length]
        connections.push({ sourceNodeId: src.id, targetNodeId: tgt.id })
      })
    }
  }

  return {
    nodes,
    connections,
    entryNodeId: nodes.length > 0 ? nodes[0].id : null,
  }
}

/** Total node count across all layers. */
export function totalNodeCount(layers) {
  return (layers ?? []).reduce((sum, l) => sum + Math.max(1, l.count || 1), 0)
}

/** Total connection count for the expanded topology (mirrors expansion rules). */
export function totalConnectionCount(layers) {
  let count = 0
  for (let i = 0; i < (layers ?? []).length - 1; i++) {
    const srcCount = Math.max(1, layers[i].count || 1)
    const tgtCount = Math.max(1, layers[i + 1].count || 1)
    count += layers[i].type === 'LOAD_BALANCER'
      ? srcCount * tgtCount   // LB fan-out
      : srcCount              // 1-to-1 round-robin
  }
  return count
}
