package com.redhat.ceylon.eclipse.core.typechecker;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;

import org.antlr.runtime.CommonToken;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;

import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.analyzer.ModuleManager;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnits;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder;
import com.redhat.ceylon.eclipse.core.model.JDTModule;
import com.redhat.ceylon.eclipse.core.model.ProjectSourceFile;
import com.redhat.ceylon.eclipse.core.vfs.ResourceVirtualFile;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Modules;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Unit;

public class ProjectPhasedUnit extends IdePhasedUnit {
    private IFolder sourceFolderResource;
    private WeakHashMap<EditedPhasedUnit, String> workingCopies = new WeakHashMap<EditedPhasedUnit, String>();
    
    public ProjectPhasedUnit(ResourceVirtualFile unitFile, ResourceVirtualFile srcDir,
            CompilationUnit cu, Package p, ModuleManager moduleManager,
            TypeChecker typeChecker, List<CommonToken> tokenStream) {
        super(unitFile, srcDir, cu, p, moduleManager, typeChecker, tokenStream);
        sourceFolderResource = (IFolder) srcDir.getResource();
        srcDir.getResource().getProject();
    }
    
    public ProjectPhasedUnit(PhasedUnit other) {
        super(other);
    }

    public IFile getSourceFileResource() {
        return (IFile) ((ResourceVirtualFile) getUnitFile()).getResource();
    }
    

    public IFolder getSourceFolderResource() {
        return sourceFolderResource;
    }
    

    public IProject getProjectResource() {
        return sourceFolderResource.getProject();
    }

    @Override
    protected Unit newUnit() {
        return new ProjectSourceFile(this);
    }
    
    public void addWorkingCopy(EditedPhasedUnit workingCopy) {
        synchronized (workingCopies) {
            String fullPath = workingCopy.getUnit() != null ? workingCopy.getUnit().getFullPath() : null;
            Iterator<String> itr = workingCopies.values().iterator();
            while (itr.hasNext()) {
                String workingCopyPath = itr.next();
                if (workingCopyPath.equals(fullPath)) {
                    itr.remove();
                }
            }
            workingCopies.put(workingCopy, fullPath);
        }
    }
    
    public Iterator<EditedPhasedUnit> getWorkingCopies() {
        return workingCopies.keySet().iterator();
    }

    private List<JDTModule> getReferencingModules() {
        List<JDTModule> result = new ArrayList<>();
        Module currentModule = getUnit().getPackage().getModule();        
        for(IProject referencingProject : getProjectResource().getReferencingProjects()) {
            JDTModule referencingSourceModule = null;
            Modules referencingProjectModules = CeylonBuilder.getProjectModules(referencingProject);
            if (referencingProjectModules != null) {
                for (Module m : referencingProjectModules.getListOfModules()) {
                    if (m.getNameAsString().equals(currentModule.getNameAsString())) {
                        assert(m instanceof JDTModule);
                        referencingSourceModule = (JDTModule) m;
                        break;
                    }
                }
            }
            if (referencingSourceModule != null) {
                result.add(referencingSourceModule);
            }
        }
        return result;
    }

    public void install() {
        TypeChecker typechecker = getTypeChecker();
        if (typechecker == null) {
            return;
        }
        PhasedUnits phasedUnits = typechecker.getPhasedUnits();
        ProjectPhasedUnit oldPhasedUnit = (ProjectPhasedUnit) phasedUnits.getPhasedUnitFromRelativePath(getPathRelativeToSrcDir());
        if (oldPhasedUnit == this) {
            return; // Nothing to do : the PhasedUnit is already installed in the typechecker
        }
        if (oldPhasedUnit != null) {
            getUnit().getDependentsOf().addAll(oldPhasedUnit.getUnit().getDependentsOf());
            Iterator<EditedPhasedUnit> workingCopies = oldPhasedUnit.getWorkingCopies(); 
            while (workingCopies.hasNext()) {
                addWorkingCopy(workingCopies.next());
            }
            
            oldPhasedUnit.remove();
            
            // pour les ICrossProjectReference, le but c'est d'enlever ce qu'il y avait (binaires ou source) 
            // Ensuite pour les éléments nouveaux , dans le cas binaire il seront normalement trouvés si le 
            // classpath est normalement remis à jour, et pour les éléments source, on parcourt tous les projets
            // 
        }
        
        phasedUnits.addPhasedUnit(getUnitFile(), this);
        for (JDTModule referencingModule : getReferencingModules()) {
            referencingModule.addedOriginalUnit(getPathRelativeToSrcDir());
        }
        
        // Pour tous les projets dépendants, on appelle addPhasedUnit () sur le module correspondant, qui doit être un module source externe
        // Attention : penser à ajouter une étape de retypecheck des modules dépendants à la compil incrémentale. De toute manière ceux qui sont déjà faits ne seront pas refaits.  
    }

    public void remove() {
        TypeChecker typechecker = getTypeChecker();
        if (typechecker == null) {
            return;
        }
        PhasedUnits phasedUnits = typechecker.getPhasedUnits();
        phasedUnits.removePhasedUnitForRelativePath(getPathRelativeToSrcDir()); // remove also the ProjectSourceFile (unit) from the Package
        for (JDTModule referencingModule : getReferencingModules()) {
            referencingModule.removedOriginalUnit(getPathRelativeToSrcDir());
        }
    }
}
