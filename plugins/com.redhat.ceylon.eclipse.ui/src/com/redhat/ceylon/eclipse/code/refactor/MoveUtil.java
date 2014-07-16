package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.code.correct.CorrectionUtil.getDocument;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.applyImports;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.isImported;
import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.getFile;
import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.getSelectedNode;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getUnits;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.util.Nodes.getNodeEndOffset;
import static com.redhat.ceylon.eclipse.util.Nodes.getNodeLength;
import static com.redhat.ceylon.eclipse.util.Nodes.getNodeStartOffset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Referenceable;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ImportMemberOrType;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.editor.EditorUtil;
import com.redhat.ceylon.eclipse.core.vfs.IFileVirtualFile;

public class MoveUtil {

    public static int addImportEdits(Tree.Declaration node, TextChange fc,
            IDocument doc, final Tree.CompilationUnit ncu, 
            final Set<String> packages, Declaration declaration) {
        Package p = ncu.getUnit().getPackage();
        Map<Declaration, String> imports = 
                getImports(node, p.getNameAsString(), ncu, packages);
        return applyImports(fc, imports, ncu, doc, declaration);
    }

    public static Map<Declaration,String> getImports(Tree.Declaration node,
            final String packageName, final Tree.CompilationUnit ncu,
            final Set<String> packages) {
        final Map<Declaration, String> imports = 
                new HashMap<Declaration, String>();
        node.visit(new Visitor() {
            private void add(Declaration d, Tree.Identifier id) {
                if (d!=null && id!=null) {
                    String pn = d.getUnit().getPackage().getNameAsString();
                    if (d.isToplevel() &&
                            !pn.equals(packageName) &&
                            !pn.equals(Module.LANGUAGE_MODULE_NAME) &&
                            (ncu==null || !isImported(d, ncu))) {
                        imports.put(d, id.getText());
                        packages.add(pn);
                    }
                }
            }
            @Override
            public void visit(Tree.BaseType that) {
                super.visit(that);
                add(that.getDeclarationModel(), that.getIdentifier());
            }
            @Override
            public void visit(Tree.BaseMemberOrTypeExpression that) {
                super.visit(that);
                add(that.getDeclaration(), that.getIdentifier());
            }
            @Override
            public void visit(Tree.MemberLiteral that) {
                add(that.getDeclaration(), that.getIdentifier());
            }
        });
        return imports;
    }
    
    public static String getImportText(Tree.Declaration node, 
            String targetPackage, String delim) {
        HashSet<String> packages = new HashSet<String>();
        Map<Declaration, String> imports = 
                getImports(node, targetPackage, null, packages);
        StringBuilder sb = new StringBuilder();
        for (String p: packages) {
            sb.append("import ").append(p).append(" { ");
            boolean first = true;
            for (Map.Entry<Declaration, String> e: imports.entrySet()) {
                Declaration d = e.getKey();
                String pn = d.getUnit().getPackage()
                        .getQualifiedNameString();
                if (pn.equals(p)) {
                    String name = d.getName();
                    String alias = e.getValue();
                    if (!first) sb.append(", ");
                    if (!name.equals(alias)) {
                        sb.append(alias).append("=");
                    }
                    sb.append(name);
                    first = false;
                }
            }
            sb.append(" }").append(delim);
        }
        return sb.toString();
    }

    public static void refactorProjectImports(Tree.Declaration node,
            IFile originalFile, IFile targetFile, CompositeChange change, 
            String originalPackage, String targetPackage) {
        if (!originalPackage.equals(targetPackage)) {
            for (PhasedUnit pu: getAllUnits(originalFile.getProject())) {
//                if (!node.getUnit().equals(pu.getUnit())) {
                    IFile file = ((IFileVirtualFile) pu.getUnitFile()).getFile();
                    if (!file.equals(originalFile) && !file.equals(targetFile)) {
                        TextFileChange tfc = new TextFileChange("Fix Import", file);
                        tfc.setEdit(new MultiTextEdit());
                        refactorImports(node, tfc, originalPackage, targetPackage, 
                                pu.getCompilationUnit());
                        if (tfc.getEdit().hasChildren()) {
                            change.add(tfc);
                        }
                    }
//                }
            }
        }
    }

    public static void refactorImports(Tree.Declaration node, TextChange tc, 
            String originalPackage, String targetPackage,
            Tree.CompilationUnit cu) {
        Declaration dec = node.getDeclarationModel();
        String pn = cu.getUnit().getPackage().getNameAsString();
        boolean inOriginalPackage = pn.equals(originalPackage);
        boolean inNewPackage = pn.equals(targetPackage);
        boolean foundOriginal = 
                removeImport(originalPackage, dec, cu, tc, 
                        Collections.<String>emptySet());
        if (foundOriginal && !inNewPackage || 
                inOriginalPackage && !inNewPackage && 
                        isUsedInUnit(cu, dec)) {
            addImport(targetPackage, dec, cu, tc);
        }
    }

    public static boolean isUsedInUnit(Tree.CompilationUnit rootNode, 
            final Declaration dec) {
        class DetectUsageVisitor extends Visitor {
            boolean detected;
            @Override
            public void visit(Tree.Declaration that) {
                if (!that.getDeclarationModel().equals(dec)) {
                    super.visit(that);
                }
            }
            @Override
            public void visit(Tree.BaseType that) {
                TypeDeclaration d = that.getDeclarationModel();
                if (d!=null && d.equals(dec)) {
                    detected = true;
                }
            }
            @Override
            public void visit(Tree.BaseMemberOrTypeExpression that) {
                Declaration d = that.getDeclaration();
                if (d!=null && d.equals(dec)) {
                    detected = true;
                }
            }
        }
        DetectUsageVisitor duv = new DetectUsageVisitor();
        duv.visit(rootNode);
        boolean used = duv.detected;
        return used;
    }

    public static boolean removeImport(String originalPackage, final Declaration dec,
            Tree.CompilationUnit cu, TextChange tc, Set<String> packages) {
        boolean foundOriginal = false;
        Tree.ImportList il = cu.getImportList();
        for (Tree.Import imp: il.getImports()) {
            Referenceable model = imp.getImportPath().getModel();
            if (model!=null) {
                if (model.getNameAsString().equals(originalPackage)) {
                    Tree.ImportMemberOrTypeList imtl = 
                            imp.getImportMemberOrTypeList();
                    if (imtl!=null) {
                        List<ImportMemberOrType> imts = 
                                imtl.getImportMemberOrTypes();
                        for (int j=0; j<imts.size(); j++) {
                            Tree.ImportMemberOrType imt = imts.get(j);
                            Declaration d = imt.getDeclarationModel();
                            if (d!=null && d.equals(dec)) {
                                int offset;
                                int length;
                                if (j>0) {
                                    offset = getNodeEndOffset(imts.get(j-1));
                                    length = getNodeEndOffset(imt)-offset;
                                }
                                else if (j<imts.size()-1) {
                                    offset = getNodeStartOffset(imt);
                                    length = getNodeStartOffset(imts.get(j+1))-offset;
                                }
                                else {
                                    if (packages.contains(originalPackage)) { 
                                        //we're adding to this import statement,
                                        //so don't delete the whole import
                                        offset = getNodeStartOffset(imt);
                                        length = getNodeLength(imt);
                                    }
                                    else {
                                        offset = getNodeStartOffset(imp);
                                        length = getNodeLength(imp);
                                    }
                                }
                                tc.addEdit(new DeleteEdit(offset,length));
                                foundOriginal = true;
                                break;
                                //TODO: return the alias!
                            }
                        }
                    }
                    break;
                }
            }
        }
        return foundOriginal;
    }

    private static void addImport(String targetPackage, Declaration dec, 
            Tree.CompilationUnit cu, TextChange tc) {
        String name = dec.getName();
        boolean foundMoved = false;
        Tree.ImportList il = cu.getImportList();
        for (Tree.Import i: il.getImports()) {
            Referenceable model = i.getImportPath().getModel();
            if (model!=null) {
                if (model.getNameAsString().equals(targetPackage)) {
                    Tree.ImportMemberOrTypeList imtl = 
                            i.getImportMemberOrTypeList();
                    if (imtl!=null) {
                        String addition;
                        int offset;
                        List<Tree.ImportMemberOrType> imts = 
                                imtl.getImportMemberOrTypes();
                        if (imts.isEmpty()) {
                            offset = getNodeStartOffset(imtl);
                            addition = " " + name;
                        }
                        else {
                            offset = getNodeEndOffset(imts.get(imts.size()-1));
                            addition = ", " + name;
                        }
                        //TODO: the alias!
                        tc.addEdit(new InsertEdit(offset, addition));
                        foundMoved = true;
                    }
                    break;
                }
            }
        }
        if (!foundMoved) {
            String text = "import " + targetPackage + " { " + name + " }" + 
                    getDefaultLineDelimiter(getDocument(tc));
            tc.addEdit(new InsertEdit(0, text));
        }
    }

    static TextChange createEditorChange(CeylonEditor editor,
            IDocument document) {
        if (editor.isDirty()) {
            return new DocumentChange("Move from Source File", 
                    document);
        }
        else {
            return new TextFileChange("Move from Source File", 
                    getFile(editor.getEditorInput()));
        }
    }

    public static boolean canMoveDeclaration(CeylonEditor editor) {
        Node node = getSelectedNode(editor);
        if (node instanceof Tree.Declaration) {
            Declaration d = ((Tree.Declaration) node).getDeclarationModel();
            return d!=null && d.isToplevel();
        }
        else {
            return false;
        }
    }

    public static String getDeclarationName(CeylonEditor editor) {
        Node node = getSelectedNode(editor);
        if (node instanceof Tree.Declaration) {
            return ((Tree.Declaration) node).getIdentifier().getText();
        }
        else {
            return null;
        }
    }

    static List<PhasedUnit> getAllUnits(IProject project) {
        List<PhasedUnit> units = new ArrayList<PhasedUnit>();
        units.addAll(getUnits(project));
        for (IProject p: project.getReferencingProjects()) {
            units.addAll(getUnits(p));
        }
        return units;
    }

    static IStructuredSelection getSelection() {
        IEditorPart ed = EditorUtil.getCurrentEditor();
        if (ed!=null) {
            IEditorInput input = ed.getEditorInput();
            if (input instanceof IFileEditorInput) {
                return new StructuredSelection(((IFileEditorInput) input).getFile());
            }
        }
        return null;
    }

}
