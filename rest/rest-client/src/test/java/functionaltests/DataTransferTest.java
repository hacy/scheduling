/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2015 INRIA/University of
 *                 Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 *
 *  Initial developer(s):               The ActiveEon Team
 *                        http://www.activeeon.com/
 *  Contributor(s):
 *
 * ################################################################
 * $$ACTIVEEON_INITIAL_DEV$$
 */
package functionaltests;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.ow2.proactive.authentication.ConnectionInfo;
import org.ow2.proactive.scheduler.common.exception.NotConnectedException;
import org.ow2.proactive.scheduler.common.exception.PermissionException;
import org.ow2.proactive.scheduler.rest.ds.*;
import org.ow2.proactive_grid_cloud_portal.common.FileType;
import org.ow2.proactive_grid_cloud_portal.dataspace.dto.ListFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Random;

import static functionaltests.RestFuncTHelper.getRestServerUrl;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.ow2.proactive.scheduler.rest.ds.IDataSpaceClient.Dataspace.USER;


public class DataTransferTest extends AbstractRestFuncTestCase {

    private static final int FILE_SIZE = 100;

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @BeforeClass
    public static void beforeClass() throws Exception {
        init();
    }

    @Test
    public void testUploadSingleFile() throws Exception {

        File tmpFile = tmpDir.newFile("tmpfile.tmp");
        Files.write(randomFileContents(), tmpFile);

        // use standard client
        IDataSpaceClient client = clientInstance();
        LocalFileSource source = new LocalFileSource(tmpFile);
        RemoteDestination dest = new RemoteDestination(USER, "testUploadSingleFile/tmpfile.tmp");

        assertTrue(client.upload(source, dest));
        String destDirPath = URI.create(getScheduler().getUserSpaceURIs().get(0)).getPath();
        File destFile = new File(destDirPath, "testUploadSingleFile/tmpfile.tmp");
        assertTrue(Files.equal(tmpFile, destFile));

        // use RemoteSpace API
        FileUtils.deleteQuietly(destFile);
        client.getUserSpace().pushFile(tmpFile, "testUploadSingleFile/tmpfile.tmp");
        assertTrue(Files.equal(tmpFile, destFile));
    }

    @Test
    public void testUploadAllFilesInDirectory() throws Exception {
        // entire folder
        TestFilesToUploadCreator testFiles = new TestFilesToUploadCreator().invoke();
        File tempTextFile = testFiles.getTempTextFile();
        File tempFile = testFiles.getTempFile();

        // use standard client
        IDataSpaceClient client = clientInstance();
        LocalDirSource source = new LocalDirSource(tmpDir.getRoot());
        RemoteDestination dest = new RemoteDestination(USER, "testUploadAllFilesInDirectory");

        assertTrue(client.upload(source, dest));
        String destRootUri = URI.create(getScheduler().getUserSpaceURIs().get(0)).getPath();
        assertTrue(Files.equal(tempTextFile, new File(destRootUri,
                "testUploadAllFilesInDirectory/tempFile.txt")));
        assertTrue(Files.equal(tempFile, new File(destRootUri,
            "testUploadAllFilesInDirectory/tempDir/tempFile.tmp")));

        // use RemoteSpace API
        FileUtils.deleteDirectory(new File(destRootUri,
                "testUploadAllFilesInDirectory"));

        client.getUserSpace().pushFile(tmpDir.getRoot(), "testUploadAllFilesInDirectory");

        assertTrue(Files.equal(tempTextFile, new File(destRootUri,
                "testUploadAllFilesInDirectory/tempFile.txt")));
        assertTrue(Files.equal(tempFile, new File(destRootUri,
                "testUploadAllFilesInDirectory/tempDir/tempFile.tmp")));

    }

    @Test
    public void testUploadSelectedFilesUsingGlobPattern() throws Exception {
        TestFilesToUploadCreator testFiles = new TestFilesToUploadCreator().invoke();
        File tempTextFile = testFiles.getTempTextFile();
        File tempFile = testFiles.getTempFile();


        // use standard client
        IDataSpaceClient client = clientInstance();
        LocalDirSource source = new LocalDirSource(tmpDir.getRoot());
        source.setIncludes("*.txt");
        RemoteDestination dest = new RemoteDestination(USER, "testUploadSelectedFilesUsingGlobPattern");
        assertTrue(client.upload(source, dest));

        String destRootUri = URI.create(getScheduler().getUserSpaceURIs().get(0)).getPath();
        File[] destRootFiles = new File(destRootUri, "testUploadSelectedFilesUsingGlobPattern").listFiles();
        assertEquals(1, destRootFiles.length);
        assertTrue(Files.equal(tempTextFile, destRootFiles[0]));

        // use RemoteSpace API
        FileUtils.deleteDirectory(new File(destRootUri,
                "testUploadSelectedFilesUsingGlobPattern"));
        client.getUserSpace().pushFiles(tmpDir.getRoot(), "*.txt", "testUploadSelectedFilesUsingGlobPattern");
        destRootFiles = new File(destRootUri, "testUploadSelectedFilesUsingGlobPattern").listFiles();
        assertEquals(1, destRootFiles.length);
        assertTrue(Files.equal(tempTextFile, destRootFiles[0]));
    }

