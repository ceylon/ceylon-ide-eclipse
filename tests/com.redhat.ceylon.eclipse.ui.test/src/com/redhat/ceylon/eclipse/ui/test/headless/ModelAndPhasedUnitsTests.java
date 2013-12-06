/*************************************************************************************
 * Copyright (c) 2011 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     JBoss by Red Hat - Initial implementation.
 ************************************************************************************/
package com.redhat.ceylon.eclipse.ui.test.headless;

import java.util.Arrays;

import junit.framework.Assert;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot;
import org.junit.Test;

import com.redhat.ceylon.compiler.loader.AbstractModelLoader;
import com.redhat.ceylon.compiler.loader.ModelLoader.DeclarationType;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.eclipse.core.model.CeylonUnit;
import com.redhat.ceylon.eclipse.core.model.CrossProjectSourceFile;
import com.redhat.ceylon.eclipse.core.model.ExternalSourceFile;
import com.redhat.ceylon.eclipse.core.model.JavaClassFile;
import com.redhat.ceylon.eclipse.core.model.JavaCompilationUnit;
import com.redhat.ceylon.eclipse.core.model.ProjectSourceFile;
import com.redhat.ceylon.eclipse.core.typechecker.CrossProjectPhasedUnit;
import com.redhat.ceylon.eclipse.core.typechecker.ExternalPhasedUnit;
import com.redhat.ceylon.eclipse.core.typechecker.ProjectPhasedUnit;
import com.redhat.ceylon.eclipse.ui.test.AbstractMultiProjectTest;

@SuppressWarnings("restriction")
public class ModelAndPhasedUnitsTests extends AbstractMultiProjectTest {

    @SuppressWarnings("unchecked")
    private <T extends PhasedUnit> T checkPhasedUnitClass(String phasedUnitPath, Class<T> phasedUnitClass) {
        PhasedUnit pu = null;
        
        pu = typeChecker.getPhasedUnitFromRelativePath(phasedUnitPath);
        Assert.assertNotNull("No phased unit for path : " + phasedUnitPath, pu);
        Assert.assertEquals(pu.getUnitFile().getName(), phasedUnitClass, pu.getClass());
        
        return (T) pu;
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Unit> T checkDeclarationUnit(Module module, String declarationName, 
            Class<T> unitClass, 
            String fullPath, 
            String relativePath, 
            String fileName) {
        Declaration declaration = modelLoader.getDeclaration(module, declarationName, DeclarationType.VALUE);
        Assert.assertNotNull("No declaration for name = " + declarationName, declaration);
        Unit unit = declaration.getUnit();
        Assert.assertNotNull("Null Unit for declaration : " + declarationName, unit);
        Assert.assertEquals("Unit for declaration : " + declarationName, unitClass, unit.getClass());
        Assert.assertEquals("Unit Full Path for declaration : " + declarationName, fullPath, unit.getFullPath());
        Assert.assertEquals("Unit Relative Path for declaration : " + declarationName, relativePath, unit.getRelativePath());
        Assert.assertEquals("Unit Filename for declaration : " + declarationName, fileName, unit.getFilename());
        return (T) unit;
    }
    

    @Test
    public void checkMainProjectPhasedUnits() throws CoreException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        if (compilationError != null) {
            throw compilationError;
        }
        
        checkPhasedUnitClass("usedModule/CeylonDeclarations_Main_Ceylon_Project.ceylon", 
                ProjectPhasedUnit.class);
    }
    
    @Test
	public void checkExternalPhasedUnits() throws CoreException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        if (compilationError != null) {
            throw compilationError;
        }
        
