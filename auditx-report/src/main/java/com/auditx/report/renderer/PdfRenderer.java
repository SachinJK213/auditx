package com.auditx.report.renderer;

import com.auditx.report.dto.ReportData;

public interface PdfRenderer {

    /**
     * Converts assembled report data into an HTML string.
     */
    String toHtml(ReportData reportData);

    /**
     * Renders the given HTML content to PDF bytes.
     * The default stub returns the HTML bytes as-is; a Puppeteer-based
     * implementation can be substituted without changing data assembly logic.
     */
    byte[] render(String htmlContent);
}
