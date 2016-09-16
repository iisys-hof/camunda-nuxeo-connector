package de.hofuniversity.iisys.cmis;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.FileableCmisObject;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.ObjectId;
import org.apache.chemistry.opencmis.client.api.QueryResult;
import org.apache.chemistry.opencmis.commons.BasicPermissions;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.Ace;
import org.apache.chemistry.opencmis.commons.data.Acl;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.AclPropagation;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.hofuniversity.iisys.camunda.workflows.nuxeo.NuxeoUtilityTest;
import de.hofuniversity.iisys.cmis.OpenCMISClient.CMISDocumentTypes;

/**
 * If You change the Nuxeo-System. The UUIDs will be different than the used ones here! You need to change those accordingly.
 * @author cstrobel
 */
public class OpenCMISClientTest {
    private static final String TESTNAME = "testDoc";
    private static final transient Logger LOG = LoggerFactory.getLogger(OpenCMISClientTest.class);
    private static final String TEST_FOLDER_UUID = NuxeoUtilityTest.TEST_FOLDER_UUID;
    protected static OpenCMISClient cmisClient;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        final String url = "http://127.0.0.1:8080/nuxeo/atom/cmis";
        final String user = "demo";
        final String password = "secret";
        final BindingType bindingType = BindingType.ATOMPUB;

        cmisClient = new OpenCMISClient(url, user, password, "default", bindingType);
    }


    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void test() {
    }
    @Test
    public void testCreateFile() throws UnsupportedEncodingException {
        //get a Folder
        CmisObject folder = cmisClient.getObject(TEST_FOLDER_UUID);

        ObjectId file = createTestDocumentInFolder(folder, TESTNAME, CMISDocumentTypes.DOC);
        LOG.debug(cmisClient.getObject(file).getProperties().toString());

        cmisClient.delete(file);
    }

    private ObjectId createTestDocumentInFolder(CmisObject folder, final String name, CMISDocumentTypes documentType) throws UnsupportedEncodingException {
        ContentStream contentStream = cmisClient.getContentStreamForSource("text/plain; charset=UTF-8", "xyzTest", "CMIS-TestFile");
        Map<String, String> map = new HashMap<String, String>();
        map.put(PropertyIds.OBJECT_TYPE_ID, documentType.getIdentifier());
        map.put(PropertyIds.NAME, name);

        ObjectId file = cmisClient.createDocument(map, OpenCMISClient.getObjectId(folder.getId()), contentStream, VersioningState.MAJOR);
        return file;
    }

    @Test
    public void testQuery1() {
        ItemIterable<QueryResult> queryResult = cmisClient.query("SELECT * FROM cmis:document", true);
        for (QueryResult item : queryResult) {
            System.out
                    .println("Found " + item.getPropertyByQueryName("cmis:name").getFirstValue() + " of type " + item.getPropertyByQueryName("cmis:objectTypeId").getFirstValue());
        }
        assertEquals("", true, queryResult.getTotalNumItems() > 0);
    }
    // @Test
    // public void testCollections() {
    // ItemIterable<QueryResult> queryResult = cmisClient.query(OpenCMISClient.ALL_COLLECTION_QUERY, true);
    // assertEquals("", true, queryResult.getTotalNumItems() > 0);
    // for (QueryResult item : queryResult) {
    // for (PropertyData<?> xy : item.getProperties()) {
    // System.out.println(xy.getDisplayName() + ":" + xy.getFirstValue());
    // }
    // System.out
    // .println("Found " + item.getPropertyByQueryName("cmis:name").getFirstValue() + " of type " +
    // item.getPropertyByQueryName("cmis:objectTypeId").getFirstValue());
    // }
    // assertEquals("", true, queryResult.getTotalNumItems() > 0);
    // }
    // @Test
    // public void testCreateCollection() {
    // Map<String, Object> properties = new HashMap<>();
    // properties.put(PropertyIds.NAME, "col1");
    // properties.put(PropertyIds.OBJECT_TYPE_ID, CMISDocumentTypes.COL.getIdentifier());
    //
    // // create new Folder
    // ObjectId createdItem = cmisClient.createItem(properties, cmisClient.getRootFolder());
    // CmisObject object = cmisClient.getObject(createdItem);
    // assertEquals("", "Collection", object.getProperty("cmis:objectTypeId").getValueAsString());
    // cmisClient.delete(createdItem);
    // }

    // @Test
    // public void testAddDocumentsToCollectionAndGetDocsFromCollection() throws Exception {
    // Map<String, Object> properties = new HashMap<>();
    // properties.put(PropertyIds.NAME, "col1");
    // properties.put(PropertyIds.OBJECT_TYPE_ID, CMISDocumentTypes.COL.getIdentifier());
    //
    // // create new Folder
    // ObjectId collection = cmisClient.createItem(properties, cmisClient.getRootFolder());
    //
    // // get target Folder
    // CmisObject folder = cmisClient.getObject(TEST_FOLDER_UUID);
    // // test Doc
    // Document document = cmisClient
    // .getLatestDocumentVersion(createTestDocumentInFolder(cmisClient.getObject("74df269f-b7b6-43d2-a016-12f212d0d055"), "testDoc", CMISDocumentTypes.DOC)); //
    // test-space
    // try {
    // // acc Doc to Collection
    // document.addToFolder(collection, true);
    // } catch (Exception e) {
    // LOG.error("", e);
    // throw new Exception("Not supported by Nuxeo/Cmis", e);
    // } finally {
    // cmisClient.delete(collection);
    // cmisClient.delete(document);
    // }
    // }

    @Test
    public void testGetVersionOfDoc() throws Exception {
        // get target Folder
        Folder folder = (Folder) cmisClient.getObject(TEST_FOLDER_UUID);
        // test Doc
        // Create a doc
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(PropertyIds.OBJECT_TYPE_ID, "cmis:document");
        properties.put(PropertyIds.NAME, "xy");
        String docText = "This is a sample document";
        byte[] content = docText.getBytes();
        InputStream stream = new ByteArrayInputStream(content);
        ContentStream contentStream = cmisClient.getObjectFactory().createContentStream("xy", Long.valueOf(content.length), "text/plain", stream);

        Document doc = folder.createDocument(properties, contentStream, VersioningState.MAJOR);
        try {
            assertEquals("", 1, doc.getAllVersions().size());
            Map<String, String> map = new HashMap<>();
            map.put(PropertyIds.OBJECT_TYPE_ID, "cmis:document");
            map.put(PropertyIds.NAME, "xy");
            ObjectId newDoc = doc.updateProperties(map, true);
            contentStream = cmisClient.getContentStreamForSource("text/plain", "xy", "stream");
            doc.checkIn(false, map, contentStream, "just a minor change");
            assertEquals("", 1 + 1, cmisClient.getLatestDocumentVersion(newDoc).getAllVersions().size());
        } catch (Exception e) {
            LOG.error("", e);
            throw e;
        } finally {
            // delete
            cmisClient.delete(doc);
        }
    }

    @Test
    public void testMoveDocumentToFolder() throws Exception {
        // get target Folder
        CmisObject folder = cmisClient.getObject(TEST_FOLDER_UUID);
        // test Doc
        Document document = cmisClient
.getLatestDocumentVersion(createTestDocumentInFolder(cmisClient.getObject(TEST_FOLDER_UUID), "testDoc", CMISDocumentTypes.DOC)); // test-space
        LOG.debug(document.getProperties() + "");
        // folder
        try {

            // check path
            String path = "/default-domain/workspaces/java-junit-testfol";
            assertEquals("", path, document.getPaths().get(0).substring(0, path.length()));

            // move it move it
            // FIXME org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException: null...
            FileableCmisObject newDoc = cmisClient.moveDocument(document.getId().toString(), document.getParents().get(0).getId(), folder.getId().toString());

            // check path
            path = "/default-domain/workspaces/cstrobel-test/testFolder/testDoc";
            assertEquals("", path, newDoc.getPaths().get(0).substring(0, path.length()));
            assertEquals("", newDoc.getPaths().size(), 1);
            newDoc.delete(true);
        } catch (Exception e) {
            LOG.error("", e);
            throw new Exception("NullPointer from CMIS depths", e);
        } finally {
            // delete

            // cmisClient.delete(document);
        }
    }
    @Test
    public void testSetPermission() throws Exception {
        // get target Folder
        Folder folder = (Folder) cmisClient.getObject(TEST_FOLDER_UUID);

        Map<String, Object> properties = new HashMap<>();
        properties.put(PropertyIds.NAME, "folder1");
        properties.put(PropertyIds.OBJECT_TYPE_ID, "cmis:folder");

        // create new Folder
        Folder tempFolder = folder.createFolder(properties);

        int pre = cmisClient.getAcl(tempFolder, false).getAces().size();
        try {

            List<String> permissions = new ArrayList<String>();

            permissions.add(BasicPermissions.READ);

            Ace aceIn = cmisClient.getObjectFactory().createAce("demo", permissions);
            List<Ace> aces = new ArrayList<>();
            aces.add(aceIn);

            cmisClient.applyAcl(tempFolder, aces, new ArrayList<Ace>(), AclPropagation.PROPAGATE);

            // check
            Acl acl = cmisClient.getAcl(tempFolder, false);
            assertEquals("", pre + 1, acl.getAces().size());// FIXME should be updated

            Acl folderAcl = cmisClient.getAcl(folder, false);
            LOG.debug(folderAcl + "");

            // remove Permissions again
            cmisClient.applyAcl(tempFolder, new ArrayList<Ace>(), aces, AclPropagation.PROPAGATE);

            // check again
            acl = cmisClient.getAcl(tempFolder, false);
            assertEquals("", pre + 1, acl.getAces().size());// FIXME should be updated
        } catch (Exception e) {
            LOG.error("", e);
            throw e;
        } finally {
            // delete
            cmisClient.delete(tempFolder);
        }
    }
}
