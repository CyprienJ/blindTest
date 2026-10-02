package com.blindtest.model.dto;

import org.springframework.data.domain.Sort;

/**
 * Order of the administrator track list, bound from the {@code sort} and {@code direction}
 * query parameters. {@code null} values select the default: newest additions first.
 *
 * @param sort column to order by
 * @param direction ascending or descending; defaults to newest first for {@link Field#ADDED}
 *     and A to Z for {@link Field#CATEGORY}
 */
public record AdminTrackSort(Field sort, Sort.Direction direction) {

    /** Sortable columns of the administrator track list. */
    public enum Field {
        ADDED,
        CATEGORY
    }

    /** The default order: newest additions first. */
    public static AdminTrackSort defaultOrder() {
        return new AdminTrackSort(Field.ADDED, Sort.Direction.DESC);
    }

    /** Direction of the selected column: newest first for added, A to Z for category. */
    public Sort.Direction effectiveDirection() {
        if (direction != null) {
            return direction;
        }
        return sort == Field.CATEGORY ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    /** The selected column, defaulting to {@link Field#ADDED}. */
    public Field effectiveField() {
        return sort == null ? Field.ADDED : sort;
    }
}
