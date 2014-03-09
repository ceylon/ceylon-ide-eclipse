package com.redhat.ceylon.eclipse.code.editor;

import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.getSelection;
import static org.eclipse.jdt.ui.PreferenceConstants.APPEARANCE_JAVADOC_FONT;
import static org.eclipse.jface.dialogs.DialogSettings.getOrCreateSection;
import static org.eclipse.jface.text.AbstractInformationControlManager.ANCHOR_GLOBAL;
import static org.eclipse.jface.text.IDocument.DEFAULT_CONTENT_TYPE;

import org.eclipse.jface.bindings.keys.KeySequence;
import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ContentAssistEvent;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ICompletionListener;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.information.IInformationPresenter;
import org.eclipse.jface.text.information.IInformationProvider;
import org.eclipse.jface.text.information.IInformationProviderExtension;
import org.eclipse.jface.text.information.InformationPresenter;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.quickassist.IQuickAssistAssistant;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.browser.BrowserInformationControl;
import com.redhat.ceylon.eclipse.code.complete.CeylonCompletionProcessor;
import com.redhat.ceylon.eclipse.code.correct.CeylonCorrectionProcessor;
import com.redhat.ceylon.eclipse.code.hover.AnnotationHover;
import com.redhat.ceylon.eclipse.code.hover.BestMatchHover;
import com.redhat.ceylon.eclipse.code.hover.DocumentationHover;
import com.redhat.ceylon.eclipse.code.html.HTMLTextPresenter;
import com.redhat.ceylon.eclipse.code.outline.CeylonOutlineBuilder;
import com.redhat.ceylon.eclipse.code.outline.HierarchyInput;
import com.redhat.ceylon.eclipse.code.outline.HierarchyPopup;
import com.redhat.ceylon.eclipse.code.outline.OutlinePopup;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.code.resolve.CeylonHyperlinkDetector;
import com.redhat.ceylon.eclipse.code.resolve.JavaHyperlinkDetector;
import com.redhat.ceylon.eclipse.code.search.FindContainerVisitor;
import com.redhat.ceylon.eclipse.code.search.FindReferencesPopup;
import com.redhat.ceylon.eclipse.ui.CeylonPlugin;
import com.redhat.ceylon.eclipse.util.Nodes;

public class CeylonSourceViewerConfiguration extends TextSourceViewerConfiguration {
    
    public static final String AUTO_INSERT = "autoInsert";
    public static final String AUTO_ACTIVATION = "autoActivation";
    public static final String AUTO_ACTIVATION_CHARS = "autoActivationChars";
    public static final String AUTO_ACTIVATION_DELAY = "autoActivationDelay";
    public static final String LINKED_MODE = "linkedModeCompletion";
    public static final String LINKED_MODE_RENAME = "linkedModeRename";
    public static final String LINKED_MODE_EXTRACT = "linkedModeExtract";
    public static final String PASTE_CORRECT_INDENTATION = "pasteCorrectIndentation";
    
    public static final String CLOSE_PARENS = "closeParens";
    public static final String CLOSE_BRACKETS = "closeBrackets";
    public static final String CLOSE_ANGLES = "closeAngles";
    public static final String CLOSE_BACKTICKS = "closeBackticks";
    public static final String CLOSE_BRACES = "closeBraces";
    public static final String CLOSE_QUOTES = "closeQuotes";
    
    public static final String NORMALIZE_WS = "normalizedWs";
    public static final String NORMALIZE_NL = "normalizedNl";
    public static final String CLEAN_IMPORTS = "cleanImports";
    
    protected final CeylonEditor editor;
    
    public CeylonSourceViewerConfiguration(CeylonEditor editor) {
        super(EditorsUI.getPreferenceStore());
        setPreferenceDefaults();
        this.editor = editor;
    }
    
