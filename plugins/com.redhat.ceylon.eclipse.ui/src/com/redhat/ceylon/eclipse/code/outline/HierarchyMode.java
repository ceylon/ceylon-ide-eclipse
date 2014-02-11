package com.redhat.ceylon.eclipse.code.outline;

import com.redhat.ceylon.eclipse.ui.CeylonResources;

public enum HierarchyMode implements CeylonResources { 
    HIERARCHY, SUPERTYPES, SUBTYPES; 
    HierarchyMode next() {
        switch (this) {
        case HIERARCHY:
            return SUPERTYPES;
        case SUPERTYPES:
            return SUBTYPES;
        case SUBTYPES:
            return HIERARCHY;
        default:
            throw new RuntimeException();
        }
    }
    String image() {
        switch (this) {
        case HIERARCHY:
            return CEYLON_HIER;
        case SUPERTYPES:
            return CEYLON_SUP;
        case SUBTYPES:
            return CEYLON_SUB;
        default:
            throw new RuntimeException();
        }
    }
}