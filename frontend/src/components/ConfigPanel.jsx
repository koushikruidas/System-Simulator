import React, { useId } from 'react'
import { totalNodeCount, totalConnectionCount } from '../utils/expandTopology.js'

// ── Presets ────────────────────────────────────────────────────────────────────
const PRESETS = {
  simple: {
    label: 'Simple (1-1-1)',
    requestCount: 1,
    layers: [
      { id: 'p1', type: 'LOAD_BALANCER', count: 1, config: { capacity: 1, queueLimit: 0, latency: 1, strategy: 'ROUND_ROBIN' } },
      { id: 'p2', type: 'SERVICE',       count: 1, config: { capacity: 1, queueLimit: 1, latency: 5 } },
      { id: 'p3', type: 'DATABASE',      count: 1, config: { capacity: 1, queueLimit: 1, latency: 10 } },
    ],
  },
  dual: {
    label: '2-Service LB',
    requestCount: 6,
    layers: [
      { id: 'p1', type: 'LOAD_BALANCER', count: 1, config: { capacity: 4, queueLimit: 0, latency: 0, strategy: 'ROUND_ROBIN' } },
      { id: 'p2', type: 'SERVICE',       count: 2, config: { capacity: 2, queueLimit: 5, latency: 10 } },
      { id: 'p3', type: 'DATABASE',      count: 1, config: { capacity: 4, queueLimit: 10, latency: 5 } },
    ],
  },
  scaled: {
    label: 'Scaled (2-10-2)',
    requestCount: 20,
    layers: [
      { id: 'p1', type: 'LOAD_BALANCER', count: 2,  config: { capacity: 10, queueLimit: 0, latency: 1,  strategy: 'ROUND_ROBIN' } },
      { id: 'p2', type: 'SERVICE',       count: 10, config: { capacity: 3,  queueLimit: 10, latency: 15 } },
      { id: 'p3', type: 'DATABASE',      count: 2,  config: { capacity: 10, queueLimit: 20, latency: 8 } },
    ],
  },
}

// ── Colours per type ───────────────────────────────────────────────────────────
const TYPE_COLOR = {
  LOAD_BALANCER: { bar: '#7c3aed', light: '#ede9fe', text: 'text-violet-700', label: 'LB' },
  SERVICE:       { bar: '#2563eb', light: '#dbeafe', text: 'text-blue-700',   label: 'SVC' },
  DATABASE:      { bar: '#059669', light: '#d1fae5', text: 'text-emerald-700',label: 'DB' },
  CACHE:         { bar: '#d97706', light: '#fef3c7', text: 'text-amber-700',  label: 'CACHE' },
}
const TYPES = ['LOAD_BALANCER', 'SERVICE', 'DATABASE', 'CACHE']
const STRATEGIES = ['ROUND_ROBIN', 'LEAST_CONNECTIONS']

let _uid = 0
function uid() { return `layer-${++_uid}-${Date.now()}` }

// ── Small helpers ──────────────────────────────────────────────────────────────
const inputCls = 'w-full border border-gray-200 rounded px-2 py-1 text-xs focus:outline-none focus:border-blue-400 bg-white'
const selectCls = `${inputCls} cursor-pointer`

function NumInput({ value, min = 0, onChange }) {
  return (
    <input
      type="number" min={min} value={value}
      onChange={e => onChange(Math.max(min, parseInt(e.target.value) || min))}
      className={inputCls}
    />
  )
}

function Label({ children }) {
  return <div className="text-[10px] text-gray-400 font-medium uppercase tracking-wide mb-0.5">{children}</div>
}

// ── Layer Card ─────────────────────────────────────────────────────────────────
function LayerCard({ layer, index, total, onChange, onRemove, onDuplicate, onMoveUp, onMoveDown }) {
  const color = TYPE_COLOR[layer.type] ?? TYPE_COLOR.SERVICE

  function cfg(key, val) {
    onChange({ ...layer, config: { ...layer.config, [key]: val } })
  }

  return (
    <div className="rounded-lg border border-gray-200 overflow-hidden shadow-sm bg-white">
      {/* Header bar */}
      <div className="flex items-center gap-2 px-3 py-1.5" style={{ background: color.light, borderBottom: `2px solid ${color.bar}` }}>
        <span className={`text-[11px] font-bold ${color.text}`}>{color.label}</span>

        <select
          value={layer.type}
          onChange={e => onChange({ ...layer, type: e.target.value, config: { ...layer.config, strategy: undefined } })}
          className="flex-1 text-[11px] font-medium border-0 bg-transparent focus:outline-none cursor-pointer"
          style={{ color: color.bar }}
        >
          {TYPES.map(t => <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>)}
        </select>

        <div className="flex items-center gap-0.5 ml-auto">
          <button onClick={() => onMoveUp(index)}   disabled={index === 0}      title="Move up"
            className="w-5 h-5 flex items-center justify-center rounded text-gray-400 hover:text-gray-700 disabled:opacity-20 text-xs">↑</button>
          <button onClick={() => onMoveDown(index)} disabled={index === total - 1} title="Move down"
            className="w-5 h-5 flex items-center justify-center rounded text-gray-400 hover:text-gray-700 disabled:opacity-20 text-xs">↓</button>
          <button onClick={() => onDuplicate(index)} title="Duplicate"
            className="w-5 h-5 flex items-center justify-center rounded text-gray-400 hover:text-blue-500 text-xs">⊕</button>
          <button onClick={() => onRemove(index)} title="Remove"
            className="w-5 h-5 flex items-center justify-center rounded text-gray-300 hover:text-red-500 text-xs font-bold">✕</button>
        </div>
      </div>

      {/* Config body */}
      <div className="p-2.5 grid grid-cols-2 gap-x-2 gap-y-1.5">
        <div>
          <Label>Count</Label>
          <NumInput min={1} value={layer.count} onChange={v => onChange({ ...layer, count: v })} />
        </div>
        <div>
          <Label>Latency (ms)</Label>
          <NumInput min={0} value={layer.config.latency ?? 0} onChange={v => cfg('latency', v)} />
        </div>
        <div>
          <Label>Capacity</Label>
          <NumInput min={1} value={layer.config.capacity ?? 1} onChange={v => cfg('capacity', v)} />
        </div>
        <div>
          <Label>Queue Limit</Label>
          <NumInput min={0} value={layer.config.queueLimit ?? 0} onChange={v => cfg('queueLimit', v)} />
        </div>
        {layer.type === 'LOAD_BALANCER' && (
          <div className="col-span-2">
            <Label>Strategy</Label>
            <select value={layer.config.strategy ?? 'ROUND_ROBIN'} onChange={e => cfg('strategy', e.target.value)} className={selectCls}>
              {STRATEGIES.map(s => <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>)}
            </select>
          </div>
        )}
        {layer.type === 'CACHE' && (
          <>
            <div>
              <Label>Hit Rate (0–1)</Label>
              <input
                type="number" min={0} max={1} step={0.1}
                value={layer.config.hitRate ?? 0.5}
                onChange={e => cfg('hitRate', Math.min(1, Math.max(0, parseFloat(e.target.value) || 0)))}
                className={inputCls}
              />
            </div>
            <div>
              <Label>Hit Latency (ms)</Label>
              <NumInput min={0} value={layer.config.hitLatency ?? 1} onChange={v => cfg('hitLatency', v)} />
            </div>
          </>
        )}
      </div>
    </div>
  )
}

