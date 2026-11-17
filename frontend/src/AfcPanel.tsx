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

type AfcAcceptance = {
  passed: number
  total: number
  checks: { id: string; description: string; passed: boolean; detail: string }[]
}

const HIGH_RISK = {
  alertId: 'AFC-1001',
  customerId: 'CUST-88213-ACT-4111111111111111',
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

type PathMeta = { label: string; fg: string; bg: string; blurb: string }

const PATH_META: Record<string, PathMeta> = {
  MANDATORY_REVIEW: {
    label: 'Mandatory human review',
    fg: '#991b1b',
    bg: '#fee2e2',
    blurb: 'Rule score is 70 or above. A compliance officer must review this alert.',
  },
  REVIEW_WITH_ADVISORY: {
    label: 'Human review (agent is advisory)',
    fg: '#92400e',
    bg: '#fef3c7',
    blurb: 'Rule score is between 40 and 69. The agent opinion is attached but cannot decide.',
  },
  HUMAN_REVIEW_REQUIRED: {
    label: 'Human review required',
    fg: '#92400e',
    bg: '#fef3c7',
    blurb: 'Low rule score does not close a case. An investigator records every disposition.',
  },
}

function pathMetaFor(path: string | undefined): PathMeta {
  return (
    (path && PATH_META[path]) || {
      label: path ?? 'Waiting for the policy gate',
      fg: '#334155',
      bg: '#e2e8f0',
      blurb: 'The deterministic gate has not decided yet.',
    }
  )
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
  const [acceptance, setAcceptance] = useState<AfcAcceptance | null>(null)

  const vars = status?.variables ?? {}
  const gatePath = vars.afcPolicyPath as string | undefined
  const agentRec = vars.afcAgentRecommendation as string | undefined
  const ruleScore = vars.afcRuleScore as number | undefined
  const pathMeta = pathMetaFor(gatePath)
  const agentWantedClose = agentRec === 'close'
  const overrideMoment = agentWantedClose && gatePath !== undefined
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

  useEffect(() => {
    let cancelled = false
    fetch('/api/afc/acceptance-tests')
      .then((res) => (res.ok ? res.json() : null))
      .then((data: AfcAcceptance | null) => {
        if (!cancelled && data) setAcceptance(data)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

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

  const presetButton = (label: string, hint: string, value: unknown) => (
    <button
      type="button"
      className="btn ghost"
      style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 2 }}
      onClick={() => setPayload(JSON.stringify(value, null, 2))}
    >
      <span style={{ fontWeight: 600 }}>{label}</span>
      <span style={{ fontSize: '0.72rem', opacity: 0.7 }}>{hint}</span>
    </button>
  )

  return (
    <section className="panel enter" style={{ marginTop: '2rem' }}>
      <div className="panel-head">
        <p className="eyebrow">Track 2 · Governance-as-Code</p>
        <h2>AML Alert Triage (AIGF UC-4)</h2>
        <p className="panel-copy">
          A second regulated use case on the steel thread pipeline. The model gives an opinion, but
          the risk score only labels the review path. Every alert waits for an investigator
          disposition.
        </p>
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(14rem, 1fr))',
          gap: '0.75rem',
          margin: '0.5rem 0 1rem',
        }}
      >
        {[
          ['1. Rules score the alert', 'Pure Java, no model. Same input, same score.'],
          ['2. Model gives an opinion', 'Advisory only. It sees a redacted alert.'],
          ['3. Policy decides', 'The gate reads the score, not the model. Every case waits for an investigator.'],
        ].map(([title, detail]) => (
          <div
            key={title}
            style={{
              border: '1px solid #e2e8f0',
              borderRadius: 8,
              padding: '0.6rem 0.75rem',
            }}
          >
            <p style={{ margin: 0, fontWeight: 600 }}>{title}</p>
            <p style={{ margin: '0.2rem 0 0', fontSize: '0.8rem', opacity: 0.75 }}>{detail}</p>
          </div>
        ))}
      </div>

      {acceptance && (
        <p
          style={{
            margin: '0 0 0.75rem',
            fontSize: '0.85rem',
            color: acceptance.passed === acceptance.total ? '#065f46' : '#991b1b',
          }}
        >
          Acceptance (mi-5): {acceptance.passed}/{acceptance.total} deterministic checks pass
        </p>
      )}

      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '0.75rem' }}>
        {presetButton('High risk', 'structuring + PEP + sanctions', HIGH_RISK)}
        {presetButton('Medium risk', 'structuring + velocity', MEDIUM_RISK)}
        {presetButton('Low risk', 'single small card payment', LOW_RISK)}
      </div>

      <textarea
        value={payload}
        onChange={(e) => setPayload(e.target.value)}
        rows={10}
        spellCheck={false}
        style={{ width: '100%', fontFamily: 'monospace', fontSize: '0.8rem' }}
      />

      <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', margin: '0.75rem 0' }}>
        <button type="button" className="btn primary" onClick={submit} disabled={busy}>
          {busy ? 'Starting…' : 'Start alert triage'}
        </button>
        {instanceId && <code style={{ fontSize: '0.8rem' }}>{instanceId}</code>}
      </div>

      {error && <p style={{ color: '#b91c1c' }}>{error}</p>}

      {status && (
        <div style={{ marginTop: '0.5rem' }}>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(15rem, 1fr))',
              gap: '0.75rem',
            }}
          >
            <div style={{ border: '1px solid #e2e8f0', borderRadius: 8, padding: '0.75rem' }}>
              <p style={{ margin: 0, fontSize: '0.72rem', textTransform: 'uppercase', opacity: 0.6 }}>
                Deterministic score
              </p>
              <p style={{ margin: '0.25rem 0', fontSize: '2rem', fontWeight: 700 }}>
                {ruleScore ?? '…'}
              </p>
              <p style={{ margin: 0, fontSize: '0.8rem', opacity: 0.75 }}>
                {String(vars.afcRuleSummary ?? '')}
              </p>
            </div>

            <div style={{ border: '1px solid #e2e8f0', borderRadius: 8, padding: '0.75rem' }}>
              <p style={{ margin: 0, fontSize: '0.72rem', textTransform: 'uppercase', opacity: 0.6 }}>
                Agent opinion (advisory)
              </p>
              <p style={{ margin: '0.25rem 0', fontSize: '1.4rem', fontWeight: 700 }}>
                {agentRec ?? '…'}
              </p>
              <p style={{ margin: 0, fontSize: '0.8rem', opacity: 0.75 }}>
                {String(vars.afcAgentRationale ?? '')}
              </p>
              <p style={{ margin: '0.3rem 0 0', fontSize: '0.72rem', opacity: 0.65 }}>
                tools: {String(vars.afcToolCalls ?? 'none')}
              </p>
            </div>

            <div
              style={{
                border: `1px solid ${pathMeta.fg}33`,
                background: pathMeta.bg,
                color: pathMeta.fg,
                borderRadius: 8,
                padding: '0.75rem',
              }}
            >
              <p style={{ margin: 0, fontSize: '0.72rem', textTransform: 'uppercase', opacity: 0.7 }}>
                Deterministic policy decision
              </p>
              <p style={{ margin: '0.25rem 0', fontSize: '1.2rem', fontWeight: 700 }}>
                {pathMeta.label}
              </p>
              <p style={{ margin: 0, fontSize: '0.8rem' }}>{pathMeta.blurb}</p>
            </div>
          </div>

          {overrideMoment && (
            <p
              style={{
                marginTop: '0.75rem',
                padding: '0.6rem 0.75rem',
                borderRadius: 8,
                background: '#fef3c7',
                color: '#92400e',
                fontWeight: 600,
              }}
            >
              The agent wanted to close this alert. Policy still requires human review.
            </p>
          )}

          <p style={{ margin: '0.5rem 0 0', fontSize: '0.85rem', opacity: 0.75 }}>
            State: {status.state}
            {status.currentActivityIds?.length ? ` · ${status.currentActivityIds.join(', ')}` : ''}
            {vars.afcFinalDecision ? ` · final decision: ${String(vars.afcFinalDecision)}` : ''}
          </p>

          {openTask && (
            <div style={{ marginTop: '0.75rem', display: 'grid', gap: '0.5rem' }}>
              <p style={{ margin: 0 }}>
                <strong>Human review required (mi-11)</strong> · {openTask.name}
              </p>
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                <select value={decision} onChange={(e) => setDecision(e.target.value)}>
                  <option value="escalated">Escalate to investigation</option>
                  <option value="closed">Close alert</option>
                </select>
                <input
                  value={reviewer}
                  onChange={(e) => setReviewer(e.target.value)}
                  placeholder="Reviewer"
                />
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

          <p style={{ marginTop: '0.75rem' }}>
            <a href={`/api/afc/instances/${instanceId}/evidence.pdf`}>Download evidence pack (PDF)</a>
            {' · '}
            <a href="/architecture/afc-alert-triage.calm.json" target="_blank" rel="noreferrer">
              View CALM architecture
            </a>
          </p>
        </div>
      )}
    </section>
  )
}
