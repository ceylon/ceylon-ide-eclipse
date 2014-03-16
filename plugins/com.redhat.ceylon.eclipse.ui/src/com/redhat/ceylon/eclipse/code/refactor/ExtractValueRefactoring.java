package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.applyImports;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.importType;
import static com.redhat.ceylon.eclipse.util.Nodes.findStatement;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.util.Indents.getIndent;
import static org.eclipse.ltk.core.refactoring.RefactoringStatus.createWarningStatus;

import java.util.HashSet;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.ui.texteditor.ITextEditor;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.util.Nodes;

public class ExtractValueRefactoring extends AbstractRefactoring {
    
    private String newName;
    private boolean explicitType;
    private boolean getter;
    private ProducedType type;
    private boolean canBeInferred;
    
    public ExtractValueRefactoring(ITextEditor editor) {
        super(editor);
        newName = Nodes.guessName(node)[0];
    }
    
    @Override
    public boolean isEnabled() {
        return sourceFile!=null &&
                !sourceFile.getName().equals("module.ceylon") &&
                !sourceFile.getName().equals("package.ceylon") &&
                node instanceof Tree.Term;
    }

    public String getName() {
        return "Extract Value";
    }

    public boolean forceWizardMode() {
        Declaration existing = node.getScope()
                .getMemberOrParameter(node.getUnit(), newName, null, false);
        return existing!=null;
    }
    
    public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        // Check parameters retrieved from editor context
        return new RefactoringStatus();
    }

    public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        Declaration existing = node.getScope()
                .getMemberOrParameter(node.getUnit(), newName, null, false);
        if (null!=existing) {
            return createWarningStatus("An existing declaration named '" +
                    newName + "' already exists in the same scope");
        }
        return new RefactoringStatus();
    }

    public TextChange createChange(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        TextChange tfc = newLocalChange();
        extractInFile(tfc);
        return tfc;
    }
    
    IRegion typeRegion;
    IRegion decRegion;
    IRegion refRegion;

    void extractInFile(TextChange tfc) throws CoreException {
        tfc.setEdit(new MultiTextEdit());
        IDocument doc = tfc.getCurrentDocument(null);
        Tree.Term term = (Tree.Term) node;
        Tree.Statement statement = findStatement(rootNode, node);
        boolean toplevel;
        if (statement instanceof Tree.Declaration) {
            toplevel = ((Tree.Declaration) statement).getDeclarationModel().isToplevel();
        }
        else {
            toplevel = false;
        }
        String exp = toString(unparenthesize(term));
        type = node.getUnit()
                .denotableType(term.getTypeModel());
        int il;
        String typeDec;
        if (type==null || type.isUnknown()) {
            typeDec = "dynamic";
            il = 0;
        }
        else if (explicitType||toplevel) {
            typeDec = type.getProducedTypeName(node.getUnit());
            HashSet<Declaration> decs = new HashSet<Declaration>();
            importType(decs, type, rootNode);
            il = applyImports(tfc, decs, rootNode, doc);
        }
        else {
            canBeInferred = true;
            typeDec = "value";
            il = 0;
        }
        String dec = typeDec + " " +  newName + 
                (getter ? " { return " + exp  + "; } " : " = " + exp + ";");
        String text = dec + getDefaultLineDelimiter(doc) + getIndent(statement, doc);
        Integer start = statement.getStartIndex();
        tfc.addEdit(new InsertEdit(start, text));
        tfc.addEdit(new ReplaceEdit(Nodes.getNodeStartOffset(node), Nodes.getNodeLength(node), newName));
        typeRegion = new Region(start, typeDec.length());
        decRegion = new Region(start+typeDec.length()+1, newName.length());
        refRegion = new Region(Nodes.getNodeStartOffset(node)+text.length()+il, newName.length());
    }
    
    public boolean canBeInferred() {
        return canBeInferred;
    }

    public void setNewName(String text) {
        newName = text;
    }
    
    public String getNewName() {
        return newName;
    }
    
    public void setExplicitType() {
        this.explicitType = !explicitType;
    }
    
    public void setGetter() {
        this.getter = !getter;
    }

    ProducedType getType() {
        return type;
    }
    
	public String[] getNameProposals() {
		return Nodes.guessName(node);
	}
    
}
