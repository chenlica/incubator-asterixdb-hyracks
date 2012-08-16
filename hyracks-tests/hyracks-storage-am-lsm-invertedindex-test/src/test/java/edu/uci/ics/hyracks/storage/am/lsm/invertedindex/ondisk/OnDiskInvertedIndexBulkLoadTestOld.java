/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.hyracks.storage.am.lsm.invertedindex.ondisk;

import java.io.DataOutput;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import junit.framework.Assert;

import org.junit.AfterClass;
import org.junit.Test;

import edu.uci.ics.hyracks.api.comm.IFrameTupleAccessor;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTraits;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.data.std.accessors.PointableBinaryComparatorFactory;
import edu.uci.ics.hyracks.data.std.primitive.IntegerPointable;
import edu.uci.ics.hyracks.data.std.primitive.UTF8StringPointable;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAppender;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.FrameTupleReference;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ITupleReference;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTree;
import edu.uci.ics.hyracks.storage.am.common.api.IIndexBulkLoader;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.IndexOp;
import edu.uci.ics.hyracks.storage.am.lsm.invertedindex.api.IInvertedListBuilder;
import edu.uci.ics.hyracks.storage.am.lsm.invertedindex.api.IInvertedListCursor;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;
import edu.uci.ics.hyracks.storage.common.file.IFileMapProvider;
import edu.uci.ics.hyracks.test.support.TestStorageManagerComponentHolder;
import edu.uci.ics.hyracks.test.support.TestUtils;

public class OnDiskInvertedIndexBulkLoadTestOld extends AbstractInvIndexTest {

    private static final int PAGE_SIZE = 32768;
    private static final int NUM_PAGES = 100;
    private static final int MAX_OPEN_FILES = 10;
    private static final int HYRACKS_FRAME_SIZE = 32768;
    private IHyracksTaskContext stageletCtx = TestUtils.create(HYRACKS_FRAME_SIZE);

