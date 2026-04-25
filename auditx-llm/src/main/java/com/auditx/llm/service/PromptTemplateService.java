package com.auditx.llm.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class PromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateService.class);

    private final ResourceLoader resourceLoader;
    private final Map<String, String> templates = new HashMap<>();

    private static final String[] TEMPLATE_NAMES = {"explain", "query", "summarize"};

    public PromptTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void loadTemplates() {
        for (String name : TEMPLATE_NAMES) {
            String path = "classpath:prompts/" + name + ".txt";
            try {
                Resource resource = resourceLoader.getResource(path);
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                templates.put(name, content);
                log.info("Loaded prompt template: {}", name);
            } catch (IOException e) {
                log.error("Failed to load prompt template: {}", name, e);
            }
        }
    }

    public String render(String templateName, Map<String, String> variables) {
        String template = templates.get(templateName);
        if (template == null) {
            throw new IllegalArgumentException("Unknown prompt template: " + templateName);
        }
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }
}
