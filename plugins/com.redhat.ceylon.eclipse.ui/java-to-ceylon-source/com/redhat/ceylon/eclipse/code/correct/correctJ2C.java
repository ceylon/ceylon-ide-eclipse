package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.java2ceylon.Java2CeylonProxies.modelJ2C;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MultiTextEdit;

import com.redhat.ceylon.compiler.java.runtime.model.TypeDescriptor;
import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.java2ceylon.CorrectJ2C;
import com.redhat.ceylon.eclipse.util.EditorUtil;
import com.redhat.ceylon.ide.common.correct.QuickFixData;
import com.redhat.ceylon.ide.common.correct.addAnnotationQuickFix_;
import com.redhat.ceylon.ide.common.correct.assignToLocalQuickFix_;
import com.redhat.ceylon.ide.common.correct.ideQuickFixManager_;
import com.redhat.ceylon.ide.common.correct.importProposals_;
import com.redhat.ceylon.ide.common.correct.refineEqualsHashQuickFix_;
import com.redhat.ceylon.ide.common.correct.refineFormalMembersQuickFix_;
import com.redhat.ceylon.ide.common.model.BaseCeylonProject;
import com.redhat.ceylon.ide.common.platform.CommonDocument;
import com.redhat.ceylon.ide.common.platform.DeleteEdit;
import com.redhat.ceylon.ide.common.platform.InsertEdit;
import com.redhat.ceylon.ide.common.platform.ReplaceEdit;
import com.redhat.ceylon.ide.common.platform.TextEdit;
import com.redhat.ceylon.ide.common.util.toJavaList_;
import com.redhat.ceylon.model.typechecker.model.Declaration;

import ceylon.interop.java.CeylonSet;
import ceylon.interop.java.CeylonStringIterable;

public class correctJ2C implements CorrectJ2C {
    @Override
    public importProposals_ importProposals() {
        return importProposals_.get_();
    }
    
    @Override
    public ideQuickFixManager_ eclipseQuickFixManager() {
        return ideQuickFixManager_.get_();
    }
    
    public addAnnotationQuickFix_ addAnnotationsQuickFix() {
        return addAnnotationQuickFix_.get_();
    }
    
    @Override
    public void addQuickFixes(
            ProblemLocation problem,
            Tree.CompilationUnit rootNode,
            Node node,
            IProject project,
            Collection<ICompletionProposal> proposals,
            CeylonEditor editor, 
            TypeChecker tc, 
            IFile file,
            IDocument doc) {
        BaseCeylonProject ceylonProject = modelJ2C().ceylonModel().getProject(project);
        EclipseQuickFixData data = new EclipseQuickFixData(problem, rootNode, 
                node, project, proposals, editor, ceylonProject, doc);

        ideQuickFixManager_.get_().addQuickFixes(data, tc);
    }

    @Override
    public void addQuickAssists(
            Tree.CompilationUnit rootNode,
            Node node,
            IProject project,
            Collection<ICompletionProposal> proposals,
            CeylonEditor editor, 
            IFile file,
            IDocument doc,
            Tree.Statement statement,
            Tree.Declaration declaration,
            Tree.NamedArgument argument,
            Tree.ImportMemberOrType imp,
            Tree.OperatorExpression oe,
            int currentOffset) {
        BaseCeylonProject ceylonProject = modelJ2C().ceylonModel().getProject(project);
        EclipseQuickFixData data = new EclipseQuickFixData(null, rootNode, 
                node, project, proposals, editor, ceylonProject, doc);

        ideQuickFixManager_.get_().addQuickAssists(data, statement, declaration, argument, imp, oe, currentOffset);
    }

    @Override
    public void addRefineFormalMembersProposal(
            CompilationUnit rootNode,
            Node node,
            List<ICompletionProposal> list,
            CeylonEditor ce,
            IProject project) {

        IDocument doc = EditorUtil.getDocument(ce.getEditorInput());
        QuickFixData data = newData(rootNode, node, list, ce, project, doc);

        refineFormalMembersQuickFix_.get_()
            .addRefineFormalMembersProposal(data, false);
    }

