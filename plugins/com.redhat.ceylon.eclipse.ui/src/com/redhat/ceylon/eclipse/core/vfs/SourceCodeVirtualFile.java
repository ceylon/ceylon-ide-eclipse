package com.redhat.ceylon.eclipse.core.vfs;

import static java.util.Collections.unmodifiableList;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IPath;

import com.redhat.ceylon.compiler.typechecker.io.VirtualFile;


public class SourceCodeVirtualFile implements VirtualFile {
    
    public static final List<VirtualFile> EMPTY_CHILDREN = unmodifiableList( new ArrayList<VirtualFile>(0) );
    
    private InputStream stream;
    private String path;
    private String name;
    
    public SourceCodeVirtualFile(String fileContent) {
        //TODO: this is broken for non-ASCII encodings!
        stream = new ByteArrayInputStream(fileContent.getBytes());
        path = "unknown.ceylon";
        name = "unknown.ceylon";
    }
    
    public SourceCodeVirtualFile(String fileContent, IPath path) {
        //TODO: this is broken for non-ASCII encodings!
        stream = new ByteArrayInputStream(fileContent.getBytes());
        this.path = path.toString();
        name = path.toFile().getName();
    }
    
    public boolean isFolder() {
        return false;
    }
    
    public String getName() {
        return name;
    }
    
    public String getPath() {
        return path;
    }
    
    public InputStream getInputStream() {
        return stream;
    }
    
    public List<VirtualFile> getChildren() {
        return EMPTY_CHILDREN;
    }
    
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SourceCodeVirtualFile");
        return sb.toString();
    }  
    
    @Override
    public int hashCode() {
        return getPath().hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VirtualFile) {
            return ((VirtualFile) obj).getPath().equals(getPath());
        }
        else {
            return super.equals(obj);
        }
    }
}
