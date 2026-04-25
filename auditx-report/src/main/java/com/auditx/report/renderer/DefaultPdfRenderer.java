package com.auditx.report.renderer;

import com.auditx.report.dto.AlertSummary;
import com.auditx.report.dto.AuditEventSummary;
import com.auditx.report.dto.ReportData;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class DefaultPdfRenderer implements PdfRenderer {

    @Override
    public String toHtml(ReportData data) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>")
            .append("<meta charset=\"UTF-8\">")
            .append("<title>AUDITX Compliance Report</title>")
            .append("<style>body{font-family:Arial,sans-serif;margin:40px}")
            .append("table{border-collapse:collapse;width:100%}")
            .append("th,td{border:1px solid #ccc;padding:8px;text-align:left}")
            .append("th{background:#f4f4f4}</style>")
            .append("</head><body>");

        // Title and metadata
        html.append("<h1>AUDITX Compliance Report</h1>")
            .append("<p><strong>Tenant:</strong> ").append(escape(data.tenantId())).append("</p>")
            .append("<p><strong>Report Type:</strong> ").append(escape(data.reportType())).append("</p>")
            .append("<p><strong>Date Range:</strong> ")
            .append(data.startDate()).append(" to ").append(data.endDate()).append("</p>")
            .append("<p><strong>Total Events:</strong> ").append(data.totalEvents()).append("</p>");

        // Events by action
        html.append("<h2>Events by Action</h2>");
        if (data.eventsByAction().isEmpty()) {
            html.append("<p>No events recorded for this period.</p>");
        } else {
            html.append("<table><tr><th>Action</th><th>Count</th></tr>");
            for (Map.Entry<String, Long> entry : data.eventsByAction().entrySet()) {
                html.append("<tr><td>").append(escape(entry.getKey()))
                    .append("</td><td>").append(entry.getValue()).append("</td></tr>");
            }
            html.append("</table>");
        }

        // High-risk events
        html.append("<h2>High-Risk Events (Risk Score &ge; 70)</h2>");
        if (data.highRiskEvents().isEmpty()) {
            html.append("<p>No high-risk events in this period.</p>");
        } else {
            html.append("<table><tr>")
                .append("<th>Event ID</th><th>User ID</th><th>Action</th>")
                .append("<th>Source IP</th><th>Outcome</th><th>Timestamp</th><th>Risk Score</th>")
                .append("</tr>");
            for (AuditEventSummary e : data.highRiskEvents()) {
                html.append("<tr>")
                    .append("<td>").append(escape(e.eventId())).append("</td>")
                    .append("<td>").append(escape(e.userId())).append("</td>")
                    .append("<td>").append(escape(e.action())).append("</td>")
                    .append("<td>").append(escape(e.sourceIp())).append("</td>")
                    .append("<td>").append(escape(e.outcome())).append("</td>")
                    .append("<td>").append(e.timestamp()).append("</td>")
                    .append("<td>").append(e.riskScore()).append("</td>")
                    .append("</tr>");
            }
            html.append("</table>");
        }

        // Alerts
        html.append("<h2>Alerts</h2>");
        if (data.alerts().isEmpty()) {
            html.append("<p>No alerts in this period.</p>");
        } else {
            html.append("<table><tr>")
                .append("<th>Alert ID</th><th>Event ID</th><th>Risk Score</th>")
                .append("<th>Status</th><th>Created At</th>")
                .append("</tr>");
            for (AlertSummary a : data.alerts()) {
                html.append("<tr>")
                    .append("<td>").append(escape(a.alertId())).append("</td>")
                    .append("<td>").append(escape(a.eventId())).append("</td>")
                    .append("<td>").append(a.riskScore()).append("</td>")
                    .append("<td>").append(escape(a.status())).append("</td>")
                    .append("<td>").append(a.createdAt()).append("</td>")
                    .append("</tr>");
            }
            html.append("</table>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    @Override
    public byte[] render(String htmlContent) {
        // Stub: returns HTML bytes as-is.
        // In production, replace with a Puppeteer service call.
        return htmlContent.getBytes(StandardCharsets.UTF_8);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }
}
