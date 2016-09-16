package de.hofuniversity.iisys.camunda.workflows.nuxeo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nuxeo.ecm.automation.client.model.Blob;
import org.nuxeo.ecm.automation.client.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If You change the Nuxeo-System. The UUIDs will be different than the used ones here! You need to change those accordingly.
 * @author cstrobel
 */
public class NuxeoUtilityTest {
    private static final transient Logger LOG = LoggerFactory.getLogger(NuxeoUtilityTest.class);

    private static final String URL = "http://127.0.0.1:8080/nuxeo/";

    public static final String TEST_SECTION = "d002f289-bf07-4881-be5a-cb2429ec3f58";
    public static final String TEST_DOCUMENT_UUID = "88cdccd0-ab0c-439d-ac2f-5c1cf65396a2";
    public static final String TEST_FOLDER_UUID = "dceba628-4e7d-4fdd-b7ed-09e0ac39695a";
    public static final String TEST_FOLDER2_UUID = "e5594598-a2df-4c24-b947-c9db46d0e36e";

    private static NuxeoUtility nuxeo;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        nuxeo = new NuxeoUtility(URL, "demo", "secret");
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        nuxeo.shutdown();
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testGetDocuments() throws Exception {
        // LOG.debug(nuxeo.getAllDocumentIds() + "");
        // LOG.debug(nuxeo.getSession() + "");

        Document doc = nuxeo.getDocument(TEST_DOCUMENT_UUID);
        assertNotNull(doc);
    }

    @Test
    public void testGetCollections() throws Exception {
        List<Document> collections = nuxeo.getCollections("");
        assertEquals("currently 1", true, collections.size() >= 1);

        // LOG.debug("Docs:" + collections);
        // for (Document doc : collections) {
        // LOG.debug(doc.getTitle());
        // }
        // -----
        List<Document> collections2 = nuxeo.getCollections("testCollection");
        assertEquals("currently one Colletions is named like that", true, collections2.size() >= 1);

        // LOG.debug("Docs:" + collections2);
        // for (Document doc : collections2) {
        // LOG.debug(doc.getTitle());
        // }
    }

    @Test
    public void testCreateCollection() throws Exception {
        // TODO find a way to check if the description was set
        String name = "tempCollection";
        Document document = nuxeo.createCollection(name, "description", null);
        assertNotNull(document);
        assertEquals("", name, document.getTitle());
        // assertEquals("", "description", document.getString("dc:description"));

        nuxeo.deleteDocument(document);

        // ----Doc2
        Document document2 = nuxeo.createCollection(name, null, null);
        assertEquals("", name, document2.getTitle());
        assertNotNull(document2);
        nuxeo.deleteDocument(document2);

        // ----Doc3
        Exception exc = null;
        try {
            Document document3 = nuxeo.createCollection(name, "", document);
            nuxeo.deleteDocument(document3);
        } catch (Exception e) {
            // not a folder exc
            exc = e;
        }
        assertNotNull(exc);

        // ----Doc4
        Document document4 = nuxeo.createCollection(name, "", null);
        assertEquals("", name, document4.getTitle());
        nuxeo.deleteDocument(document4);

    }

    @Test
    public void testAddDocumentsToCollectionAndGetDocsFromCollection() throws Exception {

        // get a Folder
        // // Default domain> Workspaces> cstrobel-test> testFolder
        Document folder = nuxeo.getDocument(TEST_FOLDER_UUID);

        Document testDocument = createTestDocumentInFolder(folder);

        assertEquals("", "Note", testDocument.getType());
        String path = "/default-domain/workspaces/java-junit-testfolders/testFolder/testDoc";
        assertEquals("", path, testDocument.getPath().substring(0, path.length()));// To cut the UUID

        // finally create Collection and add the Documents to the collection
        List<Document> list = new ArrayList<>();
        list.add(testDocument);

        Document collection = nuxeo.createCollection("someTestCollection", "", null);
        nuxeo.addDocumentsToCollection(collection, list);

        // check if the documents are realy added to the collection
        List<Document> documentsFromCollection = nuxeo.getDocumentsFromCollection(collection);
        boolean isTheDocumentInIt = false;
        for (Document doc : documentsFromCollection) {
            if (doc.getId().equals(testDocument.getId())) {
                isTheDocumentInIt = true;
                break;
            }
        }
        assertEquals("The Doc should be in the Collection", true, isTheDocumentInIt);

        // finally delete the test suff
        for (Document doc : list) {
            nuxeo.deleteDocument(doc);
        }
        nuxeo.deleteDocument(collection);

        LOG.debug("" + collection);
    }

