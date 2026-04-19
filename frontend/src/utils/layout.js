const X_GAP = 230
const Y_GAP = 120
const OFFSET_X = 60
const OFFSET_Y = 80

export function computeLayout(nodes, connections, entryNodeId) {
  const nodeIds = nodes.map(n => n.id)

  // BFS from entry to assign levels
  const levels = {}
  const queue = []

  if (entryNodeId && nodeIds.includes(entryNodeId)) {
    levels[entryNodeId] = 0
    queue.push(entryNodeId)
  } else if (nodeIds.length > 0) {
    levels[nodeIds[0]] = 0
    queue.push(nodeIds[0])
  }

  while (queue.length > 0) {
    const current = queue.shift()
    const downstreams = connections
      .filter(c => c.sourceNodeId === current)
      .map(c => c.targetNodeId)

    for (const ds of downstreams) {
      if (!(ds in levels)) {
        levels[ds] = levels[current] + 1
        queue.push(ds)
      }
    }
  }

  // Assign any unreachable nodes to next available level
  nodeIds.forEach(id => {
    if (!(id in levels)) levels[id] = 0
  })

  // Group by level
  const byLevel = {}
  nodeIds.forEach(id => {
    const lvl = levels[id] ?? 0
    if (!byLevel[lvl]) byLevel[lvl] = []
    byLevel[lvl].push(id)
  })

  // Compute positions — vertically centred per level
  const positions = {}
  Object.entries(byLevel).forEach(([lvl, ids]) => {
    const count = ids.length
    ids.forEach((id, idx) => {
      positions[id] = {
        x: parseInt(lvl) * X_GAP + OFFSET_X,
        y: (idx - (count - 1) / 2) * Y_GAP + OFFSET_Y * 2,
      }
    })
  })

  return positions
}
