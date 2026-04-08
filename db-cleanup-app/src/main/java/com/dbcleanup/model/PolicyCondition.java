package com.dbcleanup.model;

/**
 * Represents an additional filter condition within a cleanup policy.
 * Conditions are applied alongside the age-based filter when selecting
 * documents for deletion.
 */
public class PolicyCondition {

    /** The MongoDB field name to filter on. */
    private String field;

    /**
     * The comparison operator.
     * Supported values: EQ, NEQ, LT, LTE, GT, GTE, IN, NIN, EXISTS
     */
    private String operator;

    /** The value to compare against (String, Number, Boolean, or List). */
    private Object value;

    public PolicyCondition() {}

    public PolicyCondition(String field, String operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }

    @Override
    public String toString() {
        return "PolicyCondition{field='" + field + "', operator='" + operator + "', value=" + value + "}";
    }
}
