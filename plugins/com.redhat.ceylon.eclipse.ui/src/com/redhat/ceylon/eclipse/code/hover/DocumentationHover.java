package com.redhat.ceylon.eclipse.code.hover;

/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Genady Beryozkin <eclipse@genady.org> - [hovering] tooltip for constant string does not show constant value - https://bugs.eclipse.org/bugs/show_bug.cgi?id=85382
 *******************************************************************************/

import static com.redhat.ceylon.eclipse.code.browser.BrowserInformationControl.isAvailable;
import static com.redhat.ceylon.eclipse.code.complete.CodeCompletions.appendParametersDescription;
import static com.redhat.ceylon.eclipse.code.complete.CodeCompletions.getDescriptionFor;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.getDefaultValueDescription;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.getInitalValueDescription;
import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.getSelectionFromThread;
import static com.redhat.ceylon.eclipse.code.editor.Navigation.gotoDeclaration;
import static com.redhat.ceylon.eclipse.code.html.HTMLPrinter.addPageEpilog;
import static com.redhat.ceylon.eclipse.code.html.HTMLPrinter.convertToHTMLContent;
import static com.redhat.ceylon.eclipse.code.html.HTMLPrinter.insertPageProlog;
import static com.redhat.ceylon.eclipse.code.html.HTMLPrinter.toHex;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.getLabel;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.getModuleLabel;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.getPackageLabel;
import static com.redhat.ceylon.eclipse.code.resolve.JavaHyperlinkDetector.getJavaElement;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getModelLoader;
import static com.redhat.ceylon.eclipse.util.Highlights.CHARS;
import static com.redhat.ceylon.eclipse.util.Highlights.NUMBERS;
import static com.redhat.ceylon.eclipse.util.Highlights.STRINGS;
import static com.redhat.ceylon.eclipse.util.Highlights.getCurrentThemeColor;
import static com.redhat.ceylon.eclipse.util.Nodes.getReferencedNode;
import static java.lang.Character.codePointCount;
import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;
import static org.eclipse.jdt.internal.ui.JavaPluginImages.setLocalImageDescriptors;
import static org.eclipse.jdt.ui.PreferenceConstants.APPEARANCE_JAVADOC_FONT;
import static org.eclipse.ui.ISharedImages.IMG_TOOL_BACK;
import static org.eclipse.ui.ISharedImages.IMG_TOOL_BACK_DISABLED;
import static org.eclipse.ui.ISharedImages.IMG_TOOL_FORWARD;
import static org.eclipse.ui.ISharedImages.IMG_TOOL_FORWARD_DISABLED;
import static org.eclipse.ui.PlatformUI.getWorkbench;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.text.javadoc.JavadocContentAccess2;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IInputChangedListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import com.github.rjeschke.txtmark.Configuration;
import com.github.rjeschke.txtmark.Configuration.Builder;
import com.github.rjeschke.txtmark.Processor;
import com.redhat.ceylon.cmr.api.JDKUtils;
import com.redhat.ceylon.cmr.api.ModuleSearchResult.ModuleDetails;
import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.NothingType;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.ProducedReference;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.Referenceable;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.TypeAlias;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.UnionType;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.model.UnknownType;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AnonymousAnnotation;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.eclipse.code.browser.BrowserInformationControl;
import com.redhat.ceylon.eclipse.code.browser.BrowserInput;
import com.redhat.ceylon.eclipse.code.correct.ExtractFunctionProposal;
import com.redhat.ceylon.eclipse.code.correct.ExtractValueProposal;
import com.redhat.ceylon.eclipse.code.correct.SpecifyTypeProposal;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.html.HTML;
import com.redhat.ceylon.eclipse.code.html.HTMLPrinter;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.code.search.FindAssignmentsAction;
import com.redhat.ceylon.eclipse.code.search.FindReferencesAction;
import com.redhat.ceylon.eclipse.code.search.FindRefinementsAction;
import com.redhat.ceylon.eclipse.code.search.FindSubtypesAction;
import com.redhat.ceylon.eclipse.core.model.CeylonUnit;
import com.redhat.ceylon.eclipse.core.model.JDTModelLoader;
import com.redhat.ceylon.eclipse.util.Nodes;


