package com.redhat.ceylon.eclipse.ui.test.swtbot;


import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.keyboard.KeyboardFactory;
import org.eclipse.swtbot.swt.finder.keyboard.KeyboardStrategy;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.eclipse.swtbot.swt.finder.utils.Position;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotLink;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsCollectionContaining;
import org.hamcrest.core.IsEqual;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.redhat.ceylon.eclipse.code.editor.EditorUtil;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.CeylonBuildHook;
import com.redhat.ceylon.eclipse.ui.test.AbstractMultiProjectTest;
import com.redhat.ceylon.eclipse.ui.test.Utils;

import static com.redhat.ceylon.eclipse.ui.test.Utils.openInEditor;

@RunWith(SWTBotJunit4ClassRunner.class)
public class IncrementalBuildTests extends AbstractMultiProjectTest {
    private static SWTWorkbenchBot  bot;
    
    @BeforeClass
    public static void beforeClass() throws InterruptedException {
        bot = Utils.createBot();
        importAndBuild();
    }
    
    @After
    public void resetWorkbench() {
        Utils.resetWorkbench(bot);
    }
    
    @Test
    public void bug589_AddedJavaMethodNotSeen() throws InterruptedException, CoreException {
        openInEditor(mainProject, "src/mainModule/run.ceylon");
        openInEditor(mainProject, "javaSrc/mainModule/JavaClassInCeylonModule_Main_Ceylon_Project.java");
        
        SWTBotEclipseEditor javaFileEditor = Utils.showEditorByTitle(bot, "JavaClassInCeylonModule_Main_Ceylon_Project.java");
        Position javaClassDeclarationPosition = Utils.positionInTextEditor(javaFileEditor, "public class JavaClassInCeylonModule_Main_Ceylon_Project", 0);
        String javaEditorText = javaFileEditor.getText();
        javaFileEditor.insertText(javaClassDeclarationPosition.line + 1, 0, "public void newMethodToTest() {}\n");
        
        Utils.CeylonBuildSummary buildSummary = new Utils.CeylonBuildSummary(mainProject);
        buildSummary.install();
        javaFileEditor.save();
        try {
            buildSummary.waitForBuildEnd(30);
            
            SWTBotEclipseEditor ceylonFileEditor = Utils.showEditorByTitle(bot, "run.ceylon");
            Position javaClassUsePosition = Utils.positionInTextEditor(ceylonFileEditor, "value v5 = JavaClassInCeylonModule_Main_Ceylon_Project();", 0);
            String ceylonEditorText = ceylonFileEditor.getText();
            ceylonFileEditor.insertText(javaClassUsePosition.line + 1, 0,"v5.newMethodToTest();\n");
            
            /*
            ceylonFileEditor.navigateTo(18, 3);
            List<String> proposals = javaFileEditor.getAutoCompleteProposals("");
            assertThat("The new method of the Java class should be proposed",
                   proposals,
                   new IsCollectionContaining(new IsEqual("test()")));
                   */
            
            buildSummary = new Utils.CeylonBuildSummary(mainProject);
            buildSummary.install();
            ceylonFileEditor.save();
            try {
                buildSummary.waitForBuildEnd(30);
                assertThat("The build should not have any error",
                        Utils.getProjectErrorMarkers(mainProject),
                        Matchers.empty());
            }
            finally {
                ceylonFileEditor.setText(ceylonEditorText);
                ceylonFileEditor.saveAndClose();
            }
        }
        finally {
            javaFileEditor.setText(javaEditorText);
            javaFileEditor.saveAndClose();
        }
    }

    @Test
    public void bug821_AddedJavaClassNotSeen() throws InterruptedException, CoreException {

        Utils.CeylonBuildSummary buildSummary = new Utils.CeylonBuildSummary(mainProject);
        buildSummary.install();
        IFile useFile = copyFileFromResources("bug821", "mainModule/Use.ceylon", mainProject, "src");        
        try {
            buildSummary.waitForBuildEnd(30);
            assertThat("The build should not have any error",
                    Utils.getProjectErrorMarkers(mainProject),
                    Matchers.contains(stringContainsInOrder(Arrays.asList("src/mainModule/Use.ceylon", "l.2","type declaration does not exist"))));
            
            buildSummary = new Utils.CeylonBuildSummary(mainProject);
            buildSummary.install();
            IFile declarationFile = copyFileFromResources("bug821", "mainModule/UsedDeclaration.java", mainProject, "javaSrc");
            try {
                buildSummary.waitForBuildEnd(30);
                assertThat("The build should not have any error",
                        Utils.getProjectErrorMarkers(mainProject),
                        Matchers.empty());
            }
            finally {
                declarationFile.delete(true, null);
            }
        }
        finally {
            useFile.delete(true, null);
        }
    }
    
}
