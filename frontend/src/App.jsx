import React, { useState, useEffect, useRef, useCallback } from 'react'
import ConfigPanel from './components/ConfigPanel.jsx'
import SimulationGraph from './components/SimulationGraph.jsx'
import RequestPanel from './components/RequestPanel.jsx'
import { runSimulation } from './api/simulate.js'

const DEFAULT_CONFIG = {
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

export default function App() {
  const [config, setConfig]           = useState(DEFAULT_CONFIG)
  const [simResult, setSimResult]     = useState(null)
  const [loading, setLoading]         = useState(false)
  const [error, setError]             = useState(null)

  const [selectedId, setSelectedId]   = useState(null)
  const [animStep, setAnimStep]       = useState(-1)
  const [isPlaying, setIsPlaying]     = useState(false)

  const intervalRef = useRef(null)

  const selectedRequest = simResult?.requests?.find(r => r.requestId === selectedId)
  const pathLength = selectedRequest?.path?.length ?? 0

  // Animation loop
  useEffect(() => {
    if (isPlaying) {
      intervalRef.current = setInterval(() => {
        setAnimStep(prev => {
          const next = prev + 1
          if (next >= pathLength) {
            setIsPlaying(false)
            return pathLength - 1
          }
          return next
        })
      }, 900)
    }
    return () => clearInterval(intervalRef.current)
  }, [isPlaying, pathLength])

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
      if (animStep >= pathLength - 1) setAnimStep(0)
      else if (animStep < 0) setAnimStep(0)
      setIsPlaying(true)
    }
  }, [isPlaying, animStep, pathLength])

  const handleReset = useCallback(() => {
    clearInterval(intervalRef.current)
    setIsPlaying(false)
    setAnimStep(-1)
  }, [])

  const handleConfigChange = useCallback((newConfig) => {
    setConfig(newConfig)
    setSimResult(null)
    setSelectedId(null)
    setAnimStep(-1)
    setIsPlaying(false)
    setError(null)
  }, [])

  async function handleRun() {
    setLoading(true)
    setError(null)
    setSimResult(null)
    setSelectedId(null)
    setAnimStep(-1)
    setIsPlaying(false)
    try {
      const result = await runSimulation(config)
      setSimResult(result)
      if (result.requests?.length > 0) {
        setSelectedId(result.requests[0].requestId)
      }
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col h-screen bg-slate-50">

      {/* Header */}
      <header className="h-11 bg-white border-b border-gray-200 flex items-center px-5 gap-4 shadow-sm flex-shrink-0">
        <span className="text-sm font-bold text-gray-800 tracking-tight">🔬 System Simulator</span>
        {simResult && (
          <div className="flex items-center gap-4 text-[11px] text-gray-500 ml-4">
            <span className="text-emerald-600 font-medium">✓ {simResult.successfulRequests} completed</span>
            {simResult.failedRequests > 0 && (
              <span className="text-red-500 font-medium">✗ {simResult.failedRequests} dropped</span>
            )}
            <span>avg <strong className="text-gray-700">{simResult.averageLatency}ms</strong></span>
            <span className="text-gray-300">|</span>
            {Object.entries(simResult.nodeMetrics ?? {}).map(([id, m]) => (
              <span key={id} className="text-gray-500">
                <span className="font-medium text-gray-700">{id}</span> {m.processedRequests}req
                {m.droppedRequests > 0 && <span className="text-red-500"> ({m.droppedRequests}drop)</span>}
              </span>
            ))}
          </div>
        )}
      </header>

      {/* Main: Config panel + Graph */}
      <div className="flex flex-1 overflow-hidden" style={{ minHeight: 0 }}>

        {/* Config panel */}
        <aside className="w-72 flex-shrink-0 bg-white border-r border-gray-200 flex flex-col overflow-hidden">
          <ConfigPanel
            config={config}
            onChange={handleConfigChange}
            onRun={handleRun}
            loading={loading}
            error={error}
          />
        </aside>

        {/* Graph + Request panel stacked */}
        <div className="flex-1 flex flex-col overflow-hidden">

          {/* Topology graph */}
          <div className="flex-1 overflow-hidden relative" style={{ minHeight: 0 }}>
            {config.nodes.length === 0 ? (
              <div className="h-full flex items-center justify-center text-gray-400 text-sm">
                Add nodes to visualise the topology
              </div>
            ) : (
              <SimulationGraph
                key={config.nodes.map(n => n.id).join(',')}
                formConfig={config}
                simResult={simResult}
                selectedRequest={selectedRequest ?? null}
                animStep={animStep}
              />
            )}
          </div>

          {/* Request panel */}
          {simResult && (
            <div className="h-64 flex-shrink-0 overflow-hidden">
              <RequestPanel
                simResult={simResult}
                selectedId={selectedId}
                onSelect={handleSelect}
                animStep={animStep}
                isPlaying={isPlaying}
                onPlayPause={handlePlayPause}
                onReset={handleReset}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
