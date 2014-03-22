package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.LINKED_MODE_RENAME;
import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;

import org.eclipse.jdt.internal.ui.refactoring.RefactoringExecutionHelper;
import org.eclipse.jdt.ui.refactoring.RefactoringSaveHelper;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.link.LinkedPositionGroup;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ui.editors.text.EditorsUI;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.util.Escaping;
import com.redhat.ceylon.eclipse.util.Nodes;

public final class RenameLinkedMode
        extends RefactorLinkedMode {
        
    private final RenameRefactoring refactoring;
    protected LinkedPosition namePosition;
    protected LinkedPositionGroup linkedPositionGroup;
    
    public RenameLinkedMode(CeylonEditor editor) {
        super(editor);
        this.refactoring = new RenameRefactoring(editor);
    }
    
    public static boolean useLinkedMode() {
        return EditorsUI.getPreferenceStore()
                .getBoolean(LINKED_MODE_RENAME);
    }
    
    @Override
    protected boolean canStart() {
        return refactoring.isEnabled();
    }
        
    private boolean isEnabled() {
        String newName = getNewNameFromNamePosition();
        return !getInitialName().equals(newName) &&
                newName.matches("^\\w(\\w|\\d)*$") &&
                !Escaping.KEYWORDS.contains(newName);
    }

    @Override
    public void done() {
        if (isEnabled()) {
            try {
//                hideEditorActivity();
                setName(getNewNameFromNamePosition());
                revertChanges();
                if (isShowPreview()) {
                    openPreview();
                }
                else {
                    new RefactoringExecutionHelper(refactoring,
                            RefactoringStatus.WARNING,
                            RefactoringSaveHelper.SAVE_ALL,
                            editor.getSite().getShell(),
                            editor.getSite().getWorkbenchWindow())
                        .perform(false, true);
                }
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
//            finally {
//                unhideEditorActivity();
//            }
            super.done();
        }
        else {
            super.cancel();
        }
    }

    @Override
    public String getHintTemplate() {
        return "Enter new name for " + refactoring.getCount() + 
                " occurrences of '" + getName()  + "' {0}";
    }
    
    private void addLinkedPositions(IDocument document,
            CompilationUnit rootNode, int adjust,
            LinkedPositionGroup linkedPositionGroup) 
                    throws BadLocationException {
        
        int offset = Nodes.getIdentifyingNode(refactoring.getNode()).getStartIndex();
//        namePosition = new ProposalPosition(document, offset, 
//                getOriginalName().length(), 0, 
//                LinkedModeCompletionProposal.getNameProposals(offset, 0, getOriginalName()));
        namePosition = new LinkedPosition(document, offset, 
                getInitialName().length(), 0);
        linkedPositionGroup.addPosition(namePosition);
        
        int i=1;
        for (Node node: refactoring.getNodesToRename(rootNode)) {
            Node identifyingNode = Nodes.getIdentifyingNode(node);
            try {
                linkedPositionGroup.addPosition(new LinkedPosition(document, 
                        identifyingNode.getStartIndex(), 
                        identifyingNode.getText().length(), i++));
            } 
            catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
        for (Region region: refactoring.getStringsToReplace(rootNode)) {
            try {
                linkedPositionGroup.addPosition(new LinkedPosition(document, 
                        region.getOffset(), 
                        region.getLength(), i++));
            } 
            catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected String getName() {
        return refactoring.getDeclaration().getName();
    }
    
    @Override
    protected void setName(String name) {
        refactoring.setNewName(name);
    }
    
    @Override
    protected String getActionName() {
        return PLUGIN_ID + ".action.rename";
    }
    
    @Override
    protected void openPreview() {
        new RenameRefactoringAction(editor) {
            @Override
            public Refactoring createRefactoring() {
                return RenameLinkedMode.this.refactoring;
            }
            @Override
            public RefactoringWizard createWizard(Refactoring refactoring) {
                return new RenameWizard((RenameRefactoring) refactoring) {
                    @Override
                    protected void addUserInputPages() {}
                };
            }
        }.run();
    }

    @Override
    protected void openDialog() {
        new RenameRefactoringAction(editor) {
            @Override
            public AbstractRefactoring createRefactoring() {
                return RenameLinkedMode.this.refactoring;
            }
        }.run();
    }
    
    @Override
    protected String getNewNameFromNamePosition() {
        try {
            return namePosition.getContent();
        }
        catch (BadLocationException e) {
            return getInitialName();
        }
    }

    @Override
    protected void setupLinkedPositions(final IDocument document, final int adjust)
            throws BadLocationException {
        linkedPositionGroup = new LinkedPositionGroup();
        addLinkedPositions(document, 
                editor.getParseController().getRootNode(), 
                adjust, linkedPositionGroup);
        linkedModeModel.addGroup(linkedPositionGroup);
    }

//  private Image image= null;
//  private Label label= null;
    
//    private void hideEditorActivity() {
//      if (viewer instanceof SourceViewer) {
//      final SourceViewer sourceViewer= (SourceViewer) viewer;
//      Control viewerControl= sourceViewer.getControl();
//      if (viewerControl instanceof Composite) {
//          Composite composite= (Composite) viewerControl;
//          Display display= composite.getDisplay();
//
//          // Flush pending redraw requests:
//          while (! display.isDisposed() && display.readAndDispatch()) {
//          }
//
//          // Copy editor area:
//          GC gc= new GC(composite);
//          Point size;
//          try {
//              size= composite.getSize();
//              image= new Image(gc.getDevice(), size.x, size.y);
//              gc.copyArea(image, 0, 0);
//          } finally {
//              gc.dispose();
//              gc= null;
//          }
//
//          // Persist editor area while executing refactoring:
//          label= new Label(composite, SWT.NONE);
//          label.setImage(image);
//          label.setBounds(0, 0, size.x, size.y);
//          label.moveAbove(null);
//      }
//    }
    
//    private void unhideEditorActivity() {
//        if (label != null)
//            label.dispose();
//        if (image != null)
//            image.dispose();
//    }
    
}