    @Override
    public void addRefineEqualsHashProposal(
            CompilationUnit rootNode,
            Node node,
            List<ICompletionProposal> list,
            CeylonEditor ce,
            IProject project) {
        
        IDocument doc = EditorUtil.getDocument(ce.getEditorInput());
        QuickFixData data = newData(rootNode, node, list, ce, project, doc);

        refineEqualsHashQuickFix_.get_()
            .addRefineEqualsHashProposal(data, ce.getSelection().getOffset());        
    }

    @Override
    public void addAssignToLocalProposal(CompilationUnit rootNode, Node node,
            List<ICompletionProposal> list, CeylonEditor ce) {

        IDocument doc = EditorUtil.getDocument(ce.getEditorInput());
        IProject project = EditorUtil.getProject(ce.getEditorInput());
        QuickFixData data = newData(rootNode, node, list, ce, project, doc);
       
        assignToLocalQuickFix_.get_().addProposal(data);
    }
    
    @Override
    public QuickFixData newData(CompilationUnit rootNode, Node node,
            List<ICompletionProposal> list, CeylonEditor ce,
            IProject project, IDocument doc) {
        
        BaseCeylonProject ceylonProject = modelJ2C().ceylonModel()
                .getProject(project);
        
        return new EclipseQuickFixData(
                new ProblemLocation(0, 0, 0),
                rootNode, node, null, list, ce, ceylonProject, doc
        );
    }

    @Override
    public CommonDocument newDocument(IDocument nativeDoc) {
        return new EclipseDocument(nativeDoc);
    }

    @Override
    public void importEdits(Object editOrChange, CompilationUnit rootNode,
            Set<com.redhat.ceylon.model.typechecker.model.Declaration> declarations,
            Collection<String> aliases,
            IDocument doc) {

        List<InsertEdit> edits = toJavaList_.toJavaList(
                TypeDescriptor.klass(InsertEdit.class),
                importProposals_.get_().importEdits(
                        rootNode, 
                        new CeylonSet<>(null,declarations), 
                        new CeylonStringIterable(aliases),
                        null, newDocument(doc))
        );

        for (InsertEdit importEdit: edits) {
            org.eclipse.text.edits.InsertEdit ie = 
                    new org.eclipse.text.edits.InsertEdit(
                            (int) importEdit.getStart(), importEdit.getText());
            
            if (editOrChange instanceof MultiTextEdit) {
                ((MultiTextEdit) editOrChange).addChild(ie);                
            } else if (editOrChange instanceof TextFileChange) {
                ((TextFileChange) editOrChange).addEdit(ie);
            }
        }
    }

    @Override
    public void importEditForMove(TextFileChange change,
            CompilationUnit rootNode, Set<Declaration> declarations,
            Collection<String> aliases, String newName, String oldName,
            IDocument doc) {

        List<TextEdit> edits = toJavaList_.toJavaList(
                TypeDescriptor.klass(InsertEdit.class),
                importProposals_.get_().importEditForMove(rootNode, declarations,
                        aliases, newName, oldName, newDocument(doc))
        );

        for (TextEdit importEdit: edits) {
            if (importEdit instanceof InsertEdit) {
                change.addEdit(new org.eclipse.text.edits.InsertEdit(
                        (int) importEdit.getStart(), importEdit.getText()));
            } else if (importEdit instanceof ReplaceEdit) {
                change.addEdit(new org.eclipse.text.edits.ReplaceEdit(
                        (int) importEdit.getStart(),
                        (int) importEdit.getLength(),
                        importEdit.getText()));
            } else if (importEdit instanceof DeleteEdit) {
                change.addEdit(new org.eclipse.text.edits.DeleteEdit(
                        (int) importEdit.getStart(),
                        (int) importEdit.getLength()));
            }
        }
    }
}
