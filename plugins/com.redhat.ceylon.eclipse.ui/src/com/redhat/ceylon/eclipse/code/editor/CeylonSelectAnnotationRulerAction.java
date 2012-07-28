package com.redhat.ceylon.eclipse.code.editor;

import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.gotoNode;
import static com.redhat.ceylon.eclipse.code.resolve.CeylonReferenceResolver.getCompilationUnit;
import static com.redhat.ceylon.eclipse.code.resolve.CeylonReferenceResolver.getReferencedNode;
import static com.redhat.ceylon.eclipse.code.resolve.JavaHyperlinkDetector.getJavaElement;
import static org.eclipse.jdt.internal.ui.javaeditor.EditorUtility.openInEditor;
import static org.eclipse.jdt.internal.ui.javaeditor.EditorUtility.revealInEditor;

import java.util.Iterator;
import java.util.ResourceBundle;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IVerticalRulerInfo;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.SelectMarkerRulerAction;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;

public class CeylonSelectAnnotationRulerAction extends SelectMarkerRulerAction {
    
    IVerticalRulerInfo ruler;
    CeylonEditor editor;
    
    public CeylonSelectAnnotationRulerAction(ResourceBundle bundle, String prefix,
            ITextEditor editor, IVerticalRulerInfo ruler) {
        super(bundle, prefix, editor, ruler);
        this.ruler = ruler;
        this.editor = (CeylonEditor) editor;
    }
    
    @Override
    public void update() {
        //don't let super.update() be called!
    }
    
    @Override
    public void run() {
        //super.run();
        int line = ruler.getLineOfLastMouseButtonActivity()+1;
        IAnnotationModel model= editor.getDocumentProvider()
                .getAnnotationModel(editor.getEditorInput());
        for (Iterator<Annotation> iter = model.getAnnotationIterator(); 
                iter.hasNext();) {
            Annotation ann = iter.next();
            if (ann instanceof RefinementAnnotation) {
                RefinementAnnotation ra = (RefinementAnnotation) ann;
                if (ra.getLine()==line) {
                    Declaration dec = ra.getDeclaration();
                    CeylonParseController cpc = editor.getParseController();
                    CompilationUnit cu = getCompilationUnit(cpc, dec);
                    if (cu!=null) {
                    	gotoNode(getReferencedNode(dec, cu), 
                    			cpc.getProject(),
                    			cpc.getTypeChecker());
                    }
                    else {
                        gotoJavaNode(dec, cpc);
                    }
                }
            }
        }
    }

	private void gotoJavaNode(Declaration dec, CeylonParseController cpc) {
		IJavaProject jp = JavaCore.create(cpc.getProject());
		if (jp!=null) {
		    try {
		        IJavaElement element = getJavaElement(dec, jp, null);
		        if (element!=null) {
		            IEditorPart part = openInEditor(element, true);
		            if (part!=null) {
		                revealInEditor(part, element);
		            }
		        }
		    }
		    catch (Exception e) {
		        e.printStackTrace();
		    }
		}
	}

}