    public PresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
        PresentationReconciler reconciler = new PresentationReconciler();
        //make sure we pass the sourceViewer we get as an argument here
        //otherwise it breaks syntax highlighting in Code popup
        PresentationDamageRepairer damageRepairer = 
                new PresentationDamageRepairer(sourceViewer, editor);
        reconciler.setRepairer(damageRepairer, DEFAULT_CONTENT_TYPE);
        reconciler.setDamager(damageRepairer, DEFAULT_CONTENT_TYPE);
        return reconciler;
    }

    /*private final class Warmup implements IRunnableWithProgress {
        @Override
        public void run(IProgressMonitor monitor) throws InvocationTargetException,
                InterruptedException {
            
            monitor.beginTask("Warming up completion processor", 100000);
            
            List<Package> packages = editor.getParseController()
                    .getRootNode().getUnit().getPackage()
                    .getModule().getAllPackages();
            
            monitor.worked(10000);
            
            for (Package p: packages) {
                p.getMembers();
                monitor.worked(90000/packages.size());
                if (monitor.isCanceled()) return;
            }

            monitor.done();
        }
    }*/
    
    static void setPreferenceDefaults() {
        IPreferenceStore preferenceStore = EditorsUI.getPreferenceStore();
        preferenceStore.setDefault(AUTO_INSERT, true);
        preferenceStore.setDefault(AUTO_ACTIVATION, true);
        preferenceStore.setDefault(AUTO_ACTIVATION_DELAY, 500);
        preferenceStore.setDefault(AUTO_ACTIVATION_CHARS, ".");
        preferenceStore.setDefault(LINKED_MODE, true);
        preferenceStore.setDefault(LINKED_MODE_RENAME, true);
        preferenceStore.setDefault(LINKED_MODE_EXTRACT, true);
        preferenceStore.setDefault(PASTE_CORRECT_INDENTATION, true);
        preferenceStore.setDefault(NORMALIZE_WS, false);
        preferenceStore.setDefault(NORMALIZE_NL, false);
        preferenceStore.setDefault(CLEAN_IMPORTS, false);
        preferenceStore.setDefault(CLOSE_PARENS, true);
        preferenceStore.setDefault(CLOSE_BRACKETS, true);
        preferenceStore.setDefault(CLOSE_ANGLES, true);
        preferenceStore.setDefault(CLOSE_BRACES, true);
        preferenceStore.setDefault(CLOSE_QUOTES, true);
        preferenceStore.setDefault(CLOSE_BACKTICKS, true);
    }
    
    public ContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
        if (editor==null) return null;
        ContentAssistant contentAssistant = new ContentAssistant();
        contentAssistant.setRestoreCompletionProposalSize(getOrCreateSection(getSettings(),
                "completion_proposal_popup"));
        CeylonCompletionProcessor completionProcessor = new CeylonCompletionProcessor(editor);
        contentAssistant.addCompletionListener(new CompletionListener(editor, completionProcessor));
        contentAssistant.setContentAssistProcessor(completionProcessor, DEFAULT_CONTENT_TYPE);
        configCompletionPopup(contentAssistant);
        contentAssistant.enableColoredLabels(true);
        contentAssistant.setRepeatedInvocationMode(true);
        KeyStroke key = KeyStroke.getInstance(SWT.CTRL, SWT.SPACE);
        contentAssistant.setRepeatedInvocationTrigger(KeySequence.getInstance(key));
        contentAssistant.setStatusMessage(key.format() + " to toggle filter by type");
        contentAssistant.setStatusLineVisible(true);
        contentAssistant.setInformationControlCreator(new DocumentationHover(editor).getHoverControlCreator("Click for focus"));
        contentAssistant.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
