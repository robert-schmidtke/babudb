/*
 * Copyright (c) 2008, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */

package org.xtreemfs.babudb;

import java.io.File;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Map.Entry;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.xtreemfs.babudb.index.DefaultByteRangeComparator;
import org.xtreemfs.babudb.index.LSMTree;
import org.xtreemfs.include.common.logging.Logging;

public class LSMTreeTest extends TestCase {
    
    private static final String SNAP_FILE  = "/tmp/snap1.bin";
    
    private static final String SNAP_FILE2 = "/tmp/snap2.bin";
    
    private static final String SNAP_FILE3 = "/tmp/snap3.bin";
    
    private static final String SNAP_FILE4 = "/tmp/snap4.bin";
    
    public LSMTreeTest() throws Exception {
    }
    
    public void setUp() {
        Logging.start(Logging.LEVEL_ERROR);
        new File(SNAP_FILE).delete();
        new File(SNAP_FILE2).delete();
        new File(SNAP_FILE3).delete();
        new File(SNAP_FILE4).delete();
    }
    
    public void tearDown() throws Exception {
        new File(SNAP_FILE).delete();
        new File(SNAP_FILE2).delete();
        new File(SNAP_FILE3).delete();
        new File(SNAP_FILE4).delete();
    }
    
    public void testSnapshots() throws Exception {
        
        byte[][] keys = new byte[][] { "00001".getBytes(), "00002".getBytes(), "00003".getBytes(),
            "00004".getBytes(), "00005".getBytes() };
        byte[][] vals = new byte[][] { "1".getBytes(), "2".getBytes(), "3".getBytes(), "4".getBytes(),
            "5".getBytes() };
        
        LSMTree tree = new LSMTree(null, new DefaultByteRangeComparator());
        TreeMap<byte[], byte[]> map = new TreeMap<byte[], byte[]>(new DefaultByteRangeComparator());
        
        // insert some key-value pairs
        for (int i = 0; i < keys.length; i++) {
            tree.insert(keys[i], vals[i]);
            map.put(keys[i], vals[i]);
        }
        
        for (byte[] key : map.keySet())
            assertEquals(new String(map.get(key)), new String(tree.lookup(key)));
        assertEquals(new String(map.firstKey()), new String(tree.firstEntry().getKey()));
        
        // create, materialize and link a new snapshot
        int snapId = tree.createSnapshot();
        tree.materializeSnapshot(SNAP_FILE, snapId);
        tree.linkToSnapshot(SNAP_FILE);
        
        for (byte[] key : map.keySet())
            assertEquals(new String(map.get(key)), new String(tree.lookup(key)));
        assertEquals(new String(map.firstKey()), new String(tree.firstEntry().getKey()));
        
        // insert some more keys-value pairs
        byte[][] newKeys = new byte[][] { "00001".getBytes(), "00006".getBytes(), "00002".getBytes(),
            "00007".getBytes(), "00008".getBytes(), "00009".getBytes(), "00003".getBytes() };
        byte[][] newVals = new byte[][] { null, "gf".getBytes(), "werr".getBytes(), "afds".getBytes(),
            "sdaew".getBytes(), "hf".getBytes(), null };
        
        for (int i = 0; i < newKeys.length; i++) {
            tree.insert(newKeys[i], newVals[i]);
            if (newVals[i] != null)
                map.put(newKeys[i], newVals[i]);
            else
                map.remove(newKeys[i]);
        }
        
        for (byte[] key : map.keySet())
            assertEquals(new String(map.get(key)), new String(tree.lookup(key)));
        assertEquals(new String(map.firstKey()), new String(tree.firstEntry().getKey()));
        
        // perform a prefix lookup
        Iterator<Entry<byte[], byte[]>> it = tree.prefixLookup(new byte[0]);
        Iterator<Entry<byte[], byte[]>> entries = map.entrySet().iterator();
        
        int count = 0;
        for (; it.hasNext(); count++) {
            
            Entry<byte[], byte[]> entry = it.next();
            Entry<byte[], byte[]> mapEntry = entries.next();
            
            assertEquals(new String(entry.getKey()), new String(mapEntry.getKey()));
            assertEquals(new String(entry.getValue()), new String(mapEntry.getValue()));
        }
        
        // perform reverse a prefix lookup
        it = tree.prefixLookup(new byte[0], false);
        entries = map.descendingMap().entrySet().iterator();
        
        count = 0;
        for (; it.hasNext(); count++) {
            
            Entry<byte[], byte[]> entry = it.next();
            Entry<byte[], byte[]> mapEntry = entries.next();
            
            assertEquals(new String(entry.getKey()), new String(mapEntry.getKey()));
            assertEquals(new String(entry.getValue()), new String(mapEntry.getValue()));
        }
        
        assertEquals(map.size(), count);
        
        // create, materialize and link a new snapshot
        snapId = tree.createSnapshot();
        tree.materializeSnapshot(SNAP_FILE2, snapId);
        tree.linkToSnapshot(SNAP_FILE2);
        
        for (byte[] key : map.keySet())
            assertEquals(new String(map.get(key)), new String(tree.lookup(key)));
        assertEquals(new String(map.firstKey()), new String(tree.firstEntry().getKey()));
        
        // delete all entries
        for (byte[] key : keys) {
            map.remove(key);
            tree.delete(key);
        }
        
        for (byte[] key : newKeys) {
            map.remove(key);
            tree.delete(key);
        }
        
        assertEquals(0, map.size());
        assertFalse(tree.prefixLookup(new byte[0]).hasNext());
        
        // create, materialize and link a new snapshot
        snapId = tree.createSnapshot();
        tree.materializeSnapshot(SNAP_FILE3, snapId);
        tree.linkToSnapshot(SNAP_FILE3);
        
        it = tree.prefixLookup(new byte[0]);
        assertFalse(it.hasNext());
        
        tree.insert("test".getBytes(), "test".getBytes());
        tree.delete("test".getBytes());
        
        snapId = tree.createSnapshot();
        tree.materializeSnapshot(SNAP_FILE4, snapId);
        tree.linkToSnapshot(SNAP_FILE4);
        
        assertEquals(null, tree.lookup("test".getBytes()));
    }
    
    public void testPrefixLookups() throws Exception {
        
        // randomly insert 200 elements in a map
        
        final int numElements = 200;
        final DefaultByteRangeComparator comp = new DefaultByteRangeComparator();
        
        LSMTree tree = new LSMTree(null, comp);
        
        final TreeMap<byte[], byte[]> map1 = new TreeMap<byte[], byte[]>(comp);
        for (int i = 0x10; i < numElements; i++) {
            byte[] key = Integer.toHexString(i).getBytes();
            byte[] val = Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE)).getBytes();
            map1.put(key, val);
            tree.insert(key, val);
        }
        
        int snap1 = tree.createSnapshot();
        final TreeMap<byte[], byte[]> map2 = new TreeMap<byte[], byte[]>(map1);
        
        for (int i = 0x10; i < numElements; i += 2) {
            byte[] key = Integer.toHexString(i).getBytes();
            tree.insert(key, null);
            map2.remove(key);
        }
        
        int snap2 = tree.createSnapshot();
        final TreeMap<byte[], byte[]> map3 = new TreeMap<byte[], byte[]>(map2);
        
        for (int i = 0x10; i < numElements; i += 5) {
            byte[] key = Integer.toHexString(i).getBytes();
            byte[] val = Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE)).getBytes();
            tree.insert(key, val);
            map3.put(key, val);
        }
        
        // peform prefix lookups
        
        // current tree, ascending
        Iterator<Entry<byte[], byte[]>> it = tree.prefixLookup(new byte[0], true);
        Iterator<byte[]> itExpected = map3.values().iterator();
        while (it.hasNext())
            assertEquals(itExpected.next(), it.next().getValue());
        assertFalse(itExpected.hasNext());
        
        // current tree, descending
        it = tree.prefixLookup(new byte[0], false);
        itExpected = map3.descendingMap().values().iterator();
        while (it.hasNext())
            assertEquals(itExpected.next(), it.next().getValue());
        assertFalse(itExpected.hasNext());
        
        // snapshot 2, ascending
        it = tree.prefixLookup(new byte[0], snap2, true);
        itExpected = map2.values().iterator();
        while (it.hasNext())
            assertEquals(itExpected.next(), it.next().getValue());
        assertFalse(itExpected.hasNext());
        
        // snapshot 2, descending
        it = tree.prefixLookup(new byte[0], snap2, false);
        itExpected = map2.descendingMap().values().iterator();
        while (it.hasNext())
            assertEquals(itExpected.next(), it.next().getValue());
        assertFalse(itExpected.hasNext());
        
        // snapshot 1, ascending
        it = tree.prefixLookup(new byte[0], snap1, true);
        itExpected = map1.values().iterator();
        while (it.hasNext())
            assertEquals(itExpected.next(), it.next().getValue());
        assertFalse(itExpected.hasNext());
        
        // snapshot 1, descending
        it = tree.prefixLookup(new byte[0], snap1, false);
        itExpected = map1.descendingMap().values().iterator();
        while (it.hasNext())
            assertEquals(itExpected.next(), it.next().getValue());
        assertFalse(itExpected.hasNext());
        
        // real prefix lookup, current tree, ascending
        it = tree.prefixLookup("4".getBytes(), true);
        itExpected = map3.subMap("4".getBytes(), true, "5".getBytes(), false).values().iterator();
        while (it.hasNext())
            assertEquals(itExpected.next(), it.next().getValue());
        assertFalse(itExpected.hasNext());
        
        // real prefix lookup, current tree, descending
        it = tree.prefixLookup("4".getBytes(), false);
        itExpected = map3.descendingMap().subMap("5".getBytes(), false, "4".getBytes(), true).values()
                .iterator();
        while (it.hasNext())
            assertEquals(itExpected.next(), it.next().getValue());
        assertFalse(itExpected.hasNext());
        
        // real (empty) prefix lookup, current tree, ascending
        it = tree.prefixLookup("XXXXXXXX".getBytes(), true);
        itExpected = map3.subMap("XXXXXXXX".getBytes(), true, "XXXXXXXY".getBytes(), false).values()
                .iterator();
        while (it.hasNext())
            assertEquals(itExpected.next(), it.next().getValue());
        assertFalse(itExpected.hasNext());
        
        // real (empty) prefix lookup, current tree, descending
        it = tree.prefixLookup("XXXXXXXX".getBytes(), false);
        itExpected = map3.descendingMap().subMap("XXXXXXXY".getBytes(), false, "XXXXXXXX".getBytes(), true)
                .values().iterator();
        while (it.hasNext())
            assertEquals(itExpected.next(), it.next().getValue());
        assertFalse(itExpected.hasNext());
        
        // first entry, current tree
        assertEquals(map3.firstEntry().getKey(), tree.firstEntry().getKey());
        assertEquals(map3.firstEntry().getValue(), tree.firstEntry().getValue());
        
        // last entry, current tree
        assertEquals(map3.lastEntry().getKey(), tree.lastEntry().getKey());
        assertEquals(map3.lastEntry().getValue(), tree.lastEntry().getValue());
        
        // first entry, snapshot 2
        assertEquals(map2.firstEntry().getKey(), tree.firstEntry(snap2).getKey());
        assertEquals(map2.firstEntry().getValue(), tree.firstEntry(snap2).getValue());
        
        // last entry, snapshot 2
        assertEquals(map2.lastEntry().getKey(), tree.lastEntry(snap2).getKey());
        assertEquals(map2.lastEntry().getValue(), tree.lastEntry(snap2).getValue());
        
        // first entry, snapshot 1
        assertEquals(map1.firstEntry().getKey(), tree.firstEntry(snap1).getKey());
        assertEquals(map1.firstEntry().getValue(), tree.firstEntry(snap1).getValue());
        
        // last entry, snapshot 1
        assertEquals(map1.lastEntry().getKey(), tree.lastEntry(snap1).getKey());
        assertEquals(map1.lastEntry().getValue(), tree.lastEntry(snap1).getValue());
        
    }
    
    private void assertEquals(byte[] expected, byte[] result) {
        
        if (expected == null && result == null)
            return;
        
        assertEquals(expected.length, result.length);
        
        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], result[i]);
    }
    
    public static void main(String[] args) {
        TestRunner.run(LSMTreeTest.class);
    }
    
}