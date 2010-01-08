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
package org.apache.hadoop.hdfs;

import java.io.IOException;
import java.util.Random;

import junit.framework.TestCase;

import org.apache.commons.logging.impl.Log4JLogger;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.server.namenode.FSNamesystem;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.ipc.RemoteException;
import org.apache.log4j.Level;

/**
 * This class tests the FileStatus API.
 */
public class TestFileStatus extends TestCase {
  {
    ((Log4JLogger)FSNamesystem.LOG).getLogger().setLevel(Level.ALL);
    ((Log4JLogger)FileSystem.LOG).getLogger().setLevel(Level.ALL);
  }

  static final long seed = 0xDEADBEEFL;
  static final int blockSize = 8192;
  static final int fileSize = 16384;

  private void writeFile(FileSystem fileSys, Path name, int repl,
                         int fileSize, int blockSize)
    throws IOException {
    // create and write a file that contains three blocks of data
    FSDataOutputStream stm = fileSys.create(name, true,
                                            fileSys.getConf().getInt("io.file.buffer.size", 4096),
                                            (short)repl, (long)blockSize);
    byte[] buffer = new byte[fileSize];
    Random rand = new Random(seed);
    rand.nextBytes(buffer);
    stm.write(buffer);
    stm.close();
  }

  private void checkFile(FileSystem fileSys, Path name, int repl)
    throws IOException {
    DFSTestUtil.waitReplication(fileSys, name, (short) repl);
  }


  /**
   * Tests various options of DFSShell.
   */
  public void testFileStatus() throws IOException {
    Configuration conf = new HdfsConfiguration();
    MiniDFSCluster cluster = new MiniDFSCluster(conf, 1, true, null);
    FileSystem fs = cluster.getFileSystem();
    final HftpFileSystem hftpfs = cluster.getHftpFileSystem();
    final DFSClient dfsClient = new DFSClient(NameNode.getAddress(conf), conf);
    try {

      //
      // check that / exists
      //
      Path path = new Path("/");
      System.out.println("Path : \"" + path.toString() + "\"");
      System.out.println(fs.isDirectory(path));
      System.out.println(fs.getFileStatus(path).isDir()); 
      assertTrue("/ should be a directory", 
                 fs.getFileStatus(path).isDir() == true);
      
      // make sure getFileInfo returns null for files which do not exist
      FileStatus fileInfo = dfsClient.getFileInfo("/noSuchFile");
      assertTrue(fileInfo == null);

      // make sure getFileInfo throws the appropriate exception for non-relative
      // filenames
      try {
        dfsClient.getFileInfo("non-relative");
        fail("getFileInfo for a non-relative path did not thro IOException");
      } catch (RemoteException re) {
        assertTrue(re.toString().contains("Invalid file name"));
      }

      // create a file in home directory
      //
      Path file1 = new Path("filestatus.dat");
      writeFile(fs, file1, 1, fileSize, blockSize);
      System.out.println("Created file filestatus.dat with one "
                         + " replicas.");
      checkFile(fs, file1, 1);
      assertTrue(file1 + " should be a file", 
                  fs.getFileStatus(file1).isDir() == false);
      assertTrue(fs.getFileStatus(file1).getBlockSize() == blockSize);
      assertTrue(fs.getFileStatus(file1).getReplication() == 1);
      assertTrue(fs.getFileStatus(file1).getLen() == fileSize);
      System.out.println("Path : \"" + file1 + "\"");

      // create an empty directory
      //
      Path dir = new Path("/test/mkdirs");
      assertTrue(fs.mkdirs(dir));
      assertTrue(fs.exists(dir));
      assertTrue(dir + " should be a directory", 
                 fs.getFileStatus(path).isDir() == true);
      assertEquals(dir + " should be zero size ",
          0, fs.getContentSummary(dir).getLength());
      assertEquals(dir + " should be zero size using hftp",
          0, hftpfs.getContentSummary(dir).getLength());
      assertTrue(dir + " should be zero size ",
                 fs.getFileStatus(dir).getLen() == 0);
      System.out.println("Dir : \"" + dir + "\"");

      // create another file that is smaller than a block.
      //
      Path file2 = new Path("/test/mkdirs/filestatus2.dat");
      writeFile(fs, file2, 1, blockSize/4, blockSize);
      System.out.println("Created file filestatus2.dat with one "
                         + " replicas.");
      checkFile(fs, file2, 1);
      System.out.println("Path : \"" + file2 + "\"");

      // verify file attributes
      assertTrue(fs.getFileStatus(file2).getBlockSize() == blockSize);
      assertTrue(fs.getFileStatus(file2).getReplication() == 1);

      // create another file in the same directory
      Path file3 = new Path("/test/mkdirs/filestatus3.dat");
      writeFile(fs, file3, 1, blockSize/4, blockSize);
      System.out.println("Created file filestatus3.dat with one "
                         + " replicas.");
      checkFile(fs, file3, 1);

      // verify that the size of the directory increased by the size 
      // of the two files
      final int expected = blockSize/2;  
      assertEquals(dir + " size should be " + expected, 
          expected, fs.getContentSummary(dir).getLength());
      assertEquals(dir + " size should be " + expected + " using hftp", 
          expected, hftpfs.getContentSummary(dir).getLength());
    } finally {
      fs.close();
      cluster.shutdown();
    }
  }
}