//      ca.setContextInformationPopupBackground(Display.getDefault().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        //ca.enablePrefixCompletion(true); //TODO: prefix completion stuff in ICompletionProposalExtension3
        return contentAssistant;
    }

    private static final class HierarchyPresenterControlCreator
            implements IInformationControlCreator {
        private CeylonEditor editor;
        HierarchyPresenterControlCreator(CeylonEditor editor) {
            this.editor = editor;
        }
        @Override
        public IInformationControl createInformationControl(Shell parent) {
            return new HierarchyPopup(editor, parent, 
                    SWT.NONE, SWT.V_SCROLL | SWT.H_SCROLL);
        }
    }

    private static final class BrowserControlCreator 
            implements IInformationControlCreator {
        public IInformationControl createInformationControl(Shell parent) {
            try {
                return new BrowserInformationControl(parent, 
                        APPEARANCE_JAVADOC_FONT, 
                        (String) null);
            }
            catch(org.eclipse.swt.SWTError x){
                return new DefaultInformationControl(parent, "Press 'F2' for focus", 
                        new HTMLTextPresenter(true));
            }
        }
    }
    
    static void configCompletionPopup(ContentAssistant contentAssistant) {
        IPreferenceStore preferenceStore = EditorsUI.getPreferenceStore();
        contentAssistant.enableAutoInsert(preferenceStore.getBoolean(AUTO_INSERT));
        contentAssistant.enableAutoActivation(preferenceStore.getBoolean(AUTO_ACTIVATION));
        contentAssistant.setAutoActivationDelay(preferenceStore.getInt(AUTO_ACTIVATION_DELAY));
    }

    @Override
    public IQuickAssistAssistant getQuickAssistAssistant(ISourceViewer sourceViewer) {
        if (editor==null) return null;
        CeylonCorrectionProcessor quickAssist = new CeylonCorrectionProcessor(editor);
        quickAssist.setRestoreCompletionProposalSize(getOrCreateSection(getSettings(), 
                "quickassist_proposal_popup"));
        quickAssist.enableColoredLabels(true);
        return quickAssist;
    }

    public AnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
        return new AnnotationHover(editor, true);
    }

    public AnnotationHover getOverviewRulerAnnotationHover(ISourceViewer sourceViewer) {
        return new AnnotationHover(editor, true);
    }

    public IAutoEditStrategy[] getAutoEditStrategies(ISourceViewer sourceViewer, String contentType) {
        return new IAutoEditStrategy[] { new CeylonAutoEditStrategy() };
    }
        
    public ITextDoubleClickStrategy getDoubleClickStrategy(ISourceViewer sourceViewer, String contentType) {
        return new DoubleClickStrategy(); 
    }

    public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) {
        CeylonParseController pc = getParseController();
        if (pc==null) {
            return new IHyperlinkDetector[0];
        }
        else {
            return new IHyperlinkDetector[] { 
                    new CeylonHyperlinkDetector(pc), 
                    new JavaHyperlinkDetector(pc) 
                };
        }
    }
    
    //TODO: We need a CeylonParseControllerProvider 
    //      which CeylonEditor implements - since
    //      having to extend this class and override
    //      is just sucky.
    protected CeylonParseController getParseController() {
        if (editor==null) {
            return null;
        }
        else {
            return editor.getParseController();
        }
    }
    
    /**
     * Used to present hover help (anything else?)
     */
    public IInformationControlCreator getInformationControlCreator(ISourceViewer sourceViewer) {
        return new BrowserControlCreator();
    }

    public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
        if (editor==null) return null;
        return new BestMatchHover(editor);
    }

    /*public IInformationPresenter getInformationPresenter(ISourceViewer sourceViewer) {
        if (infoPresenter == null) {
            infoPresenter= new InformationPresenter(getInformationControlCreator(sourceViewer));
            infoPresenter.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
            infoPresenter.setAnchor(ANCHOR_GLOBAL);

            IInformationProvider provider= new HoverInformationProvider();
            infoPresenter.setInformationProvider(provider, IDocument.DEFAULT_CONTENT_TYPE);
            //infoPresenter.setSizeConstraints(500, 100, true, false);
            //infoPresenter.setRestoreInformationControlBounds(getSettings("outline_presenter_bounds"), true, true); //$NON-NLS-1$
        }
        return infoPresenter;
    }

    private final class HoverInformationProvider implements IInformationProvider {
        private IAnnotationModel annotationModel= editor.getDocumentProvider()
                .getAnnotationModel(editor.getEditorInput());

        private List<Annotation> getParserAnnotationsAtOffset(int offset) {
            List<Annotation> result= new LinkedList<Annotation>();
            if (annotationModel != null) {
                for(Iterator<Annotation> iter= annotationModel.getAnnotationIterator(); 
                        iter.hasNext(); ) {
                    Annotation ann= iter.next();
                    if (annotationModel.getPosition(ann).includes(offset) && 
                            isParseAnnotation(ann)) {
                        result.add(ann);
                    }
                }
            }
            return result;
        }

        public IRegion getSubject(ITextViewer textViewer, int offset) {
            List<Annotation> parserAnnsAtOffset = getParserAnnotationsAtOffset(offset);
            if (!parserAnnsAtOffset.isEmpty()) {
                Annotation ann= parserAnnsAtOffset.get(0);
                Position pos= annotationModel.getPosition(ann);
                return new Region(pos.offset, pos.length);
            }
            Node selNode= findNode(editor.getParseController().getRootNode(), offset);
            return new Region(getStartOffset(selNode), getLength(selNode));
        }

        public String getInformation(ITextViewer textViewer, IRegion subject) {
            List<Annotation> parserAnnsAtOffset = getParserAnnotationsAtOffset(subject.getOffset());
            if (!parserAnnsAtOffset.isEmpty()) {
                return parserAnnsAtOffset.get(0).getText();
            }

            CeylonParseController pc = editor.getParseController();
            Node selNode= findNode(pc.getRootNode(), subject.getOffset());
            return new CeylonDocumentationProvider().getDocumentation(selNode, pc);
            return null;
        }
    }*/

    private IDialogSettings getSettings() {
        return CeylonPlugin.getInstance().getDialogSettings(); 
    }
    
    public IInformationPresenter getDefinitionPresenter(ISourceViewer sourceViewer) {
        if (editor==null) return null;
        InformationPresenter presenter = new InformationPresenter(new DefinitionPresenterControlCreator(editor));
        presenter.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
        presenter.setAnchor(ANCHOR_GLOBAL);
        presenter.setInformationProvider(new OutlineInformationProvider(getParseController()), 
                DEFAULT_CONTENT_TYPE);
        presenter.setSizeConstraints(40, 10, true, false);
        presenter.setRestoreInformationControlBounds(getOrCreateSection(getSettings(),
                "code_presenter_bounds"), true, true);
        return presenter;
    }
    
    public IInformationPresenter getReferencesPresenter(ISourceViewer sourceViewer) {
        if (editor==null) return null;
        InformationPresenter presenter = new InformationPresenter(new ReferencesPresenterControlCreator(editor));
        presenter.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
        presenter.setAnchor(ANCHOR_GLOBAL);
        presenter.setInformationProvider(new OutlineInformationProvider(getParseController()), 
                DEFAULT_CONTENT_TYPE);
        presenter.setSizeConstraints(40, 10, true, false);
        presenter.setRestoreInformationControlBounds(getOrCreateSection(getSettings(),
                "refs_presenter_bounds"), true, true);
        return presenter;
    }
    
    private static final class OutlinePresenterControlCreator implements
            IInformationControlCreator {
        private CeylonEditor editor;
        private OutlinePresenterControlCreator(CeylonEditor editor) {
            this.editor = editor;
        }
        @Override
        public IInformationControl createInformationControl(Shell parent) {
            return new OutlinePopup(editor, parent, 
                    SWT.NONE, SWT.V_SCROLL | SWT.H_SCROLL);
        }
    }

    private static final class PopupSourceViewerConfiguration 
            extends CeylonSourceViewerConfiguration {
        private final PeekDefinitionPopup popup;
        private PopupSourceViewerConfiguration(CeylonEditor editor,
                PeekDefinitionPopup popup) {
            super(editor);
            this.popup = popup;
        }

        @Override
        protected CeylonParseController getParseController() {
            return popup.getParseController();
        }
    }
    
    private static final class DefinitionPresenterControlCreator 
            implements IInformationControlCreator {
        private CeylonEditor editor;
        private DefinitionPresenterControlCreator(CeylonEditor editor) {
            this.editor = editor;
        }
        @Override
        public IInformationControl createInformationControl(Shell parent) {
            PeekDefinitionPopup popup = 
                    new PeekDefinitionPopup(parent, SWT.NONE, editor);
            popup.viewer.configure(new PopupSourceViewerConfiguration(editor, popup));
            return popup;
        }
    }
    
    private static final class ReferencesPresenterControlCreator 
            implements IInformationControlCreator {
        private CeylonEditor editor;
        private ReferencesPresenterControlCreator(CeylonEditor editor) {
            this.editor = editor;
        }
        @Override
        public IInformationControl createInformationControl(Shell parent) {
            return new FindReferencesPopup(parent, SWT.NONE, editor);
        }
    }

    private static final class CompletionListener 
            implements ICompletionListener {
        private CeylonEditor editor;
        private CeylonCompletionProcessor processor;

        private CompletionListener(CeylonEditor editor,
                CeylonCompletionProcessor processor) {
            this.editor = editor;
            this.processor = processor;
            
        }
        @Override
        public void selectionChanged(ICompletionProposal proposal,
                boolean smartToggle) {}

        @Override
        public void assistSessionStarted(ContentAssistEvent event) {
            if (editor!=null) {
                editor.pauseBackgroundParsing();
            }
            processor.sessionStarted();
            /*try {
                editor.getSite().getWorkbenchWindow().run(true, true, new Warmup());
            } 
            catch (Exception e) {}*/
        }

        @Override
        public void assistSessionEnded(ContentAssistEvent event) {
            if (editor!=null) {
                editor.unpauseBackgroundParsing();
                editor.scheduleParsing();
            }
        }
    }

    private static final class OutlineInformationProvider 
            implements IInformationProvider, IInformationProviderExtension {
        private CeylonParseController parseController;
        OutlineInformationProvider(CeylonParseController parseController) {
            this.parseController = parseController;
        }
        @Override
        public IRegion getSubject(ITextViewer textViewer, int offset) {
            return new Region(offset, 0); // Could be anything, since it's ignored below in getInformation2()...
        }
        @Override
        public String getInformation(ITextViewer textViewer, IRegion subject) {
            // shouldn't be called, given IInformationProviderExtension???
            throw new UnsupportedOperationException();
        }
        @Override
        public Object getInformation2(ITextViewer textViewer, IRegion subject) {
            return new CeylonOutlineBuilder().buildTree(parseController);
        }
    }
    
    public IInformationPresenter getOutlinePresenter(ISourceViewer sourceViewer) {
        if (editor==null) return null;
        InformationPresenter presenter = 
                new InformationPresenter(new OutlinePresenterControlCreator(editor));
        presenter.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
        presenter.setAnchor(ANCHOR_GLOBAL);
        presenter.setInformationProvider(new OutlineInformationProvider(getParseController()), 
                DEFAULT_CONTENT_TYPE);
        presenter.setSizeConstraints(40, 10, true, false);
        presenter.setRestoreInformationControlBounds(getOrCreateSection(getSettings(),
                "outline_presenter_bounds"), true, true);
        return presenter;
    }
    
    private class HierarchyInformationProvider 
            implements IInformationProvider, IInformationProviderExtension {
        @Override
        public IRegion getSubject(ITextViewer textViewer, int offset) {
            return new Region(offset, 0); // Could be anything, since it's ignored below in getInformation2()...
        }
        @Override
        public String getInformation(ITextViewer textViewer, IRegion subject) {
            // shouldn't be called, given IInformationProviderExtension???
            throw new UnsupportedOperationException();
        }
        @Override
        public Object getInformation2(ITextViewer textViewer, IRegion subject) {
            Node selectedNode = getSelectedNode();
            Declaration declaration = Nodes.getReferencedDeclaration(selectedNode);
            if (declaration==null) {
                FindContainerVisitor fcv = new FindContainerVisitor(selectedNode);
                fcv.visit(getParseController().getRootNode());
                Tree.StatementOrArgument node = fcv.getStatementOrArgument();
                if (node instanceof Tree.Declaration) {
                    declaration = ((Tree.Declaration) node).getDeclarationModel();
                }
            }
            return new HierarchyInput(declaration, 
                    getParseController().getProject());
        }
        //TODO: this is a copy/paste from AbstractFindAction
        private Node getSelectedNode() {
            CeylonParseController cpc = getParseController();
            return cpc.getRootNode()==null ? null : 
                Nodes.findNode(cpc.getRootNode(), getSelection(editor));
        }
    }

    public IInformationPresenter getHierarchyPresenter(ISourceViewer sourceViewer) {
        if (editor==null) return null;
        InformationPresenter presenter = 
                new InformationPresenter(new HierarchyPresenterControlCreator(editor));
        presenter.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
        presenter.setAnchor(ANCHOR_GLOBAL);
        presenter.setInformationProvider(new HierarchyInformationProvider(), 
                DEFAULT_CONTENT_TYPE);
        presenter.setSizeConstraints(40, 10, true, false);
        presenter.setRestoreInformationControlBounds(getOrCreateSection(getSettings(),
                "hierarchy_presenter_bounds"), true, true);
        return presenter;
    }
    
    @Override
    public IReconciler getReconciler(ISourceViewer sourceViewer) {
        //don't spell-check!
        return null;
    }
    
}
