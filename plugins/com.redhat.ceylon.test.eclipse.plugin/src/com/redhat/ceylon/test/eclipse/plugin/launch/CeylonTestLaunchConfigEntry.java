package com.redhat.ceylon.test.eclipse.plugin.launch;

import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.msg;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestPlugin.LAUNCH_CONFIG_ENTRIES_KEY;
import static com.redhat.ceylon.test.eclipse.plugin.util.CeylonTestUtil.getModule;
import static com.redhat.ceylon.test.eclipse.plugin.util.CeylonTestUtil.getPackage;
import static com.redhat.ceylon.test.eclipse.plugin.util.CeylonTestUtil.getProject;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jface.viewers.TreePath;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages;

@SuppressWarnings("unchecked")
public class CeylonTestLaunchConfigEntry {

    private static final String TYPE_SEPARATOR = "=";
    private static final String PROJECT_SEPARATOR = ";";
    private static final String PACKAGE_SEPARATOR = "::";
    private static final String MEMBER_SEPARATOR = ".";

    public enum Type {
        PROJECT,
        MODULE,
        PACKAGE,
        CLASS,
        CLASS_LOCAL,
        METHOD,
        METHOD_LOCAL
    }

    public static CeylonTestLaunchConfigEntry build(IProject project, Type type, String modPkgDeclName) {
        CeylonTestLaunchConfigEntry entry = new CeylonTestLaunchConfigEntry();
        entry.projectName = project.getName();
        entry.type = type;
        entry.modPkgDeclName = modPkgDeclName;
        return entry;
    }

    public static CeylonTestLaunchConfigEntry buildFromTreePath(TreePath treePath) {
        Object firstSegment = treePath.getFirstSegment();
        Object lastSegment = treePath.getLastSegment();

        CeylonTestLaunchConfigEntry entry = new CeylonTestLaunchConfigEntry();
        entry.projectName = ((IProject) firstSegment).getName();

        if (lastSegment instanceof IProject) {
            entry.type = Type.PROJECT;
        } else if (lastSegment instanceof Module) {
            entry.type = Type.MODULE;
            entry.modPkgDeclName = ((Module) lastSegment).getNameAsString();
        } else if (lastSegment instanceof Package) {
            entry.type = Type.PACKAGE;
            entry.modPkgDeclName = ((Package) lastSegment).getNameAsString();
        } else if (lastSegment instanceof Class) {
            Class clazz = (Class) lastSegment;
            entry.type = clazz.isShared() ? Type.CLASS : Type.CLASS_LOCAL; 
            entry.modPkgDeclName = clazz.getQualifiedNameString();
        } else if (lastSegment instanceof Method) {
            Method method = (Method) lastSegment;
            entry.type = method.isShared() ? Type.METHOD : Type.METHOD_LOCAL;
            entry.modPkgDeclName = method.getQualifiedNameString();
        }

        return entry;
    }

    public static List<CeylonTestLaunchConfigEntry> buildFromLaunchConfig(ILaunchConfiguration config) throws CoreException {
        List<CeylonTestLaunchConfigEntry> entries = new ArrayList<CeylonTestLaunchConfigEntry>();
        List<String> attributes = config.getAttribute(LAUNCH_CONFIG_ENTRIES_KEY, new ArrayList<String>());
        for (String attribute : attributes) {
            CeylonTestLaunchConfigEntry entry = buildFromLaunchConfigAttribute(attribute);
            entries.add(entry);
        }
        return entries;
    }

    private static CeylonTestLaunchConfigEntry buildFromLaunchConfigAttribute(String attribute) {
        CeylonTestLaunchConfigEntry entry = new CeylonTestLaunchConfigEntry();

        int projectSeparatorIndex = attribute.indexOf(PROJECT_SEPARATOR);
        entry.projectName = attribute.substring(Type.PROJECT.name().length() + 1, projectSeparatorIndex);
        if (attribute.length() > projectSeparatorIndex + 1) {
            attribute = attribute.substring(projectSeparatorIndex + 1);
            int typeSeparatorIndex = attribute.indexOf(TYPE_SEPARATOR);
            entry.type = Type.valueOf(attribute.substring(0, typeSeparatorIndex));
            entry.modPkgDeclName = attribute.substring(typeSeparatorIndex + 1);
        } else {
            entry.type = Type.PROJECT;
        }
        
        entry.validate();

        return entry;
    }

    private static String buildLaunchConfigAttribute(CeylonTestLaunchConfigEntry entry) {
        StringBuilder attribute = new StringBuilder();
        attribute.append(Type.PROJECT);
        attribute.append(TYPE_SEPARATOR);
        attribute.append(entry.getProjectName());
        attribute.append(PROJECT_SEPARATOR);
        if (entry.getType() != Type.PROJECT) {
            attribute.append(entry.getType());
            attribute.append(TYPE_SEPARATOR);
            attribute.append(entry.getModPkgDeclName());
        }
        return attribute.toString();
    }
    
