package com.redhat.ceylon.eclipse.core.launch;

import static com.redhat.ceylon.eclipse.core.builder.CeylonNature.NATURE_ID;
import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.EDITOR_ID;
import static java.lang.Integer.parseInt;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.PackageFragmentRoot;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.IHyperlink;
import org.eclipse.ui.console.IPatternMatchListenerDelegate;
import org.eclipse.ui.console.PatternMatchEvent;
import org.eclipse.ui.console.TextConsole;
import org.eclipse.ui.part.FileEditorInput;

import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.core.external.CeylonArchiveFileStore;
import com.redhat.ceylon.eclipse.util.EditorUtil;

public class CeylonPatternMatchListenerDelegate 
        implements IPatternMatchListenerDelegate {

    private TextConsole console;
    
    @Override
    public void connect(TextConsole console) {
        this.console = console;
    }

    @Override
    public void disconnect() {
        console = null; 
    }

    @Override
    public void matchFound(PatternMatchEvent event) {
        try {
            //strip off leading "at " in match
            int offset = event.getOffset()+3;
            int length = event.getLength()-4;
            String text = console.getDocument().get(offset, length);
            int j = text.indexOf("(");
            int i = text.indexOf(":", j);
            final String pack = text.substring(0,j);
            final String file = text.substring(j+1, i);
            final String line = text.substring(i+1);
            console.addHyperlink(new IHyperlink() {
                @Override
                public void linkExited() {}
                @Override
                public void linkEntered() {}
                
                @Override
                public void linkActivated() {
                    gotoFileAndLine(file, line, pack);
                }
            }, event.getOffset()+4+j, event.getLength()-5-j);
        } 
        catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    public static void gotoFileAndLine(String fileName, 
            String line, String qualifiedName) {
        String packageName = extractPackageName(qualifiedName);
        IWorkspaceRoot root = getWorkspace().getRoot();
        IEditorInput input = null;
        //TODO: only look in Ceylon projects
        for (IProject p: root.getProjects()) {
            try {
                if (p.isAccessible() && p.hasNature(NATURE_ID)) {
                    IPackageFragmentRoot[] roots = 
                            JavaCore.create(p).getAllPackageFragmentRoots();
                    for (IPackageFragmentRoot pfr: roots) {
                        if (pfr.exists()) {
                            if (pfr.getKind()==PackageFragmentRoot.K_BINARY) {
                                IPath sourceAttachmentPath = pfr.getSourceAttachmentPath();
                                if (sourceAttachmentPath!=null) {
                                    String ext = sourceAttachmentPath.getFileExtension();
                                    if (ext!=null && ext.equalsIgnoreCase("src")) {
                                        String packagePath = packageName.replace('.', '/'); //TODO: I *think* this works on windows
                                        IFileStore archiveFileStore = 
                                                EFS.getStore(URIUtil.toURI(sourceAttachmentPath));
                                        if (archiveFileStore.fetchInfo().exists()) {
                                            CeylonArchiveFileStore sourceFileStore = 
                                                    new CeylonArchiveFileStore(archiveFileStore, 
                                                            new Path(packagePath).append(fileName));
                                            if (sourceFileStore.fetchInfo().exists()) {
                                                input = EditorUtil.getEditorInput(sourceFileStore);
                                                open(line, input);
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                            if (pfr.getKind()==PackageFragmentRoot.K_SOURCE) {
                                IPackageFragment pf = pfr.getPackageFragment(packageName);
                                if (pf.exists()) {
                                    IResource folder = pf.getResource();
                                    if (folder instanceof IFolder) {
                                        IFile file = ((IFolder) folder).getFile(fileName);
                                        if (file!=null && file.exists()) {
                                            input = new FileEditorInput(file);
                                            open(line, input);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Compute the package name by stripping off the method
     * and class name in the qualified name
     */
    private static String extractPackageName(String qualifiedName) {
        String pack = qualifiedName;
        int index = pack.lastIndexOf('.');
        pack = index>=0 ? pack.substring(0, index) : "";
        index = pack.lastIndexOf('.');
        pack = index>=0 ? pack.substring(0, index) : "";
        return pack;
    }
    
    private static void open(String line, IEditorInput input) {
        if (input!=null) {
            IWorkbenchPage activePage = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow().getActivePage();
            try {
                CeylonEditor editor = (CeylonEditor) 
                        activePage.openEditor(input, EDITOR_ID, true);
                IRegion li = editor.getCeylonSourceViewer().getDocument()
                        .getLineInformation(parseInt(line)-1);
                editor.selectAndReveal(li.getOffset(), li.getLength());
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }    

}
