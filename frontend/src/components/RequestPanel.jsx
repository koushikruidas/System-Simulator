import React from 'react'

function LatencyBar({ nodeId, queueTime, processingTime, totalLatency, isActive }) {
  const nodeTotal = queueTime + processingTime
  const barPct = totalLatency > 0 ? (nodeTotal / totalLatency) * 100 : 0
  const queuePct = nodeTotal > 0 ? (queueTime / nodeTotal) * 100 : 0
  const procPct = nodeTotal > 0 ? (processingTime / nodeTotal) * 100 : 100

  return (
    <div className={`mb-2.5 rounded px-2 py-1.5 ${isActive ? 'bg-amber-50 ring-1 ring-amber-200' : ''}`}>
      <div className="flex justify-between items-center mb-1">
        <span className={`text-xs font-medium ${isActive ? 'text-amber-700' : 'text-gray-700'}`}>
          {isActive ? '▶ ' : ''}{nodeId}
        </span>
        <span className="text-[10px] text-gray-500">{nodeTotal}ms</span>
      </div>
      <div className="h-5 bg-gray-100 rounded-full overflow-hidden">
        <div className="h-full flex rounded-full" style={{ width: `${Math.max(barPct, nodeTotal > 0 ? 2 : 0)}%` }}>
          {queueTime > 0 && (
            <div
              className="h-full bg-orange-400 flex items-center justify-center overflow-hidden"
              style={{ width: `${queuePct}%` }}
              title={`Queue: ${queueTime}ms`}
            >
              {queuePct > 20 && <span className="text-[9px] text-white font-medium px-0.5">{queueTime}ms</span>}
            </div>
          )}
          <div
            className="h-full bg-blue-500 flex items-center justify-center overflow-hidden flex-1"
            style={{ minWidth: 0 }}
            title={`Processing: ${processingTime}ms`}
          >
            {procPct > 15 && <span className="text-[9px] text-white font-medium px-0.5">{processingTime}ms</span>}
          </div>
        </div>
      </div>
    </div>
  )
}

function StatusBadge({ status }) {
  return status === 'COMPLETED'
    ? <span className="text-[10px] font-medium text-emerald-600 bg-emerald-50 px-1.5 py-0.5 rounded-full">✓ OK</span>
    : <span className="text-[10px] font-medium text-red-600 bg-red-50 px-1.5 py-0.5 rounded-full">✗ DROP</span>
}

export default function RequestPanel({ simResult, selectedId, onSelect, animStep, isPlaying, onPlayPause, onReset }) {
  if (!simResult) return null

  const requests = simResult.requests ?? []
  const selected = requests.find(r => r.requestId === selectedId)

  return (
    <div className="flex h-full border-t border-gray-200">

      {/* Request List */}
      <div className="w-64 flex-shrink-0 border-r border-gray-200 flex flex-col bg-white">
        <div className="px-4 py-2 border-b border-gray-100 flex items-center justify-between">
          <span className="text-xs font-semibold text-gray-700">Requests ({requests.length})</span>
          <div className="text-[10px] text-gray-500 flex gap-3">
            <span className="text-emerald-600">✓ {simResult.successfulRequests}</span>
            {simResult.failedRequests > 0 && <span className="text-red-600">✗ {simResult.failedRequests}</span>}
            <span className="text-gray-400">avg {simResult.averageLatency}ms</span>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto">
          {requests.map(req => (
            <button
              key={req.requestId}
              onClick={() => onSelect(req.requestId)}
              className={`w-full text-left px-4 py-2 border-b border-gray-50 hover:bg-gray-50 transition-colors ${
                req.requestId === selectedId ? 'bg-blue-50 border-l-2 border-l-blue-500' : ''
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-gray-800 truncate mr-2">{req.requestId}</span>
                <StatusBadge status={req.status} />
              </div>
              <div className="text-[10px] text-gray-500 mt-0.5">
                {req.totalLatency}ms · {req.path?.join(' → ')}
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Latency Breakdown */}
      <div className="flex-1 flex flex-col bg-white overflow-hidden">
        {selected ? (
          <>
            <div className="px-4 py-2 border-b border-gray-100 flex items-center justify-between">
              <div>
                <span className="text-xs font-semibold text-gray-700">{selected.requestId}</span>
                <span className="ml-2 text-[10px] text-gray-500">total {selected.totalLatency}ms</span>
                <StatusBadge status={selected.status} />
              </div>
              <div className="flex items-center gap-2">
                <div className="flex items-center gap-2 text-[10px] text-gray-500 mr-2">
                  <span className="flex items-center gap-1"><span className="inline-block w-3 h-3 rounded bg-orange-400"></span>Queue</span>
                  <span className="flex items-center gap-1"><span className="inline-block w-3 h-3 rounded bg-blue-500"></span>Processing</span>
                </div>
                <button
                  onClick={onReset}
                  className="text-[10px] px-2 py-1 rounded border border-gray-200 text-gray-600 hover:bg-gray-50"
                >↺</button>
                <button
                  onClick={onPlayPause}
                  className={`text-[10px] px-3 py-1 rounded font-medium transition-colors ${
                    isPlaying
                      ? 'bg-amber-500 text-white hover:bg-amber-600'
                      : 'bg-blue-600 text-white hover:bg-blue-700'
                  }`}
                >
                  {isPlaying ? '⏸ Pause' : '▶ Animate'}
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto px-4 py-3">
              {selected.breakdown?.map((b, idx) => (
                <LatencyBar
                  key={b.nodeId}
                  nodeId={b.nodeId}
                  queueTime={b.queueTime}
                  processingTime={b.processingTime}
                  totalLatency={selected.totalLatency}
                  isActive={animStep === idx}
                />
              ))}

              {/* Path summary */}
              <div className="mt-3 pt-3 border-t border-gray-100">
                <div className="text-[10px] text-gray-500 mb-1.5 font-medium uppercase tracking-wide">Path</div>
                <div className="flex items-center gap-1 flex-wrap">
                  {selected.path?.map((nodeId, idx) => (
                    <React.Fragment key={idx}>
                      <span className={`text-[10px] px-1.5 py-0.5 rounded font-medium ${
                        animStep === idx ? 'bg-amber-400 text-white' : 'bg-gray-100 text-gray-700'
                      }`}>{nodeId}</span>
                      {idx < selected.path.length - 1 && <span className="text-gray-300 text-[10px]">→</span>}
                    </React.Fragment>
                  ))}
                </div>
              </div>
            </div>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-gray-400">
            <div className="text-center">
              <div className="text-2xl mb-2">👆</div>
              <div className="text-xs">Select a request to see its latency breakdown</div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
