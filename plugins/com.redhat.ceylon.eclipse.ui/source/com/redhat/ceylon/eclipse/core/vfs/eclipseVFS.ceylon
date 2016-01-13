import com.redhat.ceylon.eclipse.core.model {
    ceylonModel,
    nativeFolderProperties
}
import com.redhat.ceylon.ide.common.model {
    CeylonProject
}
import com.redhat.ceylon.ide.common.vfs {
    FolderVirtualFile,
    ResourceVirtualFile,
    FileVirtualFile
}
import com.redhat.ceylon.ide.common.util {
    unsafeCast,
    equalsWithNulls
}
import java.io {
    InputStream
}
import java.lang {
    RuntimeException
}
import java.util {
    JList=List,
    ArrayList
}
import org.eclipse.core.resources {
    IResource,
    IFolder,
    IFile,
    IProject
}
import org.eclipse.core.runtime {
    IPath,
    CoreException
}
import com.redhat.ceylon.model.typechecker.model {
    Package
}
import java.lang.ref {
    WeakReference
}

shared class IFolderVirtualFile
        satisfies FolderVirtualFile<IProject, IResource, IFolder, IFile> {
    shared actual IFolder nativeResource;
    shared actual CeylonProject<IProject, IResource, IFolder, IFile> ceylonProject;
    shared new(IFolder nativeResource) {
        this.nativeResource = nativeResource;
        assert(exists existingCeylonProject = ceylonModel.getProject(nativeResource.project));
        ceylonProject = existingCeylonProject;
    }

    shared new fromProject(IProject project, IPath projectRelativePath) {
        this.nativeResource = project.getFolder(projectRelativePath);
        assert(exists existingCeylonProject = ceylonModel.getProject(nativeResource.project));
        ceylonProject = existingCeylonProject;
    }

    shared actual JList<out ResourceVirtualFile<IProject,IResource, IFolder, IFile>> children {
        value children = ArrayList<ResourceVirtualFile<IProject,IResource, IFolder, IFile>>();
        try {
            for (childResource in nativeResource.members().iterable) {
                assert (exists childResource);
                children.add(ceylonModel.vfs.createVirtualResource(childResource, ceylonProject));
            }
        } catch (CoreException e) {
            e.printStackTrace();
        }
        return children;
    }
    shared actual String name => nativeResource.name;
    shared actual String path => nativeResource.projectRelativePath.string;
    shared actual Boolean equals(Object that)
            => (super of FolderVirtualFile<IProject,IResource, IFolder, IFile>).equals(that);
    shared actual Integer hash
            => (super of FolderVirtualFile<IProject,IResource, IFolder, IFile>).hash;

    shared actual Boolean isSource => 
            let (root = rootFolder) 
            if (exists root)
            then
                if (root == this)
                then unsafeCast<Boolean>(nativeResource.getSessionProperty(nativeFolderProperties.rootIsSource))
                else root.isSource
            else false;
    
    shared actual FolderVirtualFile<IProject,IResource,IFolder,IFile>? rootFolder =>
            unsafeCast<WeakReference<FolderVirtualFile<IProject,IResource,IFolder,IFile>>?>(
                nativeResource.getSessionProperty(nativeFolderProperties.root))?.get();
    
    shared actual Package? ceylonPackage  =>
            unsafeCast<WeakReference<Package>?>(
                nativeResource.getSessionProperty(
                    nativeFolderProperties.packageModel))?.get();
}

shared class IFileVirtualFile
        satisfies FileVirtualFile<IProject,IResource, IFolder, IFile> {
    shared actual IFile nativeResource;
    shared actual CeylonProject<IProject, IResource, IFolder, IFile> ceylonProject;

    shared new(IFile nativeResource) {
        this.nativeResource = nativeResource;
        assert(exists existingCeylonProject = ceylonModel.getProject(nativeResource.project));
        ceylonProject = existingCeylonProject;
    }

    shared new fromProject(IProject project, IPath projectRelativePath) {
        this.nativeResource = project.getFile(projectRelativePath);
        assert(exists existingCeylonProject = ceylonModel.getProject(nativeResource.project));
        ceylonProject = existingCeylonProject;
    }

    shared actual Boolean equals(Object that)
            => (super of FileVirtualFile<IProject,IResource, IFolder, IFile>).equals(that);
    shared actual Integer hash
            => (super of FileVirtualFile<IProject,IResource, IFolder, IFile>).hash;
    shared actual InputStream inputStream {
        try {
            return nativeResource.getContents(true);
        } catch (CoreException e) {
            throw RuntimeException(e);
        }
    }
    shared actual String name => nativeResource.name;
    shared actual String path => nativeResource.projectRelativePath.string;
    shared actual String charset {
        try {
            return nativeResource.project.defaultCharset; // in the future, we could return the charset of the file
        }
        catch (Exception e) {
            throw RuntimeException(e);
        }

    }
}

