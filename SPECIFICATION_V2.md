# AuditX Enterprise Log Intelligence & Compliance Platform
## Comprehensive Technical Specification v2.0

**Document Owner:** Platform Architecture
**Date:** 2026-04-25
**Classification:** Internal — Engineering
**Audience:** Senior engineers, architects, product, compliance leadership

---

## 1. Executive Summary

### 1.1 Problem Statement
Enterprises generate billions of log events per day across heterogeneous sources (cloud workloads, on-prem servers, SaaS, IAM, network gear, applications). Three problems converge:

1. **Format chaos** — every source emits a different shape (JSON, syslog RFC 5424, CEF, LEEF, plain text, CSV, XML, application-specific). Existing tools demand expensive normalization licensing or hand-rolled parsers.
2. **Compliance fragmentation** — DPDP (India), GDPR (EU), SOC 2, ISO 27001, HIPAA, PCI-DSS each demand specific evidence from logs. Most SIEMs treat compliance as an add-on module billed separately.
3. **Signal-to-noise** — risk scoring is either CVSS-only (vulnerability-centric, not log-centric) or vendor-proprietary black boxes. Anomaly detection requires data-science teams the buyer doesn't have.

**AuditX** unifies these: ingest anything → normalize to a single open schema (OCSF) → enrich → score risk on a transparent multi-dimensional model → run compliance rules per regulation → detect anomalies → escalate → produce regulator-ready evidence packages.

### 1.2 Target Audience
| Persona | Primary Use Case | Buying Authority |
|---|---|---|
| **CISO / CSO** | Board-level risk dashboards, breach response evidence | Economic buyer |
| **Compliance Officer / DPO** | DPDP/GDPR Article 30 ROPA, audit evidence packages, breach notifications | Champion |
| **DevSecOps Lead** | Pipeline anomaly detection, SOC 2 CC7.2 evidence | Technical buyer |
| **SOC Analyst (Tier 1/2)** | Real-time alerts, triage workflows, escalation | Daily user |
| **Internal Auditor** | Read-only evidence trail, immutable audit log of the audit system | Influencer |
| **MSSP / Reseller** | White-label tenant management | Channel |
| **SaaS Product Companies** | Multi-tenant customer-facing audit feature | OEM buyer |

### 1.3 Market Positioning
| Capability | Splunk | QRadar | Sentinel | Elastic SIEM | **AuditX** |
|---|---|---|---|---|---|
| Per-GB ingest cost | $$$$ | $$$ | $$ (Azure tax) | $$ | $ (open core) |
| OCSF native | Partial | No | Partial | No | **Yes (primary schema)** |
| DPDP Act 2023 packs | No | No | No | No | **Yes (built-in)** |
| Transparent risk formula | No | No | No | Partial | **Yes (whitepaper-grade)** |
| Compliance score per regulation | Add-on | Add-on | Add-on | No | **Built-in** |
| Embedded multi-tenant | Limited | No | Per-workspace | Per-space | **First-class** |
| White-label | No | No | No | Limited | **Yes** |
| Time-to-deploy | Months | Months | Weeks | Weeks | **Days** |

**Differentiator pitch:** *"OCSF-native, India-first compliance, transparent risk math, multi-tenant by design, priced for the mid-market."*

---

## 2. Architecture — Current vs Target State

### 2.1 Current State (as-is)
```
+----------+     POST /logs    +-------------------+
|  Client  | ----------------> |  AuditX Monolith  |
+----------+                   |  (Spring Boot)    |
                               |  - Regex parser   |
                               |  - Risk score     |
                               |  - HTML report    |
                               |  - Alerter        |
                               +---------+---------+
                                         |
                          +--------------+--------------+
                          |              |              |
                       Kafka          MongoDB         Redis
                    (single topic)   (raw events)    (cache)
```
**Limitations:** single ingestion path, brittle regex, no compliance engine, no anomaly detection, no normalization layer.

### 2.2 Target State (to-be)

```
                         ╔═══════════════════════════════════════════╗
                         ║         AuditX Control Plane              ║
                         ║  (Tenant Mgmt, RBAC, Config, Meta-Audit)  ║
                         ╚══════════════╦════════════════════════════╝
                                        │
 ┌──── INGESTION LAYER ────────────────────────────────────────────────┐
 │                                                                     │
 │  [auditx-agent]  [file-watcher-svc]  [upload-api-svc]              │
 │   (Go binary,     (WatchService,      (REST multipart +            │
 │    mTLS, local    inotify/Linux,       S3 presign,                 │
 │    disk buffer)   checkpoint Redis)    ClamAV scan)                │
 │                                                                     │
 │  [connector-hub-svc]  ← Splunk, CloudWatch, CloudTrail, Azure,     │
 │                          GCP, Datadog, Syslog, Okta, Auth0,        │
 │                          CrowdStrike, O365, GitHub, Cloudflare…    │
 └──────────────────────────┬──────────────────────────────────────────┘
                            │  Kafka: auditx.raw.v1
                            ▼
 ┌──── NORMALIZATION LAYER ────────────────────────────────────────────┐
 │                                                                     │
 │  [parser-router-svc] → detect format (Tika + magic bytes)          │
 │       ├─► [json-parser-svc]                                        │
 │       ├─► [syslog-parser-svc]        All emit OCSF v1.3 events     │
 │       ├─► [cef-leef-parser-svc]  ──► auditx.parsed.v1             │
 │       ├─► [csv-xml-parser-svc]                                     │
 │       ├─► [regex-parser-svc]    (Grok 1500+ patterns)             │
 │       └─► [ml-nlp-parser-svc]   (spaCy NER fallback)              │
 │                                                                     │
 │  [enrichment-svc] → geo-IP, asset CMDB, IdP user, threat intel     │
 │                     Output: auditx.enriched.v1                     │
 └──────────────────────────┬──────────────────────────────────────────┘
                            ▼
 ┌──── ANALYTICS LAYER ────────────────────────────────────────────────┐
 │                                                                     │
 │  [pii-detection-svc]    → Presidio + India/EU/US recognizers       │
 │  [compliance-rule-svc]  → Drools/Easy Rules, per-reg packs         │
 │  [risk-scoring-svc]     → ACRS (AuditX Composite Risk Score)       │
 │  [anomaly-detection-svc]→ EWMA + Isolation Forest + LSTM           │
 │  [correlation-svc]      → Sigma rules + temporal correlation       │
 │                                                                     │
 │  Output: auditx.findings.v1, auditx.alerts.v1, auditx.metrics.v1  │
 └──────────────────────────┬──────────────────────────────────────────┘
                            ▼
 ┌──── ACTION LAYER ───────────────────────────────────────────────────┐
 │  [escalation-svc]  [notification-svc]  [report-svc]                │
 │  [evidence-vault-svc]  [meta-audit-svc]                            │
 │                                                                     │
 │  Channels: SMTP, Slack, Teams, PagerDuty, ServiceNow, SMS          │
 │  Reports:  PDF, Excel, JSON, CSV, Evidence ZIP (WORM-signed)       │
 └──────────────────────────┬──────────────────────────────────────────┘
                            ▼
 ┌──── PERSISTENCE & QUERY ───────────────────────────────────────────┐
 │  MongoDB       OpenSearch      Redis       S3/MinIO     PostgreSQL │
 │  (events,      (full-text,     (cache,     (raw archive, (tenants, │
 │   findings,    aggregations)   baselines,  WORM reports) RBAC,    │
 │   audit trail)                rate limits)               config)  │
 └─────────────────────────────────────────────────────────────────────┘
```