    @Test
    public void testUploadSelectedFilesUsingFilenames() throws Exception {

        TestFilesToUploadCreator testFiles = new TestFilesToUploadCreator().invoke();
        File tempTextFile = testFiles.getTempTextFile();
        File tempFile = testFiles.getTempFile();

        // use standard client
        IDataSpaceClient client = clientInstance();
        LocalDirSource source = new LocalDirSource(tmpDir.getRoot());
        source.setIncludes("**/tempFile.tmp");
        RemoteDestination dest = new RemoteDestination(USER, "testUploadSelectedFilesUsingFilenames");
        assertTrue(client.upload(source, dest));

        String destRootUri = URI.create(getScheduler().getUserSpaceURIs().get(0)).getPath();
        File[] destRootFiles = new File(destRootUri, "testUploadSelectedFilesUsingFilenames")
                .listFiles();
        assertEquals(1, destRootFiles.length);
        assertTrue(Files.equal(tempFile, destRootFiles[0]));

        // use RemoteSpace API
        FileUtils.deleteDirectory(new File(destRootUri,
                "testUploadSelectedFilesUsingFilenames"));
        client.getUserSpace().pushFiles(tmpDir.getRoot(), "**/tempFile.tmp", "testUploadSelectedFilesUsingFilenames");
        destRootFiles = new File(destRootUri, "testUploadSelectedFilesUsingFilenames").listFiles();
        assertEquals(1, destRootFiles.length);
        assertTrue(Files.equal(tempFile, destRootFiles[0]));
    }

    @Test
    public void testDownloadFile() throws Exception {
        String srcDirPath = URI.create(getScheduler().getUserSpaceURIs().get(0)).getPath();
        File srcFile = new File(srcDirPath, "tmpfile.tmp");
        if (srcFile.exists()) {
            assertTrue(srcFile.delete());
        }
        Files.write(randomFileContents(), srcFile);

        File tmpFile = tmpDir.newFile("tmpfile.tmp");
        if (tmpFile.exists()) {
            assertTrue(tmpFile.delete());
        }

        // use standard client
        IDataSpaceClient client = clientInstance();
        RemoteSource source = new RemoteSource(USER, "tmpfile.tmp");
        LocalDestination dest = new LocalDestination(tmpFile);
        assertTrue(client.download(source, dest));
        assertTrue(Files.equal(srcFile, tmpFile));

        // use RemoteSpace API
        FileUtils.deleteQuietly(tmpFile);
        File downloadedFile = client.getUserSpace().pullFile("tmpfile.tmp", tmpFile);
        assertTrue(Files.equal(srcFile, downloadedFile));

    }

    @Test
    public void testDownloadAllFilesInDirectory() throws Exception {
        String srcDirPath = URI.create(getScheduler().getUserSpaceURIs().get(0)).getPath();
        String dirName = "testDownloadAllFilesInDirectory";

        TestFilesToDownloadCreator testFilesToDownloadCreator = new TestFilesToDownloadCreator(srcDirPath, dirName).invoke();
        File srcTextFile = testFilesToDownloadCreator.getSrcTextFile();
        File srcTempFile = testFilesToDownloadCreator.getSrcTempFile();

        File destTempDir = tmpDir.newFolder("tempDir");

        // use standard client
        IDataSpaceClient client = clientInstance();
        RemoteSource source = new RemoteSource(USER, "testDownloadAllFilesInDirectory");
        LocalDestination dest = new LocalDestination(destTempDir);

        assertTrue(client.download(source, dest));

        assertTrue(Files.equal(srcTextFile, new File(destTempDir, "tempFile.txt")));
        assertTrue(Files.equal(srcTempFile, new File(destTempDir, "tempDir/tempFile.tmp")));

        // use RemoteSpace API
        File destTempDir2 = tmpDir.newFolder("tempDir2");
        client.getUserSpace().pullFile("testDownloadAllFilesInDirectory", destTempDir2);

        assertTrue(Files.equal(srcTextFile, new File(destTempDir2, "tempFile.txt")));
        assertTrue(Files.equal(srcTempFile, new File(destTempDir2, "tempDir/tempFile.tmp")));

    }

