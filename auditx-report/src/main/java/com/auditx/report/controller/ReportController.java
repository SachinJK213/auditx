package com.auditx.report.controller;

import com.auditx.report.dto.ReportRequest;
import com.auditx.report.renderer.PdfRenderer;
import com.auditx.report.service.ReportDataAssembler;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportDataAssembler assembler;
    private final PdfRenderer pdfRenderer;

    public ReportController(ReportDataAssembler assembler, PdfRenderer pdfRenderer) {
        this.assembler = assembler;
        this.pdfRenderer = pdfRenderer;
    }

    /**
     * POST /api/reports/generate
     * Generates a PDF compliance report for the given tenant and date range.
     * Returns 400 if tenantId is blank or dates are null.
     * Returns 200 with Content-Type: application/pdf on success.
     */
    @PostMapping("/generate")
    public Mono<ResponseEntity<byte[]>> generate(@RequestBody ReportRequest request) {
        // Requirement 9.4: tenantId is mandatory
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .<byte[]>build());
        }
        if (request.startDate() == null || request.endDate() == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .<byte[]>build());
        }

        return assembler.assemble(request)
                .map(reportData -> {
                    String html = pdfRenderer.toHtml(reportData);
                    byte[] pdfBytes = pdfRenderer.render(html);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.setContentDisposition(
                            ContentDisposition.attachment().filename("report.pdf").build());

                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(pdfBytes);
                });
    }
}
