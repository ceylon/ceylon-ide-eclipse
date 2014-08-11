package com.redhat.ceylon.eclipse.code.correct;

import java.util.Collection;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.ReplaceEdit;

import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.compiler.typechecker.util.ProducedTypeNamePrinter;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;

public class ExpandTypeProposal extends CorrectionProposal {

    private static final class FindTypeVisitor extends Visitor {
        private final IRegion region;
        Tree.Type result;

        private FindTypeVisitor(IRegion region) {
            this.region = region;
        }

        @Override
        public void visit(Tree.Type that) {
            // don't visit subtypes - we always want the complete type
            if (region.getOffset()>=that.getStartIndex() &&
                    region.getOffset()+region.getLength()<=that.getStopIndex()+1) {
                result = that;
            }
            // if the complete type doesn't match the region, the subtypes won't either
        }
    }

    public ExpandTypeProposal(String name, Change change, Region selection) {
        super(name, change, selection);
    }
    
    public static void addExpandTypeProposal(CeylonEditor editor, 
            final Tree.CompilationUnit rootNode, 
            IFile file, IDocument doc,
            Collection<ICompletionProposal> proposals) {
        FindTypeVisitor ftv = new FindTypeVisitor(editor.getSelection());
        ftv.visit(rootNode);
        Tree.Type result = ftv.result;
        if (result!=null) {
            ProducedType type = result.getTypeModel();
            int start = result.getStartIndex();
            int len = result.getStopIndex()-start+1;
            String text;
            try {
                text = doc.get(start, len);
            }
            catch (Exception e) {
                e.printStackTrace();
                return;
            }
            String unabbreviated = 
                    new ProducedTypeNamePrinter(false)
                        .getProducedTypeName(type, rootNode.getUnit());
            if (!unabbreviated.equals(text)) {
                TextChange change = new TextFileChange("Expand Type", file);
                change.setEdit(new ReplaceEdit(start, len, unabbreviated));
                proposals.add(new ExpandTypeProposal("Expand type abbreviation", 
                        change, new Region(start, unabbreviated.length())));
            }
        }
    }

}
