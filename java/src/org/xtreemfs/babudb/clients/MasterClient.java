/*
 * Copyright (c) 2009, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Felix Langner, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */
package org.xtreemfs.babudb.clients;

import java.net.InetSocketAddress;

import org.xtreemfs.babudb.interfaces.Chunk;
import org.xtreemfs.babudb.interfaces.DBFileMetaDataSet;
import org.xtreemfs.babudb.interfaces.LSNRange;
import org.xtreemfs.babudb.interfaces.LogEntries;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.ReplicationInterface;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.chunkRequest;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.chunkResponse;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.heartbeatRequest;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.heartbeatResponse;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.loadRequest;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.loadResponse;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.replicaRequest;
import org.xtreemfs.babudb.interfaces.ReplicationInterface.replicaResponse;
import org.xtreemfs.babudb.log.LogEntry;
import org.xtreemfs.babudb.lsmdb.LSN;
import org.xtreemfs.include.common.buffer.ReusableBuffer;
import org.xtreemfs.include.foundation.oncrpc.client.ONCRPCClient;
import org.xtreemfs.include.foundation.oncrpc.client.RPCNIOSocketClient;
import org.xtreemfs.include.foundation.oncrpc.client.RPCResponse;
import org.xtreemfs.include.foundation.oncrpc.client.RPCResponseDecoder;

/**
 * Client to communicate with the master. Supports the replication.
 * 
 * @author flangner
 * @since 05/08/2009
 */

public class MasterClient extends ONCRPCClient {

    public MasterClient(RPCNIOSocketClient client, InetSocketAddress defaultServer) {
        super(client, defaultServer, 1, ReplicationInterface.getVersion());
    }
    
    /**
     * Requests a list of {@link LogEntry}s inclusive between the given {@link LSN}s start and end at the master.
     * 
     * @param start
     * @param end
     * @return the {@link RPCResponse} receiving a list of LogEntries.
     */
    @SuppressWarnings("unchecked")
    public RPCResponse<LogEntries> getReplica (LSNRange range) {
        replicaRequest rq = new replicaRequest(range);
        
        RPCResponse<LogEntries> r = (RPCResponse<LogEntries>) sendRequest(null, rq.getOperationNumber(), rq, new RPCResponseDecoder<LogEntries>() {
        
            /*
             * (non-Javadoc)
             * @see org.xtreemfs.include.foundation.oncrpc.client.RPCResponseDecoder#getResult(org.xtreemfs.include.common.buffer.ReusableBuffer)
             */
            @Override
            public LogEntries getResult(ReusableBuffer data) {
                final replicaResponse rp = new replicaResponse();
                rp.deserialize(data);
                return rp.getReturnValue();
            }
        });
        
        return r;
    }
    
    /**
     * Requests the DBFileMetadata of the master.
     * 
     * @param lsn - of the latest written {@link LogEntry}.
     * @return the {@link RPCResponse} receiving a {@link DBFileMetaDataSet}.
     */
    @SuppressWarnings("unchecked")
    public RPCResponse<DBFileMetaDataSet> load (LSN lsn) {
        loadRequest rq = new loadRequest(
                new org.xtreemfs.babudb.interfaces.LSN(lsn.getViewId(),lsn.getSequenceNo()));
        
        RPCResponse<DBFileMetaDataSet> r = (RPCResponse<DBFileMetaDataSet>) sendRequest(null, rq.getOperationNumber(), rq, new RPCResponseDecoder<DBFileMetaDataSet>() {
        
            /*
             * (non-Javadoc)
             * @see org.xtreemfs.include.foundation.oncrpc.client.RPCResponseDecoder#getResult(org.xtreemfs.include.common.buffer.ReusableBuffer)
             */
            @Override
            public DBFileMetaDataSet getResult(ReusableBuffer data) {
                final loadResponse rp = new loadResponse();
                rp.deserialize(data);
                return rp.getReturnValue();
            }
        });
        
        return r;
    }
    
    /**
     * Requests the chunk data with the given chunk details at the master.
     * 
     * @param chunk
     * @return the {@link RPCResponse} receiving a {@link Chunk} with data attached.
     */
    @SuppressWarnings("unchecked")
    public RPCResponse<ReusableBuffer> chunk (Chunk chunk) {
        chunkRequest rq = new chunkRequest(chunk);
        
        RPCResponse<ReusableBuffer> r = (RPCResponse<ReusableBuffer>) sendRequest(null, rq.getOperationNumber(), rq, new RPCResponseDecoder<ReusableBuffer>() {
        
            /*
             * (non-Javadoc)
             * @see org.xtreemfs.include.foundation.oncrpc.client.RPCResponseDecoder#getResult(org.xtreemfs.include.common.buffer.ReusableBuffer)
             */
            @Override
            public ReusableBuffer getResult(ReusableBuffer data) {
                final chunkResponse rp = new chunkResponse();
                rp.deserialize(data);
                return rp.getReturnValue();
            }
        });
        
        return r;
    }
    
    /**
     * Updates the latest LSN of the slave at the master.
     * 
     * @param lsn
     * @return the {@link RPCResponse}.
     */
    public RPCResponse<?> heartbeat (LSN lsn) {
        heartbeatRequest rq = new heartbeatRequest(
                new org.xtreemfs.babudb.interfaces.LSN(lsn.getViewId(),lsn.getSequenceNo()));

        RPCResponse<?> r = sendRequest(null, rq.getOperationNumber(), rq, new RPCResponseDecoder<Object>() {
        
            /*
             * (non-Javadoc)
             * @see org.xtreemfs.include.foundation.oncrpc.client.RPCResponseDecoder#getResult(org.xtreemfs.include.common.buffer.ReusableBuffer)
             */
            @Override
            public Object getResult(ReusableBuffer data) {
                final heartbeatResponse rp = new heartbeatResponse();
                rp.deserialize(data);
                return null;
            }
        });
        
        return r;
    }
}