    @Test
    public void testDownloadSelectedFilesUsingGlobPattern() throws Exception {
        String srcDirPath = URI.create(getScheduler().getUserSpaceURIs().get(0)).getPath();

        String dirName = "testDownloadSelectedFilesUsingGlobPattern";

        TestFilesToDownloadCreator testFilesToDownloadCreator = new TestFilesToDownloadCreator(srcDirPath, dirName).invoke();
        File srcTextFile = testFilesToDownloadCreator.getSrcTextFile();
        File srcTempFile = testFilesToDownloadCreator.getSrcTempFile();

        File destTempDir = tmpDir.newFolder("tempDir");

        // use standard client
        IDataSpaceClient client = clientInstance();
        RemoteSource source = new RemoteSource(USER, dirName);
        source.setIncludes("*.txt");
        LocalDestination dest = new LocalDestination(destTempDir);

        assertTrue(client.download(source, dest));

        File[] listFiles = destTempDir.listFiles();

        assertEquals(1, listFiles.length);
        assertTrue(Files.equal(srcTextFile, listFiles[0]));

        // use RemoteSpace API
        File destTempDir2 = tmpDir.newFolder("tempDir2");
        client.getUserSpace().pullFiles(dirName, "*.txt", destTempDir2);

        listFiles = destTempDir2.listFiles();

        assertEquals(1, listFiles.length);
        assertTrue(Files.equal(srcTextFile, listFiles[0]));
    }

    @Test
    public void testListFilesNonRecursive() throws Exception {
        createFilesInUserSpace("testListFilesNonRecursive");

        // use standard client
        IDataSpaceClient client = clientInstance();
        RemoteSource source = new RemoteSource(USER, "testListFilesNonRecursive");

        ListFile listFile = client.list(source, false);

        List<String> directories = listFile.getDirectoryListing();
        assertEquals(1, directories.size());
        assertEquals("tempDir", directories.get(0));

        List<String> files = listFile.getFileListing();
        assertEquals(1, files.size());
        assertEquals("tempFile.txt", files.get(0));


        // use RemoteSpace API
        List<String> foundFiles = client.getUserSpace().listFiles("testListFilesNonRecursive", false);
        assertEquals(2, foundFiles.size());
        assertArrayEquals(new String[]{"tempDir", "tempFile.txt"}, foundFiles.toArray(new String[0]));
    }

    private void createFilesInUserSpace(String testListFilesNonRecursive) throws NotConnectedException, PermissionException, IOException {
        String srcDirPath = URI.create(getScheduler().getUserSpaceURIs().get(0)).getPath();
        new TestFilesToDownloadCreator(srcDirPath, testListFilesNonRecursive).invoke();
    }

    @Test
    public void testListFilesRecursive() throws Exception {
        createFilesInUserSpace("testListFilesRecursive");

        // use standard client
        IDataSpaceClient client = clientInstance();
        RemoteSource source = new RemoteSource(USER, "testListFilesRecursive");

        ListFile listFile = client.list(source, true);

        List<String> directories = listFile.getDirectoryListing();
        assertEquals(1, directories.size());
        assertEquals("tempDir", directories.get(0));

        List<String> files = listFile.getFileListing();
        assertEquals(2, files.size());
        assertEquals("tempFile.tmp", files.get(0));
        assertEquals("tempFile.txt", files.get(1));


        // use RemoteSpace API
        List<String> foundFiles = client.getUserSpace().listFiles("testListFilesRecursive", true);
        assertEquals(3, foundFiles.size());
        assertArrayEquals(new String[]{"tempDir", "tempDir/tempFile.tmp", "tempFile.txt"}, foundFiles.toArray(new String[0]));
    }

    @Test
    public void testListFilesRecursiveWithPattern() throws Exception {
        createFilesInUserSpace("testListFilesRecursiveWithPattern");

        // use standard client
        IDataSpaceClient client = clientInstance();
        RemoteSource source = new RemoteSource(USER, "testListFilesRecursiveWithPattern");
        source.setIncludes("**/*.tmp");

        ListFile listFile = client.list(source, true);

        List<String> directories = listFile.getDirectoryListing();
        assertEquals(0, directories.size());

        List<String> files = listFile.getFileListing();
        assertEquals(1, files.size());
        assertEquals("tempFile.tmp", files.get(0));


        // use RemoteSpace API
        List<String> foundFiles = client.getUserSpace().listFiles("testListFilesRecursiveWithPattern", "**/*.tmp");
        assertEquals(1, foundFiles.size());
        assertArrayEquals(new String[]{"tempDir/tempFile.tmp"}, foundFiles.toArray(new String[0]));
    }

