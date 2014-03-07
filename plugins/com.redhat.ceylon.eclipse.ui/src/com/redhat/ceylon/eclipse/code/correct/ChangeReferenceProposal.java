package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.code.complete.CeylonCompletionProcessor.getProposals;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.getOccurrenceLocation;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.IMPORT;
import static com.redhat.ceylon.eclipse.code.correct.CorrectionUtil.getLevenshteinDistance;
import static com.redhat.ceylon.eclipse.code.correct.CreateProposal.getDocument;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.importEdits;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.isImported;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.MINOR_CHANGE;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.swt.graphics.Point;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.DeclarationWithProximity;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation;
import com.redhat.ceylon.eclipse.util.Nodes;

class ChangeReferenceProposal extends CorrectionProposal 
        implements ICompletionProposalExtension {
    
    private ChangeReferenceProposal(ProblemLocation problem, 
            String name, String pkg, TextFileChange change) {
        super("Change reference to '" + name + "'" + pkg, change, 
                new Point(problem.getOffset(), name.length()), 
                MINOR_CHANGE);
    }
    
    static void addChangeReferenceProposal(ProblemLocation problem,
            Collection<ICompletionProposal> proposals, IFile file,
            String brokenName, DeclarationWithProximity dwp, int dist,
            Tree.CompilationUnit cu) {
        TextFileChange change = 
                new TextFileChange("Change Reference", file);
        change.setEdit(new MultiTextEdit());
        IDocument doc = getDocument(change);
        Declaration dec = dwp.getDeclaration();
        String pkg = "";
        if (dec.isToplevel() && 
                !isImported(dec, cu) && 
                isInPackage(cu, dec)) {
            String pn = dec.getContainer().getQualifiedNameString();
            pkg = " in '" + pn + "'";
            if (!pn.isEmpty() && 
                    !pn.equals(Module.LANGUAGE_MODULE_NAME)) {
                OccurrenceLocation ol = 
                        getOccurrenceLocation(cu, 
                                Nodes.findNode(cu, problem.getOffset()));
                if (ol!=IMPORT) {
                    List<InsertEdit> ies = 
                            importEdits(cu, Collections.singleton(dec), 
                                    null, null, doc);
                    for (InsertEdit ie: ies) {
                        change.addEdit(ie);
                    }
                }
            }
        }
        change.addEdit(new ReplaceEdit(problem.getOffset(), 
                brokenName.length(), dwp.getName())); //Note: don't use problem.getLength() because it's wrong from the problem list
        proposals.add(new ChangeReferenceProposal(problem, 
                dwp.getName(), pkg, change));
    }

    protected static boolean isInPackage(Tree.CompilationUnit cu,
            Declaration dec) {
        return !dec.getUnit().getPackage()
                .equals(cu.getUnit().getPackage());
    }

    @Override
    public void apply(IDocument document, char trigger, int offset) {
        apply(document);
    }

    @Override
    public boolean isValidFor(IDocument document, int offset) {
        return true;
    }

    @Override
    public char[] getTriggerCharacters() {
        return "r".toCharArray();
    }

    @Override
    public int getContextInformationPosition() {
        return -1;
    }

    static void addChangeReferenceProposals(Tree.CompilationUnit cu, 
            Node node, ProblemLocation problem, 
            Collection<ICompletionProposal> proposals, IFile file) {
        String brokenName = Nodes.getIdentifyingNode(node).getText();
        if (brokenName.isEmpty()) return;
        for (DeclarationWithProximity dwp: 
            getProposals(node, node.getScope(), cu).values()) {
            int dist = getLevenshteinDistance(brokenName, dwp.getName()); //+dwp.getProximity()/3;
            //TODO: would it be better to just sort by dist, and
            //      then select the 3 closest possibilities?
            if (dist<=brokenName.length()/3+1) {
                addChangeReferenceProposal(problem, proposals, file, 
                        brokenName, dwp, dist, cu);
            }
        }
    }

}