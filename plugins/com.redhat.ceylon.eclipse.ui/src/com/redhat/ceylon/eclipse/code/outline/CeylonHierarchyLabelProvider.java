package com.redhat.ceylon.eclipse.code.outline;

import static com.redhat.ceylon.eclipse.code.complete.CodeCompletions.getQualifiedDescriptionFor;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.getImageForDeclaration;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.getPackageLabel;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.MULTIPLE_TYPES_IMAGE;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.custom.StyleRange;

import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.eclipse.util.Highlights;

abstract class CeylonHierarchyLabelProvider extends
        StyledCellLabelProvider {
        
    @Override
    public void removeListener(ILabelProviderListener listener) {}

    @Override
    public boolean isLabelProperty(Object element, String property) {
        return false;
    }

    @Override
    public void dispose() {}

    @Override
    public void addListener(ILabelProviderListener listener) {}

    StyledString getStyledText(CeylonHierarchyNode n) {
        Declaration d = getDisplayedDeclaration(n);
        if (d==null) {
        	return new StyledString();
        }
        StyledString result = getQualifiedDescriptionFor(d);
        /*if (d.isClassOrInterfaceMember()) {
            Declaration container = (Declaration) d.getContainer();
            result.append(" in ")
                  .append(container.getName(), Highlights.TYPE_ID_STYLER);
        }*/
        result.append(" - ", Highlights.PACKAGE_STYLER)
              .append(getPackageLabel(d), Highlights.PACKAGE_STYLER);
        if (n.isNonUnique()) {
            result.append(" - and other supertypes")
                  .append(getViewInterfacesShortcut());
        }
        return result;
    }

    String getViewInterfacesShortcut() {
        return "";
    }
    
    abstract IProject getProject();
    abstract boolean isShowingRefinements();

    Declaration getDisplayedDeclaration(CeylonHierarchyNode n) {
        Declaration d = n.getDeclaration(getProject());
        if (d!=null && 
                isShowingRefinements() && 
                d.isClassOrInterfaceMember()) {
            d = (ClassOrInterface) d.getContainer();
        }
        return d;
    }

    
    @Override
    public void update(ViewerCell cell) {
        CeylonHierarchyNode n = 
                (CeylonHierarchyNode) cell.getElement();
        if (n.isMultiple()) {
            cell.setText("multiple supertypes" + 
                    getViewInterfacesShortcut());
            cell.setStyleRanges(new StyleRange[0]);
            cell.setImage(MULTIPLE_TYPES_IMAGE);
        }
        else {
            StyledString styledText = getStyledText(n);
            cell.setText(styledText.toString());
            cell.setStyleRanges(styledText.getStyleRanges());
            cell.setImage(getImageForDeclaration(getDisplayedDeclaration(n)));
        }
        super.update(cell);
    }
}