    private Document createTestDocumentInFolder(Document folder) throws Exception {
        // Create a Document
        // Map<String, Object> params = new HashMap<String, Object>();
        // params.put("name", "testDoc");
        // params.put("type", "Note");
        Properties properties = new Properties();
        properties.setProperty("dc:title", "testDocument");
        properties.setProperty("dc:description", "some Description");
        Document document = nuxeo.createDocument(folder, "Note", properties);
        return document;
    }
    @Test
    public void testMoveDocumentToFolder() throws Exception {
        // test Doc
        Document document = null;
        try {
            // get target Folder
            Document folder = nuxeo.getDocument(TEST_FOLDER2_UUID);
            document = createTestDocumentInFolder(nuxeo.getDocument(TEST_FOLDER_UUID));

            //check path
            String path = "/default-domain/workspaces/java-junit-testfolders/testFolder/testDocument";
            LOG.debug(document.getPath());
            assertEquals("", path, document.getPath().substring(0, path.length()));

            // move it move it
            Document newDoc = nuxeo.moveDocument(document, folder);

            //check path
            path = "/default-domain/workspaces/java-junit-testfolders/testFolder2/testDocument";
            assertEquals("", path, newDoc.getPath().substring(0, path.length()));
        } catch (Exception e) {
            throw e;
        } finally {
            // delete
            nuxeo.deleteDocument(document);
        }
    }

    @Test
    public void testRenderDocument() throws Exception {
        // get a Folder
        // // Default domain> Workspaces> cstrobel-test> testFolder
        Document folder = nuxeo.getDocument(TEST_FOLDER_UUID);
        // create TestDoc
        Document document = createTestDocumentInFolder(folder);

        // render the Document
        final String name = "testOutput.ftl";
        Blob renderDocument = nuxeo.renderDocument(document, "templates:Customer Reference using ODT Template", name, "text/xml", "ftl, mvel");
        assertEquals("", name, renderDocument.getFileName());
        assertNotNull("", renderDocument);

        // render again
        Blob renderDocument2 = nuxeo.renderDocument(document, "templates:SpecNux", null, null, null);
        assertNotNull("", renderDocument2);
        assertEquals("", "output.ftl", renderDocument2.getFileName());

        // delete doc
        nuxeo.deleteDocument(document);
    }

    @Test
    public void testAddToWorklist() throws Exception {
        // create TestDoc
        Document document = null;
        try {
            // get a Folder
            // // Default domain> Workspaces> cstrobel-test> testFolder
            Document folder = nuxeo.getDocument(TEST_FOLDER_UUID);
            document = createTestDocumentInFolder(folder);
            assertEquals("", "Note", document.getType());

            // check current Worklist
            List<Document> preDocuments = nuxeo.getDocumentsFromWorkList();
            int sizePre = preDocuments.size();

            nuxeo.addCurrentDocumentToWorklist(document);

            // check current Worklist
            List<Document> postDocuments = nuxeo.getDocumentsFromWorkList();
            int sizePost = postDocuments.size();

            assertEquals("", sizePre + 1, sizePost);

            // delete doc
            nuxeo.deleteDocument(document);

            // check current Worklist
            List<Document> postPostDocuments = nuxeo.getDocumentsFromWorkList();
            int sizePostPost = postPostDocuments.size();
            assertEquals("Should now be the size before our operation", sizePre, sizePostPost);
        } catch (Exception e) {
            throw e;
        } finally {
        }
    }

    @Test
    public void testAddAndRemovePermissionsFile() throws Exception {
        fail("Nuxeo crashes if I try to get a Document and have no access");
        // get a Folder
        // // Default domain> Workspaces> cstrobel-test> testFolder
        Document folder = nuxeo.getDocument(TEST_FOLDER_UUID);
        // create TestDoc
        Document document = createTestDocumentInFolder(folder);
        try {
            // Blob queriedUsers = nuxeo.queryUsers("*", null); // doesnt work

            // get current Permissions
            checkIfUserHasAccessForPermission("anton", "aktie", "Read", document);
            // set Permission
            nuxeo.addPermissionToDocument(document, "READ", "powerusers", null, true); // group
            Document docWithPermission = nuxeo.addPermissionToDocument(document, "READ", "anton", null, true);

            // check Permissions again
            // FIXME need mechanism to get the Permissions of a Document. GUI says "Rights modified" but nothing more. Need API Method...
            Document docc = nuxeo.getUsersAndGroupsForDocument(document, "READ", "", false, false, false);
            // LOG.debug(docc.getState());
            // LOG.debug(docc.getType());
            // LOG.debug("ContextParameters:" + docc.getContextParameters());
            // LOG.debug("Dirties:" + docc.getDirties());

            // remove Permission
            nuxeo.removePermissionFromDocument(document, "anton", null);
            // check Permissions again
            // FIXME

        } catch (Exception e) {
            throw e;
        } finally {
            // delete File
            nuxeo.deleteDocument(document);
        }
    }
    @Test
    public void testAddAndRemovePermissionsFolder() throws Exception {
        fail("Nuxeo crashes if I try to get a Document and have no access");
        // get a Folder
        // // Default domain> Workspaces> cstrobel-test> testFolder
        Document folder = nuxeo.getDocument(TEST_FOLDER_UUID);
        // create TestDoc
        Document document = createTestDocumentInFolder(folder);
        try {
            assertFalse(checkIfUserHasAccessForPermission("anton", "aktie", "Read", folder));

            // set Permission
            nuxeo.addPermissionToDocument(folder, "READ", "powerusers", null, true); // group
            Document folderWithPermission = nuxeo.addPermissionToDocument(folder, "READ", "anton", null, true);

            // check Permissions again
            assertTrue(checkIfUserHasAccessForPermission("anton", "aktie", "Read", folder));
            // FIXME need mechanism to get the Permissions of a Document. GUI says "Rights modified" but nothing more. Need API Method...
            Document folder2 = nuxeo.getUsersAndGroupsForDocument(folder, "READ", "", false, false, false);

            // remove Permission
            nuxeo.removePermissionFromDocument(document, "anton", null);
            // check Permissions again
            // FIXME

        } catch (Exception e) {
            throw e;
        } finally {
            // delete File
            nuxeo.deleteDocument(document);
        }
    }
    private boolean checkIfUserHasAccessForPermission(final String user, final String password, final String permission, final Document document) throws Exception {
        // connect to Nuxeo
        final NuxeoUtility tempNuxeo = new NuxeoUtility(URL, user, password);

        // check if the user has access
        Document docc = tempNuxeo.getDocument(document.getId());
        // LOG.debug(docc.getState());
        // LOG.debug(docc.getType());
        // LOG.debug(docc.getContextParameters() + "");
        // LOG.debug(docc.getDirties() + "");
        // LOG.debug(docc.getProperties() + "");
        // LOG.debug(docc.getFacets() + "");

        // shutdown
        tempNuxeo.shutdown();
        return docc != null;
    }

    @Test
    public void testPublishDocument() throws Exception {
        // get a Document
        Document folder = nuxeo.getDocument(TEST_FOLDER_UUID);
        // create TestDoc
        Document document = createTestDocumentInFolder(folder);
        // get a Section to publish to
        final String sectionUUID = TEST_SECTION;
        Document section = nuxeo.getDocument(sectionUUID);// Default domain> Sections> testSectionForUnitTest
        // check number of Docs in Section
        final int preSize = nuxeo.getChildrenOfDocument(section).size();

        try {
            // publish
            nuxeo.publishDocumentToSection(document, section, false);
            // check publication
            final int postSize = nuxeo.getChildrenOfDocument(section).size();
            assertEquals("Should be one more than the before check", preSize + 1, postSize);
        } catch (Exception e) {
            throw e;
        } finally {
            // delete
            nuxeo.deleteDocument(document);
        }
    }
    @Test
    public void testApproveDocument() throws Exception {
        // get a Document
        Document folder = nuxeo.getDocument(TEST_FOLDER_UUID);
        // create TestDoc
        Document document = createTestDocumentInFolder(folder);

        assertEquals("Should be Project", document.getState(), "project");
        try {

            document = nuxeo.approveDocument(document);
            assertEquals("Should be Project", document.getState(), "project");
        } catch (Exception e) {
            throw e;
        } finally {
            // delete
            nuxeo.deleteDocument(document);
        }
    }
    @Test
    public void testWorkflowStatus() throws Exception {
        fail("This Nuxeo Instance has currently no Workflows");
        // get a Document
        Document folder = nuxeo.getDocument(TEST_FOLDER_UUID);
        // create TestDoc
        Document document = createTestDocumentInFolder(folder);
        try {
            // start workflow
            Document workflow = nuxeo.startWorkflow(document, "Process_1:1:b358ea4d-c72a-11e4-b8b0-22b5c971964e", false, new Properties());

            // cancel
            nuxeo.cancelWorkflow(workflow);

            // resume
            nuxeo.resumeWorkflow(workflow.getId());
        } catch (Exception e) {
            throw e;
        } finally {
            // delete
            nuxeo.deleteDocument(document);
        }
    }

    @Test
    public void testGetVersionsOfDocument() throws Exception {
        // get a Document
        Document folder = nuxeo.getDocument(TEST_FOLDER_UUID);
        // create TestDoc
        Document document = createTestDocumentInFolder(folder);
        // get Versions
        List<Document> documentVersions = nuxeo.getDocumentVersions(document);
        int versionAmount = documentVersions.size();
        assertEquals("", 0, versionAmount);

        //add some versions
        nuxeo.addPermissionToDocument(document, "READ", "powerusers", null, true); // group
        nuxeo.addPermissionToDocument(document, "READ", "anton", null, true);
        document = nuxeo.createVersion(document, "Major", true);

        //get Versions
        documentVersions = nuxeo.getDocumentVersions(document);
        document = documentVersions.get(0);
        assertEquals("", 1, documentVersions.size());

        // 4
        document = nuxeo.createVersion(document, "Major", true);
        documentVersions = nuxeo.getDocumentVersions(document);
        assertEquals("", 2, documentVersions.size());

        // 2
        document = nuxeo.createVersion(document, "Minor", true);
        documentVersions = nuxeo.getDocumentVersions(document);
        document = documentVersions.get(0);
        assertEquals("", 2, documentVersions.size());

        // 3
        document = nuxeo.createVersion(document, "None", true);
        documentVersions = nuxeo.getDocumentVersions(document);
        document = documentVersions.get(0);
        assertEquals("", 3, documentVersions.size());

        try {
        } catch (Exception e) {
            throw e;
        } finally {
            // delete
            nuxeo.deleteDocument(document);
        }
    }

    @Test
    public void testLockDocument() throws Exception {
        // get a Document
        Document folder = nuxeo.getDocument(TEST_FOLDER_UUID);
        // create TestDoc
        Document document = createTestDocumentInFolder(folder);

        // lock
        document = nuxeo.lockDocument(document);

        // check
        assertEquals("", true, document.isLocked());
        // unlock
        document = nuxeo.unlockDocument(document);

        // check
        assertEquals("", false, document.isLocked());

        try {
        } catch (Exception e) {
            throw e;
        } finally {
            // delete
            nuxeo.deleteDocument(document);
        }
    }

    @Test
    public void testCreateFolder() throws Exception {
        // get a Document
        Document folder = nuxeo.getDocument(TEST_FOLDER_UUID);

        // FIXME error from nuxeo ..?
        Document createdFolder = nuxeo.createFolder("folderForTesting", folder.getId());
        // check
        assertEquals("", "Folder", createdFolder.getType());

        try {
        } catch (Exception e) {
            throw e;
        } finally {
            // delete
            nuxeo.deleteDocument(createdFolder);
        }
    }

    @Test
    public void testTagDocument() throws Exception {
        // get a Document
        Document folder = nuxeo.getDocument(TEST_FOLDER_UUID);
        // create TestDoc
        Document document = createTestDocumentInFolder(folder);

        nuxeo.tagDocument(document, "oneTag");

        nuxeo.tagDocument(document, "Tag1, Tag2, Tag3");

        try {
        } catch (Exception e) {
            throw e;
        } finally {
            // delete
            nuxeo.deleteDocument(document);
        }
    }
}
