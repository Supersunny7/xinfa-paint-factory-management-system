package com.sunny.paintfactory.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class DocumentSort {
    private DocumentSort() {}

    public static String sql(String sortBy, String sortDirection, String dateColumn, String numberColumn, String idColumn) {
        String direction = switch (sortDirection) {
            case "asc" -> "ASC";
            case "desc" -> "DESC";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort direction");
        };
        return switch (sortBy) {
            case "date" -> dateColumn + " " + direction + "," + numberColumn + " ASC," + idColumn + " ASC";
            case "documentNo" -> numberColumn + " " + direction + "," + dateColumn + " ASC," + idColumn + " ASC";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort field");
        };
    }
}
