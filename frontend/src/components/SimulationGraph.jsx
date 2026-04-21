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
import { computeDagreLayout, NODE_H } from '../utils/layout.js'

const TYPE_STYLE = {
  LOAD_BALANCER: { bg: '#ede9fe', border: '#7c3aed', badge: '#7c3aed', label: 'LB' },
  SERVICE:       { bg: '#dbeafe', border: '#2563eb', badge: '#2563eb', label: 'SVC' },
  DATABASE:      { bg: '#d1fae5', border: '#059669', badge: '#059669', label: 'DB' },
  CACHE:         { bg: '#fef3c7', border: '#d97706', badge: '#d97706', label: 'CACHE' },
}

// Threshold above which edges are hidden to preserve rendering performance
const MAX_EDGES_RENDERED = 300

function SimNode({ data }) {
  const style = TYPE_STYLE[data.nodeType] || TYPE_STYLE.SERVICE
  const { inPath, animating, compact } = data

  return (
    <div
      className={`rounded-lg border-2 text-center shadow-sm transition-all select-none ${animating ? 'node-animating' : ''}`}
      style={{
        width: compact ? 80 : 130,
        background: animating ? '#fef3c7' : inPath ? '#fef9c3' : style.bg,
        borderColor: animating ? '#f59e0b' : inPath ? '#fbbf24' : style.border,
        padding: compact ? '4px 6px' : '8px 10px',
        transform: animating ? 'scale(1.06)' : 'scale(1)',
      }}
    >
      <Handle type="target" position={Position.Left} style={{ background: style.border }} />

      <div className="inline-block px-1 py-0.5 rounded text-[9px] text-white font-bold mb-0.5"
        style={{ background: style.badge }}>
        {style.label}
      </div>

      <div className="font-semibold text-gray-800 truncate" style={{ fontSize: compact ? 9 : 11 }}>
        {data.label}
      </div>

      {!compact && (
        <div className="text-[9px] text-gray-400">{data.latency}ms</div>
      )}

      {!compact && data.metrics && (
        <div className="mt-1 text-[9px] flex justify-center gap-2">
          <span className="text-emerald-600 font-medium">✓{data.metrics.processedRequests}</span>
          {data.metrics.droppedRequests > 0 && (
            <span className="text-red-500 font-medium">✗{data.metrics.droppedRequests}</span>
          )}
        </div>
      )}

      {!compact && data.distribution && Object.keys(data.distribution).length > 0 && (
        <div className="mt-1 text-[9px] text-gray-500 border-t border-gray-100 pt-1">
          {Object.entries(data.distribution).slice(0, 4).map(([id, cnt]) => (
            <div key={id} className="truncate">{id}: {cnt}</div>
          ))}
          {Object.keys(data.distribution).length > 4 && (
            <div className="text-gray-400">+{Object.keys(data.distribution).length - 4} more</div>
          )}
        </div>
      )}

      <Handle type="source" position={Position.Right} style={{ background: style.border }} />
    </div>
  )
}

const nodeTypes = { simNode: SimNode }

