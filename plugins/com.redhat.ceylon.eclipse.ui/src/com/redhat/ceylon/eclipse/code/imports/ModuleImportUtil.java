package com.redhat.ceylon.eclipse.code.imports;

import static com.redhat.ceylon.compiler.typechecker.tree.Util.formatPath;
import static com.redhat.ceylon.eclipse.code.editor.Navigation.getNodePath;
import static com.redhat.ceylon.eclipse.code.editor.Navigation.gotoLocation;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getFile;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getUnits;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultIndent;
import static java.util.Collections.singletonMap;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ImportModule;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ImportModuleList;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ImportPath;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ModuleDescriptor;
import com.redhat.ceylon.eclipse.util.EditorUtil;
import com.redhat.ceylon.eclipse.util.Indents;
import com.redhat.ceylon.eclipse.util.Nodes;

public class ModuleImportUtil {

    public static void exportModuleImports(IProject project, 
            Module target, String moduleName) {
        PhasedUnit unit = findPhasedUnit(project, target);
        exportModuleImports(getFile(unit), 
                unit.getCompilationUnit(), 
                moduleName);
    }

    public static void removeModuleImports(IProject project, 
            Module target, List<String> moduleNames) {
        if (moduleNames.isEmpty()) return;
        PhasedUnit unit = findPhasedUnit(project, target);
        removeModuleImports(getFile(unit), 
                unit.getCompilationUnit(), 
                moduleNames);
    }

    public static void exportModuleImports(IFile file, CompilationUnit cu, 
            String moduleName) {
        TextFileChange textFileChange = 
                new TextFileChange("Export Module Imports", file);
        textFileChange.setEdit(new MultiTextEdit());
        InsertEdit edit = createExportEdit(cu, moduleName);
        if (edit!=null) {
            textFileChange.addEdit(edit);
        }
        EditorUtil.performChange(textFileChange);
    }
    
    public static void removeModuleImports(IFile file, CompilationUnit cu, 
            List<String> moduleNames) {
        TextFileChange textFileChange = 
                new TextFileChange("Remove Module Imports", file);
        textFileChange.setEdit(new MultiTextEdit());
        for (String moduleName: moduleNames) {
            DeleteEdit edit = createRemoveEdit(cu, moduleName);
            if (edit!=null) {
                textFileChange.addEdit(edit);
            }
        }
        EditorUtil.performChange(textFileChange);
    }
    
    public static void addModuleImport(IProject project, Module target, 
            String moduleName, String moduleVersion) {
        int offset = addModuleImports(project, target, 
                singletonMap(moduleName, moduleVersion));
        IPath path = getNodePath(findPhasedUnit(project, target)
                .getCompilationUnit(), project);
        gotoLocation(path, 
                offset + moduleName.length() + 
                        getDefaultIndent().length() + 10, 
                moduleVersion.length());
    }
    
    public static void makeModuleImportShared(IProject project, Module target, 
            String[] moduleNames) {
        PhasedUnit unit = findPhasedUnit(project, target);
        TextFileChange textFileChange = 
                new TextFileChange("Make Module Import Shared", 
                        getFile(unit));
        textFileChange.setEdit(new MultiTextEdit());
        Tree.CompilationUnit compilationUnit = unit.getCompilationUnit();
        IDocument doc = EditorUtil.getDocument(textFileChange);
        for (String moduleName: moduleNames) {
            for (Tree.ImportModule im: compilationUnit.getModuleDescriptors().get(0)
                    .getImportModuleList().getImportModules()) {
                String importedName = im.getImportPath().getModel().getNameAsString();
                if (importedName.equals(moduleName)) {
                    if (!removeSharedAnnotation(textFileChange, doc, im.getAnnotationList())) {
                        textFileChange.addEdit(new InsertEdit(im.getStartIndex(), 
                                "shared "));
                    }
                }
            }
        }
        EditorUtil.performChange(textFileChange);
    }

    public static boolean removeSharedAnnotation(TextFileChange textFileChange,
            IDocument doc, Tree.AnnotationList al) {
        boolean result = false;
        for (Tree.Annotation a: al.getAnnotations()) {
            if (((Tree.BaseMemberExpression) a.getPrimary()).getDeclaration()
                    .getName().equals("shared")) {
                int stop = a.getStopIndex()+1;
                int start = a.getStartIndex();
                try {
                    while (Character.isWhitespace(doc.getChar(stop))) {
                        stop++;
                    }
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
                textFileChange.addEdit(new DeleteEdit(start, stop-start));
                result = true;
            }
        }
        return result;
    }
    
    public static int addModuleImports(IProject project, Module target, 
            Map<String,String> moduleNamesAndVersions) {
        if (moduleNamesAndVersions.isEmpty()) return 0;
        PhasedUnit unit = findPhasedUnit(project, target);
        return addModuleImports(getFile(unit), 
                unit.getCompilationUnit(), 
                moduleNamesAndVersions);
    }

    public static int addModuleImports(IFile file, CompilationUnit cu, 
            Map<String, String> moduleNamesAndVersions) {
        TextFileChange textFileChange = 
                new TextFileChange("Add Module Imports", file);
        textFileChange.setEdit(new MultiTextEdit());
        for (Map.Entry<String, String> entry: 
            moduleNamesAndVersions.entrySet()) {
            InsertEdit edit = createAddEdit(cu, entry.getKey(), 
                    entry.getValue(), 
                    EditorUtil.getDocument(textFileChange));
            if (edit!=null) {
                textFileChange.addEdit(edit);
            }
        }
        EditorUtil.performChange(textFileChange);
        return textFileChange.getEdit().getOffset();
    }

    private static PhasedUnit findPhasedUnit(IProject project, 
            Module module) {
        Unit unit = module.getUnit();
        if (unit != null) {
            String moduleFullPath = unit.getFullPath();
            List<PhasedUnit> phasedUnits = getUnits(project);
            for (PhasedUnit phasedUnit: phasedUnits) {
                if (phasedUnit.getUnit().getFullPath()
                        .equals(moduleFullPath)) {
                    return phasedUnit;
                }
            }
        }
        return null;
    }

    public static PhasedUnit findPhasedUnit(IProject project, 
            Package pkg) {
        Unit unit = pkg.getUnit();
        if (unit != null) {
            String moduleFullPath = unit.getFullPath();
            List<PhasedUnit> phasedUnits = getUnits(project);
            for (PhasedUnit phasedUnit: phasedUnits) {
                if (phasedUnit.getUnit().getFullPath()
                        .equals(moduleFullPath)) {
                    return phasedUnit;
                }
            }
        }
        return null;
    }

    private static InsertEdit createAddEdit(CompilationUnit unit, 
            String moduleName, String moduleVersion, IDocument doc) {
        ImportModuleList iml = getImportList(unit);    
        if (iml==null) return null;
        int offset;
        if (iml.getImportModules().isEmpty()) {
            offset = iml.getStartIndex() + 1;
        }
        else {
            offset = iml.getImportModules()
                    .get(iml.getImportModules().size() - 1)
                    .getStopIndex() + 1;
        }
        String newline = Indents.getDefaultLineDelimiter(doc);
        StringBuilder importModule = new StringBuilder();
        appendImportStatement(importModule, false,
                moduleName, moduleVersion, newline);
        if (iml.getEndToken().getLine()==iml.getToken().getLine()) {
            importModule.append(newline);
        }
        return new InsertEdit(offset, importModule.toString());
    }

    public static void appendImportStatement(StringBuilder importModule,
            boolean shared, String moduleName, String moduleVersion, 
            String newline) {
        importModule.append(newline).append(getDefaultIndent());
        if (shared) {
            importModule.append("shared ");
        }
        importModule.append("import ");
        if (!moduleName.matches("^[a-z_]\\w*(\\.[a-z_]\\w*)*$")) {
            importModule.append('"').append(moduleName).append('"');
        }
        else {
            importModule.append(moduleName);
        }
        importModule.append(" \"").append(moduleVersion).append("\";");
    }

    private static DeleteEdit createRemoveEdit(CompilationUnit unit, 
            String moduleName) {
        ImportModuleList iml = getImportList(unit);    
        if (iml==null) return null;
        ImportModule prev = null;
        for (ImportModule im: iml.getImportModules()) {
            ImportPath ip = im.getImportPath();
            if (ip!=null && 
                    formatPath(ip.getIdentifiers()).equals(moduleName)) {
                int startOffset = Nodes.getNodeStartOffset(im);
                int length = Nodes.getNodeLength(im);
                //TODO: handle whitespace for first import in list
                if (prev!=null) {
                    int endOffset = Nodes.getNodeEndOffset(prev);
                    length += startOffset-endOffset;
                    startOffset = endOffset;
                }
                return new DeleteEdit(startOffset, length);
            }
            prev = im;
        }
        return null;
    }

    private static InsertEdit createExportEdit(CompilationUnit unit, 
            String moduleName) {
        ImportModuleList iml = getImportList(unit);    
        if (iml==null) return null;
        for (ImportModule im: iml.getImportModules()) {
            ImportPath ip = im.getImportPath();
            if (ip!=null && 
                    formatPath(ip.getIdentifiers()).equals(moduleName)) {
                int startOffset = Nodes.getNodeStartOffset(im);
                return new InsertEdit(startOffset, "shared ");
            }
        }
        return null;
    }

    private static ImportModuleList getImportList(CompilationUnit unit) {
        List<ModuleDescriptor> moduleDescriptors = unit.getModuleDescriptors();
        if (!moduleDescriptors.isEmpty()) {
            return moduleDescriptors.get(0).getImportModuleList();
        }
        else {
            return null;
        }
    }

}