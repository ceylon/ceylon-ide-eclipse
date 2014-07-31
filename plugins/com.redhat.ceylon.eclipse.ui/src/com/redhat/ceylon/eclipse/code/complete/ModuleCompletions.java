package com.redhat.ceylon.eclipse.code.complete;

import static com.redhat.ceylon.compiler.typechecker.tree.Util.formatPath;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.fullPath;
import static com.redhat.ceylon.eclipse.code.hover.DocumentationHover.getDocumentationFor;
import static com.redhat.ceylon.eclipse.code.hover.DocumentationHover.getDocumentationForModule;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getPackageName;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.MODULE;
import static com.redhat.ceylon.eclipse.util.ModuleQueries.getModuleSearchResults;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.swt.graphics.Point;

import com.redhat.ceylon.cmr.api.JDKUtils;
import com.redhat.ceylon.cmr.api.ModuleSearchResult.ModuleDetails;
import com.redhat.ceylon.cmr.api.ModuleVersionDetails;
import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;

public class ModuleCompletions {
    
    static final class ModuleDescriptorProposal extends CompletionProposal {
        
        ModuleDescriptorProposal(int offset, String prefix, String moduleName) {
            super(offset, prefix, MODULE, 
                    "module " + moduleName,
                    "module " + moduleName + " \"1.0.0\" {}");
        }

        @Override
        public Point getSelection(IDocument document) {
            return new Point(offset - prefix.length() + text.indexOf('\"')+1, 5);
        }
        
        @Override
        protected boolean qualifiedNameIsPath() {
            return true;
        }
    }

    static final class ModuleProposal extends CompletionProposal {
        private final int len;
        private final String versioned;
        private final ModuleDetails module;
        private final boolean withBody;
        private final ModuleVersionDetails version;
        private final String name;

        ModuleProposal(int offset, String prefix, int len, 
                String versioned, ModuleDetails module,
                boolean withBody, ModuleVersionDetails version, 
                String name) {
            super(offset, prefix, MODULE, versioned, 
                    versioned.substring(len));
            this.len = len;
            this.versioned = versioned;
            this.module = module;
            this.withBody = withBody;
            this.version = version;
            this.name = name;
        }

        @Override
        public Point getSelection(IDocument document) {
            final int off = offset+versioned.length()-prefix.length()-len;
            if (withBody) {
                final int verlen = version.getVersion().length();
                return new Point(off-verlen-2, verlen);
            }
            else {
                return new Point(off, 0);
            }
        }

        @Override
        public String getAdditionalProposalInfo() {
            return JDKUtils.isJDKModule(name) ?
                    getDocumentationForModule(name, JDKUtils.jdk.version,
                            "This module forms part of the Java SDK.") :
                    getDocumentationFor(module, version.getVersion());
        }
        
        @Override
        protected boolean qualifiedNameIsPath() {
            return true;
        }
    }
    
    static final class JDKModuleProposal extends CompletionProposal {
        private final String name;

        JDKModuleProposal(int offset, String prefix, int len, 
                String versioned, String name) {
            super(offset, prefix, MODULE, versioned, 
                    versioned.substring(len));
            this.name = name;
        }

        @Override
        public String getAdditionalProposalInfo() {
            return getDocumentationForModule(name, JDKUtils.jdk.version, 
                    "This module forms part of the Java SDK.");
        }
    }

    private static final SortedSet<String> JDK_MODULE_VERSION_SET = new TreeSet<String>();
    {
        JDK_MODULE_VERSION_SET.add(JDKUtils.jdk.version);
    }
    
    static void addModuleCompletions(CeylonParseController cpc, 
            int offset, String prefix, Tree.ImportPath path, Node node, 
            List<ICompletionProposal> result, boolean withBody) {
        String fullPath = fullPath(offset, prefix, path);
        addModuleCompletions(offset, prefix, node, result, fullPath.length(), 
                fullPath+prefix, cpc, withBody);
    }

    private static void addModuleCompletions(int offset, String prefix, Node node, 
            List<ICompletionProposal> result, final int len, String pfp,
            final CeylonParseController cpc, final boolean withBody) {
        if (pfp.startsWith("java.")) {
            for (String name: 
                    new TreeSet<String>(JDKUtils.getJDKModuleNames())) {
                if (name.startsWith(pfp) &&
                        !moduleAlreadyImported(cpc, name)) {
                    result.add(new JDKModuleProposal(offset, prefix, len,
                            getModuleString(withBody, name, JDKUtils.jdk.version), 
                            name));
                }
            }
        }
        else {
            final TypeChecker tc = cpc.getTypeChecker();
            if (tc!=null) {
                IProject project = cpc.getProject();
                for (ModuleDetails module: 
                        getModuleSearchResults(pfp, tc,project).getResults()) {
                    final String name = module.getName();
                    if (!name.equals(Module.DEFAULT_MODULE_NAME) && 
                            !moduleAlreadyImported(cpc, name)) {
                        for (final ModuleVersionDetails version: 
                            module.getVersions().descendingSet()) {
                            result.add(new ModuleProposal(offset, prefix, len, 
                                    getModuleString(withBody, name, version.getVersion()), 
                                    module, withBody, version, name));
                        }
                    }
                }
            }
        }
    }

    private static boolean moduleAlreadyImported(CeylonParseController cpc, final String mod) {
        if (mod.equals(Module.LANGUAGE_MODULE_NAME)) {
            return true;
        }
        List<Tree.ModuleDescriptor> md = cpc.getRootNode().getModuleDescriptors();
        if (!md.isEmpty()) {
            Tree.ImportModuleList iml = md.get(0).getImportModuleList();
            if (iml!=null) {
                for (Tree.ImportModule im: iml.getImportModules()) {
                    if (im.getImportPath()!=null) {
                        if (formatPath(im.getImportPath().getIdentifiers()).equals(mod)) {
                            return true;
                        }
                    }
                }
            }
        }
        //Disabled, because once the module is imported, it hangs around!
//        for (ModuleImport mi: node.getUnit().getPackage().getModule().getImports()) {
//            if (mi.getModule().getNameAsString().equals(mod)) {
//                return true;
//            }
//        }
        return false;
    }

    private static String getModuleString(boolean withBody, 
            String name, String version) {
        return withBody ? name + " \"" + version + "\";" : name;
    }

    static void addModuleDescriptorCompletion(CeylonParseController cpc, 
            int offset, String prefix, List<ICompletionProposal> result) {
        if (!"module".startsWith(prefix)) return; 
        IFile file = cpc.getProject().getFile(cpc.getPath());
        String moduleName = getPackageName(file);
        if (moduleName!=null) {
            result.add(new ModuleDescriptorProposal(offset, prefix, moduleName));
        }
    }
    
}
