package com.redhat.ceylon.eclipse.code.navigator;

import static com.redhat.ceylon.eclipse.core.external.ExternalSourceArchiveManager.getExternalSourceArchiveManager;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.internal.resources.Resource;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder;
import com.redhat.ceylon.eclipse.core.external.CeylonArchiveFileStore;
import com.redhat.ceylon.eclipse.core.model.JDTModule;

public class ExternalModuleNode implements ModuleNode {
    private RepositoryNode repositoryNode;
    private List<IPackageFragmentRoot> binaryArchives = new ArrayList<>();
    protected String moduleSignature;
    
    public ExternalModuleNode(RepositoryNode repositoryNode, String moduleSignature) {
        this.moduleSignature = moduleSignature;
        this.repositoryNode = repositoryNode;
    }

    public List<IPackageFragmentRoot> getBinaryArchives() {
        return binaryArchives;
    }

    public CeylonArchiveFileStore getSourceArchive() {
        JDTModule module = getModule();
        if (module.isCeylonArchive()) {
            String sourcePathString = module.getSourceArchivePath();
            if (sourcePathString != null) {
                IFolder sourceArchive = getExternalSourceArchiveManager().getSourceArchive(Path.fromOSString(sourcePathString));
                if (sourceArchive != null && sourceArchive.exists()) {
                    return ((CeylonArchiveFileStore) ((Resource)sourceArchive).getStore()); 
                }
            }
        }
        return null;
    }

    public RepositoryNode getRepositoryNode() {
        return repositoryNode;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime
                * result
                + ((moduleSignature == null) ? 0 : moduleSignature
                        .hashCode());
        result = prime
                * result
                + ((repositoryNode == null) ? 0 : repositoryNode.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ExternalModuleNode other = (ExternalModuleNode) obj;
        if (moduleSignature == null) {
            if (other.moduleSignature != null)
                return false;
        } else if (!moduleSignature.equals(other.moduleSignature))
            return false;
        if (repositoryNode == null) {
            if (other.repositoryNode != null)
                return false;
        } else if (!repositoryNode.equals(other.repositoryNode))
            return false;
        return true;
    }

    @Override
    public JDTModule getModule() {
        for (JDTModule module : CeylonBuilder.getProjectExternalModules(repositoryNode.project)) {
            if (module.getSignature().equals(moduleSignature)) {
                return module;
            }
        }
        return null;
    }
}