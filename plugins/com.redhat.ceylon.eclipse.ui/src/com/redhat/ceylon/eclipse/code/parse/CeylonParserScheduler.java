package com.redhat.ceylon.eclipse.code.parse;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ProgressMonitorWrapper;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IDocument;

import com.redhat.ceylon.eclipse.code.editor.AnnotationCreator;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewer;
import com.redhat.ceylon.eclipse.code.parse.TreeLifecycleListener.Stage;

public class CeylonParserScheduler extends Job {

    private boolean canceling = false;
    
    private CeylonParseController parseController;
    private CeylonEditor editor;
    private AnnotationCreator annotationCreator;
    
    private final List<TreeLifecycleListener> listeners = new ArrayList<TreeLifecycleListener>();

    public CeylonParserScheduler(CeylonParseController parseController,
            CeylonEditor editor, AnnotationCreator annotationCreator) {
        super("Parsing and typechecking " + editor.getEditorInput().getName());
        setSystem(true); //do not show this job in the Progress view
        setPriority(SHORT);
        setRule(new ISchedulingRule() {            
            @Override
            public boolean isConflicting(ISchedulingRule rule) {
                return rule==this;
            }
            @Override
            public boolean contains(ISchedulingRule rule) {
                return rule==this;
            }
        });
        
        // Note: The parse controller is now initialized before  
        // it gets handed to us here, since some other services  
        // may actually depend on that.
        this.parseController = parseController;
        this.editor = editor;
        this.annotationCreator = annotationCreator;
    }

    @Override
    protected void canceling() {
        canceling = true;
    }

    public boolean isCanceling() {
        return canceling;
    }
    
    public class Stager {
        void afterStage(Stage stage, IProgressMonitor monitor) {
            notifyModelListeners(stage, monitor);
        }
    }

    private boolean sourceStillExists() {
        IProject project= parseController.getProject();
        if (project==null) {
            return true; // this wasn't a workspace resource to begin with
        }
        if (!project.exists()) {
            return false;
        }
        IFile file= project.getFile(parseController.getPath());
        return file.exists();
    }
    
    @Override
    public IStatus run(IProgressMonitor monitor) {
        try {
            if (canceling) {
                if (monitor!=null) {
                    monitor.setCanceled(true);
                }
                return Status.CANCEL_STATUS;
            }
            if (editor.isBackgroundParsingPaused()) {
                return Status.OK_STATUS;
            }

            IProgressMonitor wrappedMonitor = new ProgressMonitorWrapper(monitor) {
                @Override
                public boolean isCanceled() {
                    boolean isCanceled = false;
                    if (Job.getJobManager().currentJob() == CeylonParserScheduler.this) {
                        isCanceled = canceling;
                    }
                    return isCanceled || super.isCanceled();
                }
            };
            
            try {
                CeylonSourceViewer csv = editor.getCeylonSourceViewer();
                IDocument document = csv==null ? null : csv.getDocument();
                // If we're editing a workspace resource, check   
                // to make sure that it still exists
                if (document==null || !sourceStillExists()) {
                    return Status.OK_STATUS;
                }

                //TODO: is this a better way to clear existing
                //      annotations/markers:
                //fMsgHandler.clearMessages();
                // don't bother to retrieve the AST; we don't 
                // need it; just make sure the document gets 
                // parsed
                parseController.parse(document, wrappedMonitor, new Stager());
                if (wrappedMonitor.isCanceled() || 
                        editor.isBackgroundParsingPaused()) {
                    annotationCreator.clearMessages();
                }
                else {
                    annotationCreator.updateAnnotations();
                }
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
            return wrappedMonitor.isCanceled() ? //&& sourceStillExists()
                    Status.OK_STATUS : 
                    Status.CANCEL_STATUS;
        }
        finally {
            canceling = false;
        }
    }

    public void addModelListener(TreeLifecycleListener listener) {
        listeners.add(listener);
    }

    public void removeModelListener(TreeLifecycleListener listener) {
        listeners.remove(listener);
    }
    
    public void dispose() {
        listeners.clear();
    }

    private synchronized void notifyModelListeners(Stage stage, IProgressMonitor monitor) {
        if (parseController!=null) {
            for (TreeLifecycleListener listener: new ArrayList<TreeLifecycleListener>(listeners)) {
                if (editor.isBackgroundParsingPaused() || 
                        monitor.isCanceled()) {
                    break;
                }
                if (listener.getStage()==stage) {
                    listener.update(parseController, monitor);
                }
            }
        }
    }
}
