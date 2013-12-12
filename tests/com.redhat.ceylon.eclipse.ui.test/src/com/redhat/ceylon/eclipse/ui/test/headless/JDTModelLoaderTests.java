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

import junit.framework.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.junit.BeforeClass;
import org.junit.Ignore;

import com.redhat.ceylon.compiler.java.codegen.Decl;
import com.redhat.ceylon.compiler.java.test.model.ModelLoaderTest;
import com.redhat.ceylon.compiler.java.test.model.RunnableTest;
import com.redhat.ceylon.compiler.loader.ModelLoader.DeclarationType;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder;
import com.redhat.ceylon.eclipse.core.model.JDTModelLoader;
import com.redhat.ceylon.eclipse.ui.test.Utils;
import com.redhat.ceylon.eclipse.ui.test.Utils.CeylonBuildSummary;

import static com.redhat.ceylon.eclipse.ui.test.Utils.buildProject; 
import static org.junit.Assert.assertTrue;

/**
 * 
 * @author david
 * 
 */
public class JDTModelLoaderTests extends ModelLoaderTest {

    static private AssertionError compilationError = null;
    static private IProject projectDeclarations = null;
    static private IProject projectReferences = null;
    
	@BeforeClass
	public static void compileDeclarationsAndReferences() throws InterruptedException, CoreException {
	    try {
	        IPath projectDescriptionPath = null;
	        IPath userDirPath = new Path(System.getProperty("user.dir"));
            final IWorkspace workspace = ResourcesPlugin.getWorkspace();
            
            CeylonBuildSummary declarationsSummary = new CeylonBuildSummary(workspace.getRoot().getProject("declarations"));
            declarationsSummary.install();

	        try {
        		projectDescriptionPath = userDirPath.append("resources/model-loader-tests/declarations/.project");
        		projectDeclarations = Utils.importProject(workspace, "model-loader-tests", projectDescriptionPath);
	        }
            catch(Exception e) {
                Assert.fail("Import of the declarations project failed with the exception : \n" + e.toString());
            }

            assertTrue("A build should have been started after import of the declarations project", declarationsSummary.waitForBuildEnd(120));
            IFile carFile = projectDeclarations.getFile("modules/declarations/1.0.0/declarations-1.0.0.car");
            carFile.refreshLocal(0, null);
            Assert.assertTrue("declarations Project should  should produce a CAR",
                carFile.exists());
	        
            assertTrue("We should be able to remove the generated src archive",
                    projectDeclarations.getFile("modules/declarations/1.0.0/declarations-1.0.0.src")
                        .getLocation().toFile().delete());
            assertTrue("We should be able to remove the sha1 of the generated src archive",
                    projectDeclarations.getFile("modules/declarations/1.0.0/declarations-1.0.0.src.sha1")
                        .getLocation().toFile().delete());

	        CeylonBuildSummary referencesSummary = new CeylonBuildSummary(workspace.getRoot().getProject("references"));
            referencesSummary.install();
            
	        try {
                projectDescriptionPath = userDirPath.append("resources/model-loader-tests/references/.project");
                projectReferences = Utils.importProject(workspace, "model-loader-tests",
                        projectDescriptionPath);
            }
            catch(Exception e) {
                Assert.fail("Build of the references project failed with the exception : \n" + e.toString());
            }
            
            assertTrue("A build should have been started after import of the declarations project", referencesSummary.waitForBuildEnd(120));
            carFile = projectReferences.getFile("modules/references/1.0.0/references-1.0.0.car");
            carFile.refreshLocal(0, null);
            Assert.assertTrue("references Project should produce a CAR", 
                    carFile.exists());
	    }
	    catch(AssertionError e) {
	        compilationError = e;
	    }
	}

    @Override
    protected void verifyClassLoading(String ceylon) {
        if (compilationError != null) {
            throw compilationError;
        }
        
        PhasedUnit phasedUnit = CeylonBuilder.getProjectTypeChecker(projectDeclarations)
                                            .getPhasedUnitFromRelativePath("declarations/" + ceylon);
        final Map<String,Declaration> decls = new HashMap<String,Declaration>();
        for(Declaration decl : phasedUnit.getUnit().getDeclarations()){
            if(decl.isToplevel()){
                decls.put(getQualifiedPrefixedName(decl), decl);
            }
        }
        
        JDTModelLoader modelLoader = CeylonBuilder.getProjectModelLoader(projectReferences);
        
        for(Entry<String, Declaration> entry : decls.entrySet()){
            String quotedQualifiedName = entry.getKey().substring(1);
            Module module = Decl.getModuleContainer(entry.getValue().getContainer());
            Declaration modelDeclaration = modelLoader.getDeclaration(module, quotedQualifiedName, 
                    entry.getValue() instanceof Value ? DeclarationType.VALUE : DeclarationType.TYPE);
            Assert.assertNotNull(modelDeclaration);
            // make sure we loaded them exactly the same
            compareDeclarations(entry.getValue(), modelDeclaration);
        }
    }

    
    
    @Override
    protected void compareDeclarations(Declaration validDeclaration,
            Declaration modelDeclaration) {
        if (modelDeclaration.getUnit() != null && 
                modelDeclaration.getUnit().getFilename() != null && 
                modelDeclaration.getUnit().getFilename().endsWith(".ceylon")) {
            return;
        }
        super.compareDeclarations(validDeclaration, modelDeclaration);
    }
    

    
    
    @Override
    @Ignore
    public void testTypeParserUsingSourceModel(){
    }

    @Override
    @Ignore
    public void bogusModelAnnotationsTopLevelAttribute() {
    }

    @Override
    @Ignore
    public void bogusModelAnnotationsTopLevelMethod() {
    }

    @Override
    @Ignore
    public void bogusModelAnnotationsTopLevelClass() {
    }
    
    @Override
    @Ignore
    public void loadVariadic() {
    }
    
    @Override
    protected void compile(String... ceylon) {
    }

    @Override
    protected String moduleForJavaModelLoading() {
        // TODO Auto-generated method stub
        return "declarations";
    }

    @Override
    protected String packageForJavaModelLoading() {
        // TODO Auto-generated method stub
        return "declarations";
    }

    @Override
    public void parallelLoader() {
        ((JDTModelLoader) CeylonBuilder.getProjectModelLoader(projectReferences)).loadJDKModules();
        super.parallelLoader();
    }

    @Override
    protected void verifyClassLoading(String ceylon, RunnableTest test,
            List<String> options) {
        test.test(CeylonBuilder.getProjectModelLoader(projectReferences));
    }
    
}
