/*
 * Copyright (c) 2008, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */

package org.xtreemfs.babudb.lsmdb;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.xtreemfs.babudb.BabuDBException;
import org.xtreemfs.babudb.BabuDBRequest;
import org.xtreemfs.babudb.lsmdb.InsertRecordGroup.InsertRecord;
import org.xtreemfs.babudb.index.LSMTree;
import org.xtreemfs.babudb.log.DiskLogger;
import org.xtreemfs.babudb.log.LogEntry;
import org.xtreemfs.babudb.log.SyncListener;
import org.xtreemfs.babudb.BabuDBException.ErrorCode;
import org.xtreemfs.babudb.UserDefinedLookup;
import org.xtreemfs.include.common.buffer.BufferPool;
import org.xtreemfs.include.common.buffer.ReusableBuffer;
import org.xtreemfs.include.common.logging.Logging;

/**
 *
 * @author bjko
 */
public class LSMDBWorker extends Thread implements SyncListener {

    public static enum RequestOperation {
        INSERT,
        LOOKUP,
        PREFIX_LOOKUP,
        USER_DEFINED_LOOKUP
    };
    
    private final BlockingQueue<LSMDBRequest<?>> requests;
    
    private transient boolean quit;
    
    private final AtomicBoolean down;
    
    private final DiskLogger    logger;

    private final boolean       pseudoSync;
        
    public LSMDBWorker(DiskLogger logger, int id, boolean pseudoSync, 
            int maxQ) {
        super("LSMDBWrkr#"+id);  
        this.down = new AtomicBoolean(false);       
        if (maxQ > 0)
            requests = new LinkedBlockingQueue<LSMDBRequest<?>>(maxQ);
        else
            requests = new LinkedBlockingQueue<LSMDBRequest<?>>();       
        this.logger = logger;
        this.pseudoSync = pseudoSync;
    }
    
    public void addRequest(LSMDBRequest<?> request) throws InterruptedException {
        requests.put(request);
    }
    
    public void shutdown() {
        quit = true;
        synchronized (this) {
            this.interrupt();
        }
    }

    public boolean isDown() {
        return down.get();
    }
    
    public void waitForShutdown() throws InterruptedException {
        synchronized (down) {
            if (!down.get()){
                down.wait();
            }
        }
    }
    
    /**
     * This function is necessary, to avoid race-conditions.
     */
    /*
     * (non-Javadoc)
     * @see java.lang.Thread#start()
     */
    @Override
    public synchronized void start() {
        quit = false;
        down.set(false);
    	super.start();
    }
    
    /*
     * (non-Javadoc)
     * @see java.lang.Thread#run()
     */
    @SuppressWarnings("unchecked")
    @Override
    public void run() {       
        while (!quit) {
            try {
                final LSMDBRequest<?> r = requests.take();
                
                switch (r.getOperation()) {
                    case INSERT : doInsert(r); break;
                    case LOOKUP : doLookup((LSMDBRequest<byte[]>) r); break;
                    case PREFIX_LOOKUP : doPrefixLookup((LSMDBRequest<Iterator<Entry<byte[], byte[]>>>) r); break;
                    case USER_DEFINED_LOOKUP : doUserLookup((LSMDBRequest<Object>) r); break;
                    default : {
                        Logging.logMessage(Logging.LEVEL_ERROR, this,"UNKNOWN OPERATION REQUESTED! PROGRAMMATIC ERROR!!!! PANIC!");
                        System.exit(1);
                    }
                }
            } catch (InterruptedException ex) {
            }
                
        }
        Logging.logMessage(Logging.LEVEL_DEBUG,this,"shutdown complete");
        synchronized (down) {
            down.set(true);
            down.notifyAll();
        }
    }

    private void doUserLookup(final LSMDBRequest<Object> r) {
        final UserDefinedLookup l = r.getUserDefinedLookup();
        final LSMLookupInterface lif = new LSMLookupInterface(r.getDatabase());
        try {
            Object result = l.execute(lif);
            r.getListener().finished(result);
        } catch (BabuDBException ex) {
            r.getListener().failed(ex);
        }
    }
    
    private void doInsert(final LSMDBRequest<?> r) throws InterruptedException {
        final InsertRecordGroup irec = r.getInsertData();
        int size = irec.getSize();
        ReusableBuffer buf = BufferPool.allocate(size);
        irec.serialize(buf);
        buf.flip();
        LogEntry e = new LogEntry(buf,this,LogEntry.PAYLOAD_TYPE_INSERT);
        e.setAttachment(r);
        logger.append(e);

        if (pseudoSync) {
            insertIntoIndex(r);
            finish(e);
        }
    }
    
    private void doLookup(final LSMDBRequest<byte[]> r) {
        final LSMDatabase db = r.getDatabase();
        final int numIndices = db.getIndexCount();
        
        if ((r.getIndexId() >= numIndices) || (r.getIndexId() < 0))
            r.getListener().failed(new BabuDBException(ErrorCode.NO_SUCH_INDEX, 
                    "index "+r.getIndexId()+" does not exist"));                 
        else
            r.getListener().finished(db.getIndex(r.getIndexId()).lookup(
                r.getLookupKey()));
    }
    
    private void doPrefixLookup(final LSMDBRequest<Iterator<Map.Entry<byte[],byte[]>>> r) {
        final LSMDatabase db = r.getDatabase();
        final int numIndices = db.getIndexCount();
        
        if ((r.getIndexId() >= numIndices) || (r.getIndexId() < 0))
            r.getListener().failed(new BabuDBException(ErrorCode.NO_SUCH_INDEX, 
                    "index "+r.getIndexId()+" does not exist"));
        else
            r.getListener().finished(db.getIndex(r.getIndexId()).prefixLookup(
                    r.getLookupKey()));
    }

    public void synced(LogEntry entry) {       
        
        if (!pseudoSync) {
            final LSMDBRequest<?> r = entry.getAttachment();
            insertIntoIndex(r);
            finish(entry);
        }
        
        entry.free();
    }

    private void insertIntoIndex(final LSMDBRequest<?> r) {
        final InsertRecordGroup irg = r.getInsertData();
        final LSMDatabase db = r.getDatabase();
        final int numIndices = db.getIndexCount();
        try {
            for (InsertRecord ir : irg.getInserts()) {
                if ((ir.getIndexId() >= numIndices) || (ir.getIndexId() < 0))
                    r.getListener().failed(new BabuDBException(
                            ErrorCode.NO_SUCH_INDEX, "index "+r.getIndexId()+
                            " does not exist"));
                final LSMTree index = db.getIndex(ir.getIndexId());
                if (ir.getValue() != null)
                    index.insert(ir.getKey(), ir.getValue());
                else
                    index.delete(ir.getKey());
            }
        } catch (Exception ex) {
            r.getListener().failed(new BabuDBException(ErrorCode.NO_SUCH_INDEX, 
                    "cannot insert, because of unexpected error",ex));
        }
    }
    
    private void finish(LogEntry le){
        final LSMDBRequest<?> r = le.getAttachment();
        r.getListener().finished();     
    }

    public void failed(LogEntry entry, Exception ex) {
        ((BabuDBRequest<?>)entry.getAttachment().getListener()).failed( 
                new BabuDBException(ErrorCode.IO_ERROR, "could not execute " +
                		"insert because of IO problem",ex));
    }   
}