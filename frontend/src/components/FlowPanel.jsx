import React, { useState } from 'react'

function LatencyBreakdownCard({ breakdown, avgLatency }) {
  const [expanded, setExpanded] = useState(false)

  const totalProc  = breakdown.reduce((s, b) => s + b.avgProcessingTime, 0)
  const totalQueue = breakdown.reduce((s, b) => s + b.avgQueueTime, 0)
  const total = totalProc + totalQueue
  const qPct  = total > 0 ? (totalQueue / total) * 100 : 0
  const pPct  = total > 0 ? (totalProc  / total) * 100 : 100

  return (
    <div
      className="mx-2 mt-2 mb-1 border border-gray-100 rounded-md cursor-pointer select-none hover:border-gray-200 transition-colors"
      onClick={() => setExpanded(e => !e)}
    >
      {/* Header row */}
      <div className="px-2.5 pt-2 pb-1 flex items-center justify-between">
        <span className="text-[9px] text-gray-500 font-semibold uppercase tracking-wide">
          Latency Breakdown
        </span>
        <div className="flex items-center gap-2">
          <span className="text-[9px] text-gray-400">avg {avgLatency.toFixed(1)}ms</span>
          <span className="text-[9px] text-gray-300">{expanded ? '▲' : '▼'}</span>
        </div>
      </div>

      {/* Compact stacked bar */}
      <div className="px-2.5 pb-2">
        <div className="h-2 bg-gray-100 rounded-full overflow-hidden flex">
          {qPct > 0 && (
            <div
              className="h-full bg-orange-400"
              style={{ width: `${qPct}%` }}
              title={`Avg Queue: ${totalQueue.toFixed(1)}ms`}
            />
          )}
          <div
            className="h-full bg-blue-500"
            style={{ width: `${pPct}%` }}
            title={`Avg Processing: ${totalProc.toFixed(1)}ms`}
          />
        </div>
        <div className="flex gap-3 mt-1">
          {totalQueue > 0 && (
            <span className="flex items-center gap-1 text-[8px] text-gray-400">
              <span className="inline-block w-2 h-2 rounded-sm bg-orange-400 flex-shrink-0" />
              Q {totalQueue.toFixed(1)}ms
            </span>
          )}
          <span className="flex items-center gap-1 text-[8px] text-gray-400">
            <span className="inline-block w-2 h-2 rounded-sm bg-blue-500 flex-shrink-0" />
            P {totalProc.toFixed(1)}ms
          </span>
        </div>
      </div>

      {/* Expanded: per-node rows */}
      {expanded && (
        <div className="border-t border-gray-100 px-2.5 py-1.5">
          {breakdown.map(b => (
            <div key={b.nodeId} className="flex items-center gap-2 py-0.5">
              <span className="text-[10px] font-medium text-gray-700 w-16 truncate">{b.nodeId}</span>
              <span className="text-[10px] text-blue-600" title="Processing time">
                ⚙ {b.avgProcessingTime.toFixed(1)}ms
              </span>
              {b.avgQueueTime > 0 && (
                <span className="text-[10px] text-orange-500" title="Queue wait time">
                  ⏳ {b.avgQueueTime.toFixed(1)}ms
                </span>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function SampleRow({ trace, isSelected, onSelect, isPlaying, onPlayPause, onReset }) {
  const isActive = isSelected
  return (
    <div
      className={`flex items-center gap-2 px-2 py-1.5 rounded cursor-pointer hover:bg-gray-50 transition-colors ${isActive ? 'bg-amber-50 ring-1 ring-amber-200' : ''}`}
      onClick={() => onSelect(trace)}
    >
      <span className="text-[10px] font-medium text-gray-600 w-20 truncate">{trace.requestId}</span>
      <span className="text-[10px] text-gray-500">{trace.totalLatency}ms</span>
      <span className={`text-[10px] font-medium ${trace.status === 'COMPLETED' ? 'text-emerald-600' : 'text-red-500'}`}>
        {trace.status === 'COMPLETED' ? '✓' : '✗'}
      </span>
      {isActive && (
        <div className="ml-auto flex items-center gap-1">
          <button onClick={e => { e.stopPropagation(); onReset() }}
            className="text-[10px] px-1.5 py-0.5 rounded border border-gray-200 text-gray-600 hover:bg-gray-100">↺</button>
          <button onClick={e => { e.stopPropagation(); onPlayPause() }}
            className={`text-[10px] px-2 py-0.5 rounded font-medium ${isPlaying ? 'bg-amber-500 text-white' : 'bg-blue-600 text-white'}`}>
            {isPlaying ? '⏸' : '▶'}
          </button>
        </div>
      )}
    </div>
  )
}

export default function FlowPanel({ simResult, selectedFlowIdx, onFlowSelect, selectedSampleTrace, onSampleSelect, animStep, isPlaying, onPlayPause, onReset }) {
  if (!simResult) return null

  const flows = simResult.flowSummary ?? []
  const selectedFlow = flows[selectedFlowIdx] ?? null

  const allSamples = [
    ...(simResult.samples?.first ?? []),
    ...(simResult.samples?.slowest ?? []),
  ]
  const deduped = allSamples.filter((t, i, arr) => arr.findIndex(x => x.requestId === t.requestId) === i)
  const flowSamples = selectedFlow
    ? deduped.filter(t => JSON.stringify(t.path) === JSON.stringify(selectedFlow.path))
    : []

  return (
    <div className="flex h-full border-t border-gray-200 bg-white">

      {/* Flow list */}
      <div className="w-64 flex-shrink-0 border-r border-gray-200 flex flex-col">
        <div className="px-3 py-2 border-b border-gray-100 flex items-center justify-between flex-shrink-0">
          <span className="text-xs font-semibold text-gray-700">Traffic Breakdown</span>
          <div className="flex gap-2 text-[10px]">
            <span className="text-emerald-600">✓ {simResult.successfulRequests}</span>
            {simResult.failedRequests > 0 && <span className="text-red-500">✗ {simResult.failedRequests}</span>}
            <span className="text-gray-400">avg {simResult.averageLatency}ms</span>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto">
          {flows.map((flow, idx) => {
            const isSelected = idx === selectedFlowIdx
            const pct = simResult.totalRequests > 0 ? (flow.count / simResult.totalRequests) * 100 : 0
            const isOk = flow.outcome === 'COMPLETED'
            return (
              <button
                key={idx}
                onClick={() => onFlowSelect(idx)}
                className={`w-full text-left px-3 py-2 border-b border-gray-50 hover:bg-gray-50 transition-colors ${isSelected ? 'bg-blue-50 border-l-2 border-l-blue-500' : ''}`}
              >
                {/* Path chips */}
                <div className="flex items-center gap-1 flex-wrap mb-1">
                  {flow.path.map((node, i) => (
                    <React.Fragment key={i}>
                      <span className="text-[9px] px-1 py-0.5 rounded bg-gray-100 text-gray-700 font-medium">{node}</span>
                      {i < flow.path.length - 1 && <span className="text-gray-300 text-[9px]">→</span>}
                    </React.Fragment>
                  ))}
                  <span className={`ml-auto text-[9px] font-medium px-1 py-0.5 rounded-full ${isOk ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'}`}>
                    {isOk ? '✓ OK' : '✗ DROP'}
                  </span>
                </div>
                {/* Proportional count bar */}
                <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden mb-1">
                  <div className={`h-full rounded-full ${isOk ? 'bg-emerald-400' : 'bg-red-400'}`} style={{ width: `${pct}%` }} />
                </div>
                <div className="flex justify-between text-[9px] text-gray-500">
                  <span>{flow.count} req</span>
                  <span>avg {flow.avgLatency.toFixed(1)}ms</span>
                </div>
              </button>
            )
          })}
          {flows.length === 0 && (
            <div className="flex items-center justify-center h-full text-gray-400 text-xs">No flows</div>
          )}
        </div>
      </div>

      {/* Detail panel */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {selectedFlow ? (
          <>
            <div className="px-4 py-2 border-b border-gray-100 flex items-center gap-2 flex-shrink-0">
              <span className="text-xs font-semibold text-gray-700">
                {selectedFlow.path.join(' → ')}
              </span>
              <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full ${selectedFlow.outcome === 'COMPLETED' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'}`}>
                {selectedFlow.outcome === 'COMPLETED' ? '✓ OK' : '✗ DROP'}
              </span>
              <span className="text-[10px] text-gray-400 ml-auto">{selectedFlow.count} requests · avg {selectedFlow.avgLatency.toFixed(1)}ms</span>
            </div>
            <div className="flex-1 overflow-y-auto">

              {/* Collapsible latency breakdown card */}
              {(selectedFlow.breakdown?.length ?? 0) > 0 && (
                <LatencyBreakdownCard
                  breakdown={selectedFlow.breakdown}
                  avgLatency={selectedFlow.avgLatency}
                />
              )}

              {/* Samples */}
              {flowSamples.length > 0 && (
                <div className="px-2 pt-2 border-t border-gray-100 mt-1">
                  <div className="text-[9px] text-gray-400 uppercase tracking-wide font-medium mb-1 px-2">
                    Samples ({flowSamples.length})
                  </div>
                  {flowSamples.map(trace => (
                    <SampleRow
                      key={trace.requestId}
                      trace={trace}
                      isSelected={selectedSampleTrace?.requestId === trace.requestId}
                      onSelect={onSampleSelect}
                      animStep={animStep}
                      isPlaying={isPlaying}
                      onPlayPause={onPlayPause}
                      onReset={onReset}
                    />
                  ))}
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-gray-400 text-xs">
            Select a flow to see its breakdown
          </div>
        )}
      </div>
    </div>
  )
}
