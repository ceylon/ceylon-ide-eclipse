package com.redhat.ceylon.eclipse.core.debug;

import java.util.Arrays;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.debug.core.IJavaStackFrame;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnits;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder;
import com.redhat.ceylon.eclipse.core.model.JDTModule;
import com.redhat.ceylon.eclipse.core.typechecker.CrossProjectPhasedUnit;
import com.redhat.ceylon.eclipse.util.JavaSearch;

public class DebugUtils {

    static IProject getProject(IDebugElement debugElement) {
        IDebugTarget target = debugElement.getDebugTarget();
        if (target instanceof CeylonJDIDebugTarget) {
            return ((CeylonJDIDebugTarget) target).getProject();
        }
        return null;
    }

    public static IMethod getStackFrameMethod(IJavaStackFrame frame) {
        IProject project = getProject(frame);
        IJavaProject javaProject = JavaCore.create(project);
        try {
            IType declaringType = javaProject.findType(frame.getDeclaringTypeName());
            if (declaringType != null) {
                for (IMethod method : declaringType.getMethods()) {
                    if (method.getElementName().equals(frame.getMethodName()) ||
                            frame.isConstructor() && method.isConstructor()) {
                        String[] methodParameterTypes = new String[method.getParameterTypes().length];
                        int i = 0;
                        for (String signature : method.getParameterTypes()) {
                            methodParameterTypes[i++] = Signature.toString(signature);
                        }
                        if (Arrays.equals(methodParameterTypes, frame.getArgumentTypeNames().toArray())) {
                            return method;
                        }
                    }
                }
            }
        } catch (CoreException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static PhasedUnit getStackFramePhasedUnit(IJavaStackFrame frame) {
        IProject project = getProject(frame);
        try {
            PhasedUnits projectPhasedUnits = CeylonBuilder.getProjectPhasedUnits(project);
            if (projectPhasedUnits != null) {
                PhasedUnit phasedUnit = null;
                phasedUnit = projectPhasedUnits.getPhasedUnitFromRelativePath(frame.getSourcePath());
                if (phasedUnit != null) {
                    return phasedUnit;
                }
            }
            for (Module module : CeylonBuilder.getProjectExternalModules(project)) {
                if (module instanceof JDTModule) {
                    JDTModule jdtModule = (JDTModule) module;
                    if (jdtModule.isCeylonArchive()) {
                        PhasedUnit phasedUnit = jdtModule.getPhasedUnitFromRelativePath(frame.getSourcePath());
                        if (phasedUnit != null) {
                            if (phasedUnit instanceof CrossProjectPhasedUnit) {
                                phasedUnit = ((CrossProjectPhasedUnit) phasedUnit).getOriginalProjectPhasedUnit();
                            }
                            return phasedUnit;
                        }
                    }
                }
            }
        } catch (DebugException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Declaration getStackFrameCeylonDeclaration(IJavaStackFrame frame) {
        IMethod method = getStackFrameMethod(frame);
        PhasedUnit unit = getStackFramePhasedUnit(frame);
        if (method != null && unit != null) {
            return JavaSearch.toCeylonDeclaration(method, Arrays.asList(unit));
        }
        return null;
    }

    public static boolean isCeylonFrame(IJavaStackFrame frame) {
        try {
            if (frame.getSourceName() != null &&
                    frame.getSourceName().endsWith(".ceylon")) {
                return true;
            }
        } catch (DebugException e) {
            e.printStackTrace();
        }
        return false;
    }

}
