/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.redhat.ceylon.eclipse.core.cpcontainer;

import static com.redhat.ceylon.eclipse.imp.builder.CeylonBuilder.getJdtClassesEnabled;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.jdt.core.JavaCore.newLibraryEntry;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.DeltaProcessingState;
import org.eclipse.jdt.internal.core.JavaElementDelta;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerContentProvider;
import org.eclipse.swt.widgets.Display;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnits;
import com.redhat.ceylon.eclipse.core.model.loader.model.JDTModuleManager;
import com.redhat.ceylon.eclipse.imp.builder.CeylonBuilder;
import com.redhat.ceylon.eclipse.ui.CeylonPlugin;

/**
 * Eclipse classpath container that will contain the Ceylon resolved entries.
 */
public class CeylonClasspathContainer implements IClasspathContainer {

    public static final String CONTAINER_ID =
        "com.redhat.ceylon.eclipse.ui.cpcontainer.CEYLON_CONTAINER";

    private IClasspathEntry[] classpathEntries;
    private IPath path;
    private CeylonResolveJob job;
    private String jdtVersion;
    private IJavaProject javaProject;

    public IJavaProject getJavaProject() {
        return javaProject;
    }

    public IClasspathAttribute[] getAttributes() {
        return attributes;
    }

    /**
     * attributes attached to the container but not Ceylon related (Webtools or AspectJfor instance)
     */
    private IClasspathAttribute[] attributes = new IClasspathAttribute[0];

    public CeylonClasspathContainer(IJavaProject javaProject, IPath path,
            IClasspathEntry[] classpathEntries, IClasspathAttribute[] attributes) {
        this.path = path;
        this.attributes = attributes; 
        this.classpathEntries = classpathEntries;
        this.javaProject = javaProject;
    }

    public CeylonClasspathContainer(CeylonClasspathContainer cp) {
        path = cp.path;
        javaProject = cp.javaProject;        
        classpathEntries = cp.classpathEntries;
        attributes = cp.attributes;
    }

    public String getDescription() {
        return "Ceylon Modules";
    }

    public int getKind() {
        return K_APPLICATION;
    }

    public IPath getPath() {
        return path;
    }

    public IClasspathEntry[] getClasspathEntries() {
        return classpathEntries;
    }

    /*private static final ISchedulingRule RESOLVE_EVENT_RULE = new ISchedulingRule() {
        public boolean contains(ISchedulingRule rule) {
            return rule == this;
        }

        public boolean isConflicting(ISchedulingRule rule) {
            return rule == this;
        }
    };*/

    private synchronized CeylonResolveJob createResolveJob(boolean isUser) {
    	if (job != null) {
    		// resolve job already running
    		return job;
    	}
    	job = new CeylonResolveJob(this);
    	job.setUser(isUser);
    	job.setRule(getWorkspace().getRoot());
    	return job;
    }

    public IStatus runResolve(boolean isUser) {
        createResolveJob(isUser).schedule();
        return Status.OK_STATUS;
    }

    public boolean resolve(IProgressMonitor monitor) {
        try {
			return resolveClasspath(monitor);//, true);
		} 
        catch (CoreException e) {
			e.printStackTrace();
		}
        return false;
    }

    void updateClasspathEntries(final IClasspathEntry[] newEntries) {
        IClasspathEntry[] entries;
        if (newEntries != null) {
            entries = newEntries;
        } else {
            entries = new IClasspathEntry[0];
        }
        setClasspathEntries(entries);
    }

    private void setClasspathEntries(final IClasspathEntry[] entries) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                classpathEntries = entries;
                notifyUpdateClasspathEntries();
            }
        });
    }

    void notifyUpdateClasspathEntries() {
        try {
            JavaCore.setClasspathContainer(path, new IJavaProject[] {javaProject},
                new IClasspathContainer[] {new CeylonClasspathContainer(CeylonClasspathContainer.this)},
                null);

            // the following code was imported from:
            // http://svn.codehaus.org/m2eclipse/trunk/org.maven.ide.eclipse/src/org/maven/ide
            // /eclipse/embedder/BuildPathManager.java
            // revision: 370; function setClasspathContainer; line 215

            // XXX In Eclipse 3.3, changes to resolved classpath are not announced by JDT Core
            // and PackageExplorer does not properly refresh when we update Ivy
            // classpath container.
            // As a temporary workaround, send F_CLASSPATH_CHANGED notifications
            // to all PackageExplorerContentProvider instances listening to
            // java ElementChangedEvent.
            // Note that even with this hack, build clean is sometimes necessary to
            // reconcile PackageExplorer with actual classpath
            // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=154071
            if (getJDTVersion().startsWith("3.3")) {
                DeltaProcessingState s = JavaModelManager.getJavaModelManager().deltaState;
                synchronized (s) {
                    IElementChangedListener[] listeners = s.elementChangedListeners;
                    for (int i = 0; i < listeners.length; i++) {
                        if (listeners[i] instanceof PackageExplorerContentProvider) {
                            JavaElementDelta delta = new JavaElementDelta(javaProject);
                            delta.changed(IJavaElementDelta.F_CLASSPATH_CHANGED);
                            listeners[i].elementChanged(new ElementChangedEvent(delta,
                                    ElementChangedEvent.POST_CHANGE));
                        }
                    }
                }
            }
        } catch (JavaModelException e) {
            // unless there are some issues with the JDT, this should never happen
            CeylonPlugin.getInstance().logException("", e);
        }
    }

    private synchronized String getJDTVersion() {
        if (jdtVersion == null) {
            Bundle[] bundles = CeylonPlugin.getInstance().getBundleContext().getBundles();
            for (int i = 0; i < bundles.length; i++) {
                if (JavaCore.PLUGIN_ID.equals(bundles[i].getSymbolicName())) {
                    jdtVersion = (String) bundles[i].getHeaders().get(Constants.BUNDLE_VERSION);
                    break;
                }
            }
        }
        return jdtVersion;
    }

    public synchronized void resetJob() {
        job = null;
    }

	boolean resolveClasspath(IProgressMonitor monitor)//, boolean skippable)
			throws CoreException {
		IProject project = getJavaProject().getProject();
		
		//TODO: the following is terrible for two reasons:
		//      - we don't really need to parse all the 
		//        source of the whole project (just the
		//        module descriptors)
		//      - as a side effect we throw away the whole
		//        model, forcing us to have to do a full 
		//        build even if nothing interesting changed!
		CeylonBuilder.parseCeylonModel(project, monitor);
		
		TypeChecker typeChecker = CeylonBuilder.getProjectTypeChecker(project);
		if (typeChecker != null) {
			Collection<IClasspathEntry> paths = new LinkedHashSet<IClasspathEntry>();
		    PhasedUnits phasedUnits = typeChecker.getPhasedUnits();
		    JDTModuleManager moduleManager = (JDTModuleManager) phasedUnits.getModuleManager();
		    for (File archive : moduleManager.getClasspath()) {
		        if (archive.exists()) {
					try {
						Path classpathArtifact = new Path(archive.getCanonicalPath());
			            IPath srcArtifact = classpathArtifact.removeFileExtension().addFileExtension("src");
			            paths.add(newLibraryEntry(classpathArtifact, srcArtifact, null));
					} 
					catch (IOException e) {
						e.printStackTrace();
					}
		        }
		    }
		    if (getJdtClassesEnabled(project)) {
		    	IPath ceylonOutputDirectory = project.getFolder("JDTClasses").getFullPath();
		    	IPath ceylonSourceDirectory = project.getFolder("source").getFullPath();
		    	paths.add(newLibraryEntry(ceylonOutputDirectory, ceylonSourceDirectory, null, true));
		    }
		    
		    IClasspathEntry[] entries = paths.toArray(new IClasspathEntry[paths.size()]);
		    //if ( !skippable || !Arrays.equals(getClasspathEntries(), entries)) {
		    	//System.out.println(Arrays.toString(entries));
		    	//System.out.println(Arrays.toString(getClasspathEntries()));
		    	updateClasspathEntries(entries);
	            CeylonPlugin.log(IStatus.INFO, "resolved dependencies of project " + 
	            		project.getName(), null);
		    	return true;
		    //}
		    
		}
		return false;
	}
}
