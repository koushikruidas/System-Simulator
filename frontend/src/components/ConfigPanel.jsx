import React from 'react'

const SIMPLE_EXAMPLE = {
  nodes: [
    { id: 'lb',      type: 'LOAD_BALANCER', capacity: 1,  queueLimit: 0,  latency: 1,  strategy: 'ROUND_ROBIN' },
    { id: 'service', type: 'SERVICE',       capacity: 1,  queueLimit: 1,  latency: 5,  strategy: null },
    { id: 'db',      type: 'DATABASE',      capacity: 1,  queueLimit: 1,  latency: 10, strategy: null },
  ],
  connections: [
    { sourceNodeId: 'lb', targetNodeId: 'service' },
    { sourceNodeId: 'service', targetNodeId: 'db' },
  ],
  requestCount: 1,
  entryNodeId: 'lb',
}

const COMPLEX_EXAMPLE = {
  nodes: [
    { id: 'lb', type: 'LOAD_BALANCER', capacity: 4,  queueLimit: 0,  latency: 0,  strategy: 'ROUND_ROBIN' },
    { id: 's1', type: 'SERVICE',       capacity: 2,  queueLimit: 5,  latency: 10, strategy: null },
    { id: 's2', type: 'SERVICE',       capacity: 2,  queueLimit: 5,  latency: 10, strategy: null },
    { id: 'db', type: 'DATABASE',      capacity: 4,  queueLimit: 10, latency: 5,  strategy: null },
  ],
  connections: [
    { sourceNodeId: 'lb', targetNodeId: 's1' },
    { sourceNodeId: 'lb', targetNodeId: 's2' },
    { sourceNodeId: 's1', targetNodeId: 'db' },
    { sourceNodeId: 's2', targetNodeId: 'db' },
  ],
  requestCount: 6,
  entryNodeId: 'lb',
}

const NODE_TYPES = ['LOAD_BALANCER', 'SERVICE', 'DATABASE']
const STRATEGIES = ['ROUND_ROBIN', 'LEAST_CONNECTIONS']

function Field({ label, children }) {
  return (
    <div>
      <label className="block text-[10px] text-gray-500 font-medium mb-0.5 uppercase tracking-wide">{label}</label>
      {children}
    </div>
  )
}

const inputCls = 'w-full border border-gray-200 rounded px-2 py-1 text-xs focus:outline-none focus:border-blue-400 bg-white'
const selectCls = `${inputCls} cursor-pointer`

