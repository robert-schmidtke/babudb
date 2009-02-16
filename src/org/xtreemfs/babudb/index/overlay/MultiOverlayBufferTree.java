/*
 * Copyright (c) 2008, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
*/

package org.xtreemfs.babudb.index.overlay;

import java.util.Iterator;
import java.util.Map.Entry;

import org.xtreemfs.babudb.index.ByteRangeComparator;

public class MultiOverlayBufferTree extends MultiOverlayTree<byte[], byte[]> {
    
    private ByteRangeComparator comp;
    
    public MultiOverlayBufferTree(byte[] markerElement, ByteRangeComparator comp) {
        super(markerElement, comp);
        this.comp = comp;
    }
    
    public Iterator<Entry<byte[], byte[]>> prefixLookup(byte[] prefix) {
        
        byte[][] keyRange = comp.prefixToRange(prefix);
        assert (keyRange.length == 2);
        
        return rangeLookup(keyRange[0], keyRange[1]);
    }
    
    public Iterator<Entry<byte[], byte[]>> prefixLookup(byte[] prefix, int overlayId) {
        
        byte[][] keyRange = comp.prefixToRange(prefix);
        assert (keyRange.length == 2);
        
        return rangeLookup(keyRange[0], keyRange[1], overlayId);
    }
    
}