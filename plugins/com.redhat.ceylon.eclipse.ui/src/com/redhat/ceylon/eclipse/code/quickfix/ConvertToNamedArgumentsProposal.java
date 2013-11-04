package com.redhat.ceylon.eclipse.code.quickfix;

import static com.redhat.ceylon.eclipse.code.quickfix.Util.getSelectedNode;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;

import java.util.Collection;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.code.refactor.AbstractRefactoring;

class ConvertToNamedArgumentsProposal implements ICompletionProposal {

    private CeylonEditor editor;
    
    public ConvertToNamedArgumentsProposal(CeylonEditor editor) {
       this.editor = editor;
    }
    
    @Override
    public Point getSelection(IDocument doc) {
    	return null;
    }

    @Override
    public Image getImage() {
    	return CeylonLabelProvider.CORRECTION;
    }

    @Override
    public String getDisplayString() {
    	return "Convert to named argument list";
    }

    @Override
    public IContextInformation getContextInformation() {
    	return null;
    }

    @Override
    public String getAdditionalProposalInfo() {
    	return null;
    }

    @Override
    public void apply(IDocument doc) {
        try {
            convertToNamedArguments(editor);
        } 
        catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
    
    public static void add(Collection<ICompletionProposal> proposals, CeylonEditor editor) {
        if (canConvert(editor)) {
            proposals.add(new ConvertToNamedArgumentsProposal(editor));
        }
    }

    public static void convertToNamedArguments(CeylonEditor editor)
            throws ExecutionException {
        CeylonParseController cpc = editor.getParseController();
        Tree.CompilationUnit cu = cpc.getRootNode();
        if (cu==null) return;
        Node node = getSelectedNode(editor);
        Tree.PositionalArgumentList pal = getArgumentList(node);
        if (pal!=null) {
            IDocument document = editor.getDocumentProvider()
                    .getDocument(editor.getEditorInput());
            final TextChange tc = new DocumentChange("Convert To Named Arguments", document);
            tc.setEdit(new MultiTextEdit());
            Integer start = pal.getStartIndex();
            int length = pal.getStopIndex()-start+1;
            StringBuilder result = new StringBuilder().append(" {");
            boolean sequencedArgs = false;
            for (Tree.PositionalArgument arg: pal.getPositionalArguments()) {
                Parameter param = arg.getParameter();
                if (param==null) return;
                if (param.isSequenced() && (arg instanceof Tree.ListedArgument)) {
                    if (sequencedArgs) result.append(",");
                    sequencedArgs=true;
                    result.append(" " + AbstractRefactoring.toString(arg, cpc.getTokens()));
                }
                else {
                    result.append(" " + param.getName() + "=" + 
                            AbstractRefactoring.toString(arg, cpc.getTokens()) + ";");
                }
            }
            result.append(" }");
            tc.addEdit(new ReplaceEdit(start, length, result.toString()));
            tc.initializeValidationData(null);
            try {
                getWorkspace().run(new PerformChangeOperation(tc), 
                        new NullProgressMonitor());
            }
            catch (CoreException ce) {
                throw new ExecutionException("Error cleaning imports", ce);
            }
        }
    }

    public static boolean canConvert(CeylonEditor editor) {
        Node node = getSelectedNode(editor);
        Tree.PositionalArgumentList pal = getArgumentList(node);
        if (pal==null) {
            return false;
        }
        else {
            //if it is an indirect invocations, or an 
            //invocation of an overloaded Java method
            //or constructor, we can't call it using
            //named arguments!
            for (Tree.PositionalArgument arg: pal.getPositionalArguments()) {
                Parameter param = arg.getParameter();
                if (param==null) return false;
            }
            return true;
        }
    }

    private static Tree.PositionalArgumentList getArgumentList(Node node) {
        if (node instanceof Tree.PositionalArgumentList) {
            return (Tree.PositionalArgumentList) node;
        }
        else if (node instanceof Tree.InvocationExpression) {
            return ((Tree.InvocationExpression) node).getPositionalArgumentList();
        }
        else {
            return null;
        }
    }
    
}