// ── Main ConfigPanel ───────────────────────────────────────────────────────────
export default function ConfigPanel({ layers, requestCount, onLayersChange, onRequestCountChange, onRun, loading, error }) {
  const nodeTotal = totalNodeCount(layers)
  const connTotal = totalConnectionCount(layers)

  function updateLayer(idx, updated) {
    onLayersChange(layers.map((l, i) => i === idx ? updated : l))
  }
  function removeLayer(idx) {
    onLayersChange(layers.filter((_, i) => i !== idx))
  }
  function duplicateLayer(idx) {
    const copy = { ...layers[idx], id: uid(), config: { ...layers[idx].config } }
    const next = [...layers]
    next.splice(idx + 1, 0, copy)
    onLayersChange(next)
  }
  function moveUp(idx) {
    if (idx === 0) return
    const next = [...layers]
    ;[next[idx - 1], next[idx]] = [next[idx], next[idx - 1]]
    onLayersChange(next)
  }
  function moveDown(idx) {
    if (idx === layers.length - 1) return
    const next = [...layers]
    ;[next[idx], next[idx + 1]] = [next[idx + 1], next[idx]]
    onLayersChange(next)
  }
  function addLayer() {
    onLayersChange([...layers, {
      id: uid(),
      type: 'SERVICE',
      count: 1,
      config: { capacity: 1, queueLimit: 1, latency: 5 },
    }])
  }

  function loadPreset(key) {
    const p = PRESETS[key]
    onLayersChange(p.layers.map(l => ({ ...l, id: uid(), config: { ...l.config } })))
    onRequestCountChange(p.requestCount)
  }

  return (
    <div className="flex flex-col h-full overflow-hidden">

      {/* Preset buttons */}
      <div className="px-3 pt-3 pb-2 border-b border-gray-100 space-y-1.5">
        <div className="text-[10px] font-semibold text-gray-400 uppercase tracking-wide mb-1">Presets</div>
        <div className="flex gap-1.5 flex-wrap">
          {Object.entries(PRESETS).map(([key, p]) => (
            <button key={key} onClick={() => loadPreset(key)}
              className="text-[10px] px-2 py-1 rounded border border-gray-200 text-gray-600 hover:bg-gray-50 hover:border-gray-300 transition-colors">
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {/* Request count */}
      <div className="px-3 py-2 border-b border-gray-100 flex items-center gap-3">
        <div className="flex-1">
          <Label>Requests</Label>
          <NumInput min={1} value={requestCount} onChange={onRequestCountChange} />
        </div>
        <div className="text-[10px] text-gray-400 text-right leading-relaxed pt-3">
          <div>{nodeTotal} nodes</div>
          <div>{connTotal} connections</div>
        </div>
      </div>

      {/* Layer list */}
      <div className="flex-1 overflow-y-auto px-3 py-2 space-y-2">
        {layers.map((layer, idx) => (
          <LayerCard
            key={layer.id}
            layer={layer}
            index={idx}
            total={layers.length}
            onChange={updated => updateLayer(idx, updated)}
            onRemove={removeLayer}
            onDuplicate={duplicateLayer}
            onMoveUp={moveUp}
            onMoveDown={moveDown}
          />
        ))}

        <button onClick={addLayer}
          className="w-full py-1.5 rounded-lg border-2 border-dashed border-gray-200 text-xs text-gray-400 hover:border-blue-300 hover:text-blue-500 transition-colors">
          + Add Layer
        </button>
      </div>

      {/* Footer */}
      <div className="px-3 pb-3 pt-2 border-t border-gray-100">
        {error && (
          <div className="mb-2 text-[10px] text-red-600 bg-red-50 border border-red-100 rounded px-2 py-1.5 break-words">
            {error}
          </div>
        )}
        <button
          onClick={onRun}
          disabled={loading || layers.length === 0}
          className={`w-full py-2 rounded-lg text-sm font-semibold transition-colors ${
            loading || layers.length === 0
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