export default function ConfigPanel({ config, onChange, onRun, loading, error }) {
  function updateNode(idx, key, value) {
    const nodes = config.nodes.map((n, i) => i === idx ? { ...n, [key]: value } : n)
    onChange({ ...config, nodes })
  }

  function addNode() {
    const node = { id: `node${config.nodes.length + 1}`, type: 'SERVICE', capacity: 1, queueLimit: 1, latency: 5, strategy: null }
    onChange({ ...config, nodes: [...config.nodes, node] })
  }

  function removeNode(idx) {
    const removed = config.nodes[idx].id
    const nodes = config.nodes.filter((_, i) => i !== idx)
    const connections = config.connections.filter(c => c.sourceNodeId !== removed && c.targetNodeId !== removed)
    onChange({ ...config, nodes, connections })
  }

  function addConnection() {
    if (config.nodes.length < 2) return
    onChange({
      ...config,
      connections: [...config.connections, {
        sourceNodeId: config.nodes[0].id,
        targetNodeId: config.nodes[1].id,
      }],
    })
  }

  function updateConnection(idx, key, value) {
    const connections = config.connections.map((c, i) => i === idx ? { ...c, [key]: value } : c)
    onChange({ ...config, connections })
  }

  function removeConnection(idx) {
    onChange({ ...config, connections: config.connections.filter((_, i) => i !== idx) })
  }

  const nodeOptions = config.nodes.map(n => (
    <option key={n.id} value={n.id}>{n.id}</option>
  ))

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className="px-4 pt-3 pb-2 border-b border-gray-100">
        <div className="flex gap-2">
          <button
            onClick={() => onChange(SIMPLE_EXAMPLE)}
            className="flex-1 text-[10px] px-2 py-1 rounded border border-gray-200 text-gray-600 hover:bg-gray-50"
          >Simple</button>
          <button
            onClick={() => onChange(COMPLEX_EXAMPLE)}
            className="flex-1 text-[10px] px-2 py-1 rounded border border-blue-200 text-blue-600 hover:bg-blue-50"
          >2-Service LB</button>
        </div>
      </div>

      {/* Scrollable body */}
      <div className="flex-1 overflow-y-auto px-4 py-3 space-y-4">

        {/* Global settings */}
        <div className="grid grid-cols-2 gap-2">
          <Field label="Requests">
            <input type="number" min="1" max="100" value={config.requestCount}
              onChange={e => onChange({ ...config, requestCount: Math.max(1, parseInt(e.target.value) || 1) })}
              className={inputCls} />
          </Field>
          <Field label="Entry Node">
            <select value={config.entryNodeId} onChange={e => onChange({ ...config, entryNodeId: e.target.value })} className={selectCls}>
              {nodeOptions}
            </select>
          </Field>
        </div>

        {/* Nodes */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-semibold text-gray-700">Nodes</span>
            <button onClick={addNode} className="text-[10px] text-blue-600 hover:text-blue-800 font-medium">+ Add</button>
          </div>
          <div className="space-y-2">
            {config.nodes.map((node, idx) => (
              <div key={idx} className="border border-gray-100 rounded-lg p-2 bg-white shadow-sm">
                <div className="flex items-center gap-1 mb-2">
                  <input
                    value={node.id}
                    onChange={e => updateNode(idx, 'id', e.target.value)}
                    className="flex-1 border-b border-gray-200 text-xs font-medium focus:outline-none focus:border-blue-400 bg-transparent pb-0.5"
                    placeholder="node-id"
                  />
                  <button onClick={() => removeNode(idx)} className="text-gray-300 hover:text-red-400 text-xs ml-1 font-bold">✕</button>
                </div>
                <div className="grid grid-cols-2 gap-1.5 text-[10px]">
                  <Field label="Type">
                    <select value={node.type} onChange={e => updateNode(idx, 'type', e.target.value)} className={selectCls}>
                      {NODE_TYPES.map(t => <option key={t} value={t}>{t.replace('_', ' ')}</option>)}
                    </select>
                  </Field>
                  {node.type === 'LOAD_BALANCER' && (
                    <Field label="Strategy">
                      <select value={node.strategy ?? 'ROUND_ROBIN'} onChange={e => updateNode(idx, 'strategy', e.target.value)} className={selectCls}>
                        {STRATEGIES.map(s => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
                      </select>
                    </Field>
                  )}
                  <Field label="Capacity">
                    <input type="number" min="1" value={node.capacity}
                      onChange={e => updateNode(idx, 'capacity', Math.max(1, parseInt(e.target.value) || 1))}
                      className={inputCls} />
                  </Field>
                  <Field label="Queue Limit">
                    <input type="number" min="0" value={node.queueLimit}
                      onChange={e => updateNode(idx, 'queueLimit', Math.max(0, parseInt(e.target.value) || 0))}
                      className={inputCls} />
                  </Field>
                  <Field label="Latency (ms)">
                    <input type="number" min="0" value={node.latency}
                      onChange={e => updateNode(idx, 'latency', Math.max(0, parseInt(e.target.value) || 0))}
                      className={inputCls} />
                  </Field>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Connections */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-semibold text-gray-700">Connections</span>
            <button onClick={addConnection} className="text-[10px] text-blue-600 hover:text-blue-800 font-medium">+ Add</button>
          </div>
          <div className="space-y-1.5">
            {config.connections.map((conn, idx) => (
              <div key={idx} className="flex items-center gap-1.5">
                <select value={conn.sourceNodeId} onChange={e => updateConnection(idx, 'sourceNodeId', e.target.value)} className={`${selectCls} flex-1`}>
                  {nodeOptions}
                </select>
                <span className="text-gray-400 text-xs">→</span>
                <select value={conn.targetNodeId} onChange={e => updateConnection(idx, 'targetNodeId', e.target.value)} className={`${selectCls} flex-1`}>
                  {nodeOptions}
                </select>
                <button onClick={() => removeConnection(idx)} className="text-gray-300 hover:text-red-400 text-xs font-bold">✕</button>
              </div>
            ))}
            {config.connections.length === 0 && (
              <p className="text-[10px] text-gray-400 italic">No connections yet.</p>
            )}
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="px-4 pb-4 pt-2 border-t border-gray-100">
        {error && (
          <div className="mb-2 text-[10px] text-red-600 bg-red-50 border border-red-100 rounded px-2 py-1.5 break-words">
            {error}
          </div>
        )}
        <button
          onClick={onRun}
          disabled={loading}
          className={`w-full py-2 rounded-lg text-sm font-semibold transition-colors ${
            loading
              ? 'bg-blue-300 text-white cursor-not-allowed'
              : 'bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800'
          }`}
        >
          {loading ? '⏳ Running…' : '▶ Run Simulation'}
        </button>
      </div>
    </div>
  )
}
