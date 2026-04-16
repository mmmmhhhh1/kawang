package org.example.kah.util;

import java.util.UUID;

public final class AllocationHandleGenerator {

    private AllocationHandleGenerator() {
    }

    public static String newHandle() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}