### 2.3 New Microservices Inventory
| # | Service | Responsibility | Language |
|---|---|---|---|
| 1 | `auditx-agent` | Edge log shipper (Filebeat-style) | Go |
| 2 | `file-watcher-svc` | Watch local/NFS paths, checkpoint | Java 21 |
| 3 | `upload-api-svc` | REST multipart + S3 presign | Java 21 |
| 4 | `connector-hub-svc` | Pull-mode 3rd-party connectors | Java 21 |
| 5 | `parser-router-svc` | Format detection + dispatch | Java 21 |
| 6 | `json-parser-svc` | JSON/JSONL/NDJSON | Java 21 |
| 7 | `syslog-parser-svc` | RFC 3164 / 5424 | Java 21 |
| 8 | `cef-leef-parser-svc` | ArcSight CEF, IBM LEEF | Java 21 |
| 9 | `csv-xml-parser-svc` | Tabular + XML formats | Java 21 |
| 10 | `regex-parser-svc` | Grok-style 1500+ patterns | Java 21 |
| 11 | `ml-nlp-parser-svc` | NER fallback for unstructured | Python (FastAPI) |
| 12 | `enrichment-svc` | Geo/CMDB/IdP/threat intel enrichment | Java 21 |
| 13 | `pii-detection-svc` | Presidio + IN/EU/US recognizers | Python (FastAPI) |
| 14 | `compliance-rule-svc` | Per-regulation rule packs (6 regulations) | Java 21 |
| 15 | `risk-scoring-svc` | ACRS calculation engine | Java 21 |
| 16 | `anomaly-detection-svc` | EWMA + Isolation Forest + LSTM | Python (FastAPI) |
| 17 | `correlation-svc` | Sigma rules + temporal correlation | Java 21 |
| 18 | `escalation-svc` | Tiered escalation state machine | Java 21 |
| 19 | `notification-svc` | Multi-channel dispatch (10 channels) | Java 21 |
| 20 | `report-svc` | PDF / Excel / JSON generation | Java 21 |
| 21 | `evidence-vault-svc` | WORM evidence storage + hash chain | Java 21 |
| 22 | `tenant-mgmt-svc` | Tenancy, billing, quotas | Java 21 |
| 23 | `rbac-iam-svc` | OIDC/SAML, roles, fine-grained authz | Java 21 |
| 24 | `meta-audit-svc` | Audit-the-auditor, hash chain | Java 21 |
| 25 | `query-api-svc` | Search, dashboards, NLQ, BFF | Java 21 |
| 26 | `auditx-ui` | React/Next.js frontend (full redesign) | TypeScript |

### 2.4 Data Flow (happy path)
```
Source → Agent/Connector/Upload
   → Kafka: auditx.raw.v1 {tenantId, sourceId, payload, receivedAt}
   → parser-router-svc detects format, routes to format-specific parser
   → parser emits OCSF v1.3 event → Kafka: auditx.parsed.v1
   → enrichment-svc adds geo, asset, user, threat-intel
   → Kafka: auditx.enriched.v1 ──┬─► MongoDB(events) → OpenSearch index
                                  ├─► pii-detection-svc
                                  ├─► compliance-rule-svc
                                  ├─► risk-scoring-svc
                                  └─► anomaly-detection-svc
   → Kafka: auditx.findings.v1
   → correlation-svc aggregates into incidents
   → Kafka: auditx.alerts.v1
   → escalation-svc + notification-svc + report-svc
```

---

## 3. Log Ingestion Layer

### 3.1 File Watcher Service (`file-watcher-svc`)
- **Tech:** Java 21, `java.nio.file.WatchService`; `jnotify`/inotify on Linux for sub-second latency.
- **Source config (stored in MongoDB `ingestion_sources`):**
```json
{
  "_id": "src_a1b2",
  "tenantId": "t_acme",
  "type": "FILE_WATCH",
  "paths": ["/var/log/app/*.log", "/mnt/nfs/audit/**/*.json"],
  "recursive": true,
  "rotationPolicy": "RENAME|COPYTRUNCATE",
  "encoding": "UTF-8",
  "tailFromEnd": true,
  "checkpointKey": "src_a1b2_checkpoint",
  "throttleEventsPerSec": 5000
}
```
- **Checkpointing:** offsets in Redis (`watcher:ckpt:{srcId}` → `{file, inode, offset}`) to survive restarts.
- **Backpressure:** pause reads when Kafka producer lag > threshold.

### 3.2 REST Upload Endpoint (`upload-api-svc`)
- `POST /api/v1/ingest/upload` — multipart, up to 100 MB inline
- `POST /api/v1/ingest/upload/presign` → returns S3/MinIO presigned PUT URL for files > 100 MB
- `POST /api/v1/ingest/upload/complete` → triggers `auditx.raw.v1` event with S3 pointer
- **Validation:** Apache Tika MIME sniffing, ClamAV sidecar for malware scan, gzip/zip auto-extract.
- **Required headers:** `X-Tenant-Id`, `X-Source-Tag`, `X-Idempotency-Key`.

### 3.3 Third-Party Connectors (`connector-hub-svc`)
Common SPI for pull-mode connectors:
```java
public interface LogConnector {
    ConnectorMetadata metadata();
    void configure(ConnectorConfig cfg);
    Flux<RawLogEvent> poll(Cursor cursor);  // reactive, cursor-based
    Cursor checkpoint();
}
```

