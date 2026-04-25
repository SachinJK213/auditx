import React, { useState, useEffect, useCallback, useRef } from 'react';
import './App.css';

const KIBANA_URL = 'http://localhost:5601';

// ─── Design tokens ────────────────────────────────────────────────────────────
const C = {
  bg:       '#04080f',
  surface:  '#080f1c',
  surface2: '#0c1526',
  hi:       '#0d1829',
  border:   '#162035',
  border2:  '#1e2f4a',
  primary:  '#0057b8',
  accent:   '#00aaff',
  accent2:  '#3ec6ff',
  text:     '#e2eaf8',
  muted:    '#5a7aa8',
  dim:      '#2d4a6e',
  ok:       '#22c55e',
  err:      '#ef4444',
  warn:     '#f59e0b',
  purple:   '#8b5cf6',
  pink:     '#ec4899',
};

const RISK = {
  CRITICAL: { color: '#dc2626', bg: 'rgba(220,38,38,0.12)', label: 'CRITICAL' },
  HIGH:     { color: '#ea580c', bg: 'rgba(234,88,12,0.10)', label: 'HIGH' },
  MEDIUM:   { color: '#f59e0b', bg: 'rgba(245,158,11,0.10)', label: 'MEDIUM' },
  LOW:      { color: '#16a34a', bg: 'rgba(22,163,74,0.10)', label: 'LOW' },
  UNKNOWN:  { color: '#5a7aa8', bg: 'transparent', label: '—' },
};

function riskLevel(score) {
  if (score == null) return 'UNKNOWN';
  if (score >= 80) return 'CRITICAL';
  if (score >= 60) return 'HIGH';
  if (score >= 30) return 'MEDIUM';
  return 'LOW';
}

// ─── Shared styles ────────────────────────────────────────────────────────────
const inp = {
  background: C.hi, border: `1px solid ${C.border2}`, color: C.text,
  borderRadius: 7, padding: '8px 12px', fontSize: 13,
  width: '100%', boxSizing: 'border-box', outline: 'none',
  transition: 'border-color .15s',
};
const lbl = {
  color: C.muted, fontSize: 10, fontWeight: 700, letterSpacing: '1px',
  textTransform: 'uppercase', marginBottom: 5, display: 'block',
};

// ─── Atoms ────────────────────────────────────────────────────────────────────
function RiskBadge({ score }) {
  const lv = riskLevel(score);
  const r  = RISK[lv];
  return (
    <span style={{
      background: r.bg, color: r.color, border: `1px solid ${r.color}33`,
      borderRadius: 5, padding: '2px 9px', fontSize: 10, fontWeight: 800,
      letterSpacing: '0.6px', display: 'inline-block', minWidth: 68, textAlign: 'center',
    }}>
      {r.label}{score != null && lv !== 'UNKNOWN' ? ` ${Math.round(score)}` : ''}
    </span>
  );
}

function StatCard({ icon, label, value, color, sub, trend }) {
  return (
    <div style={{
      background: C.surface, border: `1px solid ${C.border}`, borderRadius: 12,
      padding: '20px 22px', flex: 1, minWidth: 160, position: 'relative', overflow: 'hidden',
    }}>
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, height: 2,
        background: `linear-gradient(90deg, ${color}88, transparent)`,
      }} />
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
        <div>
          <div style={{ ...lbl, marginBottom: 10 }}>{label}</div>
          <div style={{ color: color || C.text, fontSize: 32, fontWeight: 800, lineHeight: 1 }}>
            {value ?? '—'}
          </div>
          {sub && <div style={{ color: C.dim, fontSize: 11, marginTop: 7 }}>{sub}</div>}
        </div>
        <div style={{
          width: 40, height: 40, borderRadius: 10, background: `${color}14`,
          display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20,
        }}>{icon}</div>
      </div>
      {trend != null && (
        <div style={{
          marginTop: 12, paddingTop: 10, borderTop: `1px solid ${C.border}`,
          fontSize: 11, color: trend >= 0 ? C.err : C.ok,
        }}>
          {trend >= 0 ? '▲' : '▼'} {Math.abs(trend)}% vs yesterday
        </div>
      )}
    </div>
  );
}

function Pill({ children, active, onClick, color }) {
  return (
    <button onClick={onClick} style={{
      padding: '8px 18px', cursor: 'pointer', fontWeight: 600, fontSize: 12,
      background: active ? `${color || C.accent}1a` : 'transparent',
      color: active ? (color || C.accent) : C.muted,
      border: 'none',
      borderBottom: active ? `2px solid ${color || C.accent}` : '2px solid transparent',
      outline: 'none', transition: 'all .15s', whiteSpace: 'nowrap',
    }}>
      {children}
    </button>
  );
}

function EmptyState({ icon, msg }) {
  return (
    <div style={{ padding: '48px 24px', textAlign: 'center' }}>
      <div style={{ fontSize: 36, marginBottom: 12, opacity: 0.3 }}>{icon}</div>
      <div style={{ color: C.dim, fontSize: 13 }}>{msg}</div>
    </div>
  );
}

function Toast({ msg, type }) {
  if (!msg) return null;
  const bg = type === 'ok' ? '#052e16' : type === 'err' ? '#1a0505' : '#1a1200';
  const bd = type === 'ok' ? C.ok : type === 'err' ? C.err : C.warn;
  return (
    <div style={{
      position: 'fixed', bottom: 24, right: 24, zIndex: 999,
      background: bg, border: `1px solid ${bd}`, borderRadius: 9,
      padding: '12px 18px', fontSize: 13, color: bd,
      boxShadow: `0 4px 24px ${bd}22`, maxWidth: 360,
      animation: 'slideIn .2s ease',
    }}>
      {msg}
    </div>
  );
}

// ─── Bars chart ───────────────────────────────────────────────────────────────
function Bars({ data, title }) {
  if (!data || !Object.keys(data).length) {
    return (
      <div>
        <div style={{ ...lbl, marginBottom: 14 }}>{title}</div>
        <div style={{ color: C.dim, fontSize: 12, padding: '8px 0' }}>No data yet</div>
      </div>
    );
  }
  const palette = [C.accent, C.purple, C.ok, C.warn, C.pink, C.primary, C.err];
  const total   = Object.values(data).reduce((a, b) => a + b, 0);
  const entries = Object.entries(data).sort((a, b) => b[1] - a[1]);
  return (
    <div>
      <div style={{ ...lbl, marginBottom: 14 }}>{title}</div>
      {entries.map(([k, v], i) => {
        const pct = total > 0 ? Math.round((v / total) * 100) : 0;
        return (
          <div key={k} style={{ marginBottom: 11 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5, fontSize: 12 }}>
              <span style={{ color: C.text }}>{k}</span>
              <span style={{ color: C.muted }}>{v.toLocaleString()} <span style={{ color: C.dim }}>({pct}%)</span></span>
            </div>
            <div style={{ background: C.hi, borderRadius: 4, height: 5 }}>
              <div style={{
                background: palette[i % palette.length], width: `${pct}%`,
                height: 5, borderRadius: 4, transition: 'width .6s ease',
              }} />
            </div>
          </div>
        );
      })}
    </div>
  );
}

// ─── Events table ─────────────────────────────────────────────────────────────
function EventsTable({ rows, loading }) {
  const [filter, setFilter] = useState('');
  if (loading) return <EmptyState icon="⏳" msg="Loading events…" />;
  const filtered = filter
    ? rows.filter(e =>
        [e.userId, e.action, e.sourceIp, e.outcome].some(f =>
          f?.toLowerCase().includes(filter.toLowerCase())))
    : rows;
  if (!filtered.length) return <EmptyState icon="📭" msg={filter ? 'No matching events.' : 'No events for this period. Try sending one below.'} />;

  const th = {
    padding: '10px 14px', textAlign: 'left', fontWeight: 700, color: C.muted,
    fontSize: 10, letterSpacing: '0.8px', textTransform: 'uppercase',
    borderBottom: `1px solid ${C.border}`, whiteSpace: 'nowrap', background: C.bg,
  };
  const td = (extra = {}) => ({
    padding: '10px 14px', borderBottom: `1px solid ${C.border}22`, ...extra,
  });

  return (
    <div>
      <div style={{ padding: '12px 14px', borderBottom: `1px solid ${C.border}` }}>
        <input
          placeholder="Filter by user, action, IP, outcome…"
          value={filter} onChange={e => setFilter(e.target.value)}
          style={{ ...inp, width: 280 }}
        />
      </div>
      <div style={{ overflowX: 'auto', maxHeight: 460, overflowY: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead><tr>
            {['Time', 'User', 'Action', 'Source IP', 'Country', 'Outcome', 'Risk'].map(h =>
              <th key={h} style={th}>{h}</th>)}
          </tr></thead>
          <tbody>
            {filtered.map((e, i) => {
              const lv = riskLevel(e.riskScore);
              return (
                <tr key={e.eventId || i} style={{
                  background: RISK[lv].bg || (i % 2 === 0 ? C.bg : C.surface),
                  transition: 'background .15s',
                }}>
                  <td style={td({ color: C.muted, fontSize: 11, whiteSpace: 'nowrap' })}>
                    {e.timestamp ? new Date(e.timestamp).toLocaleString() : '—'}
                  </td>
                  <td style={td({ fontWeight: 600 })}>{e.userId || '—'}</td>
                  <td style={td({ color: C.accent })}>{e.action || '—'}</td>
                  <td style={td({ color: C.muted, fontFamily: 'monospace', fontSize: 12 })}>{e.sourceIp || '—'}</td>
                  <td style={td({ fontSize: 11 })}>
                    {e.country
                      ? <span style={{ color: C.muted }}>{e.countryCode} {e.city}</span>
                      : <span style={{ color: C.dim }}>—</span>}
                  </td>
                  <td style={td()}>
                    <span style={{
                      color: e.outcome === 'SUCCESS' ? C.ok : C.err,
                      fontWeight: 700, fontSize: 12,
                    }}>{e.outcome || '—'}</span>
                  </td>
                  <td style={td()}><RiskBadge score={e.riskScore} /></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ─── Alerts table ─────────────────────────────────────────────────────────────
function AlertsTable({ rows, loading }) {
  if (loading) return <EmptyState icon="⏳" msg="Loading alerts…" />;
  if (!rows.length) return <EmptyState icon="✅" msg="No alerts for this period." />;

  const th = {
    padding: '10px 14px', textAlign: 'left', fontWeight: 700, color: C.muted,
    fontSize: 10, letterSpacing: '0.8px', textTransform: 'uppercase',
    borderBottom: `1px solid ${C.border}`, background: C.bg,
  };
  const td = (extra = {}) => ({
    padding: '10px 14px', borderBottom: `1px solid ${C.border}22`, ...extra,
  });
  const statusColor = s =>
    s === 'OPEN' ? C.warn : s === 'WEBHOOK_FAILED' ? C.err : s === 'ACKNOWLEDGED' ? C.accent : C.ok;

  return (
    <div style={{ overflowX: 'auto', maxHeight: 460, overflowY: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
        <thead><tr>
          {['Created', 'User', 'Risk', 'Status', 'Alert ID'].map(h =>
            <th key={h} style={th}>{h}</th>)}
        </tr></thead>
        <tbody>
          {rows.map((a, i) => (
            <tr key={a.alertId || i} style={{ background: i % 2 === 0 ? C.bg : C.surface }}>
              <td style={td({ color: C.muted, fontSize: 11, whiteSpace: 'nowrap' })}>
                {a.createdAt ? new Date(a.createdAt).toLocaleString() : '—'}
              </td>
              <td style={td({ fontWeight: 600 })}>{a.userId || '—'}</td>
              <td style={td()}><RiskBadge score={a.riskScore} /></td>
              <td style={td()}>
                <span style={{
                  color: statusColor(a.status), fontWeight: 700, fontSize: 11,
                  background: `${statusColor(a.status)}18`, padding: '2px 8px',
                  borderRadius: 4, letterSpacing: '0.5px',
                }}>
                  {a.status}
                </span>
              </td>
              <td style={td({ color: C.dim, fontFamily: 'monospace', fontSize: 11 })}>
                {a.alertId?.slice(0, 20)}…
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ─── Live Feed ────────────────────────────────────────────────────────────────
const MAX_VISIBLE = 200;
const RING_SIZE   = 5000;

function LiveFeed({ tenant }) {
  const [connected, setConnected] = useState(false);
  const [paused,    setPaused]    = useState(false);
  const [rows,      setRows]      = useState([]);
  const [eps,       setEps]       = useState(0);
  const [total,     setTotal]     = useState(0);

  const bufferRef   = useRef([]);
  const pausedRef   = useRef(false);
  const epsCountRef = useRef(0);
  const esRef       = useRef(null);

  useEffect(() => { pausedRef.current = paused; }, [paused]);

  useEffect(() => {
    const id = setInterval(() => {
      setEps(epsCountRef.current);
      epsCountRef.current = 0;
    }, 1000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    const id = setInterval(() => {
      if (!pausedRef.current && bufferRef.current.length > 0) {
        setRows(prev => {
          const combined = [...prev, ...bufferRef.current];
          return combined.slice(-MAX_VISIBLE);
        });
        setTotal(t => t + bufferRef.current.length);
        bufferRef.current = [];
      }
    }, 250);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    if (!tenant) return;
    if (esRef.current) esRef.current.close();
    setRows([]); setTotal(0); setConnected(false);

    const es = new EventSource(`/stream/api/v1/stream/live?tenantId=${encodeURIComponent(tenant)}`);
    esRef.current = es;
    es.addEventListener('log', e => {
      try {
        const ev = JSON.parse(e.data);
        epsCountRef.current += 1;
        bufferRef.current.push(ev);
        if (bufferRef.current.length > RING_SIZE) bufferRef.current.shift();
      } catch { /* skip malformed */ }
    });
    es.onopen  = () => setConnected(true);
    es.onerror = () => setConnected(false);
    return () => { es.close(); setConnected(false); };
  }, [tenant]);

  const th = {
    padding: '9px 12px', textAlign: 'left', fontWeight: 700, color: C.muted,
    fontSize: 10, letterSpacing: '0.7px', textTransform: 'uppercase',
    borderBottom: `1px solid ${C.border}`, whiteSpace: 'nowrap',
    position: 'sticky', top: 0, background: C.bg, zIndex: 1,
  };
  const td = { padding: '8px 12px', borderBottom: `1px solid ${C.border}11`, fontSize: 12 };

  return (
    <div>
      {/* Toolbar */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 16, padding: '10px 14px',
        borderBottom: `1px solid ${C.border}`, background: C.surface2, flexWrap: 'wrap',
      }}>
        {/* Live dot */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
          <div style={{
            width: 8, height: 8, borderRadius: '50%',
            background: connected ? C.ok : C.err,
            boxShadow: connected ? `0 0 8px ${C.ok}` : 'none',
            animation: connected && !paused ? 'livePulse 1.8s ease infinite' : 'none',
          }} />
          <span style={{ fontSize: 11, fontWeight: 800, color: connected ? C.ok : C.muted,
                         letterSpacing: '0.8px' }}>
            {connected ? (paused ? 'PAUSED' : 'LIVE') : 'OFFLINE'}
          </span>
        </div>

        <div style={{ height: 14, width: 1, background: C.border }} />

        <span style={{ fontSize: 12, color: C.muted, fontFamily: 'monospace' }}>
          <span style={{ color: eps > 0 ? C.accent2 : C.dim, fontWeight: 700 }}>{eps}</span>
          <span style={{ color: C.dim }}> eps</span>
        </span>

        <span style={{ fontSize: 12, color: C.muted, fontFamily: 'monospace' }}>
          <span style={{ color: C.text, fontWeight: 600 }}>{total.toLocaleString()}</span>
          <span style={{ color: C.dim }}> received</span>
        </span>

        <span style={{ fontSize: 11, color: C.dim }}>
          showing latest {rows.length}
        </span>

        <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
          <button
            onClick={() => { setPaused(p => !p); if (paused) bufferRef.current = []; }}
            style={{
              background: paused ? `${C.warn}22` : C.hi,
              color: paused ? C.warn : C.muted,
              border: `1px solid ${paused ? C.warn : C.border}`,
              borderRadius: 6, padding: '5px 14px', cursor: 'pointer',
              fontSize: 11, fontWeight: 700, letterSpacing: '0.5px',
            }}>
            {paused ? '▶ RESUME' : '⏸ PAUSE'}
          </button>
          <button
            onClick={() => { setRows([]); setTotal(0); setEps(0); bufferRef.current = []; epsCountRef.current = 0; }}
            style={{
              background: C.hi, color: C.muted, border: `1px solid ${C.border}`,
              borderRadius: 6, padding: '5px 14px', cursor: 'pointer', fontSize: 11,
            }}>
            Clear
          </button>
        </div>
      </div>

      {/* Stream */}
      {rows.length === 0 ? (
        <EmptyState
          icon={connected ? '📡' : '🔌'}
          msg={connected
            ? 'Stream connected. Waiting for events — send one using Quick Send below.'
            : 'Live stream offline. Ensure auditx-live-stream container is running.'}
        />
      ) : (
        <div style={{ overflowX: 'auto', maxHeight: 460, overflowY: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr>
              {['Time', 'User', 'Action', 'Source IP', 'Outcome', 'Risk', 'Rules Matched'].map(h =>
                <th key={h} style={th}>{h}</th>)}
            </tr></thead>
            <tbody>
              {[...rows].reverse().map((e, i) => {
                const lv = e.riskLevel || riskLevel(e.riskScore);
                const r  = RISK[lv] || RISK.UNKNOWN;
                return (
                  <tr key={e.eventId || i} style={{
                    background: lv === 'CRITICAL' ? 'rgba(220,38,38,0.08)'
                              : lv === 'HIGH'     ? 'rgba(234,88,12,0.06)'
                              : i % 2 === 0       ? C.bg : C.surface,
                    borderLeft: lv === 'CRITICAL' ? `3px solid ${r.color}` : '3px solid transparent',
                  }}>
                    <td style={{ ...td, color: C.dim, whiteSpace: 'nowrap', fontSize: 11 }}>
                      {e.receivedAt ? new Date(e.receivedAt).toLocaleTimeString() : '—'}
                    </td>
                    <td style={{ ...td, fontWeight: 600 }}>{e.userId || '—'}</td>
                    <td style={{ ...td, color: C.accent }}>{e.action || '—'}</td>
                    <td style={{ ...td, color: C.muted, fontFamily: 'monospace', fontSize: 11 }}>{e.sourceIp || '—'}</td>
                    <td style={td}>
                      <span style={{ color: e.outcome === 'SUCCESS' ? C.ok : C.err, fontWeight: 700, fontSize: 11 }}>
                        {e.outcome || '—'}
                      </span>
                    </td>
                    <td style={td}><RiskBadge score={e.riskScore} /></td>
                    <td style={{ ...td, color: C.dim, fontSize: 11, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {e.ruleMatches?.join(', ') || <span style={{ color: C.border2 }}>none</span>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ─── Quick Send ───────────────────────────────────────────────────────────────
const SCENARIOS = [
  { label: '✅ Normal Login',      userId: 'alice',   action: 'LOGIN',          sourceIp: '192.168.1.10', outcome: 'SUCCESS' },
  { label: '❌ Failed Login',      userId: 'mallory', action: 'LOGIN',          sourceIp: '203.0.113.42', outcome: 'FAILURE' },
  { label: '🗑 Delete User',       userId: 'bob',     action: 'DELETE_USER',    sourceIp: '10.0.0.5',    outcome: 'SUCCESS' },
  { label: '📤 Export Data',       userId: 'carol',   action: 'EXPORT_DATA',    sourceIp: '172.16.0.8',  outcome: 'SUCCESS' },
  { label: '🔐 MFA Challenge',     userId: 'dave',    action: 'MFA_CHALLENGE',  sourceIp: '198.51.100.5',outcome: 'FAILURE' },
  { label: '🔑 Change Password',   userId: 'eve',     action: 'CHANGE_PASSWORD',sourceIp: '10.0.1.12',   outcome: 'SUCCESS' },
];

function QuickSend({ tenant, onSent }) {
  const [form, setForm]     = useState(SCENARIOS[0]);
  const [status, setStatus] = useState('');
  const [busy, setBusy]     = useState(false);

  const apply = s => setForm({ ...s });

  const send = async () => {
    setBusy(true); setStatus('');
    const payload =
      `timestamp=${new Date().toISOString()} userId=${form.userId} action=${form.action} ` +
      `sourceIp=${form.sourceIp} tenantId=${tenant} outcome=${form.outcome}`;
    try {
      const res = await fetch('/ingest/api/events/raw', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-API-Key': 'demo-api-key' },
        body: JSON.stringify({ payload, payloadType: 'RAW', idempotencyKey: `ui-${Date.now()}` }),
      });
      const d = await res.json();
      if (res.status === 202) {
        setStatus({ type: 'ok', msg: `✓ Accepted — ${d.eventId}` });
        onSent?.();
      } else {
        setStatus({ type: 'err', msg: `✗ HTTP ${res.status}` });
      }
    } catch (e) {
      setStatus({ type: 'err', msg: `✗ ${e.message}` });
    }
    setBusy(false);
  };

  const f = (key) => (
    <div key={key}>
      <span style={lbl}>{key === 'userId' ? 'User' : key === 'action' ? 'Action' : key === 'sourceIp' ? 'Source IP' : 'Outcome'}</span>
      {key === 'outcome' ? (
        <select value={form[key]} onChange={e => setForm(p => ({ ...p, [key]: e.target.value }))} style={inp}>
          <option>SUCCESS</option><option>FAILURE</option>
        </select>
      ) : key === 'action' ? (
        <select value={form[key]} onChange={e => setForm(p => ({ ...p, [key]: e.target.value }))} style={inp}>
          {['LOGIN','LOGOUT','DELETE_USER','EXPORT_DATA','CHANGE_PASSWORD','MFA_CHALLENGE','READ','WRITE'].map(a =>
            <option key={a}>{a}</option>)}
        </select>
      ) : (
        <input value={form[key]} onChange={e => setForm(p => ({ ...p, [key]: e.target.value }))} style={inp} />
      )}
    </div>
  );

  return (
    <div style={{
      background: C.surface, border: `1px solid ${C.border}`,
      borderRadius: 12, padding: 24,
    }}>
      <div style={{ fontWeight: 800, fontSize: 15, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{
          background: `${C.accent}18`, color: C.accent, borderRadius: 7,
          width: 30, height: 30, display: 'inline-flex', alignItems: 'center',
          justifyContent: 'center', fontSize: 15,
        }}>⚡</span>
        Quick Send
      </div>

      {/* Scenario chips */}
      <div style={{ marginBottom: 16 }}>
        <span style={lbl}>Demo Scenarios</span>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 6 }}>
          {SCENARIOS.map((s, i) => (
            <button key={i} onClick={() => apply(s)} style={{
              background: form.userId === s.userId && form.action === s.action ? `${C.accent}20` : C.hi,
              color: form.userId === s.userId && form.action === s.action ? C.accent : C.muted,
              border: `1px solid ${form.userId === s.userId && form.action === s.action ? C.accent : C.border}`,
              borderRadius: 6, padding: '4px 11px', cursor: 'pointer', fontSize: 11, fontWeight: 600,
            }}>
              {s.label}
            </button>
          ))}
        </div>
      </div>

      {/* Form fields */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
        {['userId', 'action', 'sourceIp', 'outcome'].map(f)}
      </div>

      <button onClick={send} disabled={busy} style={{
        width: '100%', background: busy ? C.hi : C.primary,
        color: '#fff', border: 'none', borderRadius: 8, padding: '11px 0',
        cursor: busy ? 'not-allowed' : 'pointer', fontWeight: 700, fontSize: 14,
        opacity: busy ? 0.7 : 1, transition: 'all .15s', letterSpacing: '0.3px',
      }}>
        {busy ? 'Sending…' : 'Send Event →'}
      </button>

      {status && (
        <div style={{
          marginTop: 10, fontSize: 12, fontFamily: 'monospace',
          color: status.type === 'ok' ? C.ok : C.err,
          background: status.type === 'ok' ? '#052e1666' : '#1a050566',
          padding: '8px 12px', borderRadius: 6, wordBreak: 'break-all',
        }}>
          {status.msg}
        </div>
      )}
    </div>
  );
}

// ─── Report panel ─────────────────────────────────────────────────────────────
function ReportPanel({ tenant }) {
  const today = new Date().toISOString().split('T')[0];
  const [dates, setDates]   = useState({ start: '2024-01-01', end: today });
  const [busy, setBusy]     = useState(false);
  const [status, setStatus] = useState('');

  const download = async () => {
    setBusy(true); setStatus('');
    try {
      const res = await fetch('/api/reports/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ tenantId: tenant, startDate: dates.start, endDate: dates.end, reportType: 'FULL' }),
      });
      if (!res.ok) { setStatus(`✗ HTTP ${res.status}`); return; }
      const blob = await res.blob();
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href = url; a.download = `auditx-report-${tenant}-${dates.start}.html`;
      document.body.appendChild(a); a.click();
      document.body.removeChild(a); URL.revokeObjectURL(url);
      setStatus('✓ Report downloaded');
    } catch (e) { setStatus(`✗ ${e.message}`); }
    setBusy(false);
  };

  return (
    <div style={{
      background: C.surface, border: `1px solid ${C.border}`,
      borderRadius: 12, padding: 24,
    }}>
      <div style={{ fontWeight: 800, fontSize: 15, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{
          background: `${C.purple}18`, color: C.purple, borderRadius: 7,
          width: 30, height: 30, display: 'inline-flex', alignItems: 'center',
          justifyContent: 'center', fontSize: 15,
        }}>📄</span>
        Compliance Report
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 14 }}>
        <div>
          <span style={lbl}>Start Date</span>
          <input type="date" value={dates.start}
            onChange={e => setDates(d => ({ ...d, start: e.target.value }))} style={inp} />
        </div>
        <div>
          <span style={lbl}>End Date</span>
          <input type="date" value={dates.end}
            onChange={e => setDates(d => ({ ...d, end: e.target.value }))} style={inp} />
        </div>
      </div>

      <div style={{
        background: C.hi, border: `1px solid ${C.border}`, borderRadius: 8,
        padding: '10px 14px', marginBottom: 16, fontSize: 12, color: C.muted, lineHeight: 1.7,
      }}>
        Includes: event summary · action breakdown · high-risk events (≥70) · all alerts · tenant: <strong style={{ color: C.text }}>{tenant}</strong>
      </div>

      <button onClick={download} disabled={busy} style={{
        width: '100%', background: busy ? C.hi : C.purple,
        color: '#fff', border: 'none', borderRadius: 8, padding: '11px 0',
        cursor: busy ? 'not-allowed' : 'pointer', fontWeight: 700, fontSize: 14,
        opacity: busy ? 0.7 : 1, transition: 'all .15s',
      }}>
        {busy ? '⏳ Generating…' : '⬇ Download Report'}
      </button>

      {status && (
        <div style={{
          marginTop: 10, fontSize: 12, color: status.startsWith('✓') ? C.ok : C.err,
          fontFamily: 'monospace',
        }}>
          {status}
        </div>
      )}
    </div>
  );
}

// ─── Upload Panel ─────────────────────────────────────────────────────────────
function UploadPanel({ tenant, onUploaded }) {
  const [file, setFile]       = useState(null);
  const [dragOver, setDragOver] = useState(false);
  const [busy, setBusy]       = useState(false);
  const [result, setResult]   = useState(null);
  const [error, setError]     = useState('');

  const detectFormat = name => {
    if (!name) return 'Auto-detect';
    const n = name.toLowerCase();
    if (n.endsWith('.json') || n.endsWith('.jsonl')) return 'JSON / JSONL';
    if (n.endsWith('.csv')) return 'CSV';
    if (n.endsWith('.log') || n.endsWith('.syslog')) return 'Syslog / Raw';
    return 'Auto-detect';
  };

  const upload = async () => {
    if (!file) return;
    setBusy(true); setResult(null); setError('');
    const fd = new FormData();
    fd.append('file', file);
    try {
      const res = await fetch('/ingest/api/v1/ingest/upload', {
        method: 'POST',
        headers: { 'X-API-Key': 'demo-api-key' },
        body: fd,
      });
      if (!res.ok) { setError(`HTTP ${res.status} — check that auditx-ingestion is running`); setBusy(false); return; }
      const d = await res.json();
      setResult(d);
      onUploaded?.();
    } catch (e) { setError(e.message); }
    setBusy(false);
  };

  const FORMATS = [
    { fmt: 'JSON', color: C.accent,  ex: '{"userId":"alice","action":"LOGIN","sourceIp":"10.0.0.1","tenantId":"tenant-demo","outcome":"SUCCESS"}' },
    { fmt: 'CSV',  color: C.purple,  ex: '2024-01-01T00:00:00Z,alice,LOGIN,10.0.0.1,tenant-demo,SUCCESS' },
    { fmt: 'Syslog', color: C.warn,  ex: '<34>2024-01-01T00:00:00Z host app: userId=alice action=LOGIN outcome=SUCCESS sourceIp=10.0.0.1 tenantId=tenant-demo' },
    { fmt: 'Raw',  color: C.ok,      ex: 'timestamp=2024-01-01T00:00:00Z userId=alice action=LOGIN sourceIp=10.0.0.1 tenantId=tenant-demo outcome=SUCCESS' },
  ];

  return (
    <div style={{ padding: 20 }}>
      {/* Drop zone */}
      <div
        onDragOver={e => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={e => { e.preventDefault(); setDragOver(false); const f = e.dataTransfer.files[0]; if (f) { setFile(f); setResult(null); setError(''); } }}
        onClick={() => document.getElementById('ax-file-input').click()}
        style={{
          border: `2px dashed ${dragOver ? C.accent : file ? C.ok : C.border2}`,
          borderRadius: 12, padding: '32px 20px', textAlign: 'center',
          cursor: 'pointer', transition: 'all .2s',
          background: dragOver ? `${C.accent}09` : file ? `${C.ok}07` : 'transparent',
          marginBottom: 18,
        }}>
        <input
          id="ax-file-input" type="file"
          accept=".json,.jsonl,.csv,.log,.syslog,.txt"
          onChange={e => { setFile(e.target.files[0]); setResult(null); setError(''); }}
          style={{ display: 'none' }}
        />
        <div style={{ fontSize: 34, marginBottom: 8 }}>{file ? '📄' : '📁'}</div>
        {file ? (
          <div>
            <div style={{ color: C.ok, fontWeight: 700, fontSize: 14 }}>{file.name}</div>
            <div style={{ color: C.muted, fontSize: 11, marginTop: 5 }}>
              {(file.size / 1024).toFixed(1)} KB · Format: {detectFormat(file.name)}
            </div>
          </div>
        ) : (
          <div>
            <div style={{ color: C.muted, fontSize: 13, fontWeight: 600 }}>Drop a log file here or click to browse</div>
            <div style={{ color: C.dim, fontSize: 11, marginTop: 6 }}>Supports .json · .jsonl · .csv · .log · .syslog · .txt</div>
          </div>
        )}
      </div>

      {/* Format examples */}
      <div style={{ marginBottom: 18 }}>
        <span style={lbl}>Supported Formats — one event per line</span>
        {FORMATS.map(({ fmt, color, ex }) => (
          <div key={fmt} style={{
            marginBottom: 8, borderRadius: 8, overflow: 'hidden',
            border: `1px solid ${C.border}`, borderLeft: `3px solid ${color}`,
          }}>
            <div style={{ background: C.surface2, padding: '5px 12px', display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 10, fontWeight: 800, color, letterSpacing: '0.5px' }}>{fmt}</span>
            </div>
            <div style={{ background: C.hi, padding: '7px 12px', fontFamily: 'monospace', fontSize: 10, color: C.dim, wordBreak: 'break-all' }}>
              {ex}
            </div>
          </div>
        ))}
      </div>

      {/* Upload button */}
      <button onClick={upload} disabled={!file || busy} style={{
        width: '100%', background: !file || busy ? C.hi : C.primary,
        color: !file ? C.dim : '#fff', border: `1px solid ${!file ? C.border : C.primary}`,
        borderRadius: 8, padding: '12px 0', cursor: !file || busy ? 'not-allowed' : 'pointer',
        fontWeight: 700, fontSize: 14, opacity: busy ? 0.7 : 1, transition: 'all .15s',
      }}>
        {busy ? '⏳ Uploading…' : file ? `Upload ${file.name}` : 'Select a file first'}
      </button>

      {/* Result */}
      {result && (
        <div style={{
          marginTop: 14, background: result.accepted > 0 ? '#052e1666' : '#1a050566',
          border: `1px solid ${result.accepted > 0 ? C.ok : C.err}`,
          borderRadius: 8, padding: '12px 16px',
        }}>
          <div style={{ fontWeight: 700, fontSize: 13, color: result.accepted > 0 ? C.ok : C.err, marginBottom: 8 }}>
            {result.accepted > 0 ? `✓ ${result.accepted} event${result.accepted !== 1 ? 's' : ''} accepted` : '✗ Upload failed'}
          </div>
          <div style={{ display: 'flex', gap: 20, fontSize: 12 }}>
            <span style={{ color: C.muted }}>Total: <b style={{ color: C.text }}>{result.total}</b></span>
            <span style={{ color: C.muted }}>Accepted: <b style={{ color: C.ok }}>{result.accepted}</b></span>
            {result.failed > 0 && <span style={{ color: C.muted }}>Failed: <b style={{ color: C.err }}>{result.failed}</b></span>}
          </div>
        </div>
      )}

      {error && (
        <div style={{ marginTop: 14, color: C.err, fontSize: 12, background: '#1a050566', padding: '10px 14px', borderRadius: 8, fontFamily: 'monospace' }}>
          ✗ {error}
        </div>
      )}
    </div>
  );
}

// ─── Sources Panel ────────────────────────────────────────────────────────────
function SourcesPanel() {
  const SOURCES = [
    { icon: '📡', label: 'REST API',     desc: 'POST /api/events/raw · Key=value, JSON, CSV, Syslog', color: C.accent,  active: true },
    { icon: '📄', label: 'File Upload',  desc: 'POST /api/v1/ingest/upload · Batch file ingestion',   color: C.purple, active: true },
    { icon: '🔗', label: 'Webhook',      desc: 'Incoming webhooks — Phase 2',                          color: C.warn,   active: false },
    { icon: '☁',  label: 'CloudWatch',  desc: 'AWS CloudWatch Logs — Phase 2',                        color: C.ok,     active: false },
    { icon: '🖥',  label: 'Syslog UDP',  desc: 'UDP 514 Syslog receiver — Phase 2',                   color: C.pink,   active: false },
  ];

  const FORMATS = [
    { fmt: 'JSON',   color: C.accent,  note: 'Any JSON object with standard fields' },
    { fmt: 'CSV',    color: C.purple,  note: 'timestamp,userId,action,sourceIp,tenantId,outcome' },
    { fmt: 'Syslog', color: C.warn,    note: 'RFC 3164: <priority>timestamp host app: message' },
    { fmt: 'Raw',    color: C.ok,      note: 'Key=value pairs: timestamp= userId= action= ...' },
  ];

  return (
    <div style={{ padding: 20 }}>
      <div style={{ fontWeight: 800, fontSize: 15, marginBottom: 18 }}>Ingestion Sources</div>

      {SOURCES.map(s => (
        <div key={s.label} style={{
          background: s.active ? C.hi : 'transparent',
          border: `1px solid ${s.active ? C.border2 : C.border}`,
          borderRadius: 10, padding: '12px 14px', marginBottom: 10,
          display: 'flex', alignItems: 'center', gap: 14,
          opacity: s.active ? 1 : 0.35,
        }}>
          <div style={{
            width: 38, height: 38, borderRadius: 9, flexShrink: 0,
            background: `${s.color}18`, display: 'flex',
            alignItems: 'center', justifyContent: 'center', fontSize: 17,
          }}>{s.icon}</div>
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 700, fontSize: 13 }}>{s.label}</div>
            <div style={{ color: C.muted, fontSize: 11, marginTop: 2 }}>{s.desc}</div>
          </div>
          {s.active
            ? <div style={{ width: 7, height: 7, borderRadius: '50%', background: C.ok, boxShadow: `0 0 6px ${C.ok}`, flexShrink: 0 }} />
            : <span style={{ fontSize: 9, fontWeight: 800, color: C.dim, letterSpacing: '0.8px', flexShrink: 0 }}>PHASE 2</span>}
        </div>
      ))}

      <div style={{ marginTop: 22 }}>
        <span style={lbl}>Parser Format Support</span>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginTop: 10 }}>
          {FORMATS.map(f => (
            <div key={f.fmt} style={{
              background: C.hi, border: `1px solid ${C.border}`,
              borderLeft: `3px solid ${f.color}`, borderRadius: 8,
              padding: '10px 12px',
            }}>
              <div style={{ fontWeight: 800, fontSize: 11, color: f.color, marginBottom: 4 }}>{f.fmt}</div>
              <div style={{ fontSize: 10, color: C.dim, lineHeight: 1.5 }}>{f.note}</div>
            </div>
          ))}
        </div>
      </div>

      <div style={{ marginTop: 22 }}>
        <span style={lbl}>Parser Pipeline</span>
        <div style={{ fontSize: 12, color: C.muted, lineHeight: 2 }}>
          {['Ingest (8081)', 'Kafka [raw-events]', 'Parser (8082)', 'Kafka [structured-events]', 'Risk Engine (8083)'].map((step, i, arr) => (
            <div key={step} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <div style={{ width: 7, height: 7, borderRadius: '50%', background: C.accent, flexShrink: 0 }} />
              <span style={{ color: i === 0 || i === arr.length - 1 ? C.text : C.muted }}>{step}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─── PII Tab ──────────────────────────────────────────────────────────────────
const PII_COLORS = {
  EMAIL:       C.accent,
  AADHAAR:     C.err,
  PAN:         C.warn,
  PHONE_IN:    C.purple,
  CREDIT_CARD: C.err,
  SSN:         C.err,
};

function PiiTab({ tenant }) {
  const [summary,  setSummary]  = useState(null);
  const [findings, setFindings] = useState([]);
  const [loading,  setLoading]  = useState(true);

  useEffect(() => {
    if (!tenant) return;
    setLoading(true);
    Promise.all([
      fetch(`/pii/api/pii/summary?tenantId=${encodeURIComponent(tenant)}&days=30`),
      fetch(`/pii/api/pii/findings?tenantId=${encodeURIComponent(tenant)}&days=7`),
    ])
      .then(async ([sr, fr]) => {
        if (sr.ok) setSummary(await sr.json());
        if (fr.ok) setFindings(await fr.json());
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [tenant]);

  const th = {
    padding: '10px 14px', textAlign: 'left', fontWeight: 700, color: C.muted,
    fontSize: 10, letterSpacing: '0.8px', textTransform: 'uppercase',
    borderBottom: `1px solid ${C.border}`, whiteSpace: 'nowrap', background: C.bg,
  };
  const td = (extra = {}) => ({
    padding: '10px 14px', borderBottom: `1px solid ${C.border}22`, ...extra,
  });

  if (loading) return <EmptyState icon="⏳" msg="Loading PII findings…" />;

  return (
    <div style={{ padding: 20 }}>
      {/* Warning notice */}
      <div style={{
        background: `${C.warn}11`, border: `1px solid ${C.warn}44`,
        borderRadius: 8, padding: '10px 14px', marginBottom: 18,
        fontSize: 12, color: C.warn, display: 'flex', alignItems: 'center', gap: 8,
      }}>
        <span>⚠</span>
        PII events are flagged — review and redact before sharing logs.
      </div>

      {/* Summary stat card */}
      <div style={{ marginBottom: 18 }}>
        <StatCard
          icon="🔍"
          label="Events with PII Detected"
          value={summary?.eventsWithPii ?? '—'}
          color={C.err}
          sub="Last 30 days"
        />
      </div>

      {/* Findings table */}
      {!findings.length ? (
        <EmptyState icon="✅" msg="No PII findings for this period." />
      ) : (
        <div style={{ overflowX: 'auto', maxHeight: 460, overflowY: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead><tr>
              {['Time', 'Event ID', 'PII Types Found', 'Match Count'].map(h =>
                <th key={h} style={th}>{h}</th>)}
            </tr></thead>
            <tbody>
              {findings.map((f, i) => (
                <tr key={f.eventId || i} style={{ background: i % 2 === 0 ? C.bg : C.surface }}>
                  <td style={td({ color: C.muted, fontSize: 11, whiteSpace: 'nowrap' })}>
                    {f.timestamp ? new Date(f.timestamp).toLocaleString() : '—'}
                  </td>
                  <td style={td({ fontFamily: 'monospace', fontSize: 11, color: C.dim })}>
                    {f.eventId ? f.eventId.slice(0, 16) : '—'}
                  </td>
                  <td style={td()}>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                      {(f.piiTypes || []).map(type => (
                        <span key={type} style={{
                          background: `${PII_COLORS[type] || C.muted}1a`,
                          color: PII_COLORS[type] || C.muted,
                          border: `1px solid ${PII_COLORS[type] || C.muted}44`,
                          borderRadius: 4, padding: '2px 7px',
                          fontSize: 10, fontWeight: 700, letterSpacing: '0.4px',
                        }}>
                          {type}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td style={td({ fontFamily: 'monospace', fontWeight: 700, color: C.warn })}>
                    {f.matchCount ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ─── App ──────────────────────────────────────────────────────────────────────
export default function App() {
  const [tenant,  setTenant]  = useState('tenant-demo');
  const [days,    setDays]    = useState(365);
  const [tab,     setTab]     = useState('live');
  const [summary, setSummary] = useState(null);
  const [events,  setEvents]  = useState([]);
  const [alerts,  setAlerts]  = useState([]);
  const [loading, setLoading] = useState(false);
  const [toast,   setToast]   = useState(null);
  const [err,     setErr]     = useState('');

  const showToast = (msg, type = 'ok') => {
    setToast({ msg, type });
    setTimeout(() => setToast(null), 3500);
  };

  const load = useCallback(async () => {
    if (!tenant.trim()) return;
    setLoading(true); setErr('');
    try {
      const [s, e, a] = await Promise.all([
        fetch(`/api/dashboard/summary?tenantId=${encodeURIComponent(tenant)}&days=${days}`),
        fetch(`/api/dashboard/events?tenantId=${encodeURIComponent(tenant)}&days=${days}&limit=200`),
        fetch(`/api/dashboard/alerts?tenantId=${encodeURIComponent(tenant)}&days=${days}`),
      ]);
      if (s.ok) setSummary(await s.json());
      if (e.ok) setEvents(await e.json());
      if (a.ok) setAlerts(await a.json());
    } catch {
      setErr('Cannot reach backend. Make sure docker compose is running.');
    }
    setLoading(false);
  }, [tenant, days]);

  useEffect(() => { load(); }, [load]);

  const s = summary || {};

  return (
    <div style={{ minHeight: '100vh', background: C.bg, color: C.text, fontFamily: "'Inter','Segoe UI',sans-serif" }}>

      {/* ── Header ── */}
      <div style={{
        background: C.surface, borderBottom: `1px solid ${C.border}`,
        padding: '0 28px', display: 'flex', alignItems: 'center',
        justifyContent: 'space-between', height: 60, position: 'sticky', top: 0, zIndex: 10,
        backdropFilter: 'blur(8px)',
      }}>
        {/* Logo */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            width: 36, height: 36,
            background: 'linear-gradient(135deg, #0057b8, #00aaff)',
            borderRadius: 9, display: 'flex', alignItems: 'center',
            justifyContent: 'center', fontWeight: 900, fontSize: 15,
            color: '#fff', letterSpacing: '-1px', flexShrink: 0,
            boxShadow: '0 0 16px #00aaff33',
          }}>CI</div>
          <div>
            <div style={{ fontWeight: 800, fontSize: 16, letterSpacing: '-0.3px', lineHeight: 1.2 }}>
              Cross Identity
            </div>
            <div style={{ fontSize: 9, color: C.muted, letterSpacing: '1.2px', textTransform: 'uppercase' }}>
              AuditX — Identity Observability
            </div>
          </div>
        </div>

        {/* Centre controls */}
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ ...lbl, margin: 0 }}>Tenant</span>
            <input
              value={tenant} onChange={e => setTenant(e.target.value)}
              style={{ ...inp, width: 180, padding: '6px 10px', fontSize: 12 }}
            />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ ...lbl, margin: 0 }}>Period</span>
            <select
              value={days} onChange={e => setDays(Number(e.target.value))}
              style={{ ...inp, width: 140, padding: '6px 10px', fontSize: 12 }}
            >
              <option value={1}>Last 24 h</option>
              <option value={7}>Last 7 days</option>
              <option value={30}>Last 30 days</option>
              <option value={90}>Last 90 days</option>
              <option value={365}>Last year</option>
            </select>
          </div>
          <button onClick={load} style={{
            background: C.primary, color: '#fff', border: 'none', borderRadius: 7,
            padding: '7px 18px', cursor: 'pointer', fontWeight: 700, fontSize: 12,
          }}>
            Load
          </button>
        </div>

        {/* Right links */}
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <a href={KIBANA_URL} target="_blank" rel="noreferrer" style={{
            color: C.muted, fontSize: 11, textDecoration: 'none',
            padding: '5px 12px', border: `1px solid ${C.border}`, borderRadius: 6,
            display: 'flex', alignItems: 'center', gap: 5,
          }}>
            📊 Kibana
          </a>
          <button onClick={load} style={{
            background: C.hi, color: C.muted, border: `1px solid ${C.border}`,
            borderRadius: 6, padding: '5px 12px', cursor: 'pointer', fontSize: 11,
          }}>↻ Refresh</button>
        </div>
      </div>

      {/* ── Main ── */}
      <div style={{ padding: '24px 28px', maxWidth: 1560, margin: '0 auto' }}>

        {/* Error banner */}
        {err && (
          <div style={{
            background: '#1a0505', border: `1px solid ${C.err}`, borderRadius: 9,
            padding: '11px 16px', marginBottom: 20, color: '#fca5a5', fontSize: 13,
            display: 'flex', alignItems: 'center', gap: 8,
          }}>
            ⚠ {err}
          </div>
        )}

        {/* ── Stat cards ── */}
        <div style={{ display: 'flex', gap: 14, marginBottom: 24, flexWrap: 'wrap' }}>
          <StatCard
            icon="📥" label="Total Events"
            value={loading ? '…' : s.totalEvents?.toLocaleString() ?? '—'}
            color={C.accent} sub={`Last ${days} day${days !== 1 ? 's' : ''}`}
          />
          <StatCard
            icon="🔴" label="High-Risk Events"
            value={loading ? '…' : s.highRiskEvents?.toLocaleString() ?? '—'}
            color={C.err} sub="Score ≥ 70"
          />
          <StatCard
            icon="🔔" label="Alerts Fired"
            value={loading ? '…' : s.totalAlerts?.toLocaleString() ?? '—'}
            color={C.warn} sub="Threshold exceeded"
          />
          <StatCard
            icon="📊" label="Avg Risk Score"
            value={loading ? '…' : s.avgRiskScore ?? '—'}
            color={
              s.avgRiskScore >= 60 ? C.err :
              s.avgRiskScore >= 30 ? C.warn : C.ok
            }
            sub="0 – 100 scale"
          />
          <StatCard
            icon="🔍" label="PII Detections"
            value={loading ? '…' : (s.piiEvents ?? '—')}
            color={C.err} sub="Events with PII found"
          />
        </div>

        {/* ── Body grid ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 280px', gap: 20, marginBottom: 20 }}>

          {/* Left — main panel */}
          <div style={{
            background: C.surface, border: `1px solid ${C.border}`,
            borderRadius: 12, overflow: 'hidden',
          }}>
            {/* Tab bar */}
            <div style={{
              display: 'flex', borderBottom: `1px solid ${C.border}`,
              padding: '0 12px', background: C.surface2,
            }}>
              <Pill active={tab === 'live'}    onClick={() => setTab('live')}    color={C.ok}>⚡ Live Feed</Pill>
              <Pill active={tab === 'events'}  onClick={() => setTab('events')}  color={C.accent}>
                Events {events.length > 0 && <span style={{ color: C.dim }}>({events.length})</span>}
              </Pill>
              <Pill active={tab === 'alerts'}  onClick={() => setTab('alerts')}  color={C.warn}>
                Alerts {alerts.length > 0 && <span style={{ color: C.dim }}>({alerts.length})</span>}
              </Pill>
              <Pill active={tab === 'upload'}  onClick={() => setTab('upload')}  color={C.purple}>Upload</Pill>
              <Pill active={tab === 'sources'} onClick={() => setTab('sources')} color={C.accent2}>Sources</Pill>
              <Pill active={tab === 'pii'} onClick={() => setTab('pii')} color={C.err}>PII Scan</Pill>
            </div>

            {/* Tab content */}
            {tab === 'live'    && <LiveFeed tenant={tenant} />}
            {tab === 'events'  && <EventsTable rows={events} loading={loading} />}
            {tab === 'alerts'  && <AlertsTable rows={alerts} loading={loading} />}
            {tab === 'upload'  && <UploadPanel tenant={tenant} onUploaded={() => setTimeout(load, 3500)} />}
            {tab === 'sources' && <SourcesPanel />}
            {tab === 'pii'     && <PiiTab tenant={tenant} />}
          </div>

          {/* Right — sidebar */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {/* Action breakdown */}
            <div style={{
              background: C.surface, border: `1px solid ${C.border}`,
              borderRadius: 12, padding: 20,
            }}>
              <Bars data={s.eventsByAction} title="Events by Action" />
            </div>

            {/* Outcome split */}
            <div style={{
              background: C.surface, border: `1px solid ${C.border}`,
              borderRadius: 12, padding: 20,
            }}>
              <div style={{ ...lbl, marginBottom: 14 }}>Outcome Split</div>
              {s.eventsByOutcome
                ? Object.entries(s.eventsByOutcome).map(([k, v]) => (
                    <div key={k} style={{
                      display: 'flex', justifyContent: 'space-between',
                      padding: '8px 0', borderBottom: `1px solid ${C.border}22`,
                      alignItems: 'center',
                    }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <div style={{
                          width: 6, height: 6, borderRadius: '50%',
                          background: k === 'SUCCESS' ? C.ok : C.err,
                        }} />
                        <span style={{ color: k === 'SUCCESS' ? C.ok : C.err, fontWeight: 700, fontSize: 12 }}>{k}</span>
                      </div>
                      <span style={{ color: C.muted, fontSize: 13, fontFamily: 'monospace' }}>
                        {v.toLocaleString()}
                      </span>
                    </div>
                  ))
                : <div style={{ color: C.dim, fontSize: 12 }}>No data</div>}
            </div>

            {/* Risk distribution */}
            <div style={{
              background: C.surface, border: `1px solid ${C.border}`,
              borderRadius: 12, padding: 20,
            }}>
              <div style={{ ...lbl, marginBottom: 14 }}>Risk Levels</div>
              {['CRITICAL','HIGH','MEDIUM','LOW'].map(lv => {
                const count = events.filter(e => riskLevel(e.riskScore) === lv).length;
                const r = RISK[lv];
                return (
                  <div key={lv} style={{
                    display: 'flex', justifyContent: 'space-between',
                    padding: '7px 0', borderBottom: `1px solid ${C.border}22`,
                    alignItems: 'center',
                  }}>
                    <span style={{
                      color: r.color, fontSize: 10, fontWeight: 800,
                      letterSpacing: '0.5px', background: r.bg,
                      padding: '2px 7px', borderRadius: 4,
                    }}>{lv}</span>
                    <span style={{ color: count > 0 ? C.text : C.dim, fontSize: 13, fontFamily: 'monospace', fontWeight: 600 }}>
                      {count}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* ── Bottom row ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <QuickSend tenant={tenant} onSent={() => setTimeout(load, 3500)} />
          <ReportPanel tenant={tenant} />
        </div>

        {/* Footer */}
        <div style={{
          marginTop: 28, paddingTop: 18, borderTop: `1px solid ${C.border}`,
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        }}>
          <span style={{ color: C.dim, fontSize: 11 }}>
            Cross Identity · AuditX Platform · Phase 2
          </span>
          <div style={{ display: 'flex', gap: 16 }}>
            <span style={{ color: C.dim, fontSize: 11 }}>
              Tenant: <span style={{ color: C.muted }}>{tenant}</span>
            </span>
            <span style={{ color: C.dim, fontSize: 11 }}>
              Port ref: Ingestion :8081 · Risk :8083 · Report :8086 · Live :8089
            </span>
          </div>
        </div>
      </div>

      <Toast msg={toast?.msg} type={toast?.type} />

      <style>{`
        * { box-sizing: border-box; }
        ::-webkit-scrollbar { width: 6px; height: 6px; }
        ::-webkit-scrollbar-track { background: ${C.bg}; }
        ::-webkit-scrollbar-thumb { background: ${C.border2}; border-radius: 3px; }
        input:focus, select:focus { border-color: ${C.accent} !important; }
        @keyframes livePulse {
          0%, 100% { box-shadow: 0 0 6px ${C.ok}; opacity: 1; }
          50%       { box-shadow: 0 0 12px ${C.ok}; opacity: 0.6; }
        }
        @keyframes slideIn {
          from { transform: translateY(16px); opacity: 0; }
          to   { transform: translateY(0);    opacity: 1; }
        }
      `}</style>
    </div>
  );
}
