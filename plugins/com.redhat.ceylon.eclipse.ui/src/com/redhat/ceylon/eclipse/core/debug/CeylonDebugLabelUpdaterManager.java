package com.redhat.ceylon.eclipse.core.debug;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.internal.debug.ui.DebugUIMessages;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Functional;

public class CeylonDebugLabelUpdaterManager {
    static CeylonDebugLabelUpdater stackFrameUpdater = new StackFrameLabelUpdater();
    static CeylonDebugLabelUpdater getUpdater(IJavaStackFrame frame) {
        return stackFrameUpdater;
    }
}

class StackFrameLabelUpdater implements CeylonDebugLabelUpdater {
    private final String line = Pattern.quote(DebugUIMessages.JDIModelPresentation_line__76);
    private final String notAvailable = Pattern.quote(DebugUIMessages.JDIModelPresentation_not_available);
    private final String nativeMethod = Pattern.quote(DebugUIMessages.JDIModelPresentation_native_method);
    private final String unknownLineNumber = Pattern.quote(DebugUIMessages.JDIModelPresentation__unknown_line_number__8);
    private final Pattern javaStackFrameLabelPattern = Pattern.compile(
            "(.*)( "+line+" (?:\\d+|"+notAvailable+"(?: "+nativeMethod+")?)|"+unknownLineNumber+")(.*)$"
            );

    public Matcher matches(String existingLabel) {
        Matcher matcher = javaStackFrameLabelPattern.matcher(existingLabel);
        if (matcher.matches()) {
            return matcher;
        }
        return null;
    }
    
    public String updateLabel(Matcher matcher, Declaration declaration) {
        StringBuffer result = new StringBuffer(declaration.getQualifiedNameString());
        if (declaration instanceof Functional) {
            result.append("()");
        }
        result.append(" - ")
        .append(declaration.getUnit().getFilename())
        .append(" - ")
        .append(matcher.group(2))
        .append(matcher.group(3));
        return result.toString();
    }
}