**Supported sources (Phase 1 + 2):**
| # | Source | Mode | Protocol |
|---|---|---|---|
| 1 | Splunk | Pull | REST `/services/search/jobs` |
| 2 | AWS CloudWatch Logs | Pull | `FilterLogEvents` API |
| 3 | AWS CloudTrail | S3+SQS | EventBridge |
| 4 | AWS VPC Flow Logs | S3 | — |
| 5 | Azure Monitor / Log Analytics | Pull | Kusto KQL |
| 6 | Microsoft Sentinel | Pull | REST |
| 7 | GCP Cloud Logging | Pull | Pub/Sub |
| 8 | Datadog | Pull | Logs Search API |
| 9 | Syslog (UDP/TCP/TLS 6514) | Push | RFC 5424 |
| 10 | Okta System Log | Pull | `/api/v1/logs` |
| 11 | Auth0 | Pull | Logs API |
| 12 | CrowdStrike Falcon | Pull | Streaming API |
| 13 | O365 / Microsoft 365 | Pull | Management Activity API |
| 14 | Google Workspace | Pull | Reports API |
| 15 | Cloudflare | Push | Logpush |
| 16 | GitHub | Pull | Audit Log API |
| 17 | GitLab | Pull | Audit Events API |
| 18 | Kafka (external) | Pull | KafkaConsumer |
| 19 | NATS / RabbitMQ | Pull | Native client |
| 20 | Generic Webhook | Push | `POST /webhook/{srcId}` |

### 3.4 Edge Agent (`auditx-agent`)
- **Why Go:** static binary, ~30 MB memory, runs on Windows/Linux/macOS/ARM.
- **Features:** tail+watch, gzip+batch, mTLS to ingest gateway, local disk buffer (BoltDB), sampling, agent-side redaction (regex), exponential backoff.
- **Config:** YAML, hot-reloadable via `tenant-mgmt-svc /api/v1/agents/{id}/config`.

---

## 4. Universal Log Normalization Engine

### 4.1 Schema Choice: OCSF v1.3 (primary) + ECS field aliases

**Why OCSF over ECS / CIM / CEF:**
| Criterion | OCSF | ECS | Splunk CIM | CEF |
|---|---|---|---|---|
| Vendor neutral | ✅ (Linux Foundation) | Elastic-led | Splunk-only | HP/MicroFocus |
| Hierarchical event classes | ✅ | Flat | Partial | Flat |
| Cloud-native | ✅ | ✅ | Retro | No |
| Active growth (2024-26) | ✅ Strong | Moderate | Slow | Stagnant |

**Decision:** OCSF as the canonical wire format; ECS aliases exposed in query API for analyst familiarity.

### 4.2 Multi-Strategy Parser Pipeline
```
RawLogEvent
    │
    ▼
[1] Format Sniffer (parser-router-svc)
    ├─ Magic bytes (gzip, zip, parquet)
    ├─ Apache Tika MIME detection
    ├─ First-line heuristics:
    │    starts with "{" → JSON
    │    starts with "<" → XML
    │    matches "<\d+>\d " → syslog RFC 5424
    │    matches "CEF:0|" → CEF
    │    matches "LEEF:" → LEEF
    │    has commas + header row → CSV
    │    else → unstructured text
    ▼
[2] Strategy Cascade (try in order, stop on success)
    a. Structured parser (JSON/XML/CSV)         ── ~80% of volume
    b. Grammar parser (syslog/CEF/LEEF)         ── ~12%
    c. Grok regex (regex-parser-svc)             ── ~6%  (1500+ patterns)
    d. ML-NLP fallback (ml-nlp-parser-svc)       ── ~2%  (spaCy NER)
    e. Raw envelope (preserve as message field)  ── ε    (never lose data)
    ▼
[3] OCSF Mapper
    Map source-specific fields → OCSF class + attributes
    Detect event class from event_type, action, signature_id
    ▼
[4] Validator (JSON Schema for OCSF v1.3)
    Drop or quarantine on hard violation; tag soft warnings
```

### 4.3 Parser Tech Stack
| Concern | Library |
|---|---|
| MIME detection | **Apache Tika 2.x** |
| JSON / JSONL | **Jackson** (streaming JsonParser) |
| XML | **Woodstox** (StAX) |
| CSV | **univocity-parsers** + Apache Commons CSV |
| Syslog | **syslog-java-client** + custom RFC 5424 BNF parser |
| CEF / LEEF | Custom-built (no mature OSS; ~400 LOC each) |
| Grok | **java-grok** (Krakens-Lab fork) |
| Regex (high perf) | **RE2/J** (linear-time, no catastrophic backtracking) |
| NLP / NER | **spaCy 3.x** + HuggingFace **DistilBERT-NER** |
| Schema validation | **networknt/json-schema-validator** |

### 4.4 Enrichment (`enrichment-svc`)
| Enrichment | Source | Cache TTL |
|---|---|---|
| Geo-IP (country, city, lat/lon, ASN) | **MaxMind GeoIP2 / GeoLite2** | 24 h Redis |
| User identity (name, dept, manager) | IdP (Okta/Azure AD/Keycloak) via SCIM 2.0 | 1 h Redis |
| Asset / CMDB | ServiceNow CMDB or internal registry | 1 h Redis |
| DNS reverse lookup | `dnsjava` async resolver | 10 min Caffeine |
| Threat intel (IOC match) | MISP API + AlienVault OTX + STIX/TAXII feed | OpenSearch index |
| TLS cert intel | crt.sh API | 24 h Redis |

