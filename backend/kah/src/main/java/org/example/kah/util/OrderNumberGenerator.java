package org.example.kah.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String next() {
        int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "OD" + LocalDateTime.now().format(FORMATTER) + suffix;
    }
}
