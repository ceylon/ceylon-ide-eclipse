package com.redhat.ceylon.eclipse.code.complete;

import static com.redhat.ceylon.eclipse.code.complete.CodeCompletions.getDescriptionFor;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.applyImports;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.importDeclaration;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.getDecoratedImage;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.getDecorationAttributes;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.CEYLON_FUN;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.CEYLON_LOCAL_FUN;

import java.util.HashSet;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.text.edits.MultiTextEdit;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.DeclarationWithProximity;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;

final class FunctionCompletionProposal extends
        CompletionProposal {
    
    private final CeylonParseController cpc;
    private final Declaration dec;

    private FunctionCompletionProposal(int offset, String prefix,
            String desc, String text, Declaration dec,
            CeylonParseController cpc) {
        super(offset, prefix, 
                getDecoratedImage(dec.isShared() ? 
                        CEYLON_FUN : CEYLON_LOCAL_FUN,
                    getDecorationAttributes(dec), false), 
                desc, text);
        this.cpc = cpc;
        this.dec = dec;
    }

    private DocumentChange createChange(IDocument document)
            throws BadLocationException {
        DocumentChange change = 
                new DocumentChange("Complete Invocation", document);
        change.setEdit(new MultiTextEdit());
        HashSet<Declaration> decs = new HashSet<Declaration>();
        Tree.CompilationUnit cu = cpc.getRootNode();
        importDeclaration(decs, dec, cu);
        int il=applyImports(change, decs, cu, document);
        change.addEdit(createEdit(document));
        offset+=il;
        return change;
    }

    @Override
    public boolean isAutoInsertable() {
        return false;
    }

    @Override
    public void apply(IDocument document) {
        try {
            createChange(document).perform(new NullProgressMonitor());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    protected static void addFunctionProposal(int offset,
            final CeylonParseController cpc, 
            Tree.Primary primary,
            List<ICompletionProposal> result, 
            DeclarationWithProximity dwp,
            IDocument doc) {
        Tree.Term arg = primary;
        while (arg instanceof Tree.Expression) {
            arg = ((Tree.Expression) arg).getTerm(); 
        }
        final int start = arg.getStartIndex();
        final int stop = arg.getStopIndex();
        int origin = primary.getStartIndex();
        String argText;
        String prefix;
        try {
            //the argument
            argText = doc.get(start, stop-start+1);
            //the text to replace
            prefix = doc.get(origin, offset-origin);
        }
        catch (BadLocationException e) {
            return;
        }
        final Declaration dec = dwp.getDeclaration();
        String text = dec.getName(arg.getUnit())
                + "(" + argText + ")";
        if (((Functional)dec).isDeclaredVoid()) {
            text += ";";
        }
        result.add(new FunctionCompletionProposal(offset, prefix, 
                getDescriptionFor(dwp) + "(...)", text, dec, cpc));
    }
}