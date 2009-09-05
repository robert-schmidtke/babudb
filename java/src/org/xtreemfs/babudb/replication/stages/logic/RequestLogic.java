/*
 * Copyright (c) 2009, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Felix Langner, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */
package org.xtreemfs.babudb.replication.stages.logic;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.xtreemfs.babudb.BabuDBException;
import org.xtreemfs.babudb.interfaces.LSNRange;
import org.xtreemfs.babudb.interfaces.LogEntries;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.errnoException;
import org.xtreemfs.babudb.interfaces.utils.ONCRPCException;
import org.xtreemfs.babudb.log.LogEntry;
import org.xtreemfs.babudb.log.LogEntryException;
import org.xtreemfs.babudb.log.SyncListener;
import org.xtreemfs.babudb.lsmdb.LSN;
import org.xtreemfs.babudb.replication.operations.ErrNo;
import org.xtreemfs.babudb.replication.stages.ReplicationStage;
import org.xtreemfs.babudb.replication.stages.ReplicationStage.ConnectionLostException;
import org.xtreemfs.include.common.buffer.BufferPool;
import org.xtreemfs.include.common.logging.Logging;
import org.xtreemfs.include.foundation.oncrpc.client.RPCResponse;

import static org.xtreemfs.babudb.replication.stages.ReplicationStage.ConnectionLostException;
import static org.xtreemfs.babudb.replication.stages.logic.LogicID.*;
/**
 * <p>Requests missing {@link LogEntry}s at the 
 * master and inserts them into the DBS.</p>
 * 
 * @author flangner
 * @since 06/08/2009
 */

public class RequestLogic extends Logic {

    private static final Checksum checksum = new CRC32();
    
    public RequestLogic(ReplicationStage stage) {
        super(stage);
    }

    /*
     * (non-Javadoc)
     * @see org.xtreemfs.babudb.replication.stages.logic.Logic#getId()
     */
    @Override
    public LogicID getId() {
        return REQUEST;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() throws InterruptedException, ConnectionLostException{
        Logging.logMessage(Logging.LEVEL_INFO, this, "Replica-range is missing: %s", stage.missing.toString());
        
        // get the missing logEntries
        RPCResponse<LogEntries> rp = null;    
        LogEntries logEntries = null;
        
        try {
            rp = stage.dispatcher.master.getReplica(stage.missing);
            logEntries = (LogEntries) rp.get();
            final AtomicInteger count = new AtomicInteger(logEntries.size());
            // insert all logEntries
            for (org.xtreemfs.babudb.interfaces.LogEntry le : logEntries) {
                try {
                    final LogEntry logentry = LogEntry.deserialize(le.getPayload(), checksum);
                    final LSN lsn = logentry.getLSN();
                    SharedLogic.handleLogEntry(logentry, new SyncListener() {
                    
                        @Override
                        public void synced(LogEntry entry) {
                            synchronized (count) {
                                stage.lastInserted = lsn;
                                if (count.decrementAndGet() == 0)
                                    count.notify();
                            }
                            entry.free();
                        }
                    
                        @Override
                        public void failed(LogEntry entry, Exception ex) {
                            Logging.logError(Logging.LEVEL_ERROR, stage, ex);
                            synchronized (count) {
                                count.set(-1);
                                count.notify();
                            }
                            entry.free();
                        }
                    }, stage.dispatcher.dbs);
                } finally {
                    checksum.reset();
                }  
            }
            
            // block until all inserts are finished
            synchronized (count) {
                while (count.get() > 0)
                    count.wait();
            }
            
            // at least one insert failed
            if (count.get() == -1) throw new LogEntryException("At least one insert could not be proceeded.");
 
            // all went fine --> back to basic
            stage.dispatcher.updateLatestLSN(stage.lastInserted);
            stage.missing = null;
            stage.setLogic(BASIC, "Request went fine, we can went on with the basicLogic.");
            
            if (Thread.interrupted()) throw new InterruptedException("Replication was interrupted after executing a replicaOperation.");
        } catch (ONCRPCException e) {
            // remote failure (connection lost)
            int errNo = (e != null && e instanceof errnoException) ? 
                    ((errnoException) e).getError_code() : ErrNo.UNKNOWN;
            throw new ConnectionLostException(e.getTypeName()+": "+e.getMessage(),errNo);
        } catch (IOException ioe) {
            // failure on transmission
            throw new ConnectionLostException(ioe.getMessage(),ErrNo.UNKNOWN);
        } catch (LogEntryException lee) {
            // decoding failed --> retry with new range
            Logging.logError(Logging.LEVEL_WARN, this, lee);
            stage.missing = new LSNRange(stage.missing.getViewId(), stage.lastInserted.getSequenceNo()+1L, stage.missing.getSequenceEnd());
        } catch (BabuDBException be) {
            // the insert failed due an DB error
            Logging.logError(Logging.LEVEL_WARN, this, be);
            stage.setLogic(LOAD, be.getMessage());
        } catch (InterruptedException i) {
            // finally will not be executed, if the thread was interrupted ... -.-
            if (rp!=null) rp.freeBuffers();
            if (logEntries!=null) 
                for (org.xtreemfs.babudb.interfaces.LogEntry le : logEntries) 
                    if (le.getPayload()!=null) BufferPool.free(le.getPayload());
            throw i;
        } finally {
            // finally will not be executed, if the thread was interrupted ... -.-
            if (rp!=null) rp.freeBuffers();
            if (logEntries!=null) 
                for (org.xtreemfs.babudb.interfaces.LogEntry le : logEntries) 
                    if (le.getPayload()!=null) BufferPool.free(le.getPayload());
        }
    }
}