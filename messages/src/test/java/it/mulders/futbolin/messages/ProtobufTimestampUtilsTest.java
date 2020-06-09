package it.mulders.futbolin.messages;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

class ProtobufTimestampUtilsTest implements WithAssertions {
    @Test
    void now_should_create_timestamp() {
        var before = LocalDateTime.now();
        var result = ProtobufTimestampUtils.now();
        var after = LocalDateTime.now();

        var zoneOffset = OffsetDateTime.now().getOffset();
        var converted = LocalDateTime.ofEpochSecond(result.getSeconds(), result.getNanos(), zoneOffset);

        assertThat(converted).isBetween(before, after);
    }
}