package com.auditx.piidetector.model;

import com.auditx.common.dto.PiiFindingDto;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "pii_findings")
public class PiiFindingDocument {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    private String eventId;
    private List<PiiFindingDto.PiiMatch> matches;
    private int matchCount;
    private Instant scannedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public List<PiiFindingDto.PiiMatch> getMatches() { return matches; }
    public void setMatches(List<PiiFindingDto.PiiMatch> matches) { this.matches = matches; }

    public int getMatchCount() { return matchCount; }
    public void setMatchCount(int matchCount) { this.matchCount = matchCount; }

    public Instant getScannedAt() { return scannedAt; }
    public void setScannedAt(Instant scannedAt) { this.scannedAt = scannedAt; }
}