function GraphInner({ formConfig, simResult, selectedPath, animStep }) {
  const { nodes: configNodes, connections } = formConfig
  const nodeCount = configNodes.length
  const compact = nodeCount > 30

  const pathSet = useMemo(() => new Set(selectedPath ?? []), [selectedPath])

  const pathEdges = useMemo(() => {
    if (!selectedPath) return new Set()
    const s = new Set()
    for (let i = 0; i < selectedPath.length - 1; i++) s.add(`${selectedPath[i]}-${selectedPath[i + 1]}`)
    return s
  }, [selectedPath])

  const lbDistribution = useMemo(() => {
    if (!simResult) return {}
    const dist = {}
    const lbIds = new Set(configNodes.filter(n => n.type === 'LOAD_BALANCER').map(n => n.id))
    for (const flow of simResult.flowSummary ?? []) {
      const path = flow.path ?? []
      for (let i = 0; i < path.length - 1; i++) {
        if (lbIds.has(path[i])) {
          if (!dist[path[i]]) dist[path[i]] = {}
          dist[path[i]][path[i + 1]] = (dist[path[i]][path[i + 1]] ?? 0) + flow.count
        }
      }
    }
    return dist
  }, [simResult, configNodes])

  const positions = useMemo(() => {
    // LB nodes grow taller after a simulation run (distribution list adds ~14px per item).
    // Pass the actual per-node heights to dagre so it reserves the right amount of space.
    const nodeHeights = {}
    configNodes.forEach(n => {
      const distSize = Object.keys(lbDistribution[n.id] ?? {}).length
      if (distSize > 0) {
        const shownLines = Math.min(distSize, 4) + (distSize > 4 ? 1 : 0)
        nodeHeights[n.id] = NODE_H + 16 + shownLines * 14
      }
    })
    return computeDagreLayout(configNodes, connections, nodeHeights)
  }, [configNodes, connections, lbDistribution])

  const rfNodes = useMemo(() =>
    configNodes.map((n, idx) => ({
      id: n.id,
      type: 'simNode',
      position: positions[n.id] ?? { x: idx * 160, y: 100 },
      data: {
        label: n.id,
        nodeType: n.type,
        latency: n.latency,
        compact,
        inPath: pathSet.has(n.id),
        animating: selectedPath != null && animStep >= 0 && selectedPath[animStep] === n.id,
        metrics: simResult?.nodeMetrics?.[n.id] ?? null,
        distribution: lbDistribution[n.id] ?? {},
      },
    })),
    [configNodes, positions, pathSet, animStep, selectedPath, simResult, lbDistribution, compact]
  )

  const tooManyEdges = connections.length > MAX_EDGES_RENDERED

  const rfEdges = useMemo(() => {
    if (tooManyEdges) {
      // Only render path edges when there are too many total edges
      return (selectedPath
        ? selectedPath.slice(0, -1).map((src, i) => {
            const tgt = selectedPath[i + 1]
            const id = `${src}-${tgt}`
            return { id, source: src, target: tgt, animated: true, style: { stroke: '#f59e0b', strokeWidth: 2.5 } }
          })
        : [])
    }
    return connections.map(c => {
      const id = `${c.sourceNodeId}-${c.targetNodeId}`
      const inPath = pathEdges.has(id)
      return {
        id,
        source: c.sourceNodeId,
        target: c.targetNodeId,
        animated: inPath,
        style: { stroke: inPath ? '#f59e0b' : '#cbd5e1', strokeWidth: inPath ? 2.5 : 1 },
      }
    })
  }, [connections, pathEdges, selectedPath, tooManyEdges])

  return (
    <ReactFlow
      nodes={rfNodes}
      edges={rfEdges}
      nodeTypes={nodeTypes}
      fitView
      fitViewOptions={{ padding: 0.25 }}
      minZoom={0.05}
      maxZoom={2}
      nodesDraggable={false}
      nodesConnectable={false}
      elementsSelectable={false}
      proOptions={{ hideAttribution: true }}
    >
      <Background gap={24} color="#e2e8f0" />
      <Controls showInteractive={false} />
      <MiniMap
        nodeColor={n => TYPE_STYLE[n.data?.nodeType]?.badge ?? '#94a3b8'}
        zoomable pannable
        style={{ width: 120, height: 80 }}
      />
      {tooManyEdges && (
        <div style={{
          position: 'absolute', bottom: 10, left: '50%', transform: 'translateX(-50%)',
          background: 'rgba(255,255,255,0.9)', border: '1px solid #e2e8f0',
          borderRadius: 6, padding: '3px 10px', fontSize: 10, color: '#64748b', zIndex: 5,
        }}>
          {connections.length} connections — only path edges shown. Select a request to highlight its route.
        </div>
      )}
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