        checkPhasedUnitClass("source_and_binary_external_module/CeylonDeclarations_External_Source_Binary.ceylon", 
                ExternalPhasedUnit.class);
    }
    
    @Test
    public void checkReferencedProjectPhasedUnits() throws CoreException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        if (compilationError != null) {
            throw compilationError;
        }
        
        CrossProjectPhasedUnit pu;
        ProjectPhasedUnit opu;

        pu = checkPhasedUnitClass("referencedCeylonProject/CeylonDeclarations_Referenced_Ceylon_Project.ceylon", 
                CrossProjectPhasedUnit.class);
        opu = pu.getOriginalProjectPhasedUnit();
        Assert.assertEquals("referenced-ceylon-project", opu.getProjectResource().getName());
    }
    

    @SuppressWarnings("unchecked")
    public <T extends CeylonUnit> T checkCeylonSourceUnits(Class<T> unitClass,
            String root,
            String moduleName, 
            String declarationSuffix) throws CoreException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        if (compilationError != null) {
            throw compilationError;
        }
        
        CeylonUnit unit;
        CeylonUnit unit2;
        
        Module module = modelLoader.findModule(moduleName, "1.0.0");
        
        unit = checkDeclarationUnit(module, moduleName + ".CeylonTopLevelClass_" + declarationSuffix, 
                unitClass, 
                root + "/" + moduleName + "/CeylonDeclarations_" + declarationSuffix + ".ceylon", 
                moduleName + "/CeylonDeclarations_" + declarationSuffix + ".ceylon", 
                "CeylonDeclarations_" + declarationSuffix + ".ceylon");

        unit2 = checkDeclarationUnit(module, moduleName + ".ceylonTopLevelObject_" + declarationSuffix, 
                unitClass, 
                root + "/" + moduleName + "/CeylonDeclarations_" + declarationSuffix + ".ceylon", 
                moduleName + "/CeylonDeclarations_" + declarationSuffix + ".ceylon", 
                "CeylonDeclarations_" + declarationSuffix + ".ceylon");
        Assert.assertTrue("Different units " + Arrays.asList(unit, unit2) + " for declarations in the same Unit", unit == unit2);

        unit2 = checkDeclarationUnit(module, moduleName + ".ceylonTopLevelMethod_" + declarationSuffix, 
                unitClass, 
                root + "/" + moduleName + "/CeylonDeclarations_" + declarationSuffix + ".ceylon", 
                moduleName + "/CeylonDeclarations_" + declarationSuffix + ".ceylon", 
                "CeylonDeclarations_" + declarationSuffix + ".ceylon");
        Assert.assertTrue("Different units " + Arrays.asList(unit, unit2) + " for declarations in the same Unit", unit == unit2);

        Assert.assertTrue("PhasedUnit of unit : " + unit.getFullPath() + " is not the same as the typechecker one", 
                unit.getPhasedUnit() == typeChecker.getPhasedUnitFromRelativePath(moduleName + "/CeylonDeclarations_" + declarationSuffix + ".ceylon"));
        return (T) unit;
    }
    

    @Test
    public void checkExternalSourceBinaryCeylonUnits() throws CoreException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        if (compilationError != null) {
            throw compilationError;
        }

        IFile archiveFile = mainProject.getFile("imported_modules/source_and_binary_external_module/1.0.0/source_and_binary_external_module-1.0.0.src");
        Assert.assertNotNull(archiveFile);
        checkCeylonSourceUnits(ExternalSourceFile.class,
                archiveFile.getLocation().toString() + "!",
                "source_and_binary_external_module", 
                "External_Source_Binary");
    }
    
    @Test
    public void checkMainProjectCeylonUnits() throws CoreException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        if (compilationError != null) {
            throw compilationError;
        }
        
        ProjectSourceFile unit = checkCeylonSourceUnits(ProjectSourceFile.class,
                "src",
                "usedModule",
                "Main_Ceylon_Project");
        Assert.assertEquals("Eclipse Project Resource for Unit : " + unit.getFullPath(),
                mainProject,
                unit.getProjectResource());
        Assert.assertEquals("Eclipse Root Folder Resource for Unit : " + unit.getFullPath(),
                mainProject.getFolder("src"),
                unit.getRootFolderResource());
        Assert.assertEquals("Eclipse File Resource for Unit : " + unit.getFullPath(),
                mainProject.getFile("src/" + unit.getRelativePath()),
                unit.getFileResource());
    }
    
    @Test
    public void checkReferencedProjectCeylonUnits() throws CoreException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        if (compilationError != null) {
            throw compilationError;
        }
        
        IFile archiveFile = referencedCeylonProject.getFile("modules/referencedCeylonProject/1.0.0/referencedCeylonProject-1.0.0.src");
        Assert.assertNotNull(archiveFile);

        CrossProjectSourceFile unit = checkCeylonSourceUnits(CrossProjectSourceFile.class,
                archiveFile.getLocation().toString() + "!",
                "referencedCeylonProject",
                "Referenced_Ceylon_Project");
        Assert.assertEquals("Eclipse Project Resource for Unit : " + unit.getFullPath(),
                referencedCeylonProject,
                unit.getProjectResource());
        Assert.assertEquals("Eclipse Root Folder Resource for Unit : " + unit.getFullPath(),
                referencedCeylonProject.getFolder("src"),
                unit.getRootFolderResource());
        Assert.assertEquals("Eclipse File Resource for Unit : " + unit.getFullPath(),
                referencedCeylonProject.getFile("src/" + unit.getRelativePath()),
                unit.getFileResource());
    }
    
    @SuppressWarnings("restriction")
    @Test 
    public void checkJavaLibrayrUnits() throws CoreException {
        if (compilationError != null) {
            throw compilationError;
        }
        
        IJavaProject javaProject = JavaCore.create(mainProject);
        
        String jarName = null;
        IClassFile javaElement = null;
        for (IPackageFragmentRoot root : javaProject.getPackageFragmentRoots()) {
            if (root instanceof JarPackageFragmentRoot) {
                JarPackageFragmentRoot jarRoot = (JarPackageFragmentRoot) root;
                IPackageFragment pkg = root.getPackageFragment("java.util.logging");
                if (pkg.exists()) {
                    javaElement = pkg.getClassFile("Logger.class");
                    jarName = jarRoot.getJar().getName();
                    break;
                }
            }
        }
        
        Module module = modelLoader.findModule(AbstractModelLoader.JAVA_BASE_MODULE_NAME, AbstractModelLoader.JDK_MODULE_VERSION);
        
        JavaClassFile javaClass = checkDeclarationUnit(module, "java.util.logging.Logger", 
                JavaClassFile.class, 
                jarName + "!/" + "java/util/logging/Logger.class", 
                "java/util/logging/Logger.class", 
                "Logger.class");
        Assert.assertEquals("Wrong Java Element : ", javaElement, javaClass.getTypeRoot());
        Assert.assertNull("Project Resource should be null :", javaClass.getProjectResource());
        Assert.assertNull("Root Folder Resource should be null :", javaClass.getRootFolderResource());
        Assert.assertNull("File Resource should be null :", javaClass.getFileResource());
    }
    
    @Test 
    public void checkMainProjectJavaCeylonUnits() throws CoreException {
        if (compilationError != null) {
            throw compilationError;
        }
        
        IJavaProject javaProject = JavaCore.create(mainProject);
        
        String rootPath = null;
        ICompilationUnit javaClassElement = null;
        ICompilationUnit javaObjectElement = null;
        ICompilationUnit javaMethodElement = null;
        for (IPackageFragmentRoot root : javaProject.getPackageFragmentRoots()) {
            IPackageFragment pkg = root.getPackageFragment("mainModule");
            if (pkg.exists() && pkg.getCompilationUnit("JavaCeylonTopLevelClass_Main_Ceylon_Project.java").exists()) {
                javaClassElement = pkg.getCompilationUnit("JavaCeylonTopLevelClass_Main_Ceylon_Project.java");
                javaObjectElement = pkg.getCompilationUnit("javaCeylonTopLevelObject_Main_Ceylon_Project_.java");
                javaMethodElement = pkg.getCompilationUnit("javaCeylonTopLevelMethod_Main_Ceylon_Project_.java");
                rootPath = root.getPath().toOSString();
                break;
            }
        }
        
        Module module = modelLoader.findModule("mainModule", "1.0.0");
        JavaCompilationUnit javaClassCompilationUnit = checkDeclarationUnit(module, "mainModule.JavaCeylonTopLevelClass_Main_Ceylon_Project", 
                JavaCompilationUnit.class, 
                rootPath + "/" + "mainModule/JavaCeylonTopLevelClass_Main_Ceylon_Project.java", 
                "mainModule/JavaCeylonTopLevelClass_Main_Ceylon_Project.java", 
                "JavaCeylonTopLevelClass_Main_Ceylon_Project.java");
        Assert.assertEquals("Wrong Java Element for Class : ", javaClassElement, javaClassCompilationUnit.getTypeRoot());
        Assert.assertNotNull("Project Resource  for Class should not be null :", javaClassCompilationUnit.getProjectResource());
        Assert.assertNotNull("Root Folder Resource  for Class should not be null :", javaClassCompilationUnit.getRootFolderResource());
        Assert.assertNotNull("File Resource should  for Class not be null :", javaClassCompilationUnit.getFileResource());

        JavaCompilationUnit javaObjectCompilationUnit = checkDeclarationUnit(module, "mainModule.javaCeylonTopLevelObject_Main_Ceylon_Project", 
                JavaCompilationUnit.class, 
                rootPath + "/" + "mainModule/javaCeylonTopLevelObject_Main_Ceylon_Project_.java", 
                "mainModule/javaCeylonTopLevelObject_Main_Ceylon_Project_.java", 
                "javaCeylonTopLevelObject_Main_Ceylon_Project_.java");
        Assert.assertEquals("Wrong Java Element for Object : ", javaObjectElement, javaObjectCompilationUnit.getTypeRoot());
        Assert.assertNotNull("Project Resource  for Object should not be null :", javaObjectCompilationUnit.getProjectResource());
        Assert.assertNotNull("Root Folder Resource  for Object should not be null :", javaObjectCompilationUnit.getRootFolderResource());
        Assert.assertNotNull("File Resource should  for Object not be null :", javaObjectCompilationUnit.getFileResource());

        JavaCompilationUnit javaMethodCompilationUnit = checkDeclarationUnit(module, "mainModule.javaCeylonTopLevelMethod_Main_Ceylon_Project_", 
                JavaCompilationUnit.class, 
                rootPath + "/" + "mainModule/javaCeylonTopLevelMethod_Main_Ceylon_Project_.java", 
                "mainModule/javaCeylonTopLevelMethod_Main_Ceylon_Project_.java", 
                "javaCeylonTopLevelMethod_Main_Ceylon_Project_.java");
        Assert.assertEquals("Wrong Java Element for Method : ", javaMethodElement, javaMethodCompilationUnit.getTypeRoot());
        Assert.assertNotNull("Project Resource  for Method should not be null :", javaMethodCompilationUnit.getProjectResource());
        Assert.assertNotNull("Root Folder Resource  for Method should not be null :", javaMethodCompilationUnit.getRootFolderResource());
        Assert.assertNotNull("File Resource should  for Method not be null :", javaMethodCompilationUnit.getFileResource());
    }
    
    @Test 
    public void checkMainProjectPureJavaUnits() throws CoreException {
        if (compilationError != null) {
            throw compilationError;
        }
        
        IJavaProject javaProject = JavaCore.create(mainProject);
        
        String rootPath = null;
        ICompilationUnit javaElement = null;
        for (IPackageFragmentRoot root : javaProject.getPackageFragmentRoots()) {
            IPackageFragment pkg = root.getPackageFragment("mainModule");
            if (pkg.exists() && pkg.getCompilationUnit("JavaClassInCeylonModule_Main_Ceylon_Project.java").exists()) {
                javaElement = pkg.getCompilationUnit("JavaClassInCeylonModule_Main_Ceylon_Project.java");
                rootPath = root.getPath().toOSString();
                break;
            }
        }
        
        Module module = modelLoader.findModule("mainModule", "1.0.0");

        JavaCompilationUnit javaClassCompilationUnit = checkDeclarationUnit(module, "mainModule.JavaClassInCeylonModule_Main_Ceylon_Project", 
                JavaCompilationUnit.class, 
                rootPath + "/" + "mainModule/JavaClassInCeylonModule_Main_Ceylon_Project.java", 
                "mainModule/JavaClassInCeylonModule_Main_Ceylon_Project.java", 
                "JavaClassInCeylonModule_Main_Ceylon_Project.java");
        Assert.assertEquals("Wrong Java Element for Pure Java Class : ", javaElement, javaClassCompilationUnit.getTypeRoot());
        Assert.assertNotNull("Project Resource  for Pure Java Class should not be null :", javaClassCompilationUnit.getProjectResource());
        Assert.assertNotNull("Root Folder Resource  Pure Java for Class should not be null :", javaClassCompilationUnit.getRootFolderResource());
        Assert.assertNotNull("File Resource should  Pure Java for Class not be null :", javaClassCompilationUnit.getFileResource());
    }
    
    @Test 
    public void checkReferencedProjectJavaCeylonUnits() throws CoreException {
        if (compilationError != null) {
            throw compilationError;
        }
        
        IJavaProject javaProject = JavaCore.create(referencedCeylonProject);
        
        String rootPath = null;
        ICompilationUnit javaClassElement = null;
        ICompilationUnit javaObjectElement = null;
        ICompilationUnit javaMethodElement = null;
        for (IPackageFragmentRoot root : javaProject.getPackageFragmentRoots()) {
            IPackageFragment pkg = root.getPackageFragment("referencedCeylonProject");
            if (pkg.exists() && pkg.getCompilationUnit("JavaCeylonTopLevelClass_Referenced_Ceylon_Project.java").exists()) {
                javaClassElement = pkg.getCompilationUnit("JavaCeylonTopLevelClass_Referenced_Ceylon_Project.java");
                javaObjectElement = pkg.getCompilationUnit("javaCeylonTopLevelObject_Referenced_Ceylon_Project_.java");
                javaMethodElement = pkg.getCompilationUnit("javaCeylonTopLevelMethod_Referenced_Ceylon_Project_.java");
                rootPath = root.getPath().toOSString();
                break;
            }
        }
        Module module = modelLoader.findModule("referencedCeylonProject", "1.0.0");

        JavaCompilationUnit javaClassCompilationUnit = checkDeclarationUnit(module, "referencedCeylonProject.JavaCeylonTopLevelClass_Referenced_Ceylon_Project", 
                JavaCompilationUnit.class, 
                rootPath + "/" + "referencedCeylonProject/JavaCeylonTopLevelClass_Referenced_Ceylon_Project.java", 
                "referencedCeylonProject/JavaCeylonTopLevelClass_Referenced_Ceylon_Project.java", 
                "JavaCeylonTopLevelClass_Referenced_Ceylon_Project.java");
        Assert.assertEquals("Wrong Java Element for Class : ", javaClassElement, javaClassCompilationUnit.getTypeRoot());
        Assert.assertNotNull("Project Resource  for Class should not be null :", javaClassCompilationUnit.getProjectResource());
        Assert.assertNotNull("Root Folder Resource  for Class should not be null :", javaClassCompilationUnit.getRootFolderResource());
        Assert.assertNotNull("File Resource should  for Class not be null :", javaClassCompilationUnit.getFileResource());

        JavaCompilationUnit javaObjectCompilationUnit = checkDeclarationUnit(module, "referencedCeylonProject.javaCeylonTopLevelObject_Referenced_Ceylon_Project", 
                JavaCompilationUnit.class, 
                rootPath + "/" + "referencedCeylonProject/javaCeylonTopLevelObject_Referenced_Ceylon_Project_.java", 
                "referencedCeylonProject/javaCeylonTopLevelObject_Referenced_Ceylon_Project_.java", 
                "javaCeylonTopLevelObject_Referenced_Ceylon_Project_.java");
        Assert.assertEquals("Wrong Java Element for Object : ", javaObjectElement, javaObjectCompilationUnit.getTypeRoot());
        Assert.assertNotNull("Project Resource  for Object should not be null :", javaObjectCompilationUnit.getProjectResource());
        Assert.assertNotNull("Root Folder Resource  for Object should not be null :", javaObjectCompilationUnit.getRootFolderResource());
        Assert.assertNotNull("File Resource should  for Object not be null :", javaObjectCompilationUnit.getFileResource());

        JavaCompilationUnit javaMethodCompilationUnit = checkDeclarationUnit(module, "referencedCeylonProject.javaCeylonTopLevelMethod_Referenced_Ceylon_Project_", 
                JavaCompilationUnit.class, 
                rootPath + "/" + "referencedCeylonProject/javaCeylonTopLevelMethod_Referenced_Ceylon_Project_.java", 
                "referencedCeylonProject/javaCeylonTopLevelMethod_Referenced_Ceylon_Project_.java", 
                "javaCeylonTopLevelMethod_Referenced_Ceylon_Project_.java");
        Assert.assertEquals("Wrong Java Element for Method : ", javaMethodElement, javaMethodCompilationUnit.getTypeRoot());
        Assert.assertNotNull("Project Resource  for Method should not be null :", javaMethodCompilationUnit.getProjectResource());
        Assert.assertNotNull("Root Folder Resource  for Method should not be null :", javaMethodCompilationUnit.getRootFolderResource());
        Assert.assertNotNull("File Resource should  for Method not be null :", javaMethodCompilationUnit.getFileResource());
    }
    
    @Test 
    public void checkReferencedProjectPureJavaUnits() throws CoreException {
        if (compilationError != null) {
            throw compilationError;
        }
        
        IJavaProject javaProject = JavaCore.create(referencedCeylonProject);
        
        String rootPath = null;
        ICompilationUnit javaElement = null;
        for (IPackageFragmentRoot root : javaProject.getPackageFragmentRoots()) {
            IPackageFragment pkg = root.getPackageFragment("referencedCeylonProject");
            if (pkg.exists() && pkg.getCompilationUnit("JavaClassInCeylonModule_Referenced_Ceylon_Project.java").exists()) {
                javaElement = pkg.getCompilationUnit("JavaClassInCeylonModule_Referenced_Ceylon_Project.java");
                rootPath = root.getPath().toOSString();
                break;
            }
        }
        
        Module module = modelLoader.findModule("referencedCeylonProject", "1.0.0");

        JavaCompilationUnit javaClassCompilationUnit = checkDeclarationUnit(module, "referencedCeylonProject.JavaClassInCeylonModule_Referenced_Ceylon_Project", 
                JavaCompilationUnit.class, 
                rootPath + "/" + "referencedCeylonProject/JavaClassInCeylonModule_Referenced_Ceylon_Project.java", 
                "referencedCeylonProject/JavaClassInCeylonModule_Referenced_Ceylon_Project.java", 
                "JavaClassInCeylonModule_Referenced_Ceylon_Project.java");
        Assert.assertEquals("Wrong Java Element for Pure Java Class : ", javaElement, javaClassCompilationUnit.getTypeRoot());
        Assert.assertNotNull("Project Resource  for Pure Java Class should not be null :", javaClassCompilationUnit.getProjectResource());
        Assert.assertNotNull("Root Folder Resource  Pure Java for Class should not be null :", javaClassCompilationUnit.getRootFolderResource());
        Assert.assertNotNull("File Resource should  Pure Java for Class not be null :", javaClassCompilationUnit.getFileResource());
    }
}