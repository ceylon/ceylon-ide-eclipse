package com.redhat.ceylon.eclipse.util;

import static com.redhat.ceylon.eclipse.code.editor.MarkOccurrencesAction.ASSIGNMENT_ANNOTATION;
import static com.redhat.ceylon.eclipse.code.editor.MarkOccurrencesAction.OCCURRENCE_ANNOTATION;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.PROBLEM_MARKER_ID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.projection.AnnotationBag;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.ui.texteditor.MarkerAnnotation;

import com.redhat.ceylon.eclipse.code.editor.CeylonAnnotation;
import com.redhat.ceylon.eclipse.core.builder.MarkerCreator;

public class AnnotationUtils {
    
    public static final String SEARCH_ANNOTATION_TYPE = 
            NewSearchUI.PLUGIN_ID + ".results";

    private static Set<String> sAnnotationTypesToFilter = 
            new HashSet<String>();

    static {
        String prefix = "org.eclipse.ui.workbench.texteditor.";
        sAnnotationTypesToFilter.add(prefix + "quickdiffUnchanged");
        sAnnotationTypesToFilter.add(prefix + "quickdiffChange");
        sAnnotationTypesToFilter.add(prefix + "quickdiffAddition");
        sAnnotationTypesToFilter.add(prefix + "quickdiffDeletion");
        sAnnotationTypesToFilter.add("org.eclipse.debug.core.breakpoint");
        sAnnotationTypesToFilter.add(OCCURRENCE_ANNOTATION);
        sAnnotationTypesToFilter.add(ASSIGNMENT_ANNOTATION);
        sAnnotationTypesToFilter.add(ProjectionAnnotation.TYPE);
    }
    
    /**
     * @return true, if the given Annotation and Position are redundant, 
     * given the annotation information in the given Map
     */
    public static boolean addAndCheckDuplicateAnnotation(Map<Integer, 
            List<Object>> map, Annotation annotation, Position position) {
        List<Object> annotationsAtPosition;
        if (!map.containsKey(position.offset)) {
            annotationsAtPosition = new ArrayList<Object>();
            map.put(position.offset, annotationsAtPosition);
        }
        else {
            annotationsAtPosition = map.get(position.offset);
        }

        // TODO this should call out to a language extension point first 
        //      to see if the language can resolve duplicates
        
        // Check to see if an error code is present on the marker / annotation
        Integer errorCode = -1;
        if (annotation instanceof CeylonAnnotation) {
            errorCode = ((CeylonAnnotation) annotation).getId();
        } 
        else if (annotation instanceof MarkerAnnotation) {
            errorCode = ((MarkerAnnotation) annotation).getMarker()
                    .getAttribute(MarkerCreator.ERROR_CODE_KEY, -1);
        }
        
        // Fall back to comparing the text associated with this annotation
        if (errorCode == -1) {
            if (!annotationsAtPosition.contains(annotation.getText())) {
                annotationsAtPosition.add(annotation.getText());
                return false;
            }            
        } 
        else if (!annotationsAtPosition.contains(errorCode)) {
            annotationsAtPosition.add(errorCode);
            return false;    
        }

        return true;
    }

    /**
     * @return the list of Annotations that reside at the given 
     * line for the given ISourceViewer
     */
    public static List<Annotation> getAnnotationsForLine(final ISourceViewer viewer, 
            final int line) {
        return getAnnotations(viewer, new IPositionPredicate() {
            IDocument document = viewer.getDocument();
            public boolean matchPosition(Position p) {
                return positionIsAtLine(p, document, line);
            }
        });
    }

    /**
     * @return the list of Annotations that reside at the given 
     * offset for the given ISourceViewer
     */
    public static List<Annotation> getAnnotationsForOffset(ISourceViewer viewer, 
            final int offset) {
        return getAnnotations(viewer, new IPositionPredicate() {
            public boolean matchPosition(Position p) {
                return offset >= p.offset && 
                        offset < p.offset + p.length;
            }
        });
    }

    /**
     * @return true, if the given Position resides at the given 
     * line of the given IDocument
     */
    public static boolean positionIsAtLine(Position position, 
            IDocument document, int line) {
        int offset = position.getOffset();
        int length = position.getLength();
        if (offset > -1 && length > -1) {
            try {
                int startLine= document.getLineOfOffset(offset);
                int endLine = document.getLineOfOffset(offset+length);
                return line >= startLine && line <= endLine;
            } 
            catch (BadLocationException x) {}
        }
        return false;
    }
    
    /**
     * @return the list of Annotations on the given ISourceViewer 
     * that satisfy the given IPositionPredicate and that are worth 
     * showing to the user as text (e.g., ignoring debugger breakpoint 
     * annotations and source folding annotations)
     */
    public static List<Annotation> getAnnotations(ISourceViewer viewer, 
            IPositionPredicate posPred) {
        IAnnotationModel model = viewer.getAnnotationModel();
        if (model == null) {
            return null;
        }
        List<Annotation> annotations = new ArrayList<Annotation>();
        Iterator<?> iterator = model.getAnnotationIterator();

        Map<Integer,List<Object>> map = new HashMap<Integer,List<Object>>();

        while (iterator.hasNext()) {
            Annotation annotation = (Annotation) iterator.next();
            Position position = model.getPosition(annotation);
            
            if (annotation instanceof MarkerAnnotation) {
                try {
                    if (((MarkerAnnotation) annotation).getMarker().getType()
                            .equals(PROBLEM_MARKER_ID)) {
                        continue;
                    }
                } 
                catch (CoreException e) {
                    e.printStackTrace();
                    continue;
                }
            }
            if (position == null || 
                    !posPred.matchPosition(position)) {
                continue;
            }
            if (annotation instanceof AnnotationBag) {
                AnnotationBag bag = (AnnotationBag) annotation;
                for (Iterator<?> e = bag.iterator(); e.hasNext(); ) {
                    Annotation bagAnnotation = (Annotation) e.next();
                    position = model.getPosition(bagAnnotation);
                    if (position != null && 
                            includeAnnotation(bagAnnotation, position) && 
                            !addAndCheckDuplicateAnnotation(map, bagAnnotation, position))
                        annotations.add(bagAnnotation);

                }
            }
            else {
                if (includeAnnotation(annotation, position) && 
                        !addAndCheckDuplicateAnnotation(map, annotation, position)) {
                    annotations.add(annotation);
                }
            }
        }
        return annotations;
    }

    /**
     * Check preferences, etc., to determine whether this 
     * annotation is actually showing. (Don't want to show a 
     * hover for a non-visible annotation.)
     */
    private static boolean includeAnnotation(Annotation annotation, 
            Position position) {
        return !sAnnotationTypesToFilter.contains(annotation.getType());
    }
    
    
}

/**
 * Interface that represents a single-argument predicate taking 
 * a textual Position. Used by AnnotationUtils to detect annotations 
 * associated with a particular range or location in source text.
 * @author rfuhrer
 */
interface IPositionPredicate {
    boolean matchPosition(Position p);
}

