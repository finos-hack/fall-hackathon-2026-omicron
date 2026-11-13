import { useEffect, useMemo, useState } from 'react'

type AfcStatus = {
  processInstanceId: string
  state: string
  currentActivityIds?: string[]
  variables?: Record<string, unknown>
}

type AfcTask = {
  taskId: string
  name: string
  processInstanceId: string
}

const HIGH_RISK = {
  alertId: 'AFC-1001',
  customerId: 'CUST-88213',
  customerSegment: 'retail',
  pep: true,
  sanctionsNearMatch: true,
  transactions: [
    { amount: 9500, timestamp: '2026-11-10T09:00:00Z', channel: 'CASH', counterpartyCountry: 'US' },
    { amount: 9600, timestamp: '2026-11-10T11:00:00Z', channel: 'CASH', counterpartyCountry: 'US' },
    { amount: 9700, timestamp: '2026-11-10T13:00:00Z', channel: 'CASH', counterpartyCountry: 'US' },
  ],
}

const MEDIUM_RISK = {
  alertId: 'AFC-3003',
  customerId: 'CUST-55401',
  customerSegment: 'retail',
  pep: false,
  sanctionsNearMatch: false,
  transactions: [
    { amount: 9500, timestamp: '2026-11-10T09:00:00Z', channel: 'CASH', counterpartyCountry: 'US' },
    { amount: 9600, timestamp: '2026-11-10T10:00:00Z', channel: 'CASH', counterpartyCountry: 'US' },
    { amount: 9700, timestamp: '2026-11-10T11:00:00Z', channel: 'CASH', counterpartyCountry: 'US' },
    { amount: 210, timestamp: '2026-11-10T12:00:00Z', channel: 'CARD', counterpartyCountry: 'US' },
    { amount: 140, timestamp: '2026-11-10T12:30:00Z', channel: 'CARD', counterpartyCountry: 'US' },
    { amount: 300, timestamp: '2026-11-10T13:00:00Z', channel: 'CARD', counterpartyCountry: 'US' },
    { amount: 55, timestamp: '2026-11-10T13:15:00Z', channel: 'CARD', counterpartyCountry: 'US' },
    { amount: 89, timestamp: '2026-11-10T13:30:00Z', channel: 'CARD', counterpartyCountry: 'US' },
    { amount: 42, timestamp: '2026-11-10T14:00:00Z', channel: 'CARD', counterpartyCountry: 'US' },
    { amount: 67, timestamp: '2026-11-10T14:30:00Z', channel: 'CARD', counterpartyCountry: 'US' },
  ],
}

const LOW_RISK = {
  alertId: 'AFC-2002',
  customerId: 'CUST-11770',
  customerSegment: 'small-business',
  pep: false,
  sanctionsNearMatch: false,
  transactions: [
    { amount: 120, timestamp: '2026-11-10T10:00:00Z', channel: 'CARD', counterpartyCountry: 'US' },
  ],
}

export function AfcPanel() {
  const [payload, setPayload] = useState(() => JSON.stringify(HIGH_RISK, null, 2))
  const [instanceId, setInstanceId] = useState<string | null>(null)
  const [status, setStatus] = useState<AfcStatus | null>(null)
  const [tasks, setTasks] = useState<AfcTask[]>([])
  const [reviewer, setReviewer] = useState('compliance.officer')
  const [rationale, setRationale] = useState('Confirmed pattern; SAR drafting initiated.')
  const [decision, setDecision] = useState('escalated')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const gatePath = status?.variables?.afcPolicyPath as string | undefined
  const agentRec = status?.variables?.afcAgentRecommendation as string | undefined
  const openTask = useMemo(
    () => tasks.find((task) => task.processInstanceId === instanceId),
    [tasks, instanceId],
  )

  useEffect(() => {
    if (!instanceId) return
    let cancelled = false
    const tick = async () => {
      try {
        const [statusRes, tasksRes] = await Promise.all([
          fetch(`/api/afc/instances/${instanceId}/status`),
          fetch('/api/afc/tasks'),
        ])
        if (!statusRes.ok) return
        const nextStatus = (await statusRes.json()) as AfcStatus
        const nextTasks = tasksRes.ok ? ((await tasksRes.json()) as { tasks: AfcTask[] }).tasks : []
        if (!cancelled) {
          setStatus(nextStatus)
          setTasks(nextTasks)
        }
        if (nextStatus.state !== 'ACTIVE') {
          window.clearInterval(interval)
        }
      } catch {
        // keep polling; transient network errors are fine in the demo
      }
    }
    const interval = window.setInterval(tick, 3000)
    void tick()
    return () => {
      cancelled = true
      window.clearInterval(interval)
    }
  }, [instanceId])

  const submit = async () => {
    setBusy(true)
    setError(null)
    try {
      const body = JSON.parse(payload) as unknown
      const res = await fetch('/api/afc/alerts', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = (await res.json()) as { processInstanceId: string }
      setInstanceId(data.processInstanceId)
      setStatus(null)
      setTasks([])
    } catch (e) {
      setError(String(e))
    } finally {
      setBusy(false)
    }
  }

  const completeTask = async () => {
    if (!openTask) return
    setBusy(true)
    try {
      const params = new URLSearchParams({ decision, reviewer, rationale })
      await fetch(`/api/afc/tasks/${openTask.taskId}/complete?${params.toString()}`, {
        method: 'POST',
      })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel enter" style={{ marginTop: '2rem' }}>
      <div className="panel-head">
        <p className="eyebrow">Track 2 · Governance-as-Code</p>
        <h2>AML Alert Triage (AIGF UC-4)</h2>
        <p className="panel-copy">
          A second regulated use case on the steel thread pipeline. The agent gives an advisory
          recommendation; a deterministic rule engine and policy matrix decide the routing. The
          agent can add human oversight but never remove it.
        </p>
      </div>

      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '0.75rem' }}>
        <button type="button" className="btn ghost" onClick={() => setPayload(JSON.stringify(LOW_RISK, null, 2))}>
          Low risk sample
        </button>
        <button type="button" className="btn ghost" onClick={() => setPayload(JSON.stringify(MEDIUM_RISK, null, 2))}>
          Medium risk sample
        </button>
        <button type="button" className="btn ghost" onClick={() => setPayload(JSON.stringify(HIGH_RISK, null, 2))}>
          High risk sample
        </button>
      </div>

      <textarea
        value={payload}
        onChange={(e) => setPayload(e.target.value)}
        rows={12}
        spellCheck={false}
        style={{ width: '100%', fontFamily: 'monospace', fontSize: '0.8rem' }}
      />

      <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', margin: '0.75rem 0' }}>
        <button type="button" className="btn primary" onClick={submit} disabled={busy}>
          Start alert triage
        </button>
        {instanceId && <code style={{ fontSize: '0.8rem' }}>{instanceId}</code>}
      </div>

      {error && <p style={{ color: '#b91c1c' }}>{error}</p>}

      {status && (
        <div className="panel" style={{ padding: '1rem' }}>
          <p>
            <strong>State:</strong> {status.state}{' '}
            {status.currentActivityIds?.length ? `· ${status.currentActivityIds.join(', ')}` : ''}
          </p>
          <table className="table" style={{ width: '100%', marginTop: '0.5rem' }}>
            <tbody>
              <tr>
                <td>Deterministic rule score</td>
                <td>
                  <strong>{String(status.variables?.afcRuleScore ?? '…')}</strong>{' '}
                  <span style={{ opacity: 0.7 }}>({String(status.variables?.afcRuleSummary ?? '')})</span>
                </td>
              </tr>
              <tr>
                <td>Agent recommendation (advisory)</td>
                <td>{agentRec ?? '…'}</td>
              </tr>
              <tr>
                <td>Deterministic policy path</td>
                <td>
                  <strong>{gatePath ?? '…'}</strong>{' '}
                  <span style={{ opacity: 0.7 }}>({String(status.variables?.afcGateRationale ?? '')})</span>
                </td>
              </tr>
              <tr>
                <td>Final decision</td>
                <td>{String(status.variables?.afcFinalDecision ?? 'pending')}</td>
              </tr>
            </tbody>
          </table>

          {openTask && (
            <div style={{ marginTop: '0.75rem', display: 'grid', gap: '0.5rem' }}>
              <p>
                <strong>Human review required (mi-11)</strong> · {openTask.name}
              </p>
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                <select value={decision} onChange={(e) => setDecision(e.target.value)}>
                  <option value="escalated">Escalate to investigation</option>
                  <option value="closed">Close alert</option>
                </select>
                <input value={reviewer} onChange={(e) => setReviewer(e.target.value)} placeholder="Reviewer" />
                <input
                  value={rationale}
                  onChange={(e) => setRationale(e.target.value)}
                  placeholder="Rationale"
                  style={{ minWidth: '18rem' }}
                />
                <button type="button" className="btn primary" onClick={completeTask} disabled={busy}>
                  Complete review
                </button>
              </div>
            </div>
          )}

          {instanceId && (
            <p style={{ marginTop: '0.75rem' }}>
              <a href={`/api/afc/instances/${instanceId}/evidence.pdf`}>Download evidence pack (PDF)</a>
            </p>
          )}
        </div>
      )}
    </section>
  )
}
