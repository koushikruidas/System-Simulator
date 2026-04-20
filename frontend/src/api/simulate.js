export async function runSimulation(config) {
  const body = {
    requestCount: config.requestCount,
    entryNodeId: config.entryNodeId,
    nodes: config.nodes.map(n => ({
      id: n.id,
      type: n.type,
      capacity: n.capacity,
      queueLimit: n.queueLimit,
      latency: n.latency,
      ...(n.strategy   != null ? { strategy: n.strategy }     : {}),
      ...(n.hitRate    != null ? { hitRate: n.hitRate }        : {}),
      ...(n.hitLatency != null ? { hitLatency: n.hitLatency }  : {}),
    })),
    connections: config.connections,
  }

  const res = await fetch('/simulate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || `Request failed: ${res.status}`)
  }

  return res.json()
}
