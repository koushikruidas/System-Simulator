import React from 'react'
import {
  LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, Legend
} from 'recharts'

const NODE_COLORS = ['#7c3aed', '#2563eb', '#059669', '#d97706', '#db2777', '#0891b2']

function MiniChart({ title, data, children }) {
  return (
    <div className="flex flex-col h-full">
      <div className="text-[9px] font-semibold text-gray-400 uppercase tracking-wide px-1 pb-0.5">{title}</div>
      <div className="flex-1">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 2, right: 4, bottom: 2, left: -20 }}>
            <XAxis dataKey="time" tick={{ fontSize: 8 }} tickLine={false} axisLine={false} />
            <YAxis tick={{ fontSize: 8 }} tickLine={false} axisLine={false} width={32} />
            <Tooltip
              contentStyle={{ fontSize: 9, padding: '2px 6px' }}
              itemStyle={{ fontSize: 9 }}
            />
            {children}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

export default function TimeSeriesPanel({ timeSeries, nodes }) {
  const nodeIds = nodes.map(n => n.id)

  const queueData = timeSeries.map(p => {
    const entry = { time: p.time }
    for (const id of nodeIds) entry[id] = p.queues?.[id] ?? 0
    return entry
  })

  const throughputData = timeSeries.map(p => ({
    time: p.time,
    ratio: p.incoming > 0 ? +(p.processed / p.incoming).toFixed(2) : 0,
  }))

  return (
    <div className="h-full bg-white border-t border-gray-200 grid grid-cols-2 grid-rows-2 gap-px p-1" style={{ fontSize: 10 }}>

      {/* 1. Requests per time-unit */}
      <MiniChart title="Requests / Time-Unit" data={timeSeries}>
        <Line type="monotone" dataKey="incoming"  stroke="#3b82f6" dot={false} strokeWidth={1.5} name="incoming" />
        <Line type="monotone" dataKey="processed" stroke="#10b981" dot={false} strokeWidth={1.5} name="processed" />
        <Line type="monotone" dataKey="dropped"   stroke="#ef4444" dot={false} strokeWidth={1.5} name="dropped" />
        <Legend wrapperStyle={{ fontSize: 8 }} />
      </MiniChart>

      {/* 2. Queue depth per node */}
      <MiniChart title="Queue Depth per Node" data={queueData}>
        {nodeIds.map((id, i) => (
          <Line key={id} type="monotone" dataKey={id}
            stroke={NODE_COLORS[i % NODE_COLORS.length]} dot={false} strokeWidth={1.5} name={id} />
        ))}
        <Legend wrapperStyle={{ fontSize: 8 }} />
      </MiniChart>

      {/* 3. Avg latency over time */}
      <MiniChart title="Avg Latency (ms)" data={timeSeries}>
        <Line type="monotone" dataKey="avgLatency" stroke="#f59e0b" dot={false} strokeWidth={1.5} name="avg latency" />
      </MiniChart>

      {/* 4. Throughput ratio */}
      <MiniChart title="Throughput Ratio (processed/incoming)" data={throughputData}>
        <Line type="monotone" dataKey="ratio" stroke="#8b5cf6" dot={false} strokeWidth={1.5} name="ratio" />
      </MiniChart>

    </div>
  )
}