public class DocumentationHover 
        implements ITextHover, ITextHoverExtension, ITextHoverExtension2 {
    
    private CeylonEditor editor;
    
    public DocumentationHover(CeylonEditor editor) {
        this.editor = editor;
    }

    public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
        IDocument document = textViewer.getDocument();
        int start= -2;
        int end= -1;
        
        try {
            int pos= offset;
            char c;
        
            while (pos >= 0) {
                c= document.getChar(pos);
                if (!Character.isJavaIdentifierPart(c)) {
                    break;
                }
                --pos;
            }
            start= pos;
        
            pos= offset;
            int length= document.getLength();
        
            while (pos < length) {
                c= document.getChar(pos);
                if (!Character.isJavaIdentifierPart(c)) {
                    break;
        
                }
                ++pos;
            }
            end= pos;
        
        } catch (BadLocationException x) {
        }
        
        if (start >= -1 && end > -1) {
            if (start == offset && end == offset)
                return new Region(offset, 0);
            else if (start == offset)
                return new Region(start, end - start);
            else
                return new Region(start + 1, end - start - 1);
        }
        
        return null;
    }
    
    final class CeylonLocationListener implements LocationListener {
        private final BrowserInformationControl control;
        
        CeylonLocationListener(BrowserInformationControl control) {
            this.control = control;
        }
        
        @Override
        public void changing(LocationEvent event) {
            String location = event.location;
            
            //necessary for windows environment (fix for blank page)
            //somehow related to this: https://bugs.eclipse.org/bugs/show_bug.cgi?id=129236
            if (!"about:blank".equals(location)) {
                event.doit= false;
            }
            
            handleLink(location);
            
            /*else if (location.startsWith("javadoc:")) {
                final DocBrowserInformationControlInput input = (DocBrowserInformationControlInput) control.getInput();
                int beginIndex = input.getHtml().indexOf("javadoc:")+8;
                final String handle = input.getHtml().substring(beginIndex, input.getHtml().indexOf("\"",beginIndex));
                new Job("Fetching Javadoc") {
                    @Override
                    protected IStatus run(IProgressMonitor monitor) {
                        final IJavaElement elem = JavaCore.create(handle);
                        try {
                            final String javadoc = JavadocContentAccess2.getHTMLContent((IMember) elem, true);
                            if (javadoc!=null) {
                                PlatformUI.getWorkbench().getProgressService()
                                        .runInUI(editor.getSite().getWorkbenchWindow(), new IRunnableWithProgress() {
                                    @Override
                                    public void run(IProgressMonitor monitor) 
                                            throws InvocationTargetException, InterruptedException {
                                        StringBuilder sb = new StringBuilder();
                                        HTMLPrinter.insertPageProlog(sb, 0, getStyleSheet());
                                        appendJavadoc(elem, javadoc, sb);
                                        HTMLPrinter.addPageEpilog(sb);
                                        control.setInput(new DocBrowserInformationControlInput(input, null, sb.toString(), 0));
                                    }
                                }, null);
                            }
                        } 
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                        return Status.OK_STATUS;
                    }
                }.schedule();
            }*/
        }
        
        private void handleLink(String location) {
            if (location.startsWith("dec:")) {
                Referenceable target = getLinkedModel(editor, location);
                if (target!=null) {
                    close(control); //FIXME: should have protocol to hide, rather than dispose
                    gotoDeclaration(target, editor);
                }
            }
            else if (location.startsWith("doc:")) {
                Referenceable target = getLinkedModel(editor, location);
                if (target!=null) {
                    control.setInput(getHoverInfo(target, control.getInput(), editor, null));
                }
            }
            else if (location.startsWith("ref:")) {
                Referenceable target = getLinkedModel(editor, location);
                close(control);
                new FindReferencesAction(editor, (Declaration) target).run();
            }
            else if (location.startsWith("sub:")) {
                Referenceable target = getLinkedModel(editor, location);
                close(control);
                new FindSubtypesAction(editor, (Declaration) target).run();
            }
            else if (location.startsWith("act:")) {
                Referenceable target = getLinkedModel(editor, location);
                close(control);
                new FindRefinementsAction(editor, (Declaration) target).run();
            }
            else if (location.startsWith("ass:")) {
                Referenceable target = getLinkedModel(editor, location);
                close(control);
                new FindAssignmentsAction(editor, (Declaration) target).run();
            }
            else if (location.startsWith("stp:")) {
                close(control);
                CompilationUnit rn = editor.getParseController().getRootNode();
                Node node = Nodes.findNode(rn, Integer.parseInt(location.substring(4)));
                SpecifyTypeProposal.createProposal(rn, node, editor)
                        .apply(editor.getParseController().getDocument());
            }
            else if (location.startsWith("exv:")) {
                close(control);
                new ExtractValueProposal(editor).apply(editor.getParseController().getDocument());
            }
            else if (location.startsWith("exf:")) {
                close(control);
                new ExtractFunctionProposal(editor).apply(editor.getParseController().getDocument());
            }
        }
        
        @Override
        public void changed(LocationEvent event) {}
    }
    
    /**
     * Action to go back to the previous input in the hover control.
     */
    static final class BackAction extends Action {
        private final BrowserInformationControl fInfoControl;

        public BackAction(BrowserInformationControl infoControl) {
            fInfoControl= infoControl;
            setText("Back");
            ISharedImages images= getWorkbench().getSharedImages();
            setImageDescriptor(images.getImageDescriptor(IMG_TOOL_BACK));
            setDisabledImageDescriptor(images.getImageDescriptor(IMG_TOOL_BACK_DISABLED));

            update();
        }

        @Override
        public void run() {
            BrowserInput previous= (BrowserInput) fInfoControl.getInput().getPrevious();
            if (previous != null) {
                fInfoControl.setInput(previous);
            }
        }

        public void update() {
            BrowserInput current= fInfoControl.getInput();
            if (current != null && current.getPrevious() != null) {
                BrowserInput previous= current.getPrevious();
                setToolTipText("Back to " + previous.getInputName());
                setEnabled(true);
            } else {
                setToolTipText("Back");
                setEnabled(false);
            }
        }
    }

    /**
     * Action to go forward to the next input in the hover control.
     */
    static final class ForwardAction extends Action {
        private final BrowserInformationControl fInfoControl;

        public ForwardAction(BrowserInformationControl infoControl) {
            fInfoControl= infoControl;
            setText("Forward");
            ISharedImages images= getWorkbench().getSharedImages();
            setImageDescriptor(images.getImageDescriptor(IMG_TOOL_FORWARD));
            setDisabledImageDescriptor(images.getImageDescriptor(IMG_TOOL_FORWARD_DISABLED));

            update();
        }

        @Override
        public void run() {
            BrowserInput next= (BrowserInput) fInfoControl.getInput().getNext();
            if (next != null) {
                fInfoControl.setInput(next);
            }
        }

        public void update() {
            BrowserInput current= fInfoControl.getInput();
            if (current != null && current.getNext() != null) {
                setToolTipText("Forward to " + current.getNext().getInputName());
                setEnabled(true);
            } else {
                setToolTipText("Forward");
                setEnabled(false);
            }
        }
    }
    
    /**
     * Action that shows the current hover contents in the Javadoc view.
     */
    /*private static final class ShowInDocViewAction extends Action {
        private final BrowserInformationControl fInfoControl;

        public ShowInJavadocViewAction(BrowserInformationControl infoControl) {
            fInfoControl= infoControl;
            setText("Show in Ceylondoc View");
            setImageDescriptor(JavaPluginImages.DESC_OBJS_JAVADOCTAG); //TODO: better image
        }

        @Override
        public void run() {
            DocBrowserInformationControlInput infoInput= (DocBrowserInformationControlInput) fInfoControl.getInput(); //TODO: check cast
            fInfoControl.notifyDelayedInputChange(null);
            fInfoControl.dispose(); //FIXME: should have protocol to hide, rather than dispose
            try {
                JavadocView view= (JavadocView) JavaPlugin.getActivePage().showView(JavaUI.ID_JAVADOC_VIEW);
                view.setInput(infoInput);
            } catch (PartInitException e) {
                JavaPlugin.log(e);
            }
        }
    }*/
    
    /**
     * Action that opens the current hover input element.
     */
    final class OpenDeclarationAction extends Action {
        private final BrowserInformationControl fInfoControl;
        public OpenDeclarationAction(BrowserInformationControl infoControl) {
            fInfoControl = infoControl;
            setText("Open Declaration");
            setLocalImageDescriptors(this, "goto_input.gif");
        }
        @Override
        public void run() {
            close(fInfoControl); //FIXME: should have protocol to hide, rather than dispose
            CeylonBrowserInput input = (CeylonBrowserInput) fInfoControl.getInput();
            gotoDeclaration(getLinkedModel(editor, input.getAddress()), editor);
        }
    }

    private static void close(BrowserInformationControl control) {
        control.notifyDelayedInputChange(null);
        control.dispose();
    }
    
    /**
     * The hover control creator.
     */
    private IInformationControlCreator fHoverControlCreator;
    /**
     * The presentation control creator.
     */
    private IInformationControlCreator fPresenterControlCreator;

    private  IInformationControlCreator getInformationPresenterControlCreator() {
        if (fPresenterControlCreator == null)
            fPresenterControlCreator= new PresenterControlCreator(this);
        return fPresenterControlCreator;
    }

    @Override
    public IInformationControlCreator getHoverControlCreator() {
        return getHoverControlCreator("F2 for focus");
    }

    public IInformationControlCreator getHoverControlCreator(
            String statusLineMessage) {
        if (fHoverControlCreator == null) {
            fHoverControlCreator= new HoverControlCreator(this, 
                    getInformationPresenterControlCreator(), 
                    statusLineMessage);
        }
        return fHoverControlCreator;
    }

    void addLinkListener(final BrowserInformationControl control) {
        control.addLocationListener(new CeylonLocationListener(control));
    }

    public static Referenceable getLinkedModel(CeylonEditor editor, String location) {
        if (location==null) {
            return null;
        }
        TypeChecker tc = editor.getParseController().getTypeChecker();
        String[] bits = location.split(":");
        JDTModelLoader modelLoader = getModelLoader(tc);
        String moduleName = bits[1];
        Module module = modelLoader.getLoadedModule(moduleName);
        if (module==null || bits.length==2) {
            return module;
        }
        Referenceable target = module.getPackage(bits[2]);
        for (int i=3; i<bits.length; i++) {
            Scope scope;
            if (target instanceof Scope) {
                scope = (Scope) target;
            }
            else if (target instanceof TypedDeclaration) {
                scope = ((TypedDeclaration) target).getType().getDeclaration();
            }
            else {
                return null;
            }
            target = scope.getDirectMember(bits[i], null, false);
            //TODO: nasty workaround for bug in model loader 
            //      where unshared parameters are not getting 
            //      persisted as members. Delete:
            if (target==null && (scope instanceof Functional)) {
                for (ParameterList pl: ((Functional)scope).getParameterLists()) {
                    for (Parameter p: pl.getParameters()) {
                        if (p!=null && p.getName().equals(bits[i])) {
                            target = p.getModel();
                        }
                    }
                }
            }
        }
        return target;
    }
    
    public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
        CeylonBrowserInput info = (CeylonBrowserInput) getHoverInfo2(textViewer, hoverRegion);
        return info!=null ? info.getHtml() : null;
    }

    @Override
    public CeylonBrowserInput getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
        return internalGetHoverInfo(editor, hoverRegion);
    }

    static CeylonBrowserInput internalGetHoverInfo(final CeylonEditor editor, 
            IRegion hoverRegion) {
        if (editor==null || editor.getSelectionProvider()==null) {
            return null;
        }
        CeylonBrowserInput result = getExpressionHover(editor, hoverRegion);
        if (result==null) {
            result = getDeclarationHover(editor, hoverRegion);
        }
        return result;
    }

    static CeylonBrowserInput getExpressionHover(CeylonEditor editor, 
            IRegion hoverRegion) {
        CeylonParseController parseController = editor.getParseController();
        if (parseController==null) {
            return null;
        }
        Tree.CompilationUnit rn = parseController.getRootNode();
        if (rn!=null) {
            int hoffset = hoverRegion.getOffset();
            ITextSelection selection = getSelectionFromThread(editor);
            if (selection!=null && 
                selection.getOffset()<=hoffset &&
                selection.getOffset()+selection.getLength()>=hoffset) {
                Node node = Nodes.findNode(rn, selection.getOffset(),
                        selection.getOffset()+selection.getLength()-1);
                
                if (node instanceof Tree.Expression) {
                    node = ((Tree.Expression) node).getTerm();
                }
                if (node instanceof Tree.Term) {
                    return getTermTypeHoverInfo(node, selection.getText(), 
                            editor.getCeylonSourceViewer().getDocument(),
                            editor.getParseController().getProject());
                }
                else {
                    return null;
                }
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }

    static CeylonBrowserInput getDeclarationHover(CeylonEditor editor,
            IRegion hoverRegion) {
        CeylonParseController parseController = editor.getParseController();
        if (parseController==null) {
            return null;
        }
        Tree.CompilationUnit rootNode = parseController.getRootNode();
        if (rootNode!=null) {
            Node node = Nodes.findNode(rootNode, hoverRegion.getOffset());
            if (node instanceof Tree.ImportPath) {
                Referenceable r = ((Tree.ImportPath) node).getModel();
                if (r!=null) {
                    return getHoverInfo(r, null, editor, node);
                }
                else {
                    return null;
                }
            }
            else if (node instanceof Tree.LocalModifier) {
                return getInferredTypeHoverInfo(node,
                        editor.getParseController().getProject());
            }
            else if (node instanceof Tree.Literal) {
                return getTermTypeHoverInfo(node, null, 
                        editor.getCeylonSourceViewer().getDocument(),
                        editor.getParseController().getProject());
            }
            else {
                return getHoverInfo(Nodes.getReferencedDeclaration(node), 
                        null, editor, node);
            }
        }
        else {
            return null;
        }
    }

    private static CeylonBrowserInput getInferredTypeHoverInfo(Node node, IProject project) {
        ProducedType t = ((Tree.LocalModifier) node).getTypeModel();
        if (t==null) return null;
        StringBuilder buffer = new StringBuilder();
        HTMLPrinter.insertPageProlog(buffer, 0, HTML.getStyleSheet());
        HTML.addImageAndLabel(buffer, null, HTML.fileUrl("types.gif").toExternalForm(), 
                16, 16, "<tt>" + HTML.highlightLine(t.getProducedTypeName()) + "</tt>", 
                20, 4);
        buffer.append("<hr/>");
        if (!t.containsUnknowns()) {
            buffer.append("One quick assist available:<br/>");
            HTML.addImageAndLabel(buffer, null, HTML.fileUrl("correction_change.gif").toExternalForm(), 
                    16, 16, "<a href=\"stp:" + node.getStartIndex() + "\">Specify explicit type</a>", 
                    20, 4);
        }
        //buffer.append(getDocumentationFor(editor.getParseController(), t.getDeclaration()));
        HTMLPrinter.addPageEpilog(buffer);
        return new CeylonBrowserInput(null, null, buffer.toString());
    }
    
    private static CeylonBrowserInput getTermTypeHoverInfo(Node node, String selectedText, 
            IDocument doc, IProject project) {
        ProducedType t = ((Tree.Term) node).getTypeModel();
        if (t==null) return null;
//        String expr = "";
//        try {
//            expr = doc.get(node.getStartIndex(), node.getStopIndex()-node.getStartIndex()+1);
//        } 
//        catch (BadLocationException e) {
//            e.printStackTrace();
//        }
        StringBuilder buffer = new StringBuilder();
        HTMLPrinter.insertPageProlog(buffer, 0, HTML.getStyleSheet());
        String desc = node instanceof Tree.Literal ? "literal" : "expression";
        HTML.addImageAndLabel(buffer, null, HTML.fileUrl("types.gif").toExternalForm(), 
                16, 16, "<tt>" + HTML.highlightLine(t.getProducedTypeName()) + 
                "</tt> "+desc+"", 
                20, 4);
        if (node instanceof Tree.StringLiteral) {
            buffer.append( "<hr/>")
                .append("<code style='color:")
                .append(toHex(getCurrentThemeColor(STRINGS)))
                .append("'><pre>")
                .append('\"')
                .append(convertToHTMLContent(node.getText()))
                .append('\"')
                .append("</pre></code>");
            // If a single char selection, then append info on that character too
            if (selectedText != null
                    && codePointCount(selectedText, 0, selectedText.length()) == 1) {
                appendCharacterHoverInfo(buffer, selectedText);
            }
        }
        else if (node instanceof Tree.CharLiteral) {
            String character = node.getText();
            if (character.length()>2) {
                appendCharacterHoverInfo(buffer, 
                        character.substring(1, character.length()-1));
            }
        }
        else if (node instanceof Tree.NaturalLiteral) {
            buffer.append( "<hr/>")
                .append("<code style='color:")
                .append(toHex(getCurrentThemeColor(NUMBERS)))
                .append("'>");
            String text = node.getText().replace("_", "");
            switch (text.charAt(0)) {
            case '#':
                buffer.append(parseInt(text.substring(1),16));
                break;
            case '$':
                buffer.append(parseInt(text.substring(1),2));
                break;
            default:
                buffer.append(parseInt(text));
            }
            buffer.append("</code>");
        }
        else if (node instanceof Tree.FloatLiteral) {
            buffer.append( "<hr/>")
                .append("<code style='color:")
                .append(toHex(getCurrentThemeColor(NUMBERS)))
                .append("'>")
                .append(parseFloat(node.getText().replace("_", "")))
                .append("</code>");
        }
        if (selectedText!=null) {
            buffer.append("<hr/>").append("Two quick assists available:<br/>");
            HTML.addImageAndLabel(buffer, null, HTML.fileUrl("change.png").toExternalForm(), 
                    16, 16, "<a href=\"exv:\">Extract value</a>", 
                    20, 4);
            HTML.addImageAndLabel(buffer, null, HTML.fileUrl("change.png").toExternalForm(), 
                    16, 16, "<a href=\"exf:\">Extract function</a>", 
                    20, 4);
            buffer.append("<br/>");
        }
        HTMLPrinter.addPageEpilog(buffer);
        return new CeylonBrowserInput(null, null, buffer.toString());
    }

    private static void appendCharacterHoverInfo(StringBuilder buffer, String character) {
        buffer.append( "<hr/>")
            .append("<code style='color:")
            .append(toHex(getCurrentThemeColor(CHARS)))
            .append("'>")
            .append('\'')
            .append(convertToHTMLContent(character))
            .append('\'')
            .append("</code>");
        int codepoint = Character.codePointAt(character, 0);
        String name = Character.getName(codepoint);
        buffer.append("<hr/>Unicode Name: <code>").append(name).append("</code>");
        String hex = Integer.toHexString(codepoint).toUpperCase();
        while (hex.length() < 4) {
            hex = "0" + hex;
        }
        buffer.append("<br/>Codepoint: <code>").append("U+").append(hex).append("</code>");
        buffer.append("<br/>General Category: <code>").append(getCodepointGeneralCategoryName(codepoint)).append("</code>");
        Character.UnicodeScript script = Character.UnicodeScript.of(codepoint);
        buffer.append("<br/>Script: <code>").append(script.name()).append("</code>");
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codepoint);
        buffer.append("<br/>Block: <code>").append(block).append("</code><br/>");
    }

    private static String getCodepointGeneralCategoryName(int codepoint) {
        String gc;
        switch (Character.getType(codepoint)) {
        case Character.COMBINING_SPACING_MARK:
            gc = "Mark, combining spacing"; break;
        case Character.CONNECTOR_PUNCTUATION:
            gc = "Punctuation, connector"; break;
        case Character.CONTROL:
            gc = "Other, control"; break;
        case Character.CURRENCY_SYMBOL:
            gc = "Symbol, currency"; break;
        case Character.DASH_PUNCTUATION:
            gc = "Punctuation, dash"; break;
        case Character.DECIMAL_DIGIT_NUMBER:
            gc = "Number, decimal digit"; break;
        case Character.ENCLOSING_MARK:
            gc = "Mark, enclosing"; break;
        case Character.END_PUNCTUATION:
            gc = "Punctuation, close"; break;
        case Character.FINAL_QUOTE_PUNCTUATION:
            gc = "Punctuation, final quote"; break;
        case Character.FORMAT:
            gc = "Other, format"; break;
        case Character.INITIAL_QUOTE_PUNCTUATION:
            gc = "Punctuation, initial quote"; break;
        case Character.LETTER_NUMBER:
            gc = "Number, letter"; break;
        case Character.LINE_SEPARATOR:
            gc = "Separator, line"; break;
        case Character.LOWERCASE_LETTER:
            gc = "Letter, lowercase"; break;
        case Character.MATH_SYMBOL:
            gc = "Symbol, math"; break;
        case Character.MODIFIER_LETTER:
            gc = "Letter, modifier"; break;
        case Character.MODIFIER_SYMBOL:
            gc = "Symbol, modifier"; break;
        case Character.NON_SPACING_MARK:
            gc = "Mark, nonspacing"; break;
        case Character.OTHER_LETTER:
            gc = "Letter, other"; break;
        case Character.OTHER_NUMBER:
            gc = "Number, other"; break;
        case Character.OTHER_PUNCTUATION:
            gc = "Punctuation, other"; break;
        case Character.OTHER_SYMBOL:
            gc = "Symbol, other"; break;
        case Character.PARAGRAPH_SEPARATOR:
            gc = "Separator, paragraph"; break;
        case Character.PRIVATE_USE:
            gc = "Other, private use"; break;
        case Character.SPACE_SEPARATOR:
            gc = "Separator, space"; break;
        case Character.START_PUNCTUATION:
            gc = "Punctuation, open"; break;
        case Character.SURROGATE:
            gc = "Other, surrogate"; break;
        case Character.TITLECASE_LETTER:
            gc = "Letter, titlecase"; break;
        case Character.UNASSIGNED:
            gc = "Other, unassigned"; break;
        case Character.UPPERCASE_LETTER:
            gc = "Letter, uppercase"; break;
        default:
            gc = "&lt;Unknown&gt;";
        }
        return gc;
    }
    
    private static String getIcon(Object obj) {
        if (obj instanceof Module) {
            return "jar_l_obj.gif";
        }
        else if (obj instanceof Package) {
            return "package_obj.gif";
        }
        else if (obj instanceof Declaration) {
            Declaration dec = (Declaration) obj;
            if (dec instanceof Class) {
                return dec.isShared() ? 
                        "class_obj.gif" : 
                        "innerclass_private_obj.gif";
            }
            else if (dec instanceof Interface) {
                return dec.isShared() ? 
                        "int_obj.gif" : 
                        "innerinterface_private_obj.gif";
            }
            else if (dec instanceof TypeAlias||
                    dec instanceof NothingType) {
                return "types.gif";
            }
            else if (dec.isParameter()) {
                if (dec instanceof Method) {
                    return "methpro_obj.gif";
                }
                else {
                    return "field_protected_obj.gif";
                }
            }
            else if (dec instanceof Method) {
                return dec.isShared() ?
                        "public_co.gif" : 
                        "private_co.gif";
            }
            else if (dec instanceof MethodOrValue) {
                return dec.isShared() ?
                        "field_public_obj.gif" : 
                        "field_private_obj.gif";
            }
            else if (dec instanceof TypeParameter) {
                return "typevariable_obj.gif";
            }
        }
        return null;
    }

    /**
     * Computes the hover info.
     * @param previousInput the previous input, or <code>null</code>
     * @param node 
     * @param elements the resolved elements
     * @param editorInputElement the editor input, or <code>null</code>
     *
     * @return the HTML hover info for the given element(s) or <code>null</code> 
     *         if no information is available
     * @since 3.4
     */
    static CeylonBrowserInput getHoverInfo(Referenceable model, 
            BrowserInput previousInput, CeylonEditor editor, Node node) {
        if (model instanceof Declaration) {
            Declaration dec = (Declaration) model;
            return new CeylonBrowserInput(previousInput, dec, 
                    getDocumentationFor(editor.getParseController(), dec, node));
        }
        else if (model instanceof Package) {
            Package dec = (Package) model;
            return new CeylonBrowserInput(previousInput, dec, 
                    getDocumentationFor(editor.getParseController(), dec));
        }
        else if (model instanceof Module) {
            Module dec = (Module) model;
            return new CeylonBrowserInput(previousInput, dec, 
                    getDocumentationFor(editor.getParseController(), dec));
        }
        else {
            return null;
        }
    }

    private static void appendJavadoc(IJavaElement elem, StringBuilder sb) {
        if (elem instanceof IMember) {
            try {
                //TODO: Javadoc @ icon?
                IMember mem = (IMember) elem;
                String jd = JavadocContentAccess2.getHTMLContent(mem, true);
                if (jd!=null) {
                    sb.append("<br/>").append(jd);
                    String base = getBaseURL(mem, mem.isBinary());
                    int endHeadIdx= sb.indexOf("</head>");
                    sb.insert(endHeadIdx, "\n<base href='" + base + "'>\n");
                }
            } 
            catch (JavaModelException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getBaseURL(IJavaElement element, boolean isBinary) 
            throws JavaModelException {
        if (isBinary) {
            // Source attachment usually does not include Javadoc resources
            // => Always use the Javadoc location as base:
            URL baseURL= JavaUI.getJavadocLocation(element, false);
            if (baseURL != null) {
                if (baseURL.getProtocol().equals("jar")) {
                    // It's a JarURLConnection, which is not known to the browser widget.
                    // Let's start the help web server:
                    URL baseURL2= PlatformUI.getWorkbench().getHelpSystem().resolve(baseURL.toExternalForm(), true);
                    if (baseURL2 != null) { // can be null if org.eclipse.help.ui is not available
                        baseURL= baseURL2;
                    }
                }
                return baseURL.toExternalForm();
            }
        }
        else {
            IResource resource= element.getResource();
            if (resource != null) {
                /*
                 * Too bad: Browser widget knows nothing about EFS and custom URL handlers,
                 * so IResource#getLocationURI() does not work in all cases.
                 * We only support the local file system for now.
                 * A solution could be https://bugs.eclipse.org/bugs/show_bug.cgi?id=149022 .
                 */
                IPath location= resource.getLocation();
                if (location != null)
                    return location.toFile().toURI().toString();
            }
        }
        return null;
    }

    public static String getDocumentationFor(CeylonParseController cpc, 
            Package pack) {
        StringBuilder buffer= new StringBuilder();
        addMainPackageDescription(pack, buffer);
        Module mod = addPackageModuleInfo(pack, buffer);
        addPackageDocumentation(cpc, pack, buffer);
        addAdditionalPackageInfo(buffer, mod);
        addPackageMembers(buffer, pack);
        insertPageProlog(buffer, 0, HTML.getStyleSheet());
        addPageEpilog(buffer);
        return buffer.toString();
        
    }

    private static void addPackageMembers(StringBuilder buffer, 
            Package pack) {
        boolean first = true;
        for (Declaration dec: pack.getMembers()) {
            if (dec instanceof Class && ((Class)dec).isOverloaded()) {
                continue;
            }
            if (dec.isShared() && !dec.isAnonymous()) {
                if (first) {
                    buffer.append("<hr/>Contains:&nbsp;&nbsp;");
                    first = false;
                }
                else {
                    buffer.append(", ");
                }

                /*addImageAndLabel(buffer, null, fileUrl(getIcon(dec)).toExternalForm(), 
                    16, 16, "<tt><a " + link(dec) + ">" + 
                    dec.getName() + "</a></tt>", 20, 2);*/
                buffer.append("<tt><a ")
                    .append(HTML.link(dec))
                    .append(">")
                    .append(dec.getName())
                    .append("</a></tt>");
            }
        }
        if (!first) {
            buffer.append(".<br/>");
        }
    }

    private static void addAdditionalPackageInfo(StringBuilder buffer,
            Module mod) {
        if (mod.isJava()) {
            buffer.append("<p>This package is implemented in Java.</p>");
        }
        if (JDKUtils.isJDKModule(mod.getNameAsString())) {
            buffer.append("<p>This package forms part of the Java SDK.</p>");            
        }
    }

    private static void addMainPackageDescription(Package pack,
            StringBuilder buffer) {
        HTML.addImageAndLabel(buffer, pack, 
                HTML.fileUrl(getIcon(pack)).toExternalForm(), 
                16, 16, 
                "<tt style='font-size:102%'>" + 
                HTML.highlightLine(description(pack)) +
                "</tt>", 
                20, 4);
        buffer.append("<hr/>");
    }

    private static Module addPackageModuleInfo(Package pack,
            StringBuilder buffer) {
        Module mod = pack.getModule();
        HTML.addImageAndLabel(buffer, mod, HTML.fileUrl(getIcon(mod)).toExternalForm(), 
                16, 16, 
                "in module&nbsp;&nbsp;<tt><a " + HTML.link(mod) + ">" + 
                        getLabel(mod) +"</a></tt>", 
                20, 2);
        return mod;
    }

    private static void addPackageDocumentation(CeylonParseController cpc,
            Package pack, StringBuilder buffer) {
        String packageFileName = pack.getNameAsString().replace('.', '/') + 
                "/package.ceylon";
        PhasedUnit pu = cpc.getTypeChecker()
                .getPhasedUnitFromRelativePath(packageFileName);
        if (pu!=null) {
            List<Tree.PackageDescriptor> packageDescriptors = 
                    pu.getCompilationUnit().getPackageDescriptors();
            if (!packageDescriptors.isEmpty()) {
                Tree.PackageDescriptor refnode = packageDescriptors.get(0);
                if (refnode!=null) {
                    appendDocAnnotationContent(refnode.getAnnotationList(), buffer, pack);
                    appendThrowAnnotationContent(refnode.getAnnotationList(), buffer, pack);
                    appendSeeAnnotationContent(refnode.getAnnotationList(), buffer);
                }
            }
        }
    }

    private static String description(Package pack) {
        return "package " + getLabel(pack);
    }
    
    public static String getDocumentationFor(ModuleDetails mod, String version) {
        return getDocumentationForModule(mod.getName(), version, mod.getDoc());
    }
    
    public static String getDocumentationForModule(String name, 
            String version, String doc) {
        StringBuilder buffer= new StringBuilder();
        
        HTML.addImageAndLabel(buffer, null, 
                HTML.fileUrl("jar_l_obj.gif").toExternalForm(), 
                16, 16, 
                "<tt style='font-size:102%'>" + 
                HTML.highlightLine(description(name, version)) + 
                "</tt></b>",
                20, 4);
        buffer.append("<hr/>");
        
        if (doc!=null) {
            buffer.append(markdown(doc, null, null));
        }
                
        insertPageProlog(buffer, 0, HTML.getStyleSheet());
        addPageEpilog(buffer);
        return buffer.toString();
        
    }

    private static String description(String name, String version) {
        return "module " + name + " \"" + version + "\"";
    }

    private static String getDocumentationFor(CeylonParseController cpc, 
            Module mod) {
        StringBuilder buffer = new StringBuilder();
        addMainModuleDescription(mod, buffer);
        addAdditionalModuleInfo(buffer, mod);
        addModuleDocumentation(cpc, mod, buffer);
        addModuleMembers(buffer, mod);
        insertPageProlog(buffer, 0, HTML.getStyleSheet());
        addPageEpilog(buffer);
        return buffer.toString();
    }

    private static void addAdditionalModuleInfo(StringBuilder buffer, 
            Module mod) {
        if (mod.isJava()) {
            buffer.append("<p>This module is implemented in Java.</p>");
        }
        if (mod.isDefault()) {
            buffer.append("<p>The default module for packages which do not belong to explicit module.</p>");
        }
        if (JDKUtils.isJDKModule(mod.getNameAsString())) {
            buffer.append("<p>This module forms part of the Java SDK.</p>");            
        }
    }

    private static void addMainModuleDescription(Module mod,
            StringBuilder buffer) {
        HTML.addImageAndLabel(buffer, mod, 
                HTML.fileUrl(getIcon(mod)).toExternalForm(), 
                16, 16, 
                "<tt style='font-size:102%'>" + 
                HTML.highlightLine(description(mod)) + 
                "</tt>", 
                20, 4);
        buffer.append("<hr/>");
    }

    private static void addModuleDocumentation(CeylonParseController cpc,
            Module mod, StringBuilder buffer) {
        Unit unit = mod.getUnit();
        PhasedUnit pu = null;
        if (unit instanceof CeylonUnit) {
            pu = ((CeylonUnit)unit).getPhasedUnit();
        }
        if (pu!=null) {
            List<Tree.ModuleDescriptor> moduleDescriptors = 
                    pu.getCompilationUnit().getModuleDescriptors();
            if (!moduleDescriptors.isEmpty()) {
                Tree.ModuleDescriptor refnode = moduleDescriptors.get(0);
                if (refnode!=null) {
                    Scope linkScope = mod.getPackage(mod.getNameAsString());
                    appendDocAnnotationContent(refnode.getAnnotationList(), buffer, linkScope);
                    appendThrowAnnotationContent(refnode.getAnnotationList(), buffer, linkScope);
                    appendSeeAnnotationContent(refnode.getAnnotationList(), buffer);
                }
            }
        }
    }

    private static void addModuleMembers(StringBuilder buffer, 
            Module mod) {
        boolean first = true;
        for (Package pack: mod.getPackages()) {
            if (pack.isShared()) {
                if (first) {
                    buffer.append("<hr/>Contains:&nbsp;&nbsp;");
                    first = false;
                }
                else {
                    buffer.append(", ");
                }

                /*addImageAndLabel(buffer, null, fileUrl(getIcon(dec)).toExternalForm(), 
                    16, 16, "<tt><a " + link(dec) + ">" + 
                    dec.getName() + "</a></tt>", 20, 2);*/
                buffer.append("<tt><a ")
                    .append(HTML.link(pack))
                    .append(">")
                    .append(pack.getNameAsString())
                    .append("</a></tt>");
            }
        }
        if (!first) {
            buffer.append(".<br/>");
        }
    }

    private static String description(Module mod) {
        return "module " + getLabel(mod) + " \"" + mod.getVersion() + "\"";
    }

    public static String getDocumentationFor(CeylonParseController cpc, 
            Declaration dec) {
        return getDocumentationFor(cpc, dec, null);
    }
    
    private static String getDocumentationFor(CeylonParseController cpc, 
            Declaration dec, Node node) {
        if (dec==null) return null;
        StringBuilder buffer = new StringBuilder();
        insertPageProlog(buffer, 0, HTML.getStyleSheet());
        addMainDescription(buffer, dec, node, cpc);
        addContainerInfo(dec, node, buffer);
        boolean hasDoc = addDoc(cpc, dec, node, buffer);
        boolean obj = addInheritanceInfo(dec, buffer);
        addRefinementInfo(cpc, dec, node, buffer, hasDoc);
        addReturnType(dec, buffer, node, obj);
        addParameters(cpc, dec, node, buffer);
        addClassMembersInfo(dec, buffer);
        if (dec instanceof NothingType) {
            addNothingTypeInfo(buffer);
        }
        else {
            appendExtraActions(dec, buffer);

        }
        addPageEpilog(buffer);
        return buffer.toString();
    }

    private static void addMainDescription(StringBuilder buffer,
            Declaration dec, Node node, CeylonParseController cpc) {
        HTML.addImageAndLabel(buffer, dec, 
                HTML.fileUrl(getIcon(dec)).toExternalForm(), 
                16, 16, 
                "<tt style='font-size:102%'>" + 
                (dec.isDeprecated() ? "<s>":"") + 
                HTML.highlightLine(description(dec, node, cpc)) + 
                (dec.isDeprecated() ? "</s>":"") + 
                "</tt>", 
                20, 4);
        buffer.append("<hr/>");
    }

    private static void addClassMembersInfo(Declaration dec,
            StringBuilder buffer) {
        if (dec instanceof ClassOrInterface) {
            if (!dec.getMembers().isEmpty()) {
                boolean first = true;
                for (Declaration mem: dec.getMembers()) {
                    if (mem instanceof Method && 
                            ((Method)mem).isOverloaded()) {
                        continue;
                    }
                    if (mem.isShared() && !dec.isAnonymous()) {
                        if (first) {
                            buffer.append("<hr/>Members:&nbsp;&nbsp;");
                            first = false;
                        }
                        else {
                            buffer.append(", ");
                        }

                        /*addImageAndLabel(buffer, null, fileUrl(getIcon(dec)).toExternalForm(), 
                              16, 16, "<tt><a " + link(dec) + ">" + dec.getName() + "</a></tt>", 20, 2);*/
                        buffer.append("<tt><a ")
                            .append(HTML.link(mem))
                            .append(">")
                            .append(mem.getName()) 
                            .append("</a></tt>");
                    }
                }
                if (!first) {
                    buffer.append(".<br/>");
                    //extraBreak = true;
                }
            }
        }
    }

    private static void addNothingTypeInfo(StringBuilder buffer) {
        buffer.append("Special bottom type defined by the language. "
                + "<code>Nothing</code> is assignable to all types, but has no value. "
                + "A function or value of type <code>Nothing</code> either throws "
                + "an exception, or never returns.");
    }

    private static boolean addInheritanceInfo(Declaration dec,
            StringBuilder buffer) {
        //boolean extraBreak = false;
        boolean obj=false;
        if (dec instanceof TypedDeclaration) {
            TypeDeclaration td = 
                    ((TypedDeclaration) dec).getTypeDeclaration();
            if (td!=null && td.isAnonymous()) {
                obj=true;
                documentInheritance(td, buffer);    
            }
        }
        else if (dec instanceof TypeDeclaration) {
            documentInheritance((TypeDeclaration) dec, buffer);    
        }
        return obj;
    }

    private static void addRefinementInfo(CeylonParseController cpc,
            Declaration dec, Node node, StringBuilder buffer, 
            boolean hasDoc) {
        Declaration rd = dec.getRefinedDeclaration();
        if (dec!=rd) {
            buffer.append("<p>");
            TypeDeclaration superclass = (TypeDeclaration) rd.getContainer();
            ClassOrInterface outer = (ClassOrInterface) dec.getContainer();
            ProducedType sup = getQualifyingType(node, outer).getSupertype(superclass);
            String qualifyingType = sup.getProducedTypeName();
            HTML.addImageAndLabel(buffer, rd, 
                    HTML.fileUrl(rd.isFormal() ? "implm_co.gif" : "over_co.gif").toExternalForm(),
                    16, 16, 
                    "refines&nbsp;&nbsp;<tt><a " + HTML.link(rd) + ">" + 
                            rd.getName() +"</a></tt>&nbsp;&nbsp;declared by&nbsp;&nbsp;<tt>" +
                            convertToHTMLContent(qualifyingType) + 
                            "</tt>", 
                    20, 2);
            buffer.append("</p>");
            if (!hasDoc) {
                Tree.Declaration refnode2 = 
                        (Tree.Declaration) getReferencedNode(rd, cpc);
                if (refnode2!=null) {
                    appendDocAnnotationContent(refnode2.getAnnotationList(), 
                            buffer, resolveScope(rd));
                }
            }
        }
    }

    private static void addParameters(CeylonParseController cpc,
            Declaration dec, Node node, StringBuilder buffer) {
        if (dec instanceof Functional) {
            ProducedReference ptr = getProducedReference(dec, node);
            for (ParameterList pl: ((Functional) dec).getParameterLists()) {
                if (!pl.getParameters().isEmpty()) {
                    buffer.append("<p>");
                    for (Parameter p: pl.getParameters()) {
                        StringBuilder params = new StringBuilder();
                        String def;
                        if (p.getModel()!=null) {
                        	appendParametersDescription(p.getModel(), params, cpc);
                        	def = getDefaultValueDescription(p, cpc);
                        }
                        else {
                        	def = "";
                        }
                        StringBuilder doc = new StringBuilder();
                        Tree.Declaration refNode = 
                                (Tree.Declaration) getReferencedNode(p.getModel(), cpc);
                        if (refNode!=null) {
                            appendDocAnnotationContent(refNode.getAnnotationList(), 
                                    doc, resolveScope(dec));
                        }
                        ProducedType type = ptr.getTypedParameter(p).getFullType();
                        if (type==null) type = new UnknownType(dec.getUnit()).getType();
                        HTML.addImageAndLabel(buffer, p.getModel(), 
                                HTML.fileUrl("methpro_obj.gif").toExternalForm(),
                                16, 16, 
                                "accepts&nbsp;&nbsp;<tt><a " + HTML.link(type.getDeclaration()) + ">" + 
                                        convertToHTMLContent(type.getProducedTypeName()) + 
                                        "</a>&nbsp;<a " + HTML.link(p.getModel()) + ">"+ p.getName() +
                                        convertToHTMLContent(params.toString()) + "</a>" + 
                                        convertToHTMLContent(def) + "</tt>" + doc, 
                                20, 2);
                    }
                    buffer.append("</p>");
                }
            }
        }
    }

    private static void addReturnType(Declaration dec, StringBuilder buffer,
            Node node, boolean obj) {
        if (dec instanceof TypedDeclaration && !obj) {
            ProducedType ret = getProducedReference(dec, node).getType();
            if (ret!=null) {
                buffer.append("<p>");
                List<ProducedType> list;
                if (ret.getDeclaration() instanceof UnionType) {
                    list = ret.getDeclaration().getCaseTypes();
                }
                else {
                    list = Arrays.asList(ret);
                }
                StringBuilder buf = new StringBuilder("returns&nbsp;&nbsp;<tt>");
                for (ProducedType pt: list) {
                    if (pt.getDeclaration() instanceof ClassOrInterface || 
                            pt.getDeclaration() instanceof TypeParameter) {
                        buf.append("<a " + HTML.link(pt.getDeclaration()) + ">" + 
                                convertToHTMLContent(pt.getProducedTypeName()) +"</a>");
                    }
                    else {
                        buf.append(convertToHTMLContent(pt.getProducedTypeName()));
                    }
                    buf.append("|");
                }
                buf.setLength(buf.length()-1);
                buf.append("</tt>");
                HTML.addImageAndLabel(buffer, ret.getDeclaration(), 
                        HTML.fileUrl("stepreturn_co.gif").toExternalForm(), 
                        16, 16, buf.toString(), 20, 2);
                buffer.append("</p>");
            }
        }
    }

    private static ProducedReference getProducedReference(Declaration dec,
            Node node) {
        if (node instanceof Tree.MemberOrTypeExpression) {
            return ((Tree.MemberOrTypeExpression) node).getTarget();
        }
        ClassOrInterface outer = dec.isClassOrInterfaceMember() ? 
                (ClassOrInterface) dec.getContainer() : null;
        return dec.getProducedReference(getQualifyingType(node, outer),
                        Collections.<ProducedType>emptyList());
    }

    private static boolean addDoc(CeylonParseController cpc, 
            Declaration dec, Node node, StringBuilder buffer) {
        boolean hasDoc = false;
        Node rn = getReferencedNode(dec, cpc);
        if (rn instanceof Tree.Declaration) {
            Tree.Declaration refnode = (Tree.Declaration) rn;
            appendDeprecatedAnnotationContent(refnode.getAnnotationList(), 
                    buffer, resolveScope(dec));
            int len = buffer.length();
            appendDocAnnotationContent(refnode.getAnnotationList(), 
                    buffer, resolveScope(dec));
            hasDoc = buffer.length()!=len;
            appendThrowAnnotationContent(refnode.getAnnotationList(), 
                    buffer, resolveScope(dec));
            appendSeeAnnotationContent(refnode.getAnnotationList(), 
                    buffer);
        }
        else {
            appendJavadoc(dec, cpc.getProject(), buffer, node);
        }
        return hasDoc;
    }

    private static void addContainerInfo(Declaration dec, Node node,
            StringBuilder buffer) {
        Package pack = dec.getUnit().getPackage();
        if (dec.isParameter()) {
            Declaration pd = 
                    ((MethodOrValue) dec).getInitializerParameter()
                            .getDeclaration();
            HTML.addImageAndLabel(buffer, pd, 
                    HTML.fileUrl(getIcon(pd)).toExternalForm(),
                    16, 16, 
                    "parameter of&nbsp;&nbsp;<tt><a " + HTML.link(pd) + ">" + 
                            pd.getName() +"</a></tt>", 20, 2);
        }
        else if (dec instanceof TypeParameter) {
            Declaration pd = ((TypeParameter) dec).getDeclaration();
            HTML.addImageAndLabel(buffer, pd, 
                    HTML.fileUrl(getIcon(pd)).toExternalForm(),
                    16, 16, 
                    "type parameter of&nbsp;&nbsp;<tt><a " + HTML.link(pd) + ">" + 
                            pd.getName() +"</a></tt>", 
                    20, 2);
        }
        else {
            if (dec.isClassOrInterfaceMember()) {
                ClassOrInterface outer = (ClassOrInterface) dec.getContainer();
                ProducedType qt = getQualifyingType(node, outer);
                if (qt!=null) {
                    String qualifyingType = qt.getProducedTypeName();
                    HTML.addImageAndLabel(buffer, outer, 
                            HTML.fileUrl(getIcon(outer)).toExternalForm(), 
                            16, 16, 
                            "member of&nbsp;&nbsp;<tt><a " + HTML.link(outer) + ">" + 
                                    convertToHTMLContent(qualifyingType) + "</a></tt>", 20, 2);
                }
            }

            if ((dec.isShared() || dec.isToplevel()) &&
                    !(dec instanceof NothingType)) {
                String label;
                if (pack.getNameAsString().isEmpty()) {
                    label = "in default package";
                }
                else {
                    label = "in package&nbsp;&nbsp;<tt><a " + HTML.link(pack) + ">" + 
                            getPackageLabel(dec) +"</a></tt>";
                }
                HTML.addImageAndLabel(buffer, pack, 
                        HTML.fileUrl(getIcon(pack)).toExternalForm(), 
                        16, 16, label, 20, 2);
                Module mod = pack.getModule();
                HTML.addImageAndLabel(buffer, mod, 
                        HTML.fileUrl(getIcon(mod)).toExternalForm(), 
                        16, 16, 
                        "in module&nbsp;&nbsp;<tt><a " + HTML.link(mod) + ">" + 
                                getModuleLabel(dec) +"</a></tt>", 
                        20, 2);
            }
        }
    }

    private static ProducedType getQualifyingType(Node node,
            ClassOrInterface outer) {
        if (outer == null) {
            return null;
        }
        if (node instanceof Tree.MemberOrTypeExpression) {
            ProducedReference pr = ((Tree.MemberOrTypeExpression) node).getTarget();
            if (pr!=null) {
                return pr.getQualifyingType();
            }
        }
        return outer.getType();
    }

    private static void appendExtraActions(Declaration dec, 
            StringBuilder buffer) {
        buffer.append("<hr/>");
        String unitName = null;
        if (dec.getUnit() instanceof CeylonUnit) {
            // Manage the case of CeylonBinaryUnit : getFileName() would return the class file name.
            // but getCeylonFileName() will return the ceylon source file name if any.
            unitName = ((CeylonUnit)dec.getUnit()).getCeylonFileName();
        }
        if (unitName == null) {
            unitName = dec.getUnit().getFilename();
        }
                
        HTML.addImageAndLabel(buffer, null, 
                HTML.fileUrl("unit.gif").toExternalForm(), 
                16, 16, 
                "<a href='dec:" + HTML.declink(dec) + 
                        "'>declared</a> in unit&nbsp;&nbsp;<tt>"+ 
                        unitName + "</tt>", 
                20, 2);
        //}
        buffer.append("<hr/>");
        HTML.addImageAndLabel(buffer, null, 
                HTML.fileUrl("search_ref_obj.png").toExternalForm(), 
                16, 16, 
                "<a href='ref:" + HTML.declink(dec) + 
                        "'>find references</a> to&nbsp;&nbsp;<tt>" +
                        dec.getName() + "</tt>",
                20, 2);
        if (dec instanceof ClassOrInterface) {
            HTML.addImageAndLabel(buffer, null, 
                    HTML.fileUrl("search_decl_obj.png").toExternalForm(), 
                    16, 16, 
                    "<a href='sub:" + HTML.declink(dec) + 
                            "'>find subtypes</a> of&nbsp;&nbsp;<tt>" +
                            dec.getName() + "</tt>",
                    20, 2);
        }
        if (dec instanceof Value) {
            HTML.addImageAndLabel(buffer, null, 
                    HTML.fileUrl("search_ref_obj.png").toExternalForm(), 
                    16, 16, 
                    "<a href='ass:" + HTML.declink(dec) + 
                            "'>find assignments</a> to&nbsp;&nbsp;<tt>" +
                            dec.getName() + "</tt>", 
                    20, 2);
        }
        if (dec.isFormal()||dec.isDefault()) {
            HTML.addImageAndLabel(buffer, null, 
                    HTML.fileUrl("search_decl_obj.png").toExternalForm(), 
                    16, 16, 
                    "<a href='act:" + HTML.declink(dec) + 
                            "'>find refinements</a> of&nbsp;&nbsp;<tt>" +
                            dec.getName() + "</tt>", 
                    20, 2);
        }
    }

    private static void documentInheritance(TypeDeclaration dec, StringBuilder buffer) {
        if (dec instanceof Class) {
            ProducedType sup = ((Class) dec).getExtendedType();
            if (sup!=null) {
                buffer.append("<p>");
                HTML.addImageAndLabel(buffer, sup.getDeclaration(), 
                        HTML.fileUrl("super_co.gif").toExternalForm(), 
                        16, 16, 
                        "extends <tt><a " + HTML.link(sup.getDeclaration()) + ">" + 
                                convertToHTMLContent(sup.getProducedTypeName()) +"</a></tt>", 
                        20, 2);
                buffer.append("</p>");
                //extraBreak = true;
            }
        }
//        if (dec instanceof TypeDeclaration) {
            List<ProducedType> sts = ((TypeDeclaration) dec).getSatisfiedTypes();
            if (!sts.isEmpty()) {
                buffer.append("<p>");
                for (ProducedType td: sts) {
                    HTML.addImageAndLabel(buffer, td.getDeclaration(), 
                            HTML.fileUrl("super_co.gif").toExternalForm(), 
                            16, 16, 
                            "satisfies <tt><a " + HTML.link(td.getDeclaration()) + ">" + 
                                    convertToHTMLContent(td.getProducedTypeName()) +"</a></tt>", 
                            20, 2);
                    //extraBreak = true;
                }
                buffer.append("</p>");
            }
            List<ProducedType> cts = ((TypeDeclaration) dec).getCaseTypes();
            if (cts!=null) {
                buffer.append("<p>");
                for (ProducedType td: cts) {
                    HTML.addImageAndLabel(buffer, td.getDeclaration(), 
                            HTML.fileUrl("sub_co.gif").toExternalForm(), 
                            16, 16, 
                            (td.getDeclaration().isSelfType() ? "has self type" : "has case") + 
                            " <tt><a " + HTML.link(td.getDeclaration()) + ">" + 
                            convertToHTMLContent(td.getProducedTypeName()) +"</a></tt>", 
                            20, 2);
                    //extraBreak = true;
                }
                buffer.append("</p>");
            }
//        }
    }

    private static String description(Declaration dec, 
            Node node, CeylonParseController cpc) {
        ProducedReference pr = getProducedReference(dec, node);
        String result = node==null ? 
                getDescriptionFor(dec, pr, dec.getUnit()) :
                getDescriptionFor(dec, pr, node.getUnit());
        if (dec instanceof TypeDeclaration) {
            TypeDeclaration td = (TypeDeclaration) dec;
            if (td.isAlias() && td.getExtendedType()!=null) {
                result += " => ";
                result += td.getExtendedType().getProducedTypeName();
            }
        }
        else if (dec instanceof Value) {
            if (!((Value) dec).isVariable()) {
                result += getInitalValueDescription(dec, cpc);
            }
        }
        else if (dec instanceof Method) {
            result += getInitalValueDescription(dec, cpc);
        }
        /*else if (dec instanceof ValueParameter) {
            Tree.Declaration refnode = (Tree.Declaration) getReferencedNode(dec, cpc);
            if (refnode instanceof Tree.ValueParameterDeclaration) {
                Tree.DefaultArgument da = ((Tree.ValueParameterDeclaration) refnode).getDefaultArgument();
                if (da!=null) {
                    Tree.Expression e = da.getSpecifierExpression().getExpression();
                    if (e!=null) {
                        Tree.Term term = e.getTerm();
                        if (term instanceof Tree.Literal) {
                            result += " = ";
                            result += term.getText();
                        }
                        else {
                            result += " =";
                        }
                    }
                }
            }
        }*/
        return result;
    }

    private static void appendJavadoc(Declaration model, IProject project,
            StringBuilder buffer, Node node) {
        try {
            appendJavadoc(getJavaElement(model), buffer);
        }
        catch (JavaModelException jme) {
            jme.printStackTrace();
        }
    }

    private static void appendDocAnnotationContent(Tree.AnnotationList annotationList,
            StringBuilder documentation, Scope linkScope) {
        if (annotationList!=null) {
            AnonymousAnnotation aa = annotationList.getAnonymousAnnotation();
            if (aa!=null) {
                documentation.append(markdown(aa.getStringLiteral().getText(), linkScope,
                        annotationList.getUnit()));
            }
            for (Tree.Annotation annotation : annotationList.getAnnotations()) {
                Tree.Primary annotPrim = annotation.getPrimary();
                if (annotPrim instanceof Tree.BaseMemberExpression) {
                    String name = ((Tree.BaseMemberExpression) annotPrim).getIdentifier().getText();
                    if ("doc".equals(name)) {
                        Tree.PositionalArgumentList argList = annotation.getPositionalArgumentList();
                        if (argList!=null) {
                            List<Tree.PositionalArgument> args = argList.getPositionalArguments();
                            if (!args.isEmpty()) {
                                Tree.PositionalArgument a = args.get(0);
                                if (a instanceof Tree.ListedArgument) {
                                    String text = ((Tree.ListedArgument) a).getExpression()
                                            .getTerm().getText();
                                    if (text!=null) {
                                        documentation.append(markdown(text, linkScope,
                                                annotationList.getUnit()));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private static void appendDeprecatedAnnotationContent(Tree.AnnotationList annotationList,
            StringBuilder documentation, Scope linkScope) {
        if (annotationList!=null) {
            for (Tree.Annotation annotation : annotationList.getAnnotations()) {
                Tree.Primary annotPrim = annotation.getPrimary();
                if (annotPrim instanceof Tree.BaseMemberExpression) {
                    String name = ((Tree.BaseMemberExpression) annotPrim).getIdentifier().getText();
                    if ("deprecated".equals(name)) {
                        Tree.PositionalArgumentList argList = annotation.getPositionalArgumentList();
                        if (argList!=null) {
                            List<Tree.PositionalArgument> args = argList.getPositionalArguments();
                            if (!args.isEmpty()) {
                                Tree.PositionalArgument a = args.get(0);
                                if (a instanceof Tree.ListedArgument) {
                                    String text = ((Tree.ListedArgument) a).getExpression()
                                                .getTerm().getText();
                                    if (text!=null) {
                                        documentation.append(markdown("_(This is a deprecated program element.)_\n\n" + text, 
                                                linkScope, annotationList.getUnit()));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private static void appendSeeAnnotationContent(Tree.AnnotationList annotationList,
            StringBuilder documentation) {
        if (annotationList!=null) {
            for (Tree.Annotation annotation : annotationList.getAnnotations()) {
                Tree.Primary annotPrim = annotation.getPrimary();
                if (annotPrim instanceof Tree.BaseMemberExpression) {
                    String name = ((Tree.BaseMemberExpression) annotPrim).getIdentifier().getText();
                    if ("see".equals(name)) {
                        Tree.PositionalArgumentList argList = annotation.getPositionalArgumentList();
                        if (argList!=null) {
                            List<Tree.PositionalArgument> args = argList.getPositionalArguments();
                            for (Tree.PositionalArgument arg: args) {
                                if (arg instanceof Tree.ListedArgument) {
                                    Tree.Term term = ((Tree.ListedArgument) arg).getExpression().getTerm();
                                    if (term instanceof Tree.MetaLiteral) {
                                        Declaration dec = ((Tree.MetaLiteral) term).getDeclaration();
                                        if (dec!=null) {
                                            String dn = dec.getName();
                                            if (dec.isClassOrInterfaceMember()) {
                                                dn = ((ClassOrInterface) dec.getContainer()).getName() + "." + dn;
                                            }
                                            HTML.addImageAndLabel(documentation, dec, HTML.fileUrl("link_obj.gif"/*getIcon(dec)*/).toExternalForm(), 16, 16, 
                                                    "see <tt><a "+HTML.link(dec)+">"+dn+"</a></tt>", 20, 2);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private static void appendThrowAnnotationContent(Tree.AnnotationList annotationList,
            StringBuilder documentation, Scope linkScope) {
        if (annotationList!=null) {
            for (Tree.Annotation annotation : annotationList.getAnnotations()) {
                Tree.Primary annotPrim = annotation.getPrimary();
                if (annotPrim instanceof Tree.BaseMemberExpression) {
                    String name = ((Tree.BaseMemberExpression) annotPrim).getIdentifier().getText();
                    if ("throws".equals(name)) {
                        Tree.PositionalArgumentList argList = annotation.getPositionalArgumentList();
                        if (argList!=null) {
                            List<Tree.PositionalArgument> args = argList.getPositionalArguments();
                            if (args.isEmpty()) continue;
                            Tree.PositionalArgument typeArg = args.get(0);
                            Tree.PositionalArgument textArg = args.size()>1 ? args.get(1) : null;
                            if (typeArg instanceof Tree.ListedArgument && 
                                    (textArg==null || textArg instanceof Tree.ListedArgument)) {
                                Tree.Term typeArgTerm = ((Tree.ListedArgument) typeArg).getExpression().getTerm();
                                Tree.Term textArgTerm = textArg==null ? null : ((Tree.ListedArgument) textArg).getExpression().getTerm();
                                String text = textArgTerm instanceof Tree.StringLiteral ?
                                        textArgTerm.getText() : "";
                                if (typeArgTerm instanceof Tree.MetaLiteral) {
                                    Declaration dec = ((Tree.MetaLiteral) typeArgTerm).getDeclaration();
                                    if (dec!=null) {
                                        String dn = dec.getName();
                                        if (typeArgTerm instanceof Tree.QualifiedMemberOrTypeExpression) {
                                            Tree.Primary p = ((Tree.QualifiedMemberOrTypeExpression) typeArgTerm).getPrimary();
                                            if (p instanceof Tree.MemberOrTypeExpression) {
                                                dn = ((Tree.MemberOrTypeExpression) p).getDeclaration().getName()
                                                        + "." + dn;
                                            }
                                        }
                                        HTML.addImageAndLabel(documentation, dec, HTML.fileUrl("ihigh_obj.gif"/*getIcon(dec)*/).toExternalForm(), 16, 16, 
                                                "throws <tt><a "+HTML.link(dec)+">"+dn+"</a></tt>" + 
                                                        markdown(text, linkScope, annotationList.getUnit()), 20, 2);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private static String markdown(String text, final Scope linkScope, final Unit unit) {
        if( text == null || text.length() == 0 ) {
            return text;
        }

//        String unquotedText = text.substring(1, text.length()-1);

        Builder builder = Configuration.builder().forceExtentedProfile();
        builder.setCodeBlockEmitter(new CeylonBlockEmitter());
        if (linkScope!=null) {
            builder.setSpecialLinkEmitter(new CeylonSpanEmitter(linkScope, unit));
        }
        return Processor.process(text, builder.build());
    }
    
    private static Scope resolveScope(Declaration decl) {
        if (decl == null) {
            return null;
        }
        else if (decl instanceof Scope) {
            return (Scope) decl;
        }
        else {
            return decl.getContainer();
        }
    }
    
    static Module resolveModule(Scope scope) {
        if (scope == null) {
            return null;
        }
        else if (scope instanceof Package) {
            return ((Package) scope).getModule();
        }
        else {
            return resolveModule(scope.getContainer());
        }
    }
    
    /**
     * Creates the "enriched" control.
     */
    private final class PresenterControlCreator extends AbstractReusableInformationControlCreator {
        
        private final DocumentationHover docHover;
        
        PresenterControlCreator(DocumentationHover docHover) {
            this.docHover = docHover;
        }
        
        @Override
        public IInformationControl doCreateInformationControl(Shell parent) {
            if (isAvailable(parent)) {
                ToolBarManager tbm = new ToolBarManager(SWT.FLAT);
                BrowserInformationControl control = new BrowserInformationControl(parent, 
                        APPEARANCE_JAVADOC_FONT, tbm);

                final BackAction backAction = new BackAction(control);
                backAction.setEnabled(false);
                tbm.add(backAction);
                final ForwardAction forwardAction = new ForwardAction(control);
                tbm.add(forwardAction);
                forwardAction.setEnabled(false);

                //final ShowInJavadocViewAction showInJavadocViewAction= new ShowInJavadocViewAction(iControl);
                //tbm.add(showInJavadocViewAction);
                final OpenDeclarationAction openDeclarationAction = new OpenDeclarationAction(control);
                tbm.add(openDeclarationAction);

//                final SimpleSelectionProvider selectionProvider = new SimpleSelectionProvider();
                //TODO: an action to open the generated ceylondoc  
                //      from the doc archive, in a browser window
                /*if (fSite != null) {
                    OpenAttachedJavadocAction openAttachedJavadocAction= new OpenAttachedJavadocAction(fSite);
                    openAttachedJavadocAction.setSpecialSelectionProvider(selectionProvider);
                    openAttachedJavadocAction.setImageDescriptor(DESC_ELCL_OPEN_BROWSER);
                    openAttachedJavadocAction.setDisabledImageDescriptor(DESC_DLCL_OPEN_BROWSER);
                    selectionProvider.addSelectionChangedListener(openAttachedJavadocAction);
                    selectionProvider.setSelection(new StructuredSelection());
                    tbm.add(openAttachedJavadocAction);
                }*/

                IInputChangedListener inputChangeListener = new IInputChangedListener() {
                    public void inputChanged(Object newInput) {
                        backAction.update();
                        forwardAction.update();
//                        if (newInput == null) {
//                            selectionProvider.setSelection(new StructuredSelection());
//                        }
//                        else 
                        boolean isDeclaration = false;
                        if (newInput instanceof CeylonBrowserInput) {
//                            Object inputElement = ((CeylonBrowserInput) newInput).getInputElement();
//                            selectionProvider.setSelection(new StructuredSelection(inputElement));
                            //showInJavadocViewAction.setEnabled(isJavaElementInput);
                            isDeclaration = ((CeylonBrowserInput) newInput).getAddress()!=null;
                        }
                        openDeclarationAction.setEnabled(isDeclaration);
                    }
                };
                control.addInputChangeListener(inputChangeListener);

                tbm.update(true);

                docHover.addLinkListener(control);
                return control;

            } 
            else {
                return new DefaultInformationControl(parent, true);
            }
        }
        
    }
    
    private final class HoverControlCreator extends AbstractReusableInformationControlCreator {
        
        private final DocumentationHover docHover;
        private String statusLineMessage;
        private final IInformationControlCreator enrichedControlCreator;

        HoverControlCreator(DocumentationHover docHover, 
                IInformationControlCreator enrichedControlCreator,
                String statusLineMessage) {
            this.docHover = docHover;
            this.enrichedControlCreator = enrichedControlCreator;
            this.statusLineMessage = statusLineMessage;
        }
        
        @Override
        public IInformationControl doCreateInformationControl(Shell parent) {
            if (enrichedControlCreator!=null && isAvailable(parent)) {
                BrowserInformationControl control = new BrowserInformationControl(parent, 
                        APPEARANCE_JAVADOC_FONT, statusLineMessage) {
                    @Override
                    public IInformationControlCreator getInformationPresenterControlCreator() {
                        return enrichedControlCreator;
                    }
                };
                if (docHover!=null) {
                    docHover.addLinkListener(control);
                }
                return control;
            } 
            else {
                return new DefaultInformationControl(parent, statusLineMessage);
            }
        }
        
    }
    
}