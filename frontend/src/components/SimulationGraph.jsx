import React, { useMemo } from 'react'
import ReactFlow, {
  Background,
  Controls,
  Handle,
  Position,
  ReactFlowProvider,
  MiniMap,
} from 'reactflow'
import 'reactflow/dist/style.css'
import { computeLayout } from '../utils/layout.js'

// ── Node type colours ──────────────────────────────────────────────────────────
const TYPE_STYLE = {
  LOAD_BALANCER: { bg: '#ede9fe', border: '#7c3aed', badge: '#7c3aed', label: 'LB' },
  SERVICE:       { bg: '#dbeafe', border: '#2563eb', badge: '#2563eb', label: 'SVC' },
  DATABASE:      { bg: '#d1fae5', border: '#059669', badge: '#059669', label: 'DB' },
}

function SimNode({ data }) {
  const style = TYPE_STYLE[data.nodeType] || TYPE_STYLE.SERVICE
  const highlighted = data.inPath
  const animating = data.animating

  return (
    <div
      className={`rounded-xl border-2 p-3 min-w-[110px] text-center shadow-sm transition-all ${animating ? 'node-animating' : ''}`}
      style={{
        background: highlighted ? '#fef3c7' : style.bg,
        borderColor: animating ? '#f59e0b' : highlighted ? '#f59e0b' : style.border,
        transform: animating ? 'scale(1.05)' : 'scale(1)',
      }}
    >
      <Handle type="target" position={Position.Left} style={{ background: style.border }} />

      <div
        className="inline-block px-1.5 py-0.5 rounded text-[10px] text-white font-bold mb-1"
        style={{ background: style.badge }}
      >
        {style.label}
      </div>

      <div className="font-semibold text-xs text-gray-800 truncate">{data.label}</div>
      <div className="text-[10px] text-gray-500">{data.latency}ms</div>

      {data.metrics && (
        <div className="mt-1.5 text-[10px] flex justify-center gap-2">
          <span className="text-emerald-600 font-medium">✓{data.metrics.processedRequests}</span>
          {data.metrics.droppedRequests > 0 && (
            <span className="text-red-500 font-medium">✗{data.metrics.droppedRequests}</span>
          )}
        </div>
      )}

      {data.distribution && Object.keys(data.distribution).length > 0 && (
        <div className="mt-1 text-[10px] text-gray-500 border-t border-gray-200 pt-1">
          {Object.entries(data.distribution).map(([id, cnt]) => (
            <div key={id}>{id}: {cnt}req</div>
          ))}
        </div>
      )}

      <Handle type="source" position={Position.Right} style={{ background: style.border }} />
    </div>
  )
}

const nodeTypes = { simNode: SimNode }

function GraphInner({ formConfig, simResult, selectedRequest, animStep }) {
  const pathSet = useMemo(() => new Set(selectedRequest?.path ?? []), [selectedRequest])

  const pathEdges = useMemo(() => {
    if (!selectedRequest?.path) return new Set()
    const edges = new Set()
    const p = selectedRequest.path
    for (let i = 0; i < p.length - 1; i++) edges.add(`${p[i]}-${p[i + 1]}`)
    return edges
  }, [selectedRequest])

  // LB distribution from results
  const lbDistribution = useMemo(() => {
    if (!simResult) return {}
    const dist = {}
    const lbIds = new Set(formConfig.nodes.filter(n => n.type === 'LOAD_BALANCER').map(n => n.id))
    for (const req of simResult.requests ?? []) {
      const path = req.path ?? []
      for (let i = 0; i < path.length - 1; i++) {
        if (lbIds.has(path[i])) {
          const key = path[i]
          if (!dist[key]) dist[key] = {}
          const downstream = path[i + 1]
          dist[key][downstream] = (dist[key][downstream] ?? 0) + 1
        }
      }
    }
    return dist
  }, [simResult, formConfig.nodes])

  const positions = useMemo(
    () => computeLayout(formConfig.nodes, formConfig.connections, formConfig.entryNodeId),
    [formConfig.nodes, formConfig.connections, formConfig.entryNodeId]
  )

  const nodes = useMemo(() =>
    formConfig.nodes.map((n, idx) => ({
      id: n.id,
      type: 'simNode',
      position: positions[n.id] ?? { x: idx * 230, y: 100 },
      data: {
        label: n.id,
        nodeType: n.type,
        latency: n.latency,
        strategy: n.strategy,
        inPath: pathSet.has(n.id),
        animating: selectedRequest && animStep >= 0 && selectedRequest.path[animStep] === n.id,
        metrics: simResult?.nodeMetrics?.[n.id] ?? null,
        distribution: lbDistribution[n.id] ?? {},
      },
    })),
    [formConfig.nodes, positions, pathSet, animStep, selectedRequest, simResult, lbDistribution]
  )

  const edges = useMemo(() =>
    formConfig.connections.map(c => {
      const edgeId = `${c.sourceNodeId}-${c.targetNodeId}`
      const inPath = pathEdges.has(edgeId)
      return {
        id: edgeId,
        source: c.sourceNodeId,
        target: c.targetNodeId,
        animated: inPath,
        style: { stroke: inPath ? '#f59e0b' : '#94a3b8', strokeWidth: inPath ? 2.5 : 1.5 },
      }
    }),
    [formConfig.connections, pathEdges]
  )

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      fitView
      fitViewOptions={{ padding: 0.3 }}
      nodesDraggable={false}
      nodesConnectable={false}
      elementsSelectable={false}
      proOptions={{ hideAttribution: true }}
    >
      <Background gap={20} color="#e2e8f0" />
      <Controls showInteractive={false} />
      <MiniMap nodeColor={n => TYPE_STYLE[n.data?.nodeType]?.badge ?? '#94a3b8'} zoomable pannable />
    </ReactFlow>
  )
}

export default function SimulationGraph(props) {
  return (
    <div className="w-full h-full">
      <ReactFlowProvider>
        <GraphInner {...props} />
      </ReactFlowProvider>
    </div>
  )
}
