package com.redhat.ceylon.eclipse.code.complete;

public enum OccurrenceLocation {
    EXTENDS(false), 
    SATISFIES(false),
    CLASS_ALIAS(false), 
    OF(false),
    UPPER_BOUND(false),
    TYPE_ALIAS(false),
    IMPORT(false),
    EXPRESSION(false),
    PARAMETER_LIST(false),
    TYPE_PARAMETER_LIST(false),
    TYPE_ARGUMENT_LIST(false),
    META(false),
    PACKAGE_REF(true),
    MODULE_REF(true),
    INTERFACE_REF(true),
    CLASS_REF(true),
    ALIAS_REF(true),
    TYPE_PARAMETER_REF(true),
    VALUE_REF(true),
    FUNCTION_REF(true),
    DOCLINK(false);
    public final boolean reference;
    OccurrenceLocation(boolean reference) {
        this.reference = reference;
    }
}