### 4.5 Canonical Normalized Event (OCSF-extended)
```json
{
  "metadata": {
    "version": "1.3.0",
    "tenant_uid": "t_acme",
    "log_source_uid": "src_a1b2",
    "ingestion_pipeline": "json-parser-svc/v2.4.1",
    "labels": ["prod", "payments"]
  },
  "time": 1735000000000,
  "time_dt": "2026-04-25T10:00:00.000Z",
  "category_uid": 3,
  "category_name": "Identity & Access Management",
  "class_uid": 3002,
  "class_name": "Authentication",
  "activity_name": "Logon",
  "severity_id": 3,
  "severity": "Medium",
  "actor": {
    "user": { "uid": "u_8821", "name": "alice@acme.com", "type": "User", "domain": "acme.com" },
    "session": { "uid": "sess_abc123" }
  },
  "src_endpoint": {
    "ip": "203.0.113.42",
    "location": { "country": "IN", "city": "Bengaluru", "coordinates": [77.59, 12.97] }
  },
  "status": "Success",
  "raw_data": "<original line preserved>",
  "enrichments": [
    { "name": "geo_ip", "data": { "asn": 24560, "isp": "Airtel" } },
    { "name": "threat_intel", "data": { "matched": false } }
  ],
  "auditx": {
    "pii": { "detected": true, "types": ["EMAIL", "AADHAAR"], "confidence": 0.94 },
    "compliance_tags": ["DPDP.S8", "GDPR.ART30", "SOC2.CC7.2"],
    "risk": { "score": 47, "level": "Medium", "components": {} },
    "anomaly": { "score": 0.12, "is_anomaly": false }
  }
}
```

---

## 5. Compliance Analysis Engine

### 5.1 Architecture
- Engine: **Easy Rules 4** (start simple) → **Drools 8** if rule volume > 5K.
- Rules versioned in Git, hot-loaded per tenant. Tenants subscribe to regulation packs.
- Each rule emits a `Finding`:
```json
{
  "findingId": "f_xyz",
  "tenantId": "t_acme",
  "regulation": "DPDP",
  "controlId": "DPDP.S8.4",
  "title": "Personal data processed without recorded consent",
  "severity": "HIGH",
  "remediation": "Capture consent artifact; retain per Section 11.",
  "createdAt": "2026-04-25T10:00:01Z"
}
```

### 5.2 PII Detection (`pii-detection-svc`)
**Tech:** Microsoft Presidio (analyzer + anonymizer) wrapped in FastAPI; called via gRPC from Java services.

| Region Pack | Recognizers |
|---|---|
| **India (DPDP)** | Aadhaar (12-digit + Verhoeff checksum), PAN, Voter ID, Driving Licence (per-state regex), GSTIN, IFSC, Indian mobile (+91), UPI VPA, bank account |
| **EU (GDPR)** | IBAN, EU VAT, EU passport, national IDs (DE/FR/IT/ES/NL), SSN-equivalents |
| **US (HIPAA, PCI)** | SSN, credit card (Luhn), MRN, NPI, US driver licence, ICD-10 codes |
| **Generic** | Email, IPv4/v6, MAC, phone, lat/lon, JWT, AWS/GCP/Azure keys, GitHub PAT, RSA/EC private keys |

**Detection methods (defense-in-depth):**
1. Regex with checksum (Luhn for cards, Verhoeff for Aadhaar, mod-97 for IBAN).
2. Context analyzer (word "card" within 50 chars boosts confidence).
3. NER (spaCy `en_core_web_trf`, `xx_ent_wiki_sm` for multilingual).
4. BERT classifier for ambiguous strings.
5. Confidence threshold ≥ 0.85 → DETECTED; 0.6–0.85 → SUSPECTED (review queue).

**Action options per tenant policy:** TAG_ONLY, REDACT (`****`), HASH (SHA-256 + salt), TOKENIZE (FPE via Vault Transit), DROP.

### 5.3 Per-Regulation Rule Packs

#### DPDP Act 2023 (India)
| Rule ID | Section | Trigger | Severity |
|---|---|---|---|
| DPDP.S6.1 | §6 (consent) | Personal data event without `consent_id` reference | HIGH |
| DPDP.S8.4 | §8(4) data security | Unencrypted PII transmission detected | CRITICAL |
| DPDP.S8.5 | §8(5) breach | ≥ 100 PII records accessed in < 5 min by single user | CRITICAL |
| DPDP.S8.6 | §8(6) retention | Personal data accessed past `retention_until` | HIGH |
| DPDP.S8.7 | §8(7) erasure | Erasure request not actioned within 30 days | HIGH |
| DPDP.S9 | Children's data | Processing user age < 18 without parental consent | CRITICAL |
| DPDP.S10 | SDF cross-border | Transfer without DPDP-approved-country flag | HIGH |
| DPDP.S16 | DPB notification | Generate breach notification draft within 72 h | INFO (workflow) |

#### GDPR
| Rule ID | Article | Trigger |
|---|---|---|
| GDPR.ART5.1c | Art. 5(1)(c) data minimization | Bulk export exceeding declared purpose |
| GDPR.ART15 | Subject access | DSAR not fulfilled in 30 days |
| GDPR.ART17 | Right to erasure | Erasure not propagated to all stores |
| GDPR.ART30 | ROPA | Auto-build Records of Processing from event log |
| GDPR.ART32 | Security | Crypto downgrade (TLS < 1.2), weak cipher |
| GDPR.ART33 | Breach notification | 72-hour countdown timer + draft notice generator |
| GDPR.ART44 | Transfers | EU-origin PII to non-adequacy country |

#### SOC 2 Type II
| Rule ID | TSC | Trigger |
|---|---|---|
| SOC2.CC6.1 | Logical access | Login from new geo / impossible travel |
| SOC2.CC6.6 | External logical access | MFA bypass detected |
| SOC2.CC7.2 | System monitoring | Log gap > 5 min on critical asset |
| SOC2.CC7.3 | Incident detection | Anomaly score > 0.8 unactioned past SLA |
| SOC2.CC8.1 | Change mgmt | Production change without approved CR ID |

#### ISO 27001:2022 Annex A
| Rule ID | Control | Trigger |
|---|---|---|
| ISO.A.5.15 | Access control | Privilege escalation event |
| ISO.A.8.15 | Logging | Log integrity violation (hash chain break) |
| ISO.A.8.16 | Monitoring | Failed-login storm > 10/min/user |
| ISO.A.8.24 | Cryptography | Certificate expiring < 30 days |

#### HIPAA §164.312
| Rule ID | Subsection | Trigger |
|---|---|---|
| HIPAA.312.a | Access control | PHI accessed by user outside care team |
| HIPAA.312.b | Audit controls | Audit log tampering |
| HIPAA.312.c | Integrity | PHI modified without justification field |
| HIPAA.312.e | Transmission security | PHI over unencrypted channel |

