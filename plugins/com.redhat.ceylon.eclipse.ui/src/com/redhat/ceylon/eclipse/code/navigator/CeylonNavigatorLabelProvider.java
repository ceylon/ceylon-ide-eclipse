package com.redhat.ceylon.eclipse.code.navigator;

import static com.redhat.ceylon.eclipse.core.builder.CeylonNature.NATURE_ID;

import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot;
import org.eclipse.jdt.internal.ui.navigator.JavaNavigatorContentProvider;
import org.eclipse.jdt.internal.ui.navigator.JavaNavigatorLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.navigator.ICommonContentExtensionSite;
import org.eclipse.ui.navigator.ICommonLabelProvider;
import org.eclipse.ui.navigator.INavigatorContentExtension;

import com.redhat.ceylon.cmr.api.ArtifactContext;
import com.redhat.ceylon.cmr.api.Repository;
import com.redhat.ceylon.cmr.impl.JDKRepository;
import com.redhat.ceylon.cmr.impl.NodeUtils;
import com.redhat.ceylon.common.Constants;
import com.redhat.ceylon.common.config.Repositories;
import com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.RootFolderType;
import com.redhat.ceylon.eclipse.core.builder.CeylonProjectConfig;
import com.redhat.ceylon.eclipse.core.external.CeylonArchiveFileStore;
import com.redhat.ceylon.eclipse.core.external.ExternalSourceArchiveManager;
import com.redhat.ceylon.eclipse.core.model.JDTModule;

public class CeylonNavigatorLabelProvider extends
        CeylonLabelProvider implements ICommonLabelProvider {

    ICommonContentExtensionSite extensionSite;
    
    public CeylonNavigatorLabelProvider() {
        super(false);
    }

    @Override
    public StyledString getStyledText(Object element) {
        if (element instanceof ExternalModuleNode) {
            ExternalModuleNode externalModule = (ExternalModuleNode) element;
            JDTModule jdtModule = externalModule.getModule();
            StyledString moduleText = super.getStyledText(((ExternalModuleNode)element).getModule());
            if (jdtModule != null) {
                moduleText.append(" - " + jdtModule.getVersion());
            }
            return moduleText;
        }
        if (element instanceof SourceModuleNode) {
            JDTModule module = ((SourceModuleNode)element).getModule();
            StyledString result = super.getStyledText(module);
            if (module != null && module.isDefaultModule()) {
                result = result.insert('(', 0).append(')').append("");
            }
            return result;
        }
        if (element instanceof RepositoryNode) {
            RepositoryNode repoNode = (RepositoryNode) element;
            String stringToDisplay = getRepositoryString(repoNode);
            return new StyledString(stringToDisplay);
        }
        
        if (element instanceof Package || element instanceof IPackageFragment) {
            return new StyledString(super.getStyledText(element).getString());
        }
        
        if (element instanceof CeylonArchiveFileStore) {
            CeylonArchiveFileStore archiveFileStore = (CeylonArchiveFileStore)element;
            if (archiveFileStore.getParent() == null) {
                return new StyledString("Ceylon Sources");
            }
            return new StyledString(archiveFileStore.getName());
        }

        if (element instanceof JarPackageFragmentRoot) {
            JarPackageFragmentRoot jpfr = (JarPackageFragmentRoot) element;
            if (ArtifactContext.CAR.substring(1).equalsIgnoreCase(jpfr.getPath().getFileExtension())) {
                return new StyledString("Java Binaries");
            }
        }

        StyledString styledString = super.getStyledText(element);
        if (styledString.getString().equals("<something>")) {
            StyledString javaResult = getJavaNavigatorLabelProvider().getStyledText(element);
            if (! javaResult.getString().trim().isEmpty()) {
                return javaResult;
            }
        }
        
        return styledString;
    }

    private String getRepositoryString(RepositoryNode repoNode) {
        String displayString = repoNode.getDisplayString();
        String stringToDisplay = null;
        if (Constants.REPO_URL_CEYLON.equals(displayString)) {
            stringToDisplay = "Herd Modules";
        }
        if (stringToDisplay == null && JDKRepository.JDK_REPOSITORY_DISPLAY_STRING.equals(displayString)) {
            stringToDisplay = "JDK Modules";
        }
        if (stringToDisplay == null && CeylonBuilder.getInterpolatedCeylonSystemRepo(repoNode.project).equals(displayString)) {
            stringToDisplay = "System Modules";
        }
        if (stringToDisplay == null && CeylonBuilder.getCeylonModulesOutputDirectory(repoNode.project).getAbsolutePath().equals(displayString)) {
            stringToDisplay = "Output Modules";
        }
        if (stringToDisplay == null && CeylonProjectConfig.get(repoNode.project).getMergedRepositories().getCacheRepoDir().getAbsolutePath().equals(displayString)) {
            stringToDisplay = "Cached Modules";
        }
        if (stringToDisplay == null && CeylonProjectConfig.get(repoNode.project).getMergedRepositories().getUserRepoDir().getAbsolutePath().equals(displayString)) {
            stringToDisplay = "User Modules";
        }
        if (stringToDisplay == null) {
            try {
                for (IProject referencedProject: repoNode.project.getReferencedProjects()) {
                    if (referencedProject.isOpen() && referencedProject.hasNature(NATURE_ID)) {
                        if (CeylonBuilder.getCeylonModulesOutputDirectory(referencedProject).getAbsolutePath().equals(displayString)) {
                            stringToDisplay = "Modules of Referenced Project : " + referencedProject.getName() + "";
                            break;
                        }
                    }
                }
            } catch (CoreException e) {
            }
        }

        if (stringToDisplay == null) {
            for (Repositories.Repository repo : CeylonProjectConfig.get(repoNode.project).getMergedRepositories().getLocalLookupRepositories()) {
                if (repo.getUrl().startsWith("./") && repo.getUrl().length() > 2) {
                    IPath relativePath = Path.fromPortableString(repo.getUrl().substring(2));
                    IFolder folder = repoNode.project.getFolder(relativePath);
                    if (folder.exists() && folder.getLocation().toFile().getAbsolutePath().equals(displayString)) {
                        stringToDisplay = "Local Repository : " + relativePath.toString() + "";
                        break;
                    }
                }
            }
        }
        if (stringToDisplay == null && NodeUtils.UNKNOWN_REPOSITORY.equals(displayString)) {
            stringToDisplay = "Unknown Repository";
        }

        if (stringToDisplay == null) {
            stringToDisplay = displayString;
        }
        return stringToDisplay;
    }

    
    
    @Override
    public Image getImage(Object element) {
        JavaNavigatorLabelProvider javaProvider = getJavaNavigatorLabelProvider();

        if (element instanceof IPackageFragment &&
                ! CeylonBuilder.isInSourceFolder((IPackageFragment)element)) {
            return javaProvider.getImage(element);
        }

        if (element instanceof ExternalModuleNode) {
            return super.getImage(((ExternalModuleNode)element).getModule());
        }
        if (element instanceof SourceModuleNode) {
            int decorationAttributes = 0;
            for (Object child : getContentProvider().getChildren(element)) {
                if (!hasPipelinedChildren(child)) {
                    continue;
                }
                int childValue = getDecorationAttributes(child);
                if (childValue == ERROR) {
                    decorationAttributes = ERROR;
                    break;
                }
                if (childValue == WARNING) {
                    decorationAttributes = WARNING;
                }
            }
            
            return getDecoratedImage(getImageKey(((SourceModuleNode)element).getModule()), decorationAttributes);
        }
        
        if (element instanceof CeylonArchiveFileStore) {
            CeylonArchiveFileStore archiveFileStore = (CeylonArchiveFileStore)element;
            if (archiveFileStore.getParent() != null 
                    && ! archiveFileStore.fetchInfo().isDirectory()) {
                IFolder sourceArchiveFolder = ExternalSourceArchiveManager.getExternalSourceArchiveManager().getSourceArchive(archiveFileStore.getArchivePath());
                if (sourceArchiveFolder != null && sourceArchiveFolder.exists()) {
                    IResource file = sourceArchiveFolder.findMember(archiveFileStore.getEntryPath());
                    if (file instanceof IFile) {
                        element = file;
                    }
                }
            }
        }
        
        if (element instanceof IFile) {
            if (! CeylonBuilder.isCeylon((IFile) element)) {
                return javaProvider.getImage(element);
            }
        }

        return super.getImage(element); 
    }

    private boolean hasChildren(Object child) {
        return getContentProvider().hasChildren(child);
    }
    
    private boolean hasPipelinedChildren(Object child) {
        return getContentProvider().hasPipelinedChildren(child, getJavaNavigatorContentProvider().hasChildren(child));
    }
    
    @Override
    protected String getImageKey(Object element) {
        if (element instanceof RepositoryNode) {
            return RUNTIME_OBJ;
        }
        if (element instanceof IPackageFragment) {
            return CEYLON_PACKAGE;
        }
        if (element instanceof CeylonArchiveFileStore) {
            CeylonArchiveFileStore archiveFileStore = (CeylonArchiveFileStore)element;
            if (archiveFileStore.getParent() == null) {
                return CEYLON_SOURCE_ARCHIVE;
            } else {
                if (archiveFileStore.fetchInfo().isDirectory()) {
                    return CEYLON_PACKAGE;
                } else {
                    return CEYLON_FILE;
                }
            }
        }

        if (element instanceof JarPackageFragmentRoot) {
            return CEYLON_BINARY_ARCHIVE;
        }
        
        return super.getImageKey(element);
    }

    @Override
    public void restoreState(IMemento aMemento) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void saveState(IMemento aMemento) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String getDescription(Object anElement) {
        if (anElement instanceof RepositoryNode) {
            Repository repo = ((RepositoryNode)anElement).getRepository();
            if (repo != null) {
                return "Repository path : " + repo.getDisplayString();
            }
        }

        if (anElement instanceof CeylonArchiveFileStore) {
            CeylonArchiveFileStore archive = (CeylonArchiveFileStore)anElement;
            if (archive.getParent() == null) {
                return archive.getArchivePath().toOSString();
            }
        }

        return null;
    }

    @Override
    public void init(ICommonContentExtensionSite aConfig) {
        extensionSite = aConfig;
    }
    
    private INavigatorContentExtension getJavaNavigatorExtension() {
        @SuppressWarnings("unchecked")
        Set<INavigatorContentExtension> set = extensionSite.getService().findContentExtensionsByTriggerPoint(JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()));
        for (INavigatorContentExtension extension : set) {
            if (extension.getDescriptor().equals(extensionSite.getExtension().getDescriptor().getOverriddenDescriptor())) {
                return extension;
            }
        }
        return null;
    }
    
    private JavaNavigatorLabelProvider getJavaNavigatorLabelProvider() {
        INavigatorContentExtension javaExtension = getJavaNavigatorExtension();
        if (javaExtension != null) {
            return (JavaNavigatorLabelProvider) javaExtension.getLabelProvider();
        }
        return null;
    }

    private JavaNavigatorContentProvider getJavaNavigatorContentProvider() {
        INavigatorContentExtension javaExtension = getJavaNavigatorExtension();
        if (javaExtension != null) {
            return (JavaNavigatorContentProvider) javaExtension.getContentProvider();
        }
        return null;
    }
    
    private CeylonNavigatorContentProvider getContentProvider() {
        return (CeylonNavigatorContentProvider) extensionSite.getExtension().getContentProvider();
    }
}
