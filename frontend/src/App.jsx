import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import ConfigPanel from './components/ConfigPanel.jsx'
import SimulationGraph from './components/SimulationGraph.jsx'
import RequestPanel from './components/RequestPanel.jsx'
import { runSimulation } from './api/simulate.js'
import { expandTopology } from './utils/expandTopology.js'

const DEFAULT_LAYERS = [
  { id: 'init-1', type: 'LOAD_BALANCER', count: 1, config: { capacity: 1, queueLimit: 0, latency: 1, strategy: 'ROUND_ROBIN' } },
  { id: 'init-2', type: 'SERVICE',       count: 1, config: { capacity: 1, queueLimit: 1, latency: 5 } },
  { id: 'init-3', type: 'DATABASE',      count: 1, config: { capacity: 1, queueLimit: 1, latency: 10 } },
]

export default function App() {
  const [layers, setLayers]           = useState(DEFAULT_LAYERS)
  const [requestCount, setRequestCount] = useState(1)
  const [simResult, setSimResult]     = useState(null)
  const [loading, setLoading]         = useState(false)
  const [error, setError]             = useState(null)

  const [selectedId, setSelectedId]   = useState(null)
  const [animStep, setAnimStep]       = useState(-1)
  const [isPlaying, setIsPlaying]     = useState(false)

  const intervalRef = useRef(null)

  // Derive flat topology from layers — memoized so it's stable across renders
  const { nodes, connections, entryNodeId } = useMemo(
    () => expandTopology(layers),
    [layers]
  )

  // formConfig shape that SimulationGraph and the API both consume
  const formConfig = useMemo(
    () => ({ nodes, connections, entryNodeId }),
    [nodes, connections, entryNodeId]
  )

  const selectedRequest = simResult?.requests?.find(r => r.requestId === selectedId)
  const pathLength = selectedRequest?.path?.length ?? 0

  // Animation loop
  useEffect(() => {
    if (isPlaying) {
      intervalRef.current = setInterval(() => {
        setAnimStep(prev => {
          const next = prev + 1
          if (next >= pathLength) { setIsPlaying(false); return pathLength - 1 }
          return next
        })
      }, 900)
    }
    return () => clearInterval(intervalRef.current)
  }, [isPlaying, pathLength])

  const stopAnimation = useCallback(() => {
    clearInterval(intervalRef.current)
    setIsPlaying(false)
    setAnimStep(-1)
  }, [])

  const handleSelect = useCallback((reqId) => {
    clearInterval(intervalRef.current)
    setIsPlaying(false)
    setAnimStep(-1)
    setSelectedId(reqId)
  }, [])

  const handlePlayPause = useCallback(() => {
    if (isPlaying) {
      clearInterval(intervalRef.current)
      setIsPlaying(false)
    } else {
      setAnimStep(prev => (prev >= pathLength - 1 || prev < 0) ? 0 : prev)
      setIsPlaying(true)
    }
  }, [isPlaying, pathLength])

  const handleLayersChange = useCallback((newLayers) => {
    setLayers(newLayers)
    setSimResult(null)
    setSelectedId(null)
    stopAnimation()
    setError(null)
  }, [stopAnimation])

  const handleRequestCountChange = useCallback((v) => {
    setRequestCount(v)
  }, [])

  async function handleRun() {
    if (nodes.length === 0) return
    setLoading(true)
    setError(null)
    setSimResult(null)
    setSelectedId(null)
    stopAnimation()
    try {
      const result = await runSimulation({ nodes, connections, entryNodeId, requestCount })
      setSimResult(result)
      if (result.requests?.length > 0) setSelectedId(result.requests[0].requestId)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  // Graph key: only remount React Flow when topology structure changes
  const graphKey = useMemo(
    () => nodes.map(n => n.id).join(','),
    [nodes]
  )

  return (
    <div className="flex flex-col h-screen bg-slate-50">

      {/* Header */}
      <header className="h-11 bg-white border-b border-gray-200 flex items-center px-5 gap-4 shadow-sm flex-shrink-0">
        <span className="text-sm font-bold text-gray-800 tracking-tight">🔬 System Simulator</span>
        {simResult && (
          <div className="flex items-center gap-4 text-[11px] text-gray-500 ml-4 overflow-hidden">
            <span className="text-emerald-600 font-medium flex-shrink-0">✓ {simResult.successfulRequests} completed</span>
            {simResult.failedRequests > 0 && (
              <span className="text-red-500 font-medium flex-shrink-0">✗ {simResult.failedRequests} dropped</span>
            )}
            <span className="flex-shrink-0">avg <strong className="text-gray-700">{simResult.averageLatency}ms</strong></span>
            <span className="text-gray-300 flex-shrink-0">|</span>
            <div className="flex gap-3 overflow-x-auto">
              {Object.entries(simResult.nodeMetrics ?? {}).map(([id, m]) => (
                <span key={id} className="text-gray-500 flex-shrink-0">
                  <span className="font-medium text-gray-700">{id}</span> {m.processedRequests}req
                  {m.droppedRequests > 0 && <span className="text-red-500"> ({m.droppedRequests}✗)</span>}
                </span>
              ))}
            </div>
          </div>
        )}
      </header>

      {/* Body */}
      <div className="flex flex-1 overflow-hidden" style={{ minHeight: 0 }}>

        {/* Config panel */}
        <aside className="w-72 flex-shrink-0 bg-white border-r border-gray-200 flex flex-col overflow-hidden">
          <ConfigPanel
            layers={layers}
            requestCount={requestCount}
            onLayersChange={handleLayersChange}
            onRequestCountChange={handleRequestCountChange}
            onRun={handleRun}
            loading={loading}
            error={error}
          />
        </aside>

        {/* Graph + Request panel */}
        <div className="flex-1 flex flex-col overflow-hidden">

          <div className="flex-1 overflow-hidden relative" style={{ minHeight: 0 }}>
            {nodes.length === 0 ? (
              <div className="h-full flex items-center justify-center text-gray-400 text-sm">
                Add a layer to visualise the topology
              </div>
            ) : (
              <SimulationGraph
                key={graphKey}
                formConfig={formConfig}
                simResult={simResult}
                selectedRequest={selectedRequest ?? null}
                animStep={animStep}
              />
            )}
          </div>

          {simResult && (
            <div className="h-64 flex-shrink-0 overflow-hidden">
              <RequestPanel
                simResult={simResult}
                selectedId={selectedId}
                onSelect={handleSelect}
                animStep={animStep}
                isPlaying={isPlaying}
                onPlayPause={handlePlayPause}
                onReset={stopAnimation}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
