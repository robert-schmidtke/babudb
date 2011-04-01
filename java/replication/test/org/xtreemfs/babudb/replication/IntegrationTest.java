/*
 * Copyright (c) 2011, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist,
 *                     Felix Hupfeld, Felix Langner, Zuse Institute Berlin
 * 
 * Licensed under the BSD License, see LICENSE file for details.
 * 
 */
package org.xtreemfs.babudb.replication;

import static junit.framework.Assert.*;
import static org.xtreemfs.babudb.replication.TestParameters.*;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xtreemfs.babudb.BabuDBFactory;
import org.xtreemfs.babudb.api.BabuDB;
import org.xtreemfs.babudb.api.database.Database;
import org.xtreemfs.babudb.api.database.DatabaseInsertGroup;
import org.xtreemfs.babudb.config.ReplicationConfig;
import org.xtreemfs.foundation.logging.Logging;
import org.xtreemfs.foundation.logging.Logging.Category;
import org.xtreemfs.foundation.util.FSUtils;

/**
 * Test basic BabuDB IO for a fully synchronous setup of BabuDB instances using replication.
 * 
 * @author flangner
 * @since 03/31/2011
 */
public class IntegrationTest {

    private BabuDB babu0;
    private BabuDB babu1;
    private BabuDB babu2;
    
    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Logging.start(Logging.LEVEL_INFO, Category.all);
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        
        FSUtils.delTree(new File(conf0.getBaseDir()));
        FSUtils.delTree(new File(conf1.getBaseDir()));
        FSUtils.delTree(new File(conf2.getBaseDir()));
        FSUtils.delTree(new File(conf0.getDbLogDir()));
        FSUtils.delTree(new File(conf1.getDbLogDir()));
        FSUtils.delTree(new File(conf2.getDbLogDir()));
        
        FSUtils.delTree(new File(conf0.getBaseDir()));
        FSUtils.delTree(new File(conf1.getBaseDir()));
        FSUtils.delTree(new File(conf2.getBaseDir()));
        FSUtils.delTree(new File(conf0.getDbLogDir()));
        FSUtils.delTree(new File(conf1.getDbLogDir()));
        FSUtils.delTree(new File(conf2.getDbLogDir()));
        
        // starting three local BabuDB services supporting replication; based on mock databases
        FSUtils.delTree(new File(
                new ReplicationConfig("config/replication_server0.test", conf0).getTempDir()));
        babu0 = BabuDBFactory.createBabuDB(conf0);
        
        FSUtils.delTree(new File(
                new ReplicationConfig("config/replication_server1.test", conf1).getTempDir()));
        babu1 = BabuDBFactory.createBabuDB(conf1);
        
        FSUtils.delTree(new File(
                new ReplicationConfig("config/replication_server2.test", conf2).getTempDir()));
        babu2 = BabuDBFactory.createBabuDB(conf2);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
        babu0.shutdown();
        babu1.shutdown();
        babu2.shutdown();
    }

    /**
     * @throws Exception
     */
    @Test
    public void testBasicIO() throws Exception {
                
        // create some DBs
        Database test0 = babu0.getDatabaseManager().createDatabase("0", 3);    
        Database test1 = babu1.getDatabaseManager().createDatabase("1", 3);
        Database test2 = babu2.getDatabaseManager().createDatabase("2", 3);
        
        Thread.sleep(20 * 1000);
        
//        // retrieve the databases
//        test0 = babu2.getDatabaseManager().getDatabase("0");
//        test1 = babu0.getDatabaseManager().getDatabase("1");
//        test2 = babu1.getDatabaseManager().getDatabase("2");
//        
//        // make some inserts
//        DatabaseInsertGroup ig = test0.createInsertGroup();
//        ig.addInsert(0, "bla00".getBytes(), "blub00".getBytes());
//        ig.addInsert(1, "bla01".getBytes(), "blub01".getBytes());
//        ig.addInsert(2, "bla02".getBytes(), "blub02".getBytes());
//        test0.insert(ig, test0).get();
//        
//        ig = test1.createInsertGroup();
//        ig.addInsert(0, "bla10".getBytes(), "blub10".getBytes());
//        ig.addInsert(1, "bla11".getBytes(), "blub11".getBytes());
//        ig.addInsert(2, "bla12".getBytes(), "blub12".getBytes());
//        test1.insert(ig, test1).get();
//        
//        ig = test2.createInsertGroup();
//        ig.addInsert(0, "bla20".getBytes(), "blub20".getBytes());
//        ig.addInsert(1, "bla21".getBytes(), "blub21".getBytes());
//        ig.addInsert(2, "bla22".getBytes(), "blub22".getBytes());
//        test2.insert(ig, test2).get();
//        
//        // retrieve the databases
//        test0 = babu1.getDatabaseManager().getDatabase("0");
//        test1 = babu2.getDatabaseManager().getDatabase("1");
//        test2 = babu0.getDatabaseManager().getDatabase("2");
//        
//        // make some lookups
//        assertEquals("blub00", new String(test0.lookup(0, "bla00".getBytes(), test0).get()));
//        
//        test0.lookup(0, "bla20".getBytes(), test0).get();
    }
}