    @Test
    public void testDeleteFile() throws Exception {
        URI srcDirPath = URI.create(getScheduler().getUserSpaceURIs().get(0));
        File srcFile = new File(new File(srcDirPath), "tempFile.tmp");
        if (srcFile.exists()) {
            assertTrue(srcFile.delete());
        }
        Files.write(randomFileContents(), srcFile);

        // use standard client
        IDataSpaceClient client = clientInstance();
        RemoteSource source = new RemoteSource(USER, "tempFile.tmp");
        assertTrue(client.delete(source));
        assertFalse(srcFile.exists());

        // use RemoteSpace API
        Files.write(randomFileContents(), srcFile);
        client.getUserSpace().deleteFile("tempFile.tmp");
        assertFalse(srcFile.exists());
    }

    @Test
    public void testCreateFile() throws Exception {
        URI srcDirPath = URI.create(getScheduler().getUserSpaceURIs().get(0));

        String filename = "tempFile.tmp";

        RemoteSource source = new RemoteSource(USER, filename);
        source.setType(FileType.FILE);

        IDataSpaceClient client = clientInstance();
        assertTrue(client.create(source));

        File expectedFile = new File(srcDirPath.getPath(), filename);

        assertTrue(expectedFile.exists());
        assertTrue(expectedFile.isFile());
    }

    @Test
    public void testCreateFolder() throws Exception {
        URI srcDirPath = URI.create(getScheduler().getUserSpaceURIs().get(0));

        String folderName = "testcreatefolder";

        RemoteSource source = new RemoteSource(USER, folderName);
        source.setType(FileType.FOLDER);

        IDataSpaceClient client = clientInstance();
        assertTrue(client.create(source));

        File expectedFile = new File(srcDirPath.getPath(), folderName);

        assertTrue(expectedFile.exists());
        assertTrue(expectedFile.isDirectory());
    }

    @Test(expected = Exception.class)
    public void testCreateFolderWithoutSpecifyingFileType() throws Exception {
        String folderName = "testcreatefolder";

        RemoteSource source = new RemoteSource(USER, folderName);
        // file is not specified

        IDataSpaceClient client = clientInstance();
        assertTrue(client.create(source));
    }

    private byte[] randomFileContents() {
        byte[] fileContents = new byte[FILE_SIZE];
        new Random().nextBytes(fileContents);
        return fileContents;
    }

    private IDataSpaceClient clientInstance() throws Exception {
        DataSpaceClient client = new DataSpaceClient();
        client.init(new ConnectionInfo(getRestServerUrl(), getLogin(), getPassword(), null, true));
        return client;
    }

    private class TestFilesToUploadCreator {
        private File tempTextFile;
        private File tempFile;

        public File getTempTextFile() {
            return tempTextFile;
        }

        public File getTempFile() {
            return tempFile;
        }

        public TestFilesToUploadCreator invoke() throws IOException {
            tempTextFile = tmpDir.newFile("tempFile.txt");
            Files.write("some text ...".getBytes(), tempTextFile);

            File tempDir = tmpDir.newFolder("tempDir");
            tempFile = new File(tempDir, "tempFile.tmp");
            Files.createParentDirs(tempFile);
            Files.write(randomFileContents(), tempFile);
            return this;
        }
    }

    private class TestFilesToDownloadCreator {
        private String srcDirPath;
        private String dirName;
        private File srcTextFile;
        private File srcTempFile;

        public TestFilesToDownloadCreator(String srcDirPath, String dirName) {
            this.srcDirPath = srcDirPath;
            this.dirName = dirName;
        }

        public File getSrcTextFile() {
            return srcTextFile;
        }

        public File getSrcTempFile() {
            return srcTempFile;
        }

        public TestFilesToDownloadCreator invoke() throws IOException {
            File srcDir = new File(srcDirPath, dirName);
            if (srcDir.exists()) {
                FileUtils.deleteDirectory(srcDir);
            }

            srcTextFile = new File(srcDir, "tempFile.txt");
            Files.createParentDirs(srcTextFile);
            Files.write("some text ...".getBytes(), srcTextFile);

            File srcTempDir = new File(srcDir, "tempDir");
            srcTempFile = new File(srcTempDir, "tempFile.tmp");
            Files.createParentDirs(srcTempFile);
            Files.write(randomFileContents(), srcTempFile);
            return this;
        }
    }
}