    public static List<String> buildLaunchConfigAttributes(List<CeylonTestLaunchConfigEntry> entries) {
        List<String> attributes = new ArrayList<String>();
        for (CeylonTestLaunchConfigEntry entry : entries) {
            attributes.add(buildLaunchConfigAttribute(entry));
        }
        return attributes;
    }

    private Type type;
    private String projectName;
    private String moduleName;
    private String modPkgDeclName;
    private String errorMessage;

    public Type getType() {
        return type;
    }

    public String getProjectName() {
        return projectName;
    }
    
    public String getModuleName() {
        return moduleName;
    }

    public String getModPkgDeclName() {
        return modPkgDeclName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isValid() {
        return errorMessage == null;
    }

    public void validate() {
        errorMessage = null;

        IProject project = validateProject();
        if( !isValid() || type == Type.PROJECT )
            return;
        
        validateModule(project);
        if( !isValid() || type == Type.MODULE )
            return;

		Package pkg = validatePackage(project);
		if(pkg != null) {
			moduleName = pkg.getModule().getNameAsString();
		}
		if(!isValid() || type == Type.PACKAGE) {
			return;
		}
        
        Class clazz = validateClass(pkg);
        if( !isValid() || type == Type.CLASS || type == Type.CLASS_LOCAL )
            return;

        Scope methodScope = (clazz != null) ? clazz : pkg;
        validateMethod(methodScope);
    }

    private IProject validateProject() {
        IProject project = getProject(projectName);
        if (project == null) {
            errorMessage = msg(CeylonTestMessages.errorCanNotFindProject, projectName);
        }
        return project;
    }

    private void validateModule(IProject project) {
        if (type == Type.MODULE) {
            Module module = getModule(project, modPkgDeclName);
            if (module == null) {
                errorMessage = msg(CeylonTestMessages.errorCanNotFindModule, modPkgDeclName, projectName);
            }            
        }
    }

    private Package validatePackage(IProject project) {
        String pkgName = parsePackageName();
        Package pkg = getPackage(project, pkgName);
        if (pkg == null) {
            errorMessage = msg(CeylonTestMessages.errorCanNotFindPackage, pkgName, projectName);
        }
        return pkg;
    }

    private Class validateClass(Package pkg) {
        Class clazz = null;
        String className = parseClassName();
        if( className != null && (type == Type.CLASS || type == Type.CLASS_LOCAL || type == Type.METHOD || type == Type.METHOD_LOCAL) ) {
            Declaration member = pkg.getMember(className, null, false);
            if( !(member instanceof Class) ) {
                errorMessage = msg(CeylonTestMessages.errorCanNotFindClass, modPkgDeclName, projectName);
            } else {
                clazz = (Class) member;
            }
        }
        return clazz;
    }

    private void validateMethod(Scope methodScope) {
        String methodName = parseMethodName();
        if( type == Type.METHOD || type == Type.METHOD_LOCAL ) {
            Declaration member = methodScope.getMember(methodName, null, false);
            if( !(member instanceof Method) ) {
                errorMessage = msg(CeylonTestMessages.errorCanNotFindMethod, modPkgDeclName, projectName);
            }
        }
    }

    private String parsePackageName() {
        String pkgName = null;
        if (type == Type.PACKAGE) {
            pkgName = modPkgDeclName;
        } else if (type == Type.CLASS || type == Type.CLASS_LOCAL || type == Type.METHOD || type == Type.METHOD_LOCAL) {
            int pkgSeparatorIndex = modPkgDeclName.indexOf(PACKAGE_SEPARATOR);
            if (pkgSeparatorIndex != -1) {
                pkgName = modPkgDeclName.substring(0, pkgSeparatorIndex);
            }
        }
        return pkgName;
    }

    private String parseClassName() {
        String className = null;
        if (type == Type.CLASS || type == Type.CLASS_LOCAL || type == Type.METHOD || type == Type.METHOD_LOCAL) {
            int pkgSeparatorIndex = modPkgDeclName.indexOf(PACKAGE_SEPARATOR);
            if (pkgSeparatorIndex != -1) {
                className = modPkgDeclName.substring(pkgSeparatorIndex + 2);
                int memberSeparatorIndex = className.indexOf(MEMBER_SEPARATOR);
                if (memberSeparatorIndex != -1) {
                    className = className.substring(0, memberSeparatorIndex);
                }
                if (!Character.isUpperCase(className.charAt(0))) {
                    className = null;
                }
            }
        }
        return className;
    }

    private String parseMethodName() {
        String methodName = null;
        if (type == Type.METHOD || type == Type.METHOD_LOCAL) {
            int pkgSeparatorIndex = modPkgDeclName.indexOf(PACKAGE_SEPARATOR);
            if (pkgSeparatorIndex != -1) {
                methodName = modPkgDeclName.substring(pkgSeparatorIndex + 2);
                int memberSeparatorIndex = methodName.indexOf(MEMBER_SEPARATOR);
                if (memberSeparatorIndex != -1) {
                    methodName = methodName.substring(memberSeparatorIndex + 1);
                }
                if (!Character.isLowerCase(methodName.charAt(0))) {
                    methodName = null;
                }
            }
        }
        return methodName;
    }

}