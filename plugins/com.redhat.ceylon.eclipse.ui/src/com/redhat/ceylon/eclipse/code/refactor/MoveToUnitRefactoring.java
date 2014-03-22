package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getProjectTypeChecker;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.util.Nodes.getNodeLength;
import static com.redhat.ceylon.eclipse.util.Nodes.getNodeStartOffset;
import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.getFile;
import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.getSelectedNode;
import static com.redhat.ceylon.eclipse.code.refactor.MoveUtil.*;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;

public class MoveToUnitRefactoring extends Refactoring {
    
    private final CeylonEditor editor;
    private final Tree.CompilationUnit rootNode;
    private final Tree.Declaration node;
    private final IFile originalFile; 
    private final IDocument document;
    private IFile targetFile; 
    private IPath targetSourceDirPath;
    
    public void setTargetFile(IFile targetFile) {
        this.targetFile = targetFile;
    }
    
    public void setTargetSourceDirPath(IPath targetSourceDirPath) {
        this.targetSourceDirPath = targetSourceDirPath;
    }
    
    public MoveToUnitRefactoring(CeylonEditor ceylonEditor) {
        editor = ceylonEditor;
        rootNode = editor.getParseController().getRootNode();
        document = editor.getDocumentProvider()
                .getDocument(editor.getEditorInput());
        originalFile = getFile(editor.getEditorInput());
        if (rootNode!=null) {
            Node node = getSelectedNode(editor);
            if (node instanceof Tree.Declaration) {
                this.node = (Tree.Declaration) node;
            }
            else {
                this.node = null;
            }
        }
        else {
            this.node = null;
        }
    }
    
    @Override
    public boolean isEnabled() {
        return node!=null;
    }

    @Override
    public String getName() {
        return "Move To Unit";
    }

    @Override
    public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        return new RefactoringStatus();
    }

    @Override
    public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        // TODO Auto-generated method stub
        return new RefactoringStatus();
    }

    @Override
    public Change createChange(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        IProject project = targetFile.getProject();
        String relpath = targetFile.getFullPath()
                .makeRelativeTo(targetSourceDirPath)
                .toPortableString();
        PhasedUnit npu = getProjectTypeChecker(project)
                .getPhasedUnitFromRelativePath(relpath);
        Tree.CompilationUnit ncu = npu.getCompilationUnit();
        String original = rootNode.getUnit().getPackage().getNameAsString();
        String moved = ncu.getUnit().getPackage().getNameAsString();
        
        Declaration dec = node.getDeclarationModel();
        int start = getNodeStartOffset(node);
        int length = getNodeLength(node);
        
        CompositeChange change = 
                new CompositeChange("Move to Source File");
        
        TextChange targetUnitChange = 
                new TextFileChange("Move to Source File", 
                        targetFile);
        targetUnitChange.setEdit(new MultiTextEdit());
        IDocument targetUnitDocument = 
                targetUnitChange.getCurrentDocument(null);
        String contents;
        try {
            contents = document.get(start, length);
        }
        catch (BadLocationException e) {
            e.printStackTrace();
            throw new OperationCanceledException();
        }
        String delim = getDefaultLineDelimiter(targetUnitDocument);
        String text = delim + contents;
        Set<String> packages = new HashSet<String>();
        addImportEdits(node, targetUnitChange, targetUnitDocument, 
                ncu, packages, dec);
        removeImport(original, dec, ncu, targetUnitChange, packages);
        targetUnitChange.addEdit(new InsertEdit(targetUnitDocument.getLength(), text));
        change.add(targetUnitChange);
        
        TextChange originalUnitChange = createEditorChange(editor, document);
        originalUnitChange.setEdit(new MultiTextEdit());
        refactorImports(node, originalUnitChange, original, moved, rootNode);
        originalUnitChange.addEdit(new DeleteEdit(start, length));
        change.add(originalUnitChange);
        
        refactorProjectImports(node, originalFile, targetFile, change, original, moved);
        return change;
    }

    public int getOffset() {
        return 0; //TODO!!!
    }

    public IPath getTargetPath() {
        return targetFile.getFullPath();
    }

}
