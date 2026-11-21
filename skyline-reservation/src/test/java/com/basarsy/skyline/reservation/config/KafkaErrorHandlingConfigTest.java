package com.basarsy.skyline.reservation.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.basarsy.skyline.reservation.client.dto.FlightCancelledEvent;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

class KafkaErrorHandlingConfigTest {

    private final Serializer<Object> valueSerializer = deadLetterValueSerializer();

    @Test
    void recordsThatFailedDeserializationAreDeadLetteredAsTheirOriginalBytes() {
        byte[] poison = "{not json".getBytes(StandardCharsets.UTF_8);

        assertThat(valueSerializer.serialize("t", new RecordHeaders(), poison)).isEqualTo(poison);
    }

    @Test
    void eventsAreDeadLetteredAsPlainJson() {
        UUID flightId = UUID.randomUUID();
        RecordHeaders headers = new RecordHeaders();

        byte[] json = valueSerializer.serialize("t", headers, new FlightCancelledEvent(flightId));

        assertThat(new String(json, StandardCharsets.UTF_8)).isEqualTo("{\"flightId\":\"" + flightId + "\"}");
        assertThat(headers.toArray()).as("no producer class names in headers").isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Serializer<Object> deadLetterValueSerializer() {
        var template = new KafkaErrorHandlingConfig().deadLetterKafkaTemplate(new KafkaProperties(),
                new StaticListableBeanFactory().getBeanProvider(SslBundles.class));
        var factory = (DefaultKafkaProducerFactory<Object, Object>) template.getProducerFactory();
        Serializer<Object> serializer = factory.getValueSerializerSupplier().get();
        serializer.configure(factory.getConfigurationProperties(), false); // what the factory does per producer
        return serializer;
    }
}
