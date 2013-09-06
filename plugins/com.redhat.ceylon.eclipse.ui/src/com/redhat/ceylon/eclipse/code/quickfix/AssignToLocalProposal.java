package com.redhat.ceylon.eclipse.code.quickfix;

import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.CORRECTION;
import static com.redhat.ceylon.eclipse.code.refactor.AbstractRefactoring.guessName;

import java.util.Collection;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.Util;
import com.redhat.ceylon.eclipse.util.FindStatementVisitor;

class AssignToLocalProposal extends ChangeCorrectionProposal {
    
    final IFile file;
    final int offset;
    final int length;
    
    AssignToLocalProposal(int offset, int length, IFile file, 
            TextChange change) {
        super("Assign expression to new local", change);
        this.file=file;
        this.offset=offset;
        this.length=length;
    }
    
    @Override
    public void apply(IDocument document) {
        super.apply(document);
        Util.gotoLocation(file, offset, length);
    }
    
    static void addAssignToLocalProposal(IFile file, Tree.CompilationUnit cu, 
            Collection<ICompletionProposal> proposals, Node node) {
        //if (node instanceof Tree.Term) {
            FindStatementVisitor fsv = new FindStatementVisitor(node, false);
            fsv.visit(cu);
            Tree.Statement st = fsv.getStatement();
            if (st instanceof Tree.ExpressionStatement) {
                int offset = st.getStartIndex();
                TextChange change = new TextFileChange("Assign To Local", file);
//                TextChange change = new DocumentChange("Assign To Local", doc);
                change.setEdit(new MultiTextEdit());
                String name = guessName(((Tree.ExpressionStatement) st).getExpression());
                change.addEdit(new InsertEdit(offset, "value " + name + " = "));
                String terminal = st.getEndToken().getText();
                if (!terminal.equals(";")) {
                    change.addEdit(new InsertEdit(st.getStopIndex()+1, ";"));
                }
                proposals.add(new AssignToLocalProposal(offset+6, name.length(), file, change));
            }
        //}
    }
}