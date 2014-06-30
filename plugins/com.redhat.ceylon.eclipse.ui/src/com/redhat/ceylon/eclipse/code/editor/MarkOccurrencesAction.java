package com.redhat.ceylon.eclipse.code.editor;

import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.antlr.runtime.CommonToken;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.hover.DocumentationView;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.code.parse.TreeLifecycleListener;
import com.redhat.ceylon.eclipse.util.FindAssignmentsVisitor;
import com.redhat.ceylon.eclipse.util.FindDeclarationNodeVisitor;
import com.redhat.ceylon.eclipse.util.FindReferencesVisitor;
import com.redhat.ceylon.eclipse.util.Nodes;

/**
 * Action class that implements the "Mark Occurrences" mode. This action contains a number of
 * nested listener classes that monitor which editor is active, document changes, and selection
 * changes, and computes a set of "occurrence" annotations in the editor, using the language-specific
 * "mark occurrences" service.
 */
public class MarkOccurrencesAction implements IWorkbenchWindowActionDelegate, 
        CaretListener, ISelectionChangedListener, TreeLifecycleListener {
    /**
     * The ID for the kind of annotations created for "mark occurrences"
     */
    public static final String DECLARATION_ANNOTATION = PLUGIN_ID + ".declarationAnnotation";
    public static final String OCCURRENCE_ANNOTATION = PLUGIN_ID + ".occurrenceAnnotation";
    public static final String ASSIGNMENT_ANNOTATION = PLUGIN_ID + ".assignmentAnnotation";

    /**
     * True if "mark occurrences" is currently on/enabled
     */
    private boolean markingEnabled = true;

    private CeylonEditor activeEditor;

    /**
     * The IParseController for the currently-active editor, if any. Could be null
     * if the current editor is not an IMP editor.
     */
    private CeylonParseController parseController;

    /**
     * The document provider for the currently-active editor. Could be null if
     * the current editor is not an IMP editor.
     */
    private IDocumentProvider documentProvider;

    /**
     * The document for the currently-active editor, if any. Could be null if
     * the current editor is not an IMP editor.
     */
    private IDocument document;

    private Annotation[] occurrenceAnnotations;
    
    /**
     * Listens to part-related events from the workbench to monitor when text editors are
     * activated/closed, and keep the necessary listeners pointed at the active editor.
     */
    private final class EditorPartListener implements IPartListener {
        @Override
        public void partActivated(IWorkbenchPart part) {
            if (part instanceof CeylonEditor) {
                setUpActiveEditor((CeylonEditor) part);
                if (documentProvider!=null) {
                    retrieveOccurrenceAnnotations();
                    if (!markingEnabled) {
                        unregisterListeners();
                        removeExistingOccurrenceAnnotations();
                    }
                }
            }
        }
        @Override
        public void partClosed(IWorkbenchPart part) {
            if (part == activeEditor) {
                unregisterListeners();
                activeEditor = null;
                documentProvider = null;
                document = null;
                parseController = null;
                occurrenceAnnotations = null;
                DocumentationView documentationView = DocumentationView.getInstance();
                if (documentationView!=null) {
                    documentationView.update(null, -1, -1);
                }
            }
        }
        @Override
        public void partBroughtToTop(IWorkbenchPart part) { }
        @Override
        public void partDeactivated(IWorkbenchPart part) { }
        @Override
        public void partOpened(IWorkbenchPart part) { }
    }

    @Override
    public void caretMoved(CaretEvent event) {
        if (!activeEditor.isBackgroundParsingPaused() &&
                !activeEditor.isBlockSelectionModeEnabled() &&
                !activeEditor.isInLinkedMode()) {
            int offset = activeEditor.getCeylonSourceViewer()
                    .widgetOffset2ModelOffset(event.caretOffset);
            int length = 0;
            recomputeAnnotationsForSelection(offset, length, document);
        }
    }
    
    @Override
    public void selectionChanged(SelectionChangedEvent event) {
        ISelection sel = event.getSelection();
        if (sel instanceof ITextSelection &&
                !activeEditor.isBackgroundParsingPaused() &&
                !activeEditor.isBlockSelectionModeEnabled() &&
                !activeEditor.isInLinkedMode()) {
            ITextSelection selection = (ITextSelection) sel;
            recomputeAnnotationsForSelection(selection.getOffset(), 
                    selection.getLength(), document);
        }
    }

    @Override
    public void run(IAction action) {
        markingEnabled = action.isChecked();
        if (markingEnabled) {
            setUpActiveEditor((CeylonEditor) PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow().getActivePage().getActiveEditor());
        } 
        else {
            unregisterListeners();
            removeExistingOccurrenceAnnotations();
        }
    }

    private void registerListeners() {
        // getDocumentFromEditor() can return null, but register listeners
        // should only be called when there is an active editor that can
        // be presumed to have a document provider that has document
        IDocument document = getDocumentFromEditor();
        if (document!=null) {
            activeEditor.getCeylonSourceViewer().getTextWidget()
                    .addCaretListener(this);
            activeEditor.getCeylonSourceViewer().getSelectionProvider()
                    .addSelectionChangedListener(this);
        }
        activeEditor.addModelListener(this);
    }

    private void unregisterListeners() {
        if (activeEditor!=null) {
            activeEditor.getCeylonSourceViewer().getTextWidget()
                    .removeCaretListener(this);
            activeEditor.getCeylonSourceViewer().getSelectionProvider()
                    .removeSelectionChangedListener(this);
            activeEditor.removeModelListener(this);
        }
    }

    private IDocument getDocumentFromEditor() {
        IDocumentProvider provider = getDocumentProvider();
        return provider==null ? 
                null : provider.getDocument(getEditorInput());
    }

    private void recomputeAnnotationsForSelection(int offset, int length, IDocument document) {
        IAnnotationModel annotationModel = documentProvider.getAnnotationModel(getEditorInput());
        Tree.CompilationUnit root = getCompilationUnit();
        if (root == null) {
            // Get this when "selecting" an error message that is shown in the editor view
            // but is not part of the source file; just returning should leave previous
            // markings, if any, as they were (which is probably fine)
            // Also get this when the current AST is null, e.g., as in the event of
            // a parse error
            return;
        }
        Node selectedNode = Nodes.findNode(root, offset, offset+length-1);
        try {
            List<Node> declarations = getDeclarationsOf(parseController, selectedNode);
            List<Node> occurrences = getOccurrencesOf(parseController, selectedNode);
            List<Node> assignments = getAssignmentsOf(parseController, selectedNode);
            removeEditedOccurrences(document, declarations);
            removeEditedOccurrences(document, occurrences);
            removeEditedOccurrences(document, assignments);
            Map<Annotation,Position> annotationMap = new HashMap<Annotation,Position>
                    (declarations.size()+occurrences.size()+assignments.size());
            addPositionsToAnnotationMap(convertRefNodesToPositions(declarations), 
                    DECLARATION_ANNOTATION, document, annotationMap);
            addPositionsToAnnotationMap(convertRefNodesToPositions(occurrences), 
                    OCCURRENCE_ANNOTATION, document, annotationMap);
            addPositionsToAnnotationMap(convertRefNodesToPositions(assignments), 
                    ASSIGNMENT_ANNOTATION, document, annotationMap);
            placeAnnotations(annotationMap, annotationModel);
        } 
        catch (Exception e) {
            e.printStackTrace();
        }
        DocumentationView documentationView = DocumentationView.getInstance();
        if (documentationView!=null) {
            documentationView.update(activeEditor, offset, length);
        }
    }

    private static void removeEditedOccurrences(IDocument document, List<Node> occurrences)
            throws BadLocationException {
        for (Iterator<Node> i=occurrences.iterator(); i.hasNext();) {
            CommonToken tok = (CommonToken) i.next().getToken();
            try {
                String docText = document.get(tok.getStartIndex(), 
                        tok.getStopIndex()-tok.getStartIndex()+1);
                if (!docText.equals(tok.getText())) {
                    i.remove();
                }
            }
            catch (BadLocationException e) {
                i.remove();
            }
        }
    }

    private void addPositionsToAnnotationMap(Position[] positions, String type, 
            IDocument document, Map<Annotation, Position> annotationMap) {
        for(int i=0; i<positions.length; i++) {
            Position position = positions[i];
            try { // Create & add annotation
                String message= document.get(position.offset, position.length);
                annotationMap.put(new Annotation(type, false, message), position);
            } 
            catch (BadLocationException ex) {
                continue; // skip apparently bogus position
            }
        }
    }

    private void placeAnnotations(Map<Annotation,Position> annotationMap, IAnnotationModel annotationModel) {
        if (annotationModel==null || 
                (occurrenceAnnotations==null || occurrenceAnnotations.length==0) && 
                annotationMap.isEmpty()) {
            return;
        }
        synchronized (getLockObject(annotationModel)) {
            if (annotationModel instanceof IAnnotationModelExtension) {
                ((IAnnotationModelExtension) annotationModel).replaceAnnotations(occurrenceAnnotations, annotationMap);
            } 
            else {
                removeExistingOccurrenceAnnotations();
                Iterator<Map.Entry<Annotation,Position>> iter = annotationMap.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<Annotation,Position> mapEntry = iter.next();
                    annotationModel.addAnnotation((Annotation) mapEntry.getKey(), (Position) mapEntry.getValue());
                }
            }
            occurrenceAnnotations = (Annotation[]) annotationMap.keySet().toArray(new Annotation[annotationMap.keySet().size()]);
        }
    }

    private void retrieveOccurrenceAnnotations() {
        IAnnotationModel annotationModel= documentProvider.getAnnotationModel(getEditorInput());
        // Need to initialize the set of pre-existing annotations in order
        // for them to be removed properly when new occurrences are marked
        if (annotationModel != null) {
            @SuppressWarnings("unchecked")
            Iterator<Annotation> annotationIterator = annotationModel.getAnnotationIterator();
            List<Annotation> annotationList = new ArrayList<Annotation>();
            while (annotationIterator.hasNext()) {
                // SMS 23 Jul 2008:  added test for annotation type
                Annotation ann = (Annotation) annotationIterator.next();
                if (ann.getType().startsWith(DECLARATION_ANNOTATION) ||
                    ann.getType().startsWith(OCCURRENCE_ANNOTATION) ||
                    ann.getType().startsWith(ASSIGNMENT_ANNOTATION)) {
                    annotationList.add(ann);
                }
            }
            occurrenceAnnotations = annotationList.toArray(new Annotation[annotationList.size()]);
        }
    }

    void removeExistingOccurrenceAnnotations() {
        // RMF 6/27/2008 - If we've come up in an empty workspace, there won't be an active editor
        if (activeEditor == null)
            return;
        // RMF 6/27/2008 - Apparently partActivated() gets called before the editor is initialized
        // (on MacOS?), and then we can't properly initialize this MarkOccurrencesAction instance.
        // When that happens, fDocumentProvider will be null. Initialization needs a fix for that,
        // rather than this simple-minded null guard.
        if (documentProvider == null)
            return;
        IAnnotationModel annotationModel= documentProvider.getAnnotationModel(getEditorInput());
        if (annotationModel == null || occurrenceAnnotations == null)
            return;

        synchronized (getLockObject(annotationModel)) {
            if (annotationModel instanceof IAnnotationModelExtension) {
                ((IAnnotationModelExtension) annotationModel).replaceAnnotations(occurrenceAnnotations, null);
            } 
            else {
                for (int i=0, length= occurrenceAnnotations.length; i<length; i++) {
                    annotationModel.removeAnnotation(occurrenceAnnotations[i]);
                }
            }
            occurrenceAnnotations= null;
        }
    }

    private Position[] convertRefNodesToPositions(List<Node> refs) {
        Position[] positions = new Position[refs.size()];
        int i= 0;
        for (Iterator<Node> iter=refs.iterator(); iter.hasNext(); i++) {
            Node node = iter.next();
            positions[i] = new Position(Nodes.getStartOffset(node), Nodes.getLength(node));
        }
        return positions;
    }

    private Tree.CompilationUnit getCompilationUnit() {
        // Do NOT compute fCompilationUnit conditionally based
        // on the AST being null; that causes problems when switching
        // between editor windows because the old value of the AST
        // will be retained even after the new window comes up, until
        // the text in the new window is parsed.  For now just
        // get the current AST (but in the future do something more
        // sophisticated to avoid needless recomputation but only
        // when it is truly needless).
        return parseController.getRootNode();
    }

    private IEditorInput getEditorInput() {
        return activeEditor.getEditorInput();
    }

    private IDocumentProvider getDocumentProvider() {
        documentProvider = activeEditor.getDocumentProvider();
        return documentProvider;
    }

    private void setUpActiveEditor(CeylonEditor textEditor) {
        unregisterListeners();
        if (textEditor == null) {
            return;
        }
        activeEditor = textEditor;
        document = getDocumentFromEditor();
        parseController = activeEditor.getParseController();

        if (parseController == null) {
            return;
        }

        registerListeners();

        ISelection selection = activeEditor.getSelectionProvider().getSelection();
        if (selection instanceof ITextSelection) {
            ITextSelection textSelection = (ITextSelection) selection;
            recomputeAnnotationsForSelection(textSelection.getOffset(), textSelection.getLength(), document);
        }
    }

    private Object getLockObject(IAnnotationModel annotationModel) {
        if (annotationModel instanceof ISynchronizable) {
            return ((ISynchronizable) annotationModel).getLockObject();
        }
        else {
            return annotationModel;
        }
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) { }

    @Override
    public void dispose() {
        unregisterListeners();
    }

    @Override
    public void init(IWorkbenchWindow window) {
        window.getActivePage().addPartListener(new EditorPartListener());
    }

    @Override
    public void update(CeylonParseController parseController,
            IProgressMonitor monitor) {
        if (activeEditor!=null) {
//            synchronized (activeEditor) {
                if (!activeEditor.isBackgroundParsingPaused() &&
                        !activeEditor.isInLinkedMode()) {
                    try {
                        activeEditor.getEditorSite().getShell().getDisplay().asyncExec( 
                                new Runnable() {
                            @Override
                            public void run() {
                                if (activeEditor!=null) {
                                    IRegion selection = activeEditor.getSelection();
                                    recomputeAnnotationsForSelection(selection.getOffset(), 
                                            selection.getLength(), document);
                                }
                            }
                        });
                    } 
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
//            }
        }
    }

    @Override
    public Stage getStage() {
        return Stage.TYPE_ANALYSIS;
    }
    
    private List<Node> getDeclarationsOf(CeylonParseController parseController, Node node) {
        if (parseController.getStage().ordinal() >= getStage().ordinal()) {
            // Check whether we even have an AST in which to find occurrences
            Tree.CompilationUnit root = parseController.getRootNode();
            if (root == null) {
                return Collections.emptyList();
            }

            Declaration declaration = Nodes.getReferencedExplicitDeclaration(node, root);
            if (declaration==null) {
                return Collections.emptyList();
            }
            else {
                List<Node> occurrences = new ArrayList<Node>();
                FindReferencesVisitor frv = new FindReferencesVisitor(declaration);
                FindDeclarationNodeVisitor fdv = new FindDeclarationNodeVisitor(frv.getDeclaration());
                root.visit(fdv);
                Tree.Declaration decNode = fdv.getDeclarationNode();
                if (decNode!=null) {
                    occurrences.add(decNode);
                }
                return occurrences;
            }
        }
        return Collections.emptyList();
    }
    
    private List<Node> getOccurrencesOf(CeylonParseController parseController, Node node) {
        if (parseController.getStage().ordinal() >= getStage().ordinal()) {
            // Check whether we even have an AST in which to find occurrences
            Tree.CompilationUnit root = parseController.getRootNode();
            if (root == null) {
                return Collections.emptyList();
            }

            Declaration declaration = Nodes.getReferencedExplicitDeclaration(node, root);
            if (declaration==null) {
                return Collections.emptyList();
            }
            else {
                List<Node> occurrences = new ArrayList<Node>();
                FindReferencesVisitor frv = new FindReferencesVisitor(declaration);
                root.visit(frv);
                occurrences.addAll(frv.getNodes());
                return occurrences;
            }
        }
        return Collections.emptyList();
    }
    
    private List<Node> getAssignmentsOf(CeylonParseController parseController, Node node) {
        
        // Check whether we even have an AST in which to find occurrences
        Tree.CompilationUnit root = parseController.getRootNode();
        if (root == null) {
            return Collections.emptyList();
        }

        Declaration declaration = Nodes.getReferencedExplicitDeclaration(node, root);
        if (declaration==null) {
            return Collections.emptyList();
        }
        else {
            List<Node> occurrences = new ArrayList<Node>();
            FindAssignmentsVisitor frv = new FindAssignmentsVisitor(declaration);
            root.visit(frv);
            occurrences.addAll(frv.getNodes());
            return occurrences;
        }
        
    }
    
}