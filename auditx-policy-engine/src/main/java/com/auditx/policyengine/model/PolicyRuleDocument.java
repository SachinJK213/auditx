package com.auditx.policyengine.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "policy_rules")
public class PolicyRuleDocument {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    private String ruleId;
    private String ruleName;
    // SpEL expression, e.g. "#action == 'DELETE' AND #outcome == 'SUCCESS'"
    // Available variables: #userId, #action, #outcome, #sourceIp, #riskScore, #tenantId
    private String condition;
    private String severity;                    // LOW | MEDIUM | HIGH | CRITICAL
    private List<String> complianceFrameworks;  // GDPR | SOC2 | DPDP
    private boolean active;
    private String description;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public List<String> getComplianceFrameworks() { return complianceFrameworks; }
    public void setComplianceFrameworks(List<String> complianceFrameworks) { this.complianceFrameworks = complianceFrameworks; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
