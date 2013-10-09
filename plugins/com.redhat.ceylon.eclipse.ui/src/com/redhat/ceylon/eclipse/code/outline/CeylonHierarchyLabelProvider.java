package com.redhat.ceylon.eclipse.code.outline;

import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.MULTIPLE_TYPES_IMAGE;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.TYPE_ID_STYLER;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.getImageForDeclaration;
import static com.redhat.ceylon.eclipse.code.propose.CeylonContentProposer.getStyledDescriptionFor;
import static org.eclipse.jface.viewers.StyledString.QUALIFIER_STYLER;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.custom.StyleRange;

import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;

final class CeylonHierarchyLabelProvider extends
		StyledCellLabelProvider {
	
	private static final String VIEW_INTERFACES = " (" + HierarchyPopup.KEY + " to view)";
	private final CeylonHierarchyContentProvider contentProvider;
	private boolean popup;
	
	public CeylonHierarchyLabelProvider(CeylonHierarchyContentProvider contentProvider,
			boolean popup) {
		this.contentProvider = contentProvider;
		this.popup = popup;
	}

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
	    StyledString result = getStyledDescriptionFor(d);
	    /*if (d.getContainer() instanceof Declaration) {
	        result.append(" in ")
	                .append(getStyledDescriptionFor((Declaration) d.getContainer()));
	    }*/
	    if (d.isClassOrInterfaceMember()) {
	        result.append(" in ")
	                .append(((Declaration) d.getContainer()).getName(), TYPE_ID_STYLER);
	    }
	    result.append(" - ", QUALIFIER_STYLER)
	            .append(CeylonLabelProvider.getPackageLabel(d), QUALIFIER_STYLER);
	    if (n.isNonUnique()) {
	    	result.append(" - and other supertypes");
	    	if (popup) result.append(VIEW_INTERFACES);
	    }
	    return result;
	}

	Declaration getDisplayedDeclaration(CeylonHierarchyNode n) {
	    Declaration d = n.getDeclaration();
	    if (contentProvider.isShowingRefinements() && 
	    		d.isClassOrInterfaceMember()) {
	        d = (ClassOrInterface) d.getContainer();
	    }
	    return d;
	}

    
	@Override
	public void update(ViewerCell cell) {
		CeylonHierarchyNode n = (CeylonHierarchyNode) cell.getElement();
		if (n.getDeclaration()==null) {
			cell.setText("multiple supertypes" + VIEW_INTERFACES);
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