#### PCI-DSS v4.0 Requirement 10
| Rule ID | Sub-req | Trigger |
|---|---|---|
| PCI.10.2.1 | Individual access | Service-account access to CHD (must be human-attributed) |
| PCI.10.2.4 | Invalid logical access | Failed admin login |
| PCI.10.3.1 | Log content | Missing user ID, event type, or timestamp on CHD events |
| PCI.10.5.2 | Log integrity | FIM event on log files |
| PCI.10.7 | Retention | CHD logs retained < 12 months |

### 5.4 Compliance Score per Regulation (0–100)
```
ComplianceScore(R) = 100 − ( Σ (severity_weight × control_weight) / max_possible × 100 )

severity_weight: CRITICAL=10, HIGH=5, MEDIUM=2, LOW=1
control_weight:  per-regulation matrix (e.g., DPDP.S8.5 = 3.0)
max_possible:    sum of (control_weight × CRITICAL weight) across all controls in pack
```
Score buckets: ≥ 90 Compliant · 75–89 At Risk · 50–74 Non-Compliant · < 50 Critical.
Daily snapshots in `compliance_scores` collection for trend tracking.

---

## 6. Risk Scoring Framework — AuditX Composite Risk Score (ACRS) v1

### 6.1 Design Goals
- **Transparent:** every component traceable; no black box.
- **Multi-dimensional:** severity, frequency, asset, identity, compliance, anomaly.
- **CVSS-inspired:** familiar 0–100 scale, base + temporal + environmental.
- **Tunable:** weights configurable per tenant.
- **Time-aware:** decay so old events don't dominate.

### 6.2 Formula
```
ACRS = round( min(100, Base × Temporal × Environmental × ComplianceMultiplier) )

Base       = 0.40 × Severity
           + 0.20 × ThreatIntel
           + 0.20 × AnomalyScore
           + 0.10 × FrequencyFactor
           + 0.10 × IdentityRisk

Temporal   = 1 + 0.5 × Recency
             // Recency ∈ [0,1]; exp decay: e^(-Δt/τ), τ = 24h default

Environmental = 0.6 + 0.4 × AssetCriticality   // ∈ [0.6, 1.0]
                // CMDB: tier1=1.0, tier2=0.85, tier3=0.7, dev/test=0.6

ComplianceMultiplier = 1 + 0.1 × max(complianceTagSeverityScore)
                // CRITICAL DPDP/GDPR tag → 1.4×
```

### 6.3 Component Definitions
| Component | Range | Source |
|---|---|---|
| **Severity** | 0–100 | event severity_id × 20 + parser confidence adj. |
| **ThreatIntel** | 0–100 | 100=IOC match TLP:RED, 60=TLP:AMBER, 30=TLP:GREEN, 0=none |
| **AnomalyScore** | 0–100 | anomaly-detection-svc output × 100 |
| **FrequencyFactor** | 0–100 | min(100, 5 × log2(events_in_window+1)) over rolling 1 h |
| **IdentityRisk** | 0–100 | f(privileged_role, MFA_state, account_age, recent_failed_logins) |
| **Recency** | 0–1 | exp(−Δt / τ) |
| **AssetCriticality** | 0–1 | CMDB tier mapping |
| **ComplianceTagSeverity** | 0–4 | max severity of attached compliance tags |

### 6.4 Risk Levels
| Level | ACRS | Color | ACK SLA | Resolve SLA |
|---|---|---|---|---|
| Critical | 90–100 | Red | 5 min | 1 h |
| High | 70–89 | Orange | 15 min | 4 h |
| Medium | 40–69 | Yellow | 1 h | 24 h |
| Low | 20–39 | Blue | 4 h | 7 d |
| Info | 0–19 | Grey | — | — |

### 6.5 Worked Example
Failed admin login from foreign IP, IOC match, off-hours, prod DB host:
- Severity 80, ThreatIntel 60, Anomaly 70, Frequency 30, Identity 75
- Base = 0.4×80 + 0.2×60 + 0.2×70 + 0.1×30 + 0.1×75 = **68.5**
- Recency 1.0 → Temporal 1.5
- Tier-1 asset → Environmental 1.0
- DPDP CRITICAL tag → ComplianceMult 1.4
- **ACRS = round(min(100, 68.5 × 1.5 × 1.0 × 1.4)) = 100 (Critical)**

---

## 7. Anomaly Detection (`anomaly-detection-svc`)

### 7.1 Layered Approach
| Layer | Technique | Latency | Purpose |
|---|---|---|---|
| L1 | EWMA + 3σ thresholds | < 1 s | Volume spikes, rate anomalies |
| L2 | **Isolation Forest** (scikit-learn) | < 5 s | Multivariate outliers per entity |
| L3 | **LSTM Autoencoder** (TensorFlow/Keras) | batch | Sequence anomalies (login patterns) |
| L4 | **Prophet** / SARIMA | hourly | Seasonality-aware trend detection |
| L5 | **HDBSCAN** | nightly | Behavioral clusters, peer-group analysis |

### 7.2 UEBA Patterns to Detect
| Pattern | Detection Method |
|---|---|
| Impossible travel | Geo distance / time > 800 km/h threshold |
| First-time access to sensitive resource | Bloom filter per user |
| Time-of-day anomaly | Per-user hour-of-day histogram |
| Peer-group deviation | HDBSCAN cluster + cosine distance |
| Privilege escalation | Sigma rule chain |
| Data exfiltration | Bytes-out z-score + DLP classification |
| Account takeover | MFA fail → success → permission change within 10 min |
| Lateral movement | Unique host count / hour z-score |
| Brute force | Failed logins > N in rolling window |
| Insider threat | Off-hours + sensitive asset + unusual volume composite |

### 7.3 Feedback Loop
Analyst marks alert TP/FP in UI → label stored → nightly retrain weights → calibration.

---

## 8. Report Engine (`report-svc`)

### 8.1 Technology
- **PDF:** **OpenPDF** + **Flying Saucer** (HTML→PDF via Thymeleaf templates) + **JFreeChart**
- **Excel:** **Apache POI 5.x SXSSF** (streaming for large files)
- **JSON:** Jackson + published JSON Schema
- **Async:** Kafka job queue → result in S3 → presigned download URL
- **Scheduling:** Quartz clustered scheduler

