package org.example.kah.common;

import java.util.List;

/**
 * Cursor-based page response for large tables.
 */
public record CursorPageResponse<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore
) {
}