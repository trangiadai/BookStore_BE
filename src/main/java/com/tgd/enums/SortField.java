package com.tgd.enums;

public enum SortField {
    NAME("p.name"),
    PRICE("p.selling_price"),
    QUANTITY("p.quantity"),
    CREATED_AT("p.created_at"),
	AVERAGE_RATING("p.average_rating");

    private final String columnName;

    SortField(String columnName) {
        this.columnName = columnName;
    }

    public String getColumnName() {
        return columnName;
    }
}
