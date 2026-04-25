package com.auditx.parser.consumer;

import com.auditx.common.constants.KafkaTopics;
import com.auditx.common.dto.RawEventDto;
import com.auditx.parser.service.ParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RawEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RawEventConsumer.class);

    private final ParserService parserService;

    public RawEventConsumer(ParserService parserService) {
        this.parserService = parserService;
    }

    @KafkaListener(topics = KafkaTopics.RAW_EVENTS, groupId = "parser-group")
    public void consume(RawEventDto event) {
        parserService.process(event)
                .doOnError(ex -> log.error(
                        "{{\"eventId\":\"{}\",\"topic\":\"{}\",\"consumerGroup\":\"parser-group\"," +
                        "\"attemptCount\":1,\"errorMessage\":\"{}\"}}",
                        event.eventId(), KafkaTopics.RAW_EVENTS, ex.getMessage()))
                .subscribe();
    }
}
