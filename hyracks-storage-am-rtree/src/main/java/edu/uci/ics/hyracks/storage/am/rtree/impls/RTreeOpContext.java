package edu.uci.ics.hyracks.storage.am.rtree.impls;

import edu.uci.ics.hyracks.dataflow.common.data.accessors.ITupleReference;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexMetaDataFrame;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexTupleReference;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.TreeIndexOp;
import edu.uci.ics.hyracks.storage.am.rtree.api.IRTreeFrame;

public final class RTreeOpContext {
    public final TreeIndexOp op;
    public final IRTreeFrame interiorFrame;
    public final IRTreeFrame leafFrame;
    public final ITreeIndexMetaDataFrame metaFrame;
    public final ByteArrayList overflowArray;
    public final RTreeSplitKey splitKey;
    public final SpatialUtils spatialUtils;
    public ITupleReference tuple;
    public TupleEntryArrayList tupleEntries1;
    public TupleEntryArrayList tupleEntries2;
    public ITreeIndexTupleReference[] nodesMBRs;
    public final IntArrayList path;
    public final IntArrayList pageLsns;
    public final FindPathList findPathList; // works as a queue
    public Rectangle[] rec;

    public RTreeOpContext(TreeIndexOp op, IRTreeFrame interiorFrame, IRTreeFrame leafFrame,
            ITreeIndexMetaDataFrame metaFrame, int treeHeightHint, int dim) {
        this.op = op;
        this.interiorFrame = interiorFrame;
        this.leafFrame = leafFrame;
        this.metaFrame = metaFrame;
        splitKey = new RTreeSplitKey(interiorFrame.getTupleWriter().createTupleReference(), interiorFrame
                .getTupleWriter().createTupleReference());
        overflowArray = new ByteArrayList(treeHeightHint, treeHeightHint);
        spatialUtils = new SpatialUtils();
        // TODO: find a better way to know number of entries per node
        tupleEntries1 = new TupleEntryArrayList(100, 100, spatialUtils);
        tupleEntries2 = new TupleEntryArrayList(100, 100, spatialUtils);
        nodesMBRs = new ITreeIndexTupleReference[treeHeightHint];
        path = new IntArrayList(treeHeightHint, treeHeightHint);
        pageLsns = new IntArrayList(treeHeightHint, treeHeightHint);
        findPathList = new FindPathList(100, 100);
        for (int i = 0; i < treeHeightHint; i++) {
            nodesMBRs[i] = interiorFrame.getTupleWriter().createTupleReference();
            nodesMBRs[i].setFieldCount(nodesMBRs[i].getFieldCount());
        }
        rec = new Rectangle[4];
        for (int i = 0; i < 4; i++) {
            rec[i] = new Rectangle(dim);
        }
    }

    public ITupleReference getTuple() {
        return tuple;
    }

    public void setTuple(ITupleReference tuple) {
        this.tuple = tuple;
    }

    public void reset() {
        if (overflowArray != null) {
            overflowArray.clear();
        }
        if (tupleEntries1 != null) {
            tupleEntries1.clear();
        }
        if (tupleEntries2 != null) {
            tupleEntries2.clear();
        }
        if (path != null) {
            path.clear();
        }
        if (pageLsns != null) {
            pageLsns.clear();
        }
        if (findPathList != null) {
            pageLsns.clear();
        }
    }
}