package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.code.correct.CorrectionUtil.styleProposal;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.IMPORT;
import static com.redhat.ceylon.eclipse.util.Nodes.getReferencedModel;
import static com.redhat.ceylon.eclipse.util.Nodes.getReferencedNode;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.ModuleImport;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.imports.ModuleImportUtil;

public class ExportModuleImportProposal implements ICompletionProposal, 
        ICompletionProposalExtension6 {
    
    private final IProject project;
    private final Unit unit; 
    private final String name;
    
    ExportModuleImportProposal(IProject project, Unit unit, String name) {
        this.project = project;
        this.unit = unit;
        this.name = name;
    }
    
    @Override
    public void apply(IDocument document) {
        ModuleImportUtil.exportModuleImports(project, 
                unit.getPackage().getModule(), 
                name);
    }

    @Override
    public Point getSelection(IDocument document) {
        return null;
    }

    @Override
    public String getAdditionalProposalInfo() {
        return null;
    }

    @Override
    public String getDisplayString() {
        return "Export 'import " + name + "' to clients of module";
    }

    @Override
    public Image getImage() {
        return IMPORT;
    }

    @Override
    public IContextInformation getContextInformation() {
        return null;
    }

    @Override
    public StyledString getStyledDisplayString() {
        return styleProposal(getDisplayString());
    }

    static void addExportModuleImportProposalForSupertypes(Collection<ICompletionProposal> proposals, 
            IProject project, Node node, Tree.CompilationUnit rootNode) {
        Unit unit = node.getUnit();
        if (node instanceof Tree.InitializerParameter) {
            node = getReferencedNode(getReferencedModel(node), rootNode);
        }
        if (node instanceof Tree.TypedDeclaration) {
            node = ((Tree.TypedDeclaration) node).getType();
        }
        if (node instanceof Tree.ClassOrInterface) {
            Tree.ClassOrInterface c = (Tree.ClassOrInterface) node;
            ProducedType extendedType = 
                    c.getDeclarationModel().getExtendedType();
            if (extendedType!=null) {
                addExportModuleImportProposal(proposals, project, 
                        unit, extendedType.getDeclaration());
                for (ProducedType typeArgument:
                        extendedType.getTypeArgumentList()) {
                    addExportModuleImportProposal(proposals, project, 
                            unit, typeArgument.getDeclaration());
                }
            }
            
            List<ProducedType> satisfiedTypes = 
                    c.getDeclarationModel().getSatisfiedTypes();
            if (satisfiedTypes!=null) {
                for (ProducedType satisfiedType: satisfiedTypes) {
                    addExportModuleImportProposal(proposals, project, 
                            unit, satisfiedType.getDeclaration());
                    for (ProducedType typeArgument: 
                            satisfiedType.getTypeArgumentList()) {
                        addExportModuleImportProposal(proposals, project, 
                                unit, typeArgument.getDeclaration());
                    }
                }
            }
        }
        else if (node instanceof Tree.Type) {
            ProducedType type = ((Tree.Type) node).getTypeModel();
            addExportModuleImportProposal(proposals, project, 
                    unit, type.getDeclaration());
            for (ProducedType typeArgument:
                    type.getTypeArgumentList()) {
                addExportModuleImportProposal(proposals, project, 
                        unit, typeArgument.getDeclaration());
            }
        }
    }
    
    static void addExportModuleImportProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Node node) {
        if (node instanceof Tree.SimpleType) {
            Declaration dec = ((Tree.SimpleType) node).getDeclarationModel();
            addExportModuleImportProposal(proposals, project, node.getUnit(), dec);
        }
    }

    private static void addExportModuleImportProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Unit unit, Declaration dec) {
        Module decModule = dec.getUnit().getPackage().getModule();
        for (ModuleImport mi: unit.getPackage().getModule().getImports()) {
            if (mi.getModule().equals(decModule)) {
                if (mi.isExport()) {
                    return;
                }
            }
        }
        proposals.add(new ExportModuleImportProposal(project, unit, 
                decModule.getNameAsString()));
    }

}