### 8.2 PDF Report Structure
```
Cover Page          – tenant logo, period, generated-by, classification
Executive Summary   – posture, scores, top 5 risks, trend arrows
Compliance Posture  – per-regulation score gauges (DPDP/GDPR/SOC2/ISO/HIPAA/PCI)
Risk Heatmap        – severity × asset criticality matrix
Top Findings        – top 25, with evidence chains
PII Exposure        – types detected, redaction stats, hot users/assets
Anomaly Timeline    – chart + narrative
Open Incidents      – table with SLA status
Remediation Plan    – per finding, owner, due date
Evidence Appendix   – signed hashes of evidence files
Attestation         – digital signature (PAdES) by Compliance Officer role
Glossary + Method   – ACRS formula explained, data sources
```

### 8.3 Excel Workbook Sheets
1. **Summary** — KPIs, scores
2. **Findings** — every finding with full columns
3. **Events** — sample raw events (capped at 100K rows)
4. **PII_Detections** — type, count, location, redaction status
5. **Compliance_Matrix** — control × pass/fail × evidence count
6. **Risk_Top100** — top events by ACRS
7. **Anomalies** — model, score, status
8. **Users** — per-user risk roll-up
9. **Assets** — per-asset risk roll-up
10. **Methodology** — formulas + data sources

### 8.4 Scheduled vs On-Demand
- **On-demand:** `POST /api/v1/reports` → `{ jobId }`; poll `/api/v1/reports/{jobId}/download`
- **Scheduled:** cron per tenant in `report_schedules`
- Pre-built templates: Daily SOC, Weekly Compliance, Monthly Board, Quarterly Audit, Annual SOC 2

---

## 9. Escalation Matrix (`escalation-svc`)

### 9.1 Tier Model
| Tier | Role | Channels | ACK SLA (Critical) |
|---|---|---|---|
| Tier 1 | SOC Analyst | In-app + Slack | 5 min |
| Tier 2 | SOC Lead / IR | Slack + PagerDuty | 15 min |
| Tier 3 | CISO / DPO | Email + SMS + Phone | 30 min |
| Tier 4 | Executive / Legal | Email + Phone | 1 h |

### 9.2 State Machine
```
NEW → ACKNOWLEDGED → IN_PROGRESS → RESOLVED
  │       │
  ├──── timeout ──► ESCALATE_T2 ──► T3 ──► T4
  └──── auto-suppress (matched suppression rule)
```

### 9.3 Configurable Policy (per tenant, stored in `escalation_policies`)
```yaml
policy:
  name: "Critical-Prod"
  match: { severity: "CRITICAL", tags: ["env:prod"] }
  steps:
    - tier: 1
      channels: [slack:#soc, inapp]
      waitForAckMinutes: 5
    - tier: 2
      channels: [pagerduty:soc-lead]
      waitForAckMinutes: 15
    - tier: 3
      channels: [email:ciso@acme.com, sms:+91...]
      waitForAckMinutes: 30
  businessHoursOverride:
    afterHours: { skipTo: 2 }
```
Timers implemented via **Redis ZSET** (score = expiry epoch).

---

## 10. Notification System (`notification-svc`)

### 10.1 Channels & Libraries
| Channel | Library |
|---|---|
| Email | Spring Mail + JavaMail (S/MIME optional) |
| Slack | Bolt SDK / Slack Web API |
| MS Teams | Adaptive Cards via Incoming Webhook + Graph API |
| PagerDuty | Events API v2 |
| Webhook | OkHttp + HMAC-SHA256 `X-AuditX-Signature` header |
| SMS / Voice | Twilio |
| ServiceNow | Table API (incident creation) |
| Jira | REST API (issue creation) |
| OpsGenie | REST API |

### 10.2 Deduplication & Suppression
- **Dedup key:** `sha1(tenantId|controlId|actorUid|targetUid|hourBucket)`; window 10 min default.
- **Suppression:** maintenance windows, known-noisy sources, allowlists; stored in `notification_suppressions`.
- **Rate limit:** token bucket per (tenant, channel) in Redis.
- **Delivery reliability:** at-least-once via Kafka consumer + DB outbox; 5 retry attempts, 1m→32m exponential; dead-letter to `auditx.notifications.dlq`.

---

## 11. Enterprise Features

### 11.1 RBAC
| Role | Permissions |
|---|---|
| **Super Admin** (platform) | Cross-tenant, billing, releases |
| **Tenant Admin** | Manage users, sources, policies, billing |
| **Compliance Officer** | View/export reports, sign attestations, manage rule packs |
| **Analyst (SOC)** | Triage findings, ACK, escalate, suppress |
| **Auditor** | Read-only across all data + meta-audit; cannot modify |
| **Viewer** | Read-only dashboards |
| **API/Service** | Scoped tokens for ingestion or queries |

Backed by **Keycloak** (OIDC, SAML 2.0, SCIM 2.0). Fine-grained ABAC via **OpenFGA** or **Cedar**.

### 11.2 Multi-Tenancy (Hardening)
- **Isolation models:** SHARED (row-level by `tenantId`), DEDICATED_DB (premium), DEDICATED_CLUSTER (regulated).
- **Data residency:** per-tenant region pinning (`eu-west-1`, `ap-south-1`, `us-east-1`).
- **Per-tenant encryption keys:** AWS KMS / HashiCorp Vault transit, BYOK supported.
- **DPDP §16 / GDPR Chap V:** forces in-country processing for India/EU regulated tenants.

### 11.3 Data Retention Policies
| Data | Default | Configurable | Storage |
|---|---|---|---|
| Raw archive | 1 year | 90 d – 7 y | S3 + Object Lock (WORM) |
| Normalized events | 90 days hot | 30 d – 2 y | MongoDB + OpenSearch |
| Findings | 7 years | 1 – 10 y | MongoDB |
| Reports | 7 years | 1 – 10 y | S3 |
| Meta-audit | 7 years | regulator-mandated | S3 + WORM |

Legal hold flag overrides TTL expiry.

### 11.4 Meta-Audit (Audit-the-Auditor)
- Every admin action, config change, report download, rule modification, RBAC change written to `meta_audit` AND replicated to S3 WORM bucket.
- **Hash chain:** each record contains `prevHash`; nightly root signed and posted to transparency log (RFC 6962-style).
- Read-only API for Auditor role.

### 11.5 Additional Enterprise Differentiators
- **Field-level encryption** for PII columns (MongoDB CSFLE).
- **Customer-Managed Keys (CMK)** with rotation tracking.
- **Air-gapped / on-prem deployment** (Helm chart + offline image bundle).
- **Marketplace listings:** AWS, Azure, GCP, Snowflake.
- **Detection-as-Code:** Sigma + YAML rules in Git, GitOps via ArgoCD.
- **STIX/TAXII 2.1 server** for IOC sharing between tenants (opt-in).
- **Natural language queries:** LLM-backed `/api/v1/nlq` translates plain English to MongoDB + OpenSearch DSL, with strict tenant scoping.
- **White-labeling:** logo, color palette, custom domain (CNAME), email-from address, report cover.
- **SSO/SAML:** OIDC, SAML 2.0, JIT provisioning, SCIM 2.0 inbound.
- **Public Trust Center** page generated from tenant's own meta-audit.

---

## 12. Modifications to the Current Codebase

### 12.1 Existing Services to Modify
| Component | Change |
|---|---|
| `auditx-ingestion` | Keep `POST /api/events/raw` as façade → publish to `auditx.raw.v1`; deprecate inline parsing |
| `auditx-parser` | Move regex logic to `regex-parser-svc`; convert patterns to Grok format |
| `auditx-risk-engine` | Replace scoring with ACRS `risk-scoring-svc`; keep DTO adapter for migration |
| `auditx-alert` | Migrate to `escalation-svc` + `notification-svc` |
| `auditx-report` | Replace HTML report with `report-svc` (PDF+Excel+JSON) |
| `auditx-compliance` | Extend with 6-regulation pack from `compliance-rule-svc` |
| `auditx-policy-engine` | Feed findings into new `correlation-svc` |
| MongoDB schema | Add OCSF fields; write backfill migration job |
| Kafka topics | Introduce new topic taxonomy (12 topics); migrate from flat `raw-events` |
| `auditx-dashboard` | Replace with Next.js 14 + TypeScript + Tailwind full redesign |

### 12.2 New Kafka Topics
| Topic | Partitions | Retention | Partition Key |
|---|---|---|---|
| `auditx.raw.v1` | 64 | 7 d | tenantId |
| `auditx.parsed.v1` | 64 | 3 d | tenantId |
| `auditx.enriched.v1` | 64 | 3 d | tenantId |
| `auditx.findings.v1` | 32 | 30 d | tenantId |
| `auditx.alerts.v1` | 16 | 30 d | tenantId |
| `auditx.escalations.v1` | 8 | 90 d | alertId |
| `auditx.notifications.v1` | 16 | 7 d | tenantId |
| `auditx.notifications.dlq` | 4 | 30 d | tenantId |
| `auditx.metaaudit.v1` | 8 | ∞ (compacted) | tenantId |
| `auditx.metrics.v1` | 8 | 1 d | tenantId |
| `auditx.reports.jobs.v1` | 4 | 7 d | jobId |
| `auditx.dlq.v1` | 4 | 14 d | source |

### 12.3 New MongoDB Collections
| Collection | Purpose |
|---|---|
| `events` | Normalized OCSF events (replaces `audit_events`) |
| `findings` | Compliance & detection findings |
| `compliance_scores` | Daily score snapshots per regulation |
| `ingestion_sources` | Source configurations |
| `escalation_policies` | Tiered escalation policies |
| `notification_suppressions` | Suppression rules |
| `report_jobs` | Async report job state |
| `assets` | CMDB cache |
| `entities_users` | Identity cache |
| `baselines_user`, `baselines_asset` | UEBA baselines |
| `pii_findings` | PII inventory by type/tenant |
| `evidence_vault` | Evidence file references |
| `legal_holds` | Active legal holds |
| `meta_audit` | Admin action audit trail with hash chain |

### 12.4 New API Endpoints (selected)
```
INGESTION
  POST   /api/v1/ingest/upload                   multipart file
  POST   /api/v1/ingest/upload/presign            → S3 presigned URL
  POST   /api/v1/ingest/upload/complete
  POST   /api/v1/sources                         create source config
  POST   /api/v1/sources/{id}:test               validate connectivity
  POST   /webhook/{srcId}                        generic push receiver

QUERY & SEARCH
  POST   /api/v1/events:search                   Mongo+OpenSearch DSL
  POST   /api/v1/events:nlq                      natural language → DSL
  GET    /api/v1/events/{id}/timeline
  POST   /api/v1/findings/{id}:acknowledge
  POST   /api/v1/findings/{id}:resolve
  POST   /api/v1/findings/{id}:suppress

COMPLIANCE
  GET    /api/v1/compliance/scores?regulation=DPDP
  POST   /api/v1/compliance/ropa/export           GDPR Art. 30 ROPA
  POST   /api/v1/compliance/breach/draft          GDPR §33 / DPDP §16

REPORTS
  POST   /api/v1/reports                         { template, period, format }
  GET    /api/v1/reports/{jobId}/download         presigned URL
  POST   /api/v1/reports/schedules               cron-based scheduling

ESCALATION & NOTIFICATIONS
  CRUD   /api/v1/escalation/policies
  CRUD   /api/v1/notifications/channels
  POST   /api/v1/notifications/test

ADMIN
  CRUD   /api/v1/tenants
  CRUD   /api/v1/users, /api/v1/roles
  GET    /api/v1/meta-audit
  GET    /api/v1/quotas
```

### 12.5 Frontend Changes (`auditx-ui`)
**Stack:** Next.js 14 (App Router) + TypeScript + Tailwind + shadcn/ui + TanStack Query + Zustand + ECharts.

**New modules:**
1. Onboarding wizard (tenant + first source setup)
2. Source manager (file watch / upload / connector configs)
3. Live log explorer (DSL builder + NLQ search box)
4. Findings inbox with triage workflow (ACK, resolve, suppress, escalate)
5. Compliance dashboards per regulation (score gauges, control matrix)
6. Risk dashboard (heatmap, top users/assets)
7. Anomaly explorer (timeline, model attribution)
8. Escalation policy designer (visual flow builder)
9. Notification template editor (per channel, per severity)
10. Report builder + scheduler
11. Admin: tenants, users, RBAC, retention, white-label, agents
12. Meta-audit viewer (Auditor role only)
13. Trust Center page (public-share toggle)

---

## 13. Implementation Roadmap

### Phase 1 — MVP (Weeks 1–4)
**Theme:** Universal ingestion + OCSF normalization + basic alerts
- `upload-api-svc`, `file-watcher-svc`, `connector-hub-svc` (Syslog, CloudWatch, generic webhook)
- `parser-router-svc` + JSON, Syslog, CSV, Grok parsers
- OCSF schema in MongoDB `events`; backfill migration
- New Kafka topic taxonomy
- `notification-svc` (email + Slack + webhook)
- Minimal UI: source manager, findings inbox, simple PDF report

**Exit criteria:** Ingest 10K eps from 3 source types, store as OCSF, send Slack alert.

### Phase 2 — Compliance Engine (Weeks 5–12)
**Theme:** Compliance value proposition
- `pii-detection-svc` with Presidio + India recognizers
- `compliance-rule-svc` with DPDP, GDPR, SOC 2 packs
- Compliance score model + daily snapshots
- `enrichment-svc` (geo-IP + threat intel MISP)
- CEF, LEEF, XML parsers
- Full `report-svc` (PDF + Excel + JSON)
- Scheduled reports (Quartz)
- ROPA generator, Breach notification draft generator
- Meta-audit + hash chain
- Connectors: Splunk, Datadog, Okta, CloudTrail, Azure Monitor

**Exit criteria:** Tenant generates DPDP & GDPR audit-ready PDF + Excel; scores trended.

### Phase 3 — Intelligence & Enterprise (Weeks 13–24)
**Theme:** Differentiation
- `anomaly-detection-svc` (IF + EWMA + LSTM)
- `correlation-svc` with Sigma rules
- ISO 27001, HIPAA, PCI-DSS rule packs
- Full `escalation-svc` state machine + tiered paths
- UEBA baselines per user/asset
- Detection-as-Code (Git + ArgoCD)
- White-labeling, SSO/SAML, SCIM
- Data residency controls
- `evidence-vault-svc` with WORM
- Edge `auditx-agent` (Go) GA
- ML-NLP fallback parser
- NLQ search endpoint

**Exit criteria:** Anomaly TPR ≥ 80% on benchmark; SOC 2 Type II audit completed.

### Phase 4 — Enterprise Launch (Weeks 25–32)
**Theme:** Scale and ecosystem
- Marketplace listings (AWS, Azure, GCP)
- Air-gapped Helm chart + offline bundle
- Field-level encryption (CSFLE), BYOK/CMK
- TAXII 2.1 server
- Public Trust Center
- 25+ connectors
- Performance: 100K eps/cluster sustained, query P95 < 1 s on 1 B events
- Multi-region active-active

**Exit criteria:** 3 enterprise design partners in production.

---

## 14. Technology Recommendations

### 14.1 Build vs Buy Summary
**Build:** OCSF mappers, DPDP rule pack, ACRS scoring engine, escalation state machine, white-label tenancy, India PII recognizers, edge agent (Go), transparency log, CEF/LEEF parsers.

**Use OSS/Vendor:** Everything else. The differentiation is the **assembly + India-first compliance + transparent risk math**, not the substrate.

### 14.2 Key Library Decisions
| Concern | Choice |
|---|---|
| Microservices | Spring Boot 3.3+ (Java 21, virtual threads) |
| Reactive | Spring WebFlux + Project Reactor |
| Message bus | Apache Kafka 3.7+ (KRaft, no ZooKeeper) |
| Schema registry | Apicurio (open source) |
| Primary store | MongoDB 7 (sharded) |
| Search | **OpenSearch 2.x** (Apache 2.0, preferred over Elastic for licensing) |
| Cache | Redis 7 Cluster |
| Object storage | AWS S3 + Object Lock; MinIO for on-prem |
| Relational (tenants, RBAC) | PostgreSQL 16 |
| Identity | Keycloak 24+ |
| Fine-grained authz | OpenFGA or Cedar |
| Secrets / KMS | HashiCorp Vault + cloud KMS |
| Rule engine | Easy Rules 4 → Drools 8 at scale |
| MIME detection | Apache Tika 2.x |
| High-perf regex | RE2/J (linear-time) |
| Grok patterns | java-grok |
| NER / NLP | spaCy 3.x + HuggingFace transformers |
| PII detection | Microsoft Presidio + custom IN recognizers |
| Geo-IP | MaxMind GeoIP2 |
| Threat intel | MISP + AlienVault OTX + STIX/TAXII |
| Detection rules | Sigma + custom YAML |
| ML models | scikit-learn, TensorFlow/Keras, Prophet, HDBSCAN |
| Model serving | BentoML or TensorFlow Serving |
| PDF | OpenPDF + Flying Saucer + JFreeChart |
| Excel | Apache POI 5 SXSSF |
| Email | Spring Mail + JavaMail |
| Rate limiting | Bucket4j + Redis |
| Observability | OpenTelemetry + Prometheus + Grafana + Loki + Tempo |
| Frontend | Next.js 14 + TypeScript + Tailwind + shadcn/ui + ECharts |
| Edge agent | Go (custom Filebeat-style binary) |

---

## Appendix A — Glossary
| Term | Definition |
|---|---|
| **OCSF** | Open Cybersecurity Schema Framework (Linux Foundation) |
| **ECS** | Elastic Common Schema |
| **CEF** | Common Event Format (ArcSight) |
| **LEEF** | Log Event Extended Format (IBM QRadar) |
| **ACRS** | AuditX Composite Risk Score |
| **DPDP** | Digital Personal Data Protection Act 2023 (India) |
| **DPB** | Data Protection Board (India) |
| **DSAR** | Data Subject Access Request |
| **ROPA** | Records of Processing Activities (GDPR Art. 30) |
| **UEBA** | User and Entity Behavior Analytics |
| **CSFLE** | Client-Side Field Level Encryption (MongoDB) |
| **WORM** | Write Once Read Many |
| **CMK/BYOK** | Customer-Managed Keys / Bring Your Own Key |
| **SDF** | Significant Data Fiduciary (DPDP classification) |
| **CHD** | Cardholder Data (PCI-DSS) |
| **PHI** | Protected Health Information (HIPAA) |

## Appendix B — Reference Standards
- OCSF v1.3 Schema — https://schema.ocsf.io
- DPDP Act 2023 (Gazette of India)
- GDPR (EU 2016/679), Articles 5, 15, 17, 30, 32, 33, 44
- SOC 2 TSC 2017 (revised) — CC6, CC7, CC8
- ISO/IEC 27001:2022 Annex A — A.5.15, A.8.15, A.8.16, A.8.24
- HIPAA Security Rule §164.312
- PCI-DSS v4.0, Requirement 10
- Sigma Rules — https://sigmahq.io
- STIX 2.1 / TAXII 2.1 — OASIS

---

**End of Specification — v2.0**
