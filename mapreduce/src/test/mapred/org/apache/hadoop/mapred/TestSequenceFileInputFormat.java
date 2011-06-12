/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.mapred;

import java.io.*;
import java.util.*;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.logging.*;

import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.conf.*;

public class TestSequenceFileInputFormat {
  private static final Log LOG = FileInputFormat.LOG;

  private static int MAX_LENGTH = 10000;
  private static Configuration conf = new Configuration();
  static final Path TESTDIR =
    new Path(System.getProperty("test.build.data", "/tmp"),
        TestSequenceFileInputFormat.class.getSimpleName());

  @Before
  public void removeTestdir() throws IOException {
    final FileSystem rfs = FileSystem.getLocal(new Configuration()).getRaw();
    rfs.delete(TESTDIR, true);
  }

  @Test
  public void testFormat() throws Exception {
    JobConf job = new JobConf(conf);
    FileSystem fs = FileSystem.getLocal(conf);
    Path file = new Path(TESTDIR, "test.seq").makeQualified(fs);
    
    Reporter reporter = Reporter.NULL;
    
    int seed = new Random().nextInt();
    //LOG.info("seed = "+seed);
    Random random = new Random(seed);

    FileInputFormat.setInputPaths(job, TESTDIR);

    // for a variety of lengths
    for (int length = 0; length < MAX_LENGTH;
         length+= random.nextInt(MAX_LENGTH/10)+1) {

      //LOG.info("creating; entries = " + length);

      // create a file with length entries
      SequenceFile.Writer writer =
        SequenceFile.createWriter(fs, conf, file,
                                  IntWritable.class, BytesWritable.class);
      try {
        for (int i = 0; i < length; i++) {
          IntWritable key = new IntWritable(i);
          byte[] data = new byte[random.nextInt(10)];
          random.nextBytes(data);
          BytesWritable value = new BytesWritable(data);
          writer.append(key, value);
        }
      } finally {
        writer.close();
      }

      // try splitting the file in a variety of sizes
      InputFormat<IntWritable, BytesWritable> format =
        new SequenceFileInputFormat<IntWritable, BytesWritable>();
      IntWritable key = new IntWritable();
      BytesWritable value = new BytesWritable();
      for (int i = 0; i < 3; i++) {
        int numSplits =
          random.nextInt(MAX_LENGTH/(SequenceFile.SYNC_INTERVAL/20))+1;
        //LOG.info("splitting: requesting = " + numSplits);
        InputSplit[] splits = format.getSplits(job, numSplits);
        //LOG.info("splitting: got =        " + splits.length);

        // check each split
        BitSet bits = new BitSet(length);
        for (int j = 0; j < splits.length; j++) {
          RecordReader<IntWritable, BytesWritable> reader =
            format.getRecordReader(splits[j], job, reporter);
          try {
            int count = 0;
            while (reader.next(key, value)) {
              // if (bits.get(key.get())) {
              // LOG.info("splits["+j+"]="+splits[j]+" : " + key.get());
              // LOG.info("@"+reader.getPos());
              // }
              assertFalse("Key in multiple partitions.", bits.get(key.get()));
              bits.set(key.get());
              count++;
            }
            //LOG.info("splits["+j+"]="+splits[j]+" count=" + count);
          } finally {
            reader.close();
          }
        }
        assertEquals("Some keys in no partition.", length, bits.cardinality());
      }

      fs.delete(TESTDIR, true);
    }
  }

  public static void main(String[] args) throws Exception {
    new TestSequenceFileInputFormat().testFormat();
  }
}
