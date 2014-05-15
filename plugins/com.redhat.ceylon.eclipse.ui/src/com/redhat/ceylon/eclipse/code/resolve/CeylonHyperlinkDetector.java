package com.redhat.ceylon.eclipse.code.resolve;

import static com.redhat.ceylon.eclipse.code.editor.Navigation.gotoNode;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.util.Nodes;


public class CeylonHyperlinkDetector implements IHyperlinkDetector {
    private CeylonParseController pc;
    
    public CeylonHyperlinkDetector(CeylonParseController pc) {
        this.pc = pc;
    }

    private final class CeylonNodeLink implements IHyperlink {
        private final Node node;
        private final Node id;

        private CeylonNodeLink(Node node, Node id) {
            this.node = node;
            this.id = id;
        }

        @Override
        public void open() {
            gotoNode(node);
        }

        @Override
        public String getTypeLabel() {
            return null;
        }

        @Override
        public String getHyperlinkText() {
            return "Ceylon Declaration";
        }

        @Override
        public IRegion getHyperlinkRegion() {
            return new Region(id.getStartIndex(), 
                    id.getStopIndex()-id.getStartIndex()+1);
        }
    }

    @Override
    public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, 
            boolean canShowMultipleHyperlinks) {
        if (pc==null||pc.getRootNode()==null) {
            return null;
        }
        else {
            Node node = Nodes.findNode(pc.getRootNode(), region.getOffset(), 
                    region.getOffset()+region.getLength());
            Node id = Nodes.getIdentifyingNode(node);
            if (node==null) {
                return null;
            }
            else {
                Node r = Nodes.getReferencedNode(node, pc);
                if (r==null) {
                    return null;
                }
                else {
                    return new IHyperlink[] { new CeylonNodeLink(r, id) };
                }
            }
        }
    }
}
