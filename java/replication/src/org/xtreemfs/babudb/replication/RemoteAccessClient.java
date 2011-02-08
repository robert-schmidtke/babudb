/*
 * Copyright (c) 2011, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Felix Langner, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */
/*
 * AUTHORS: Felix Langner (ZIB)
 */
package org.xtreemfs.babudb.replication;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.xtreemfs.babudb.api.PersistenceManager;
import org.xtreemfs.babudb.api.database.Database;
import org.xtreemfs.babudb.replication.service.clients.ClientResponseFuture;
import org.xtreemfs.foundation.buffer.ReusableBuffer;

/**
 * RPCClient for delegating BabuDB requests to the instance with master 
 * privilege.
 * 
 * @author flangner
 * @since 01/19/2011
 */
public interface RemoteAccessClient {
    
    /**
     * RPC for delegating the duties of {@link PersistenceManager} to a remote
     * BabuDB instance with master privilege.
     * 
     * @param master
     * @param type
     * @param data
     * @return the request's response future.
     */
    public ClientResponseFuture<?> makePersistent(InetSocketAddress master, 
            int type, ReusableBuffer data);
    
    /**
     * RPC for requesting the Id of a {@link Database} belonging to the given name.
     * 
     * @param dbName
     * @param master
     * @return the request's response future.
     */
    public ClientResponseFuture<Integer> getDatabase(String dbName, InetSocketAddress master);
    
    /**
     * RPC for requesting a list of available {@link Database} IDs and names at 
     * the master.
     * 
     * @param master
     * @return the request's response future.
     */
    public ClientResponseFuture<Map<String, Integer>> getDatabases(
            InetSocketAddress master);
    
    /**
     * RPC for looking up a key's value at the master.
     * 
     * @param dbName
     * @param indexId
     * @param key
     * @param master
     * @return the request's response future.
     */
    public ClientResponseFuture<byte[]> lookup(String dbName, int indexId, 
            ReusableBuffer key, InetSocketAddress master);
    
    /**
     * RPC for a prefix-lookup at the master.
     * 
     * @param dbName
     * @param indexId
     * @param key
     * @param master
     * @return the request's response future.
     */
    public ClientResponseFuture<Iterator<Entry<byte[], byte[]>>> prefixLookup(
            String dbName, int indexId, ReusableBuffer key, 
            InetSocketAddress master);
    
    /**
     * RPC for a reverse prefix-lookup at the master.
     * 
     * @param dbName
     * @param indexId
     * @param key
     * @param master
     * @return the request's response future.
     */
    public ClientResponseFuture<Iterator<Entry<byte[], byte[]>>> prefixLookupR(
            String dbName, int indexId, ReusableBuffer key, 
            InetSocketAddress master);
    
    /**
     * RPC for a range-lookup at the master.
     * 
     * @param dbName
     * @param indexId
     * @param from
     * @param to
     * @param master
     * @return the request's response future.
     */
    public ClientResponseFuture<Iterator<Entry<byte[], byte[]>>> rangeLookup(
            String dbName, int indexId, ReusableBuffer from, ReusableBuffer to, 
            InetSocketAddress master);
    
    /**
     * RPC for a reverse range-lookup at the master.
     * 
     * @param dbName
     * @param indexId
     * @param from
     * @param to
     * @param master
     * @return the request's response future.
     */
    public ClientResponseFuture<Iterator<Entry<byte[], byte[]>>> rangeLookupR(
            String dbName, int indexId, ReusableBuffer from, ReusableBuffer to, 
            InetSocketAddress master);
}