    /**
     * This test generates a list of <word-token, id> pairs which are pre-sorted
     * on the token. Those pairs for the input to an inverted-index bulk load.
     * The contents of the inverted lists are verified against the generated
     * data.
     */
    @Test
    public void singleFieldPayloadTest() throws Exception {
        TestStorageManagerComponentHolder.init(PAGE_SIZE, NUM_PAGES, MAX_OPEN_FILES);
        IBufferCache bufferCache = TestStorageManagerComponentHolder.getBufferCache(stageletCtx);
        IFileMapProvider fmp = TestStorageManagerComponentHolder.getFileMapProvider(stageletCtx);

        FileReference invListsFile = new FileReference(new File(invListsFileName));
        FileReference btreeFile = new FileReference(new File(invListsFileName + "_btree"));

        ITypeTraits[] tokenTypeTraits = new ITypeTraits[] { UTF8StringPointable.TYPE_TRAITS };

        IBinaryComparatorFactory[] cmpFactories = new IBinaryComparatorFactory[] { PointableBinaryComparatorFactory
                .of(UTF8StringPointable.FACTORY) };

        int invListFields = 1;
        ITypeTraits[] invListTypeTraits = new ITypeTraits[invListFields];
        invListTypeTraits[0] = IntegerPointable.TYPE_TRAITS;

        int invListKeys = 1;
        IBinaryComparatorFactory[] invListCmpFactories = new IBinaryComparatorFactory[invListKeys];
        invListCmpFactories[0] = PointableBinaryComparatorFactory.of(IntegerPointable.FACTORY);

        IInvertedListBuilder invListBuilder = new FixedSizeElementInvertedListBuilder(invListTypeTraits);
        OnDiskInvertedIndex invIndex = new OnDiskInvertedIndex(bufferCache, fmp, invListBuilder, invListTypeTraits,
                invListCmpFactories, tokenTypeTraits, cmpFactories, invListsFile, btreeFile);
        invIndex.create();
        invIndex.activate();

        Random rnd = new Random();
        rnd.setSeed(50);

        ByteBuffer frame = stageletCtx.allocateFrame();
        FrameTupleAppender appender = new FrameTupleAppender(stageletCtx.getFrameSize());
        ArrayTupleBuilder tb = new ArrayTupleBuilder(2);
        DataOutput dos = tb.getDataOutput();

        ISerializerDeserializer[] insertSerde = { UTF8StringSerializerDeserializer.INSTANCE,
                IntegerSerializerDeserializer.INSTANCE };
        RecordDescriptor insertRecDesc = new RecordDescriptor(insertSerde);
        IFrameTupleAccessor accessor = new FrameTupleAccessor(stageletCtx.getFrameSize(), insertRecDesc);
        accessor.reset(frame);
        FrameTupleReference tuple = new FrameTupleReference();

        List<String> tokens = new ArrayList<String>();
        tokens.add("compilers");
        tokens.add("computer");
        tokens.add("databases");
        tokens.add("fast");
        tokens.add("hyracks");
        tokens.add("major");
        tokens.add("science");
        tokens.add("systems");
        tokens.add("university");

        ArrayList<ArrayList<Integer>> checkListElements = new ArrayList<ArrayList<Integer>>();
        for (int i = 0; i < tokens.size(); i++) {
            checkListElements.add(new ArrayList<Integer>());
        }

        int maxId = 1000000;
        int addProb = 0;
        int addProbStep = 10;

        IIndexBulkLoader bulkLoader = invIndex.createBulkLoader(BTree.DEFAULT_FILL_FACTOR, false);

        for (int i = 0; i < tokens.size(); i++) {

            addProb += addProbStep * (i + 1);
            for (int j = 0; j < maxId; j++) {
                if ((Math.abs(rnd.nextInt()) % addProb) == 0) {

                    tb.reset();
                    UTF8StringSerializerDeserializer.INSTANCE.serialize(tokens.get(i), dos);
                    tb.addFieldEndOffset();
                    IntegerSerializerDeserializer.INSTANCE.serialize(j, dos);
                    tb.addFieldEndOffset();

                    checkListElements.get(i).add(j);

                    appender.reset(frame, true);
                    appender.append(tb.getFieldEndOffsets(), tb.getByteArray(), 0, tb.getSize());

                    tuple.reset(accessor, 0);

                    try {
                        bulkLoader.add(tuple);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        bulkLoader.end();

        // ------- START VERIFICATION -----------

        FrameTupleReference searchKey = new FrameTupleReference();
        IInvertedListCursor invListCursor = new FixedSizeElementInvertedListCursor(bufferCache,
                fmp.lookupFileId(invListsFile), invListTypeTraits);

        ISerializerDeserializer[] tokenSerde = { UTF8StringSerializerDeserializer.INSTANCE };
        RecordDescriptor tokenRecDesc = new RecordDescriptor(tokenSerde);
        FrameTupleAppender tokenAppender = new FrameTupleAppender(stageletCtx.getFrameSize());
        ArrayTupleBuilder tokenTupleBuilder = new ArrayTupleBuilder(1);
        DataOutput tokenDos = tokenTupleBuilder.getDataOutput();
        IFrameTupleAccessor tokenAccessor = new FrameTupleAccessor(stageletCtx.getFrameSize(), tokenRecDesc);
        tokenAccessor.reset(frame);

        // verify created inverted lists one-by-one
        OnDiskInvertedIndexOpContext opCtx = new OnDiskInvertedIndexOpContext(invIndex.getBTree());
        opCtx.reset(IndexOp.SEARCH);
        for (int i = 0; i < tokens.size(); i++) {

            tokenTupleBuilder.reset();
            UTF8StringSerializerDeserializer.INSTANCE.serialize(tokens.get(i), tokenDos);
            tokenTupleBuilder.addFieldEndOffset();

            tokenAppender.reset(frame, true);
            tokenAppender.append(tokenTupleBuilder.getFieldEndOffsets(), tokenTupleBuilder.getByteArray(), 0,
                    tokenTupleBuilder.getSize());

            searchKey.reset(tokenAccessor, 0);

            invIndex.openInvertedListCursor(invListCursor, searchKey, opCtx);

            invListCursor.pinPages();
            int checkIndex = 0;
            while (invListCursor.hasNext()) {
                invListCursor.next();
                ITupleReference invListTuple = invListCursor.getTuple();
                int invListElement = IntegerSerializerDeserializer.getInt(invListTuple.getFieldData(0),
                        invListTuple.getFieldStart(0));
                int checkInvListElement = checkListElements.get(i).get(checkIndex).intValue();
                Assert.assertEquals(invListElement, checkInvListElement);
                checkIndex++;
            }
            invListCursor.unpinPages();
            Assert.assertEquals(checkIndex, checkListElements.get(i).size());
        }

        // check that non-existing tokens have an empty inverted list
        List<String> nonExistingTokens = new ArrayList<String>();
        nonExistingTokens.add("watermelon");
        nonExistingTokens.add("avocado");
        nonExistingTokens.add("lemon");

        for (int i = 0; i < nonExistingTokens.size(); i++) {

            tokenTupleBuilder.reset();
            UTF8StringSerializerDeserializer.INSTANCE.serialize(nonExistingTokens.get(i), tokenDos);
            tokenTupleBuilder.addFieldEndOffset();

            tokenAppender.reset(frame, true);
            tokenAppender.append(tokenTupleBuilder.getFieldEndOffsets(), tokenTupleBuilder.getByteArray(), 0,
                    tokenTupleBuilder.getSize());

            searchKey.reset(tokenAccessor, 0);

            invIndex.openInvertedListCursor(invListCursor, searchKey, opCtx);
            
            invListCursor.pinPages();
            Assert.assertEquals(invListCursor.hasNext(), false);
            invListCursor.unpinPages();
        }

        invIndex.deactivate();
        invIndex.destroy();
        bufferCache.close();
    }

    @AfterClass
    public static void deinit() {
        AbstractInvIndexTest.tearDown();
    }
}