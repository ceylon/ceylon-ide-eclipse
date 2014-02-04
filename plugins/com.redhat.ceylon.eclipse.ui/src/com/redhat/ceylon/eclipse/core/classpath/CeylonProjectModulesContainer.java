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
package com.redhat.ceylon.eclipse.core.classpath;

import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getCeylonClassesOutputFolder;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getProjectTypeChecker;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.isExplodeModulesEnabled;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.parseCeylonModel;
import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;
import static java.util.Arrays.asList;
import static java.util.Collections.synchronizedSet;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.jdt.core.JavaCore.newLibraryEntry;
import static org.eclipse.jdt.core.JavaCore.setClasspathContainer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.DeltaProcessingState;
import org.eclipse.jdt.internal.core.JavaElementDelta;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerContentProvider;

import com.redhat.ceylon.cmr.api.ArtifactContext;
import com.redhat.ceylon.cmr.api.ArtifactResultType;
import com.redhat.ceylon.cmr.api.JDKUtils;
import com.redhat.ceylon.cmr.api.RepositoryManager;
import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.context.Context;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder;
import com.redhat.ceylon.eclipse.core.model.JDTModelLoader;
import com.redhat.ceylon.eclipse.core.model.JDTModule;

/**
 * Eclipse classpath container that will contain the Ceylon resolved entries.
 */
public class CeylonProjectModulesContainer implements IClasspathContainer {

    public static final String CONTAINER_ID = PLUGIN_ID + ".cpcontainer.CEYLON_CONTAINER";

    private IClasspathEntry[] classpathEntries;
    private IPath path;
    //private String jdtVersion;
    private IJavaProject javaProject;
    
    private Set<String> modulesWithSourcesAlreadySearched = synchronizedSet(new HashSet<String>());

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

    public CeylonProjectModulesContainer(IJavaProject javaProject, IPath path,
            IClasspathEntry[] classpathEntries, IClasspathAttribute[] attributes) {
        this.path = path;
        this.attributes = attributes; 
        this.classpathEntries = classpathEntries;
        this.javaProject = javaProject;
    }

    public CeylonProjectModulesContainer(IProject project) {
		javaProject = JavaCore.create(project);
		path = new Path(CeylonProjectModulesContainer.CONTAINER_ID + "/default");
		classpathEntries = new IClasspathEntry[0];
		attributes = new IClasspathAttribute[0];
    }
    
    public CeylonProjectModulesContainer(CeylonProjectModulesContainer cp) {
        path = cp.path;
        javaProject = cp.javaProject;        
        classpathEntries = cp.classpathEntries;
        attributes = cp.attributes;
        modulesWithSourcesAlreadySearched = cp.modulesWithSourcesAlreadySearched;
    }

    public String getDescription() {
        return "Ceylon Project Modules";
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

    public IClasspathEntry addNewClasspathEntryIfNecessary(IPath modulePath) {
        synchronized (classpathEntries) {
            for (IClasspathEntry cpEntry : classpathEntries) {
                if (cpEntry.getPath().equals(modulePath)) {
                    return null;
                }
            }
            IClasspathEntry newEntry = newLibraryEntry(modulePath, null, null);
            IClasspathEntry[] newClasspathEntries = new IClasspathEntry[classpathEntries.length + 1];
            if (classpathEntries.length > 0) {
                System.arraycopy(classpathEntries, 0, newClasspathEntries, 0, classpathEntries.length);
            }
            newClasspathEntries[classpathEntries.length] = newEntry;
            classpathEntries = newClasspathEntries;
            return newEntry;
        }
    }
    
    /*private static final ISchedulingRule RESOLVE_EVENT_RULE = new ISchedulingRule() {
        public boolean contains(ISchedulingRule rule) {
            return rule == this;
        }

        public boolean isConflicting(ISchedulingRule rule) {
            return rule == this;
        }
    };*/

    public void runReconfigure() {
        modulesWithSourcesAlreadySearched.clear();
    	Job job = new Job("Resolving dependencies for project " + 
                getJavaProject().getElementName()) {
    	    @Override 
    	    protected IStatus run(IProgressMonitor monitor) {
    	    	final IProject project = javaProject.getProject();
    			IFolder explodedModulesFolder = getCeylonClassesOutputFolder(project);
	    		try {
	    			
	    			if (isExplodeModulesEnabled(project)) {
	    				if (!explodedModulesFolder.exists()) {
	    					explodedModulesFolder.create(0, true, monitor);
	    				}
	    			}
	    			else {
	    				if (explodedModulesFolder.exists()) {
	    					explodedModulesFolder.delete(true, monitor);
	    				}
	    			}
    	    		
        			final IClasspathEntry[] classpath = constructModifiedClasspath(javaProject);        			
    	            javaProject.setRawClasspath(classpath, monitor);
    	            
    	    		boolean changed = resolveClasspath(monitor, false);
    	        	if(changed) {
    	        		refreshClasspathContainer(monitor);
    	        	}
    	    		
    	            // Rebuild the project :
    	        	//   - without referenced projects
                    //   - with referencing projects
                    //   - and force the rebuild even if the model is already typechecked
    	        	
    	        	Job job = new BuildProjectAfterClasspathChangeJob("Rebuild of project " + 
    	            		project.getName(), project, false, true, true);
    	            job.setRule(project.getWorkspace().getRoot());
    	            job.schedule(3000);
    	            job.setPriority(Job.BUILD);
    				return Status.OK_STATUS;
    				
    	    	} 
    	    	catch (CoreException e) {
    	    		e.printStackTrace();
    	    		return new Status(IStatus.ERROR, PLUGIN_ID,
    	    				"could not resolve dependencies", e);
    	    	}
    	    }    		
    	};
    	job.setUser(false);
    	job.setPriority(Job.BUILD);
    	job.setRule(getWorkspace().getRoot());
        job.schedule();
    }
    
	private IClasspathEntry[] constructModifiedClasspath(IJavaProject javaProject) 
			throws JavaModelException {
		IClasspathEntry newEntry = JavaCore.newContainerEntry(path, null, 
				new IClasspathAttribute[0], false);
		IClasspathEntry[] entries = javaProject.getRawClasspath();
		List<IClasspathEntry> newEntries = new ArrayList<IClasspathEntry>(asList(entries));
		int index = 0;
		boolean mustReplace = false;
		for (IClasspathEntry entry: newEntries) {
		    if (entry.getPath().equals(newEntry.getPath()) ) {
		        mustReplace = true;
		        break;
		    }
		    index++;
		}
		if (mustReplace) {
		    newEntries.set(index, newEntry);
		}
		else {
		    newEntries.add(newEntry);
		}
		return (IClasspathEntry[]) newEntries.toArray(new IClasspathEntry[newEntries.size()]);
	}

    void notifyUpdateClasspathEntries() {
        // Changes to resolved classpath are not announced by JDT Core
		// and so PackageExplorer does not properly refresh when we update
		// the classpath container.
		// See https://bugs.eclipse.org/bugs/show_bug.cgi?id=154071
    	DeltaProcessingState s = JavaModelManager.getJavaModelManager().deltaState;
    	synchronized (s) {
    		IElementChangedListener[] listeners = s.elementChangedListeners;
    		for (int i = 0; i < listeners.length; i++) {
    			if (listeners[i] instanceof PackageExplorerContentProvider) {
    				JavaElementDelta delta = new JavaElementDelta(javaProject);
    				delta.changed(IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED);
    				listeners[i].elementChanged(new ElementChangedEvent(delta,
    						ElementChangedEvent.POST_CHANGE));
    			}
    		}
    	}
    	//I've disabled this because I don't really like having it, but
    	//it does seem to help with the issue of archives appearing
    	//empty in the package manager
    	/*try {
			javaProject.getProject().refreshLocal(IResource.DEPTH_ONE, null);
		} 
    	catch (CoreException e) {
			e.printStackTrace();
		}*/
    }

    /**
     * Resolves the classpath entries for this container.
     * @param monitor
     * @param reparse
     * @return true if the classpath was changed, false otherwise.
     */
	public boolean resolveClasspath(IProgressMonitor monitor, boolean reparse)  {
		IJavaProject javaProject = getJavaProject();
		IProject project = javaProject.getProject();

		try {

			TypeChecker typeChecker = null;
			if (!reparse) {
			    typeChecker = getProjectTypeChecker(project);
			}
			IClasspathEntry[] oldEntries = classpathEntries;
			if (typeChecker==null) {
				typeChecker = parseCeylonModel(project, monitor);
			}
			
			final Collection<IClasspathEntry> paths = findModuleArchivePaths(
					javaProject, project, typeChecker);
			if (oldEntries == null || 
			        oldEntries != classpathEntries || // entries have been added progressively during the module validation
			        !paths.equals(asList(oldEntries))) {
				this.classpathEntries = paths.toArray(new IClasspathEntry[paths.size()]);
				return true;
			}
		}
		catch (CoreException e) {
			e.printStackTrace();
		}
		return false;
		
	}

	public void refreshClasspathContainer(IProgressMonitor monitor) throws JavaModelException {
	    IJavaProject javaProject = getJavaProject();
		setClasspathContainer(path, new IJavaProject[] { javaProject },
				new IClasspathContainer[] {new CeylonProjectModulesContainer(this)}, new SubProgressMonitor(monitor, 1));
		JDTModelLoader modelLoader = CeylonBuilder.getProjectModelLoader(javaProject.getProject());
		if (modelLoader != null) {
		    modelLoader.refreshNameEnvironment();
		}
		//update the package manager UI
		new Job("update package manager") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				notifyUpdateClasspathEntries();
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	private Collection<IClasspathEntry> findModuleArchivePaths(
			IJavaProject javaProject, IProject project, TypeChecker typeChecker) 
					throws JavaModelException, CoreException {
		final Map<String, IClasspathEntry> paths = new TreeMap<String, IClasspathEntry>();

		Context context = typeChecker.getContext();
		RepositoryManager provider = context.getRepositoryManager();
		Set<Module> modulesToAdd = context.getModules().getListOfModules();
		//modulesToAdd.add(projectModules.getLanguageModule());        
		for (Module module: modulesToAdd) {
		    JDTModule jdtModule = (JDTModule) module;
		    String name = module.getNameAsString(); 
			if (name.equals(Module.DEFAULT_MODULE_NAME) ||
					JDKUtils.isJDKModule(name) ||
					JDKUtils.isOracleJDKModule(name) ||
                    module.equals(module.getLanguageModule()) ||
					isProjectModule(javaProject, module)) {
				continue;
			}
			IPath modulePath = getModuleArchive(provider, jdtModule);
			if (modulePath!=null) {
				IPath srcPath = null;
                
				for (IProject p: project.getReferencedProjects()) {
					if (p.isAccessible()
							&& p.getLocation().isPrefixOf(modulePath)) {
						//the module belongs to a referenced
						//project, so use the project source
						srcPath = p.getLocation();
						break;
					}
				}
                
                if (srcPath==null) {
                    for (IClasspathEntry entry : classpathEntries) {
                        if (entry.getPath().equals(modulePath)) {
                            srcPath = entry.getSourceAttachmentPath();
                            break;
                        }
                    }
                }

				if (srcPath==null && 
						!modulesWithSourcesAlreadySearched.contains(module.toString())) {
					//otherwise, use the src archive
					srcPath = getSourceArchive(provider, jdtModule);
				}
                modulesWithSourcesAlreadySearched.add(module.toString());
                IClasspathEntry newEntry = newLibraryEntry(modulePath, srcPath, null);
				paths.put(newEntry.toString(), newEntry);
                if (srcPath!=null) {
                	IClasspathEntry newSourceEntry = newLibraryEntry(srcPath, srcPath, null);
                	paths.put(newSourceEntry.toString(), newSourceEntry);
                }
				//}

			}
			else {
			    // FIXME: ideally we should find the module.java file and put the marker there, but
			    // I've no idea how to find it and which import is the cause of the import problem
			    // as it could be transitive
			    IMarker marker = project.createMarker(IJavaModelMarker.BUILDPATH_PROBLEM_MARKER);
			    marker.setAttribute(IMarker.MESSAGE, "no module archive found for classpath container: " + 
			            module.getNameAsString() + "/" + module.getVersion());
			    marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
			    marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			}
		}

		if (isExplodeModulesEnabled(project)) {
		    IClasspathEntry newEntry = newLibraryEntry(getCeylonClassesOutputFolder(project).getFullPath(), 
                    project.getFullPath(), null, false);
			paths.put(newEntry.toString(), newEntry);
		}
		
		return asList(paths.values().toArray(new IClasspathEntry[paths.size()]));
	}

	public static File getSourceArtifact(RepositoryManager provider,
			JDTModule module) {
	    String sourceArchivePath = module.getSourceArchivePath(); 
	    if (sourceArchivePath == null) {
	        return null;
	    }
	    
	    File sourceArchive = new File(sourceArchivePath);
	    if (sourceArchive.exists()) {
	        return sourceArchive; 
	    }
	    
	    // BEWARE : here the request to the provider is done in 2 steps, because if
	    // we do this in a single step, the Aether repo might return the .jar
	    // archive as a default result when not finding it with the .src extension.
	    // In this case it will not try the second extension (-sources.jar).
        String suffix = module.getArtifactType().equals(ArtifactResultType.MAVEN) ? 
        		ArtifactContext.MAVEN_SRC : ArtifactContext.SRC; 
	    ArtifactContext ctx = new ArtifactContext(module.getNameAsString(), 
        		module.getVersion(), suffix);
		File srcArtifact = provider.getArtifact(ctx);
		if (srcArtifact!=null) {
		    if (srcArtifact.getPath().endsWith(suffix)) {
	            return srcArtifact;
		    }
		}
		return null;
	}

    public static IPath getSourceArchive(RepositoryManager provider,
            JDTModule module) {
        File srcArtifact = getSourceArtifact(provider, module);
        if (srcArtifact!=null) {
            return new Path(srcArtifact.getPath());
        }
        return null;
    }

	public static File getModuleArtifact(RepositoryManager provider,
			JDTModule module) {
	    File moduleFile = module.getArtifact();
	    if (moduleFile == null) {
	        return null;
	    }
	    if (moduleFile.exists()) {
	        return moduleFile;
	    }
	    // Shouldn't need to execute this anymore ! 
	    // We already retrieved this information during in the ModuleVisitor.
	    // This should be a performance gain.
        ArtifactContext ctx = new ArtifactContext(module.getNameAsString(), 
                module.getVersion(), ArtifactContext.CAR);
        // try first with .car
        File moduleArtifact = provider.getArtifact(ctx);
        if (moduleArtifact==null){
            // try with .jar
            ctx = new ArtifactContext(module.getNameAsString(), 
                    module.getVersion(), ArtifactContext.JAR);
            moduleArtifact = provider.getArtifact(ctx);
        }
		return moduleArtifact;
	}

	public static IPath getModuleArchive(RepositoryManager provider,
            JDTModule module) {
        File moduleArtifact = getModuleArtifact(provider, module);
        if (moduleArtifact!=null) {
            return new Path(moduleArtifact.getPath());
        }
        return null;
    }

	public static boolean isProjectModule(IJavaProject javaProject, Module module)
			throws JavaModelException {
		boolean isSource=false;
		for (IPackageFragmentRoot s: javaProject.getPackageFragmentRoots()) {
			if (s.getKind()==IPackageFragmentRoot.K_SOURCE &&
			    s.getPackageFragment(module.getNameAsString()).exists()) {
				isSource=true;
				break;
			}
		}
		return isSource;
	}
}
