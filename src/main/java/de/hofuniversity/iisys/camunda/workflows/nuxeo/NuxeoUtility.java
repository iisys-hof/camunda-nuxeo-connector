package de.hofuniversity.iisys.camunda.workflows.nuxeo;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.OperationRequest;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;
import org.nuxeo.ecm.automation.client.model.Blob;
import org.nuxeo.ecm.automation.client.model.Document;
import org.nuxeo.ecm.automation.client.model.Documents;
import org.nuxeo.ecm.automation.client.model.FileBlob;
import org.nuxeo.ecm.automation.client.model.PaginableDocuments;
import org.nuxeo.ecm.automation.client.model.PropertyList;
import org.nuxeo.ecm.automation.client.model.PropertyMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NuxeoUtility implements Runnable
{
    private static final transient Logger LOG = LoggerFactory.getLogger(NuxeoUtility.class);
    private static final long SESSION_TIMEOUT = 30000;
    private static final long SESSION_CHECK_INTERVAL = 5000;

    private static final Object LOCK = new Object();
    private static NuxeoUtility fInstance;

    private final SimpleDateFormat fFormat;

    private final String fNuxeoAutoUrl, fNuxeoIdUrl;

    private final String fUser, fPassword;

    private final Map<String, List<String>> fSubIds;
    private final Map<String, Document> fDocuments;

    private long fLastRequest = 0;
    private HttpAutomationClient fClient;
    private Session fSession;

    public static NuxeoUtility getInstance(String url, String user, String password)
    {
        synchronized(LOCK)
        {
            if(fInstance == null)
            {
                fInstance = new NuxeoUtility(url, user, password);
            }
        }

        return fInstance;
    }

    public NuxeoUtility(String url, String user, String password)
    {
        fSubIds = new HashMap<String, List<String>>();
        fDocuments = new HashMap<String, Document>();

        fFormat = new SimpleDateFormat("yyyy-MM-dd");

        fNuxeoAutoUrl = url + "site/automation";
        fNuxeoIdUrl = url + "api/v1/id/";

        fUser = user;
        fPassword = password;

        fClient = null;
        fSession = null;
    }

    @Override
    public void run()
    {
        while(fSession != null)
        {
            try
            {
                Thread.sleep(SESSION_CHECK_INTERVAL);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }


            //terminate session if there were no requests
            if (fLastRequest < System.currentTimeMillis() + SESSION_TIMEOUT && fSession != null)
            {
                fSession.close();
                fClient.shutdown();
                fSession = null;
                fClient = null;
            }
        }
    }

    public Session getSession() throws IOException
    {
        fLastRequest = System.currentTimeMillis();

        if(fSession == null)
        {
            fClient = new HttpAutomationClient(fNuxeoAutoUrl);
            fSession = fClient.getSession(fUser, fPassword);

            //start watchdog, automatically terminating sessions
            new Thread(this).start();
        }

        return fSession;
    }

    public List<String> getAllDocumentIds() throws Exception
    {
        clearBuffers();

        final List<String> ids = new LinkedList<String>();

        Documents docs = (Documents) getSession().newRequest("Document.Query")
.setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                .set("query", "SELECT ecm:uuid FROM Document " + "WHERE ecm:primaryType = 'File'").execute();

        //debug output
        // debugLogDocs(docs);

        String id = null;
        for(Document doc : docs)
        {
            id = doc.getId();
            ids.add(id);
            fDocuments.put(id, doc);
            getAllVersions(id);
        }

        //filter out all second level IDs
        //TODO: maybe collect / cache in external list
        for(Entry<String, List<String>> subEntries : fSubIds.entrySet())
        {
            for(String subId : subEntries.getValue())
            {
                ids.remove(subId);
            }
        }

        return ids;
    }

    public List<String> getAllDocumentIds(long from, long to) throws Exception
    {
        clearBuffers();

        final List<String> ids = new LinkedList<String>();

        Date fromDate = new Date(from);
        Date toDate = new Date(to);

        Documents docs = (Documents) getSession().newRequest("Document.Query")
                .setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                .set("query",
                        "SELECT ecm:uuid FROM Document " + "WHERE ecm:primaryType = 'File' " + "AND dc:modified >= '" + fFormat.format(fromDate) + "' " + "AND dc:modified <= '"
                                + fFormat.format(toDate) + "'").execute();

        //debug output
        // debugLogDocs(docs);

        String id = null;
        for(Document doc : docs)
        {
            id = doc.getId();
            ids.add(id);
            fDocuments.put(id, doc);
            getAllVersions(id);
        }

        //filter out all second level IDs
        //TODO: maybe collect / cache in external list
        for(Entry<String, List<String>> subEntries : fSubIds.entrySet())
        {
            for(String subId : subEntries.getValue())
            {
                ids.remove(subId);
            }
        }

        return ids;
    }

    private void clearBuffers()
    {
        fDocuments.clear();
        fSubIds.clear();
    }

    public Document getDocument(String id) throws Exception
    {
        Document doc = fDocuments.get(id);

        //try retrieving the document if it's not yet available
        if(doc == null)
        {
            Documents docs = (Documents) getSession().newRequest("Document.Query")
.setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                    .set("query", "SELECT * FROM Document " + "WHERE ecm:uuid = '" + id + "'").execute();

            if(docs.size() == 1)
            {
                doc = docs.iterator().next();
            }
            else if(docs.size() == 0)
            {
                throw new Exception("document not found");
            }
            else
            {
                throw new Exception("too many documents found");
            }
        }

        return doc;
    }

    public String getDocumentVersion(String id) throws Exception
    {
        String version = null;

        Document doc = getDocument(id);
        if(doc != null)
        {
            //TODO: more complete version string encompassing more values
            version = doc.getProperties().getString("dc:modified");
        }

        return version;
    }

    public Date getLastModified(String id) throws Exception
    {
        Date date = null;

        Document doc = getDocument(id);
        if(doc != null)
        {
            date = doc.getLastModified();
        }

        return date;
    }

    public String getURI(String id) throws Exception
    {
        String uri = null;

        Document doc = getDocument(id);
        if(doc != null)
        {
            PropertyMap map = doc.getProperties().getMap("file:content");
            uri = map.getString("data");
        }

        return uri;
    }

    public Blob getBlob(String id) throws Exception
    {
        return getSession().getFile(getURI(id));
    }

    public String getPath(String id) throws Exception
    {
        String path = null;

        Document doc = getDocument(id);
        if(doc != null)
        {
            path = doc.getPath();
        }

        return path;
    }

    //TODO: placeholder
    public String getACLs(String id) throws Exception
    {
        String acl = null;

        //request via document id
        String uri = fNuxeoIdUrl + id + "/@acl";

        URL url = new URL(uri);
        acl = HttpUtil.getText(url, fUser, fPassword);

        //TODO: evaluate JSON object

        return acl;
    }

    /**
     * Returns all versions of an given Document Id
     * @param id The Id of an Document from which You want the versions from
     * @return The {@link List} of {@link String}s
     * @throws Exception
     */
    public List<String> getAllVersions(String id) throws Exception
    {
        List<String> idList = fSubIds.get(id);

        if(idList == null)
        {
            //create new ID list
            idList = new LinkedList<String>();
            fSubIds.put(id, idList);

            //actually read all version IDs
            Documents docs = (Documents) getSession().newRequest("Document.GetVersions").setHeader(Constants.HEADER_NX_SCHEMAS, "*").setInput(id).execute();

            for(Document doc : docs)
            {
                idList.add(doc.getId());
            }
        }

        return idList;
    }

    /**
     * Creates a Document with the given properties and type.
     * @param doc
     * @param type
     * @param properties
     * @return
     * @throws Exception
     */
    public Document createDocument(Document doc, final String type, final Properties properties) throws Exception
    {
        OperationRequest request = null;
        Document result = null;

        request = getSession().newRequest("Document.Create");

        // parameters
        // for(Entry<String, Object> paramE : params.entrySet())
        // {
        // request = request.set(paramE.getKey(), paramE.getValue());
        // }
        request.set("name", properties.get("dc:title"));
        request.set("type", type);
        request.set("properties", properties);
        // input document
        request.setInput(doc);

        // execute
        result = (Document) request.execute();

        return result;
    }

    /**
     * @param doc The {@link Document} where You want the Parameters to be updated
     * @param params The Parameters You want to change
     * @return the updated {@link Document}
     * @throws Exception
     */
    public Document updateDocument(Document doc, Map<String, Object> params) throws Exception
    {
        OperationRequest request = null;
        Document result = null;

        // TODO: document body?
        request = getSession().newRequest("Document.Update");

        // parameters
        for (Entry<String, Object> paramE : params.entrySet()) {
            request = request.set(paramE.getKey(), paramE.getValue());
        }

        // input document?
        request.setInput(doc);

        // execute
        result = (Document) request.execute();

        return result;
    }

    /**
     * @param document Which will be locked
     * @return The locked {@link Document}
     * @throws Exception
     */
    public Document lockDocument(Document document) throws Exception
    {
        Document result = null;

        result = (Document) getSession().newRequest("Document.Lock").setInput(document).execute();

        return result;
    }
    /**
     * @param document The {@link Document} which will be unlocked again
     * @return the unlocked {@link Document}
     * @throws Exception
     */
    public Document unlockDocument(Document document) throws Exception {
        Document result = (Document) getSession().newRequest("Document.Unlock").setInput(document).execute();
        return result;
    }
    /**
     * @param document Checks an {@link Document} out
     * @return the checked out {@link Document}
     * @throws Exception
     */
    public Document checkoutDocument(Document document) throws Exception
    {
        Document result = null;

        result = (Document) getSession().newRequest("Document.CheckOut").setInput(document).execute();

        return result;
    }

    /**
     * A Document has to be checked out before You call this method
     * @param doc Which will be checked in
     * @param params The Parameter You want the Document to be checked in
     * @return The updated Document
     * @throws Exception
     */
    public Document checkinDocument(Document doc, Map<String, Object> params) throws Exception
    {
        OperationRequest request = null;
        Document result = null;

        request = getSession().newRequest("Document.CheckIn");

        // parameters
        for (Entry<String, Object> paramE : params.entrySet()) {
            request = request.set(paramE.getKey(), paramE.getValue());
        }

        // input document?
        request.setInput(doc);

        // execute
        result = (Document) request.execute();

        return result;
    }

    /**
     * @param doc The {@link Document} which will be deleted
     * @return I don`t really know what it returns
     * @throws Exception
     */
    public Document deleteDocument(Document doc) throws Exception
    {
        Document result = null;

        result = (Document) getSession().newRequest("Document.Delete").setInput(doc).execute();

        return result;
    }

    public Document setDocumentLifeCycle(Document doc, String value) throws Exception
    {
        Document result = null;

        result = (Document) getSession().newRequest("Document.SetLifeCycle").set("value", value).setInput(doc).execute();

        return result;
    }

    /**
     * @param doc Adds the permissions to the Document
     * @param params See Nuxeo Api for details
     * @return the updated Document
     * @throws Exception
     */
    public Document addPermission(Document doc, Map<String, Object> params) throws Exception
    {
        OperationRequest request = null;
        Document result = null;

        request = getSession().newRequest("Document.AddPermission");

        // parameters
        for (Entry<String, Object> paramE : params.entrySet()) {
            request = request.set(paramE.getKey(), paramE.getValue());
        }

        // input document?
        request.setInput(doc);

        // execute
        result = (Document) request.execute();

        return result;
    }

    /**
     * Removes the given Permissions from an {@link Document}
     * @param doc Where the permissions will be removed
     * @param params See the Nuxeo Api for an explanation
     * @return The updated Document
     * @throws Exception
     */
    public Document removePermission(Document doc, Map<String, Object> params) throws Exception
    {
        OperationRequest request = null;
        Document result = null;

        request = getSession().newRequest("Document.RemovePermission");

        // parameters
        for (Entry<String, Object> paramE : params.entrySet()) {
            request = request.set(paramE.getKey(), paramE.getValue());
        }

        // input document?
        request.setInput(doc);

        // execute
        result = (Document) request.execute();

        return result;
    }

    public Document getWorkflow(String id) throws Exception
 {
        Document result = null;

        // TODO: not available?

        return result;
    }

    public Document startWorkflow(Document doc, Map<String, Object> params) throws Exception
    {
        OperationRequest request = null;
        Document result = null;

        // TODO: JSON representation?
        request = getSession().newRequest("Context.StartWorkflow");

        // parameters
        for (Entry<String, Object> paramE : params.entrySet()) {
            request = request.set(paramE.getKey(), paramE.getValue());
        }

        // input document?
        request.setInput(doc);

        // execute
        result = (Document) request.execute();

        return result;
    }

    public void setWorkflowVar(String id, String name, Object value) throws Exception
    {
        // TODO: ?
        getSession().newRequest("Context.SetWorkflowVar").set("workflowInstanceId", id).set("name", name).set("value", value).execute();

    }

    public Document setWorkflowNodeVar(Document doc, String name, Object value) throws Exception
    {
        // TODO: instance ID?
        // TODO: input not used?

        Document result = (Document) getSession().newRequest("Context.SetWorkflowVar").set("name", name).set("value", value).setInput(doc).execute();

        return result;
    }

    public void advanceWorkflow(String id, Map<String, Object> parameters) throws Exception
    {
        // TODO: not available?
    }

    public Documents cancelWorkflow(Document doc) throws Exception
    {
        // TODO: right way?
        Documents docs = (Documents) getSession().newRequest("cancelWorkflow").setInput(doc).execute();

        return docs;
    }

    public Documents terminateWorkflow(Document doc) throws Exception
    {
        // TODO: right way?
        Documents docs = (Documents) getSession().newRequest("terminateWorkflow").setInput(doc).execute();

        return docs;
    }

    public Documents getUserTasks() throws Exception
    {
        Documents result = null;

        result = (Documents) getSession().newRequest("Workflow.GetTask").execute();

        return result;
    }

    public Document getTask(String id) throws Exception
    {
        Document result = null;

        // TODO: ID parameter not supported?
        Object o = getSession().newRequest("Workflow.GetTask").execute();
        if (o instanceof Documents) {
            Documents docs = (Documents) o;

            for (Document d : docs) {
                if (id.equals(d.getId())) {
                    result = d;
                }
            }
        } else if (o instanceof Document) {
            Document doc = (Document) o;
            if (id.equals(doc.getId())) {
                result = doc;
            }
        } else {
            // TODO: exception and logging
        }

        return result;
    }

    public Document createTask(boolean routing, Map<String, Object> parameters, Document doc) throws Exception
    {
        Document result = null;
        Documents docs = null;
        OperationRequest request = null;

        // create request
        if (routing) {
            request = getSession().newRequest("Workflow.CreateRoutingTask");
        } else {
            request = getSession().newRequest("Workflow.CreateTask");
        }

        // parameters
        for (Entry<String, Object> paramE : parameters.entrySet()) {
            request = request.set(paramE.getKey(), paramE.getValue());
        }

        // input document?
        request.setInput(doc);

        // execute
        Object o = request.execute();

        // get result
        if (o instanceof Documents) {
            docs = (Documents) o;
            result = docs.get(0);
        } else if (o instanceof Document) {
            result = (Document) o;
        } else {
            // TODO: exception and logging
        }

        return result;
    }

    public void updateTask(Document doc, Map<String, Object> parameters) throws Exception
    {
        // TODO: doesn't exist?
    }

    public Document completeTask(Document doc, Map<String, Object> parameters) throws Exception
    {
        Document result = null;

        // TODO: "setTaskDone"?
        // TODO: "Document.Routing.EvaluateCondition"?

        OperationRequest request = null;

        // create request
        request = getSession().newRequest("Workflow.CompleteTaskOperation");

        // parameters
        for (Entry<String, Object> paramE : parameters.entrySet()) {
            request = request.set(paramE.getKey(), paramE.getValue());
        }

        // input document?
        request.setInput(doc);

        // execute
        Object o = request.execute();

        if (o instanceof Documents) {
            result = ((Documents) o).get(0);
        } else if (o instanceof Document) {
            result = (Document) o;
        } else {
            // TODO: exception and logging
        }

        return result;
    }

    private void debugLogDocs(final Documents docs) throws Exception
    {
        final PrintWriter writer = new PrintWriter("documents.log");

        for(final Document doc : docs)
        {
            writer.println("-------- DOC --------");

            writer.println("id: " + doc.getId());
            writer.println("title: " + doc.getTitle());
            writer.println("type: " + doc.getType());
            writer.println("inputType: " + doc.getInputType());
            writer.println("state: " + doc.getState());
            writer.println("versionLabel: " + doc.getVersionLabel());

            final PropertyMap conPars = doc.getContextParameters();
            writer.println("context params: ");
            for(String key : conPars.getKeys())
            {
                writer.println("\t" + key + ": " + conPars.get(key));
            }

            writer.println("facets: ");
            final PropertyList facets = doc.getFacets();
            final int facSize = facets.size();
            for(int i = 0; i < facSize; ++i)
            {
                writer.println("\t" + facets.getString(i));
            }

            writer.println("inputRef: " + doc.getInputRef());
            writer.println("lastModified: " + doc.getLastModified());
            writer.println("lock: " + doc.getLock());
            writer.println("lockCreated: " + doc.getLockCreated());
            writer.println("lockOwner: " + doc.getLockOwner());
            writer.println("path: " + doc.getPath());

            final PropertyMap props = doc.getProperties();
            writer.println("properties: ");
            for(String key : props.getKeys())
            {
                writer.println("\t" + key + ": " + props.get(key));
            }

            writer.println("repository: " + doc.getRepository());

            //try retrieving blob
            PropertyMap map = doc.getProperties().getMap("file:content");

            if(map != null)
            {
                String path = map.getString("data");

                if(path != null)
                {
                    final FileBlob blob = (FileBlob) getSession().getFile(path);

                    if(blob != null)
                    {
                        writer.println("BLOB:");

                        writer.println("\tfileName: " + blob.getFileName());
                        writer.println("\tinputRef: " + blob.getInputRef());
                        writer.println("\tinputType: " + blob.getInputType());
                        writer.println("\tlength: " + blob.getLength());
                        writer.println("\tmimeType: " + blob.getMimeType());

                        //TODO
                        //blob.getStream();

                        final File file = blob.getFile();
                        if(file != null)
                        {
                            writer.println("\tFile:");

                            writer.println("\t\tcanonicalPath: " + file.getCanonicalPath());
                            writer.println("\t\tname: " + file.getName());
                            writer.println("\t\tparent: " + file.getParent());
                            writer.println("\t\tlength: " + file.length());
                        }
                    }
                    else
                    {
                        writer.println("no blob");
                    }
                }
                else
                {
                    writer.println("no path");
                }
            }
            else
            {
                writer.println("no map");
            }

            writer.println();
            writer.println();
            writer.println(); 
        }

        writer.flush();
        writer.close();
    }

    public void shutdown()
    {
        if(fSession != null)
        {
            fSession.close();
            fClient.shutdown();
        }

        fSession = null;
        fClient = null;
    }

    public Document startCamundaWorkflow(Document doc, Map<String, Object> params) throws Exception
    {
        OperationRequest request = null;
        Document result = null;

        // "Context.StartWorkflow"

        // TODO: JSON representation?
        request = getSession().newRequest("Camunda.StartWorkflow");

        // parameters
        for (Entry<String, Object> paramE : params.entrySet()) {
            request = request.set(paramE.getKey(), paramE.getValue());
        }

        // input document?
        request.setInput(doc);

        // execute
        result = (Document) request.execute();

        return result;
    }

    /**
     * @param name is required
     * @param description can be null
     * @param this needs to be a Folder. The Folder can be added to the Collection
     * @throws Exception
     */
    public Document createCollection(final String name, final String description, final Document doc) throws Exception {
        OperationRequest request = getSession().newRequest("Collection.CreateCollection");
        request.set("name", name);
        if (description != null && !description.equals("")) {
            request.set("description", description);
        }

        if (doc != null) {
            request.setInput(doc);
        }

        // execute
        Object o = request.execute();
        LOG.debug(o + "");

        // return
        return (Document) o;
    }

    /**
     * @param collection the collection You want to look at
     * @return a List of Documents
     * @throws Exception
     */
    public List<Document> getDocumentsFromCollection(Document collection) throws Exception {
        OperationRequest request = getSession().newRequest("Collection.GetDocumentsFromCollection");

        // Set Params
        request.setInput(collection);

        // execute
        PaginableDocuments result = (PaginableDocuments) request.execute();

        return result.list();

    }

    public FileBlob getTopLevelFolder() throws Exception {
        OperationRequest request = getSession().newRequest("NuxeoDrive.GetTopLevelFolder");
        return (FileBlob) request.execute();
    }

    /**
     * @param searchTerm
     * @return List of Document 's (Collections)
     * @throws Exception
     */
    public List<Document> getCollections(final String searchTerm) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Collection.GetCollections");
        request.set("searchTerm", searchTerm);

        // execute
        PaginableDocuments paginableDocuments = (PaginableDocuments) request.execute();

        // return
        return paginableDocuments.list();
    }

    /**
     * @param collection The Collection the {@link Document}s will be added
     * @param documentsToAdd The {@link Document}s You want to add
     * @throws Exception
     */
    public void addDocumentsToCollection(Document collection, Collection<Document> documentsToAdd) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Collection.AddToCollection");
        // set collection to which we want to add
        request.set("collection", collection);

        // add documents
        for (Document doc : documentsToAdd) {
            request.setInput(doc);
        }

        request.execute();
    }

    /**
     * Moves a Document from one folder to another
     * @param documentToMove The {@link Document} You want to move
     * @param target Where You want to move it to
     * @return the updated {@link Document}
     * @throws Exception
     */
    public Document moveDocument(Document documentToMove, Document target) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Document.Move");
        // set doc
        request.setInput(documentToMove);
        // set target
        request.set("target", target.getId());

        Document document = (Document) request.execute();
        return document;
    }

    /**
     * @param documentToRender - the Document which You want to render
     * @param template required
     * @param filename - not required - default: output.ftl
     * @param mimetype - not required - text/xml
     * @param type - not required - ftl, mvel
     * @return the rendered Blob
     * @throws Exception
     */
    public Blob renderDocument(final Document documentToRender, final String template, final String filename, final String mimetype, final String type) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Render.Document");
        // set doc
        request.setInput(documentToRender);
        // set params
        request.set("template", template);
        request.set("filename", filename);
        request.set("mimetype", mimetype);
        request.set("type", type);

        Blob blob = (Blob) request.execute();
        return blob;

    }

    /**
     * @param document which will be added to the Worklist
     * @throws Exception
     */
    public void addCurrentDocumentToWorklist(final Document document) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Seam.AddToWorklist");
        // set doc
        request.setInput(document);

        // execute
        request.execute();
    }

    public List<Document> getDocumentsFromWorkList() throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Seam.FetchFromWorklist");
        // execute
        Documents documents = (Documents) request.execute();
        return documents.list();
    }

    /**
     * @param document is obligate - The Document
     * @param permission is obligate - The Permission You want to set
     * @param user is obligate
     * @param acl is optional - default value: local
     * @param blockInteritence is optional - default value: true
     * @return the Document
     * @throws Exception
     */
    public Document addPermissionToDocument(final Document document, final String permission, final String user, final String acl, final boolean blockInteritence) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Document.AddPermission");
        request.setInput(document);
        request.set("permission", permission);
        request.set("username", user);
        request.set("blockInheritance", blockInteritence);

        request.set("acl", acl);

        // execute
        Document documentRet = (Document) request.execute();
        return documentRet;
    }

    /**
     * @param document is obligate - The Document
     * @param user is obligate
     * @param acl is optional - default value: local
     * @return the Document
     * @throws Exception
     */
    public Document removePermissionFromDocument(final Document document, final String user, final String acl) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Document.RemovePermission");
        request.setInput(document);
        request.set("user", user);
        request.set("acl", acl);
        // execute
        Document documentRet = (Document) request.execute();
        return documentRet;
    }

    /**
     * FIXME NOT WORKING AT THE MOMENT
     * @param documentToCheck
     * @throws Exception
     */
    public void getPermissionForADocument(final Document documentToCheck) throws Exception {

        PaginableDocuments docs = (PaginableDocuments) getSession().newRequest("Document.Query")
.setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                .set("query", "SELECT * FROM Document " /* + "WHERE docID = '" + documentToCheck.getId() + "'" */).execute();

        LOG.debug(docs + "");
        for (Document doc : docs.list()) {
            LOG.debug(doc.getProperties() + "");
        }
    }
    /**
     * Queries Nuxeo for Users. See the API for Pattern and tenantId explanation
     * @param pattern - optional
     * @param tenantId - optional
     * @return a Blob of users
     * @throws Exception
     */
    public Blob queryUsers(final String pattern, final String tenantId) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Services.QueryUsers");
        if (pattern != null) {
            request.set("pattern", pattern);
        }
        if (tenantId != null) {
            request.set("tenantId", tenantId);
        }
        // execute
        Blob blob = (Blob) request.execute();
        return blob;
    }

    /**
     * Publishes a {@link Document} to a Section.
     * @param documentToPublish The {@link Document} You want to publish
     * @param targetSection The Section You want to publish the {@link Document} to
     * @param override if the eventually old existing {@link Document} should be overridden
     * @return The updated {@link Document}
     * @throws Exception
     */
    public Document publishDocumentToSection(final Document documentToPublish, final Document targetSection, final boolean override) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Document.Publish");
        // set params
        request.setInput(documentToPublish);
        request.set("target", targetSection.getId());

        Document doc = (Document) request.execute();
        return doc;
    }

    // public void getChildrenOfDocument(final String pathOrUUID) throws Exception {
        // // request
        // PaginableDocuments docs = (PaginableDocuments) getSession().newRequest("Document.Query").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
        // .set("query", "SELECT * FROM Document WHERE ecm:parentId =" + pathOrUUID/* + "WHERE docID = '" + documentToCheck.getId() + "'" */).execute();
        //
        // LOG.debug(docs + "");
        // for (Document doc : docs.list()) {
        // LOG.debug(doc.getProperties() + "");
        // }
        // ---------------
    // }

    /**
     * Gets the Children of a Folder for example
     * @param parentDocument The Parten Document
     * @return The Children
     * @throws Exception
     */
    public List<Document> getChildrenOfDocument(final Document parentDocument) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Document.GetChildren");
        // set params
        request.setInput(parentDocument);
        Documents docs = (Documents) request.execute();
        return docs.list();
    }
    /**
     * http://explorer.nuxeo.com/nuxeo/site/distribution/Nuxeo%20Platform-6.0/viewOperation/Document.GetUsersAndGroups
     * @param document to check - required
     * @param permission - required
     * @param variableName - required
     * @param ignoreGroups - optional, default: false
     * @param prefixIdentifiers - optional, default: false
     * @param resolveGroups - optional, default: false
     * @return
     * @throws Exception
     */
    public Document getUsersAndGroupsForDocument(final Document document, final String permission, final String variableName, final boolean ignoreGroups,
            final boolean prefixIdentifiers,
 final boolean resolveGroups) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Context.GetUsersGroupIdsWithPermissionOnDoc");
        // set params
        request.setInput(document);
        request.set("permission", permission);
        request.set("variable name", variableName);
        request.set("ignore groups", ignoreGroups);
        request.set("prefix identifiers", prefixIdentifiers);
        request.set("resolve groups", resolveGroups);

        Document doc = (Document) request.execute();
        LOG.debug("" + request.getContextParameters());

        return doc;
    }
    /**
     * Important Note: This Method uses a via the Nuxeo Studio created Automation Chain. If this Chain is not available You will get an Exception.
     * @param document
     * @return
     * @throws Exception
     */
    public Document approveDocument(final Document document) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("ApproveDocument");
        // set params
        request.setInput(document);

        Document doc = (Document) request.execute();
        return doc;
    }

    /**
     * @param document which You want to tag
     * @param tags the Tags You want to add. You can use a comma separated tag String
     * @return the updated Document
     * @throws Exception
     */
    public Document tagDocument(final Document document, String tags) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Services.TagDocument");
        // set params
        request.setInput(document);
        request.set("tags", tags);

        Document doc = (Document) request.execute();
        return doc;
    }

    public void resumeWorkflow(final String workflowId) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Workflow.ResumeNodeOperation");
        // set params
        request.set("workflowInstanceId", workflowId);
        request.execute();
    }
    // public void cancelWorkflow(final String workflowId) throws Exception {
    // // request
    // OperationRequest request = getSession().newRequest("Context.CancelWorkflow");
    // // set params
    // request.set("id", workflowId);
    // request.execute();
    // }

    public Document startWorkflow(final Document document, final String workflowId, final boolean start, final Properties properties) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Context.StartWorkflow");
        // set params
        request.setInput(document);
        request.set("id", workflowId);
        request.set("start", start);
        request.set("variables", properties);
        // exec + return
        Document doc = (Document) request.execute();
        return doc;
    }

    /**
     * @param document The Document You want the versions from
     * @return The List of {@link Document}s (Versions)
     * @throws Exception if something went wrong
     */
    public List<Document> getDocumentVersions(final Document document) throws Exception {
        // request
        OperationRequest request = getSession().newRequest("Document.GetVersions");
        // set params
        request.setInput(document);
        // exec + return
        List<Document> docs = ((Documents) request.execute()).list();
        return docs;
    }
    public Document createFolder(final String name, final String parentId) throws Exception {
        // request
        Properties properties = new Properties();
        properties.setProperty("dc:title", name);
        // properties.setProperty("dc:description", "some Description");
        Document doc = createDocument(getDocument(parentId), "Folder", properties);
        // exec + return
        return doc;
    }
    /**
     * @param document
     * @param increment not required - Values: None, Minor, Major
     * @param saveDocument default: no
     * @return
     * @throws IOException
     */
    public Document createVersion(Document document, String increment, boolean saveDocument) throws IOException {
        // request
        OperationRequest request = getSession().newRequest("Document.CreateVersion");
        // set params
        request.setInput(document);
        request.set("increment", increment);
        request.set("saveDocument", saveDocument);

        // exec + return
        Document doc = ((Document) request.execute());
        return doc;

    }
    public static void main(String[] args) throws Exception
    {
        NuxeoUtility util = new NuxeoUtility("http://127.0.0.1:8080/nuxeo/", "demo", "secret");

        Document doc = util.getDocument("40709d0b-e2b6-489b-8afd-bf655d5b84eb");

        // Map<String, Object> params = new HashMap<String, Object>();
        // params.put("workflowId", "Process_1:1:b358ea4d-c72a-11e4-b8b0-22b5c971964e");
        //
        // doc = util.startCamundaWorkflow(doc, params);

        // util.setWorkflowVar("a3786aeb-5e88-47fc-a75e-975c0bed1a5d", "finished", new Boolean(true));

        PropertyMap props = doc.getProperties();

        // update values
        // list values are handled strangely (comma separated values)
        // doc.set("workflowId", "df7542fa-de88-11e4-9ea2-22b5c971964e");

        doc.set("taskId", "0da81b8a-de9d-11e4-9ea2-22b5c971964e");

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("properties", props);

        doc = util.updateDocument(doc, params);

        doc = util.getDocument("40709d0b-e2b6-489b-8afd-bf655d5b84eb");
        props = doc.getProperties();

        System.out.println(props.get("workflowId"));

        util.shutdown();
    }

    private static void oldCode(NuxeoUtility util) throws Exception
    {
        List<String> ids = util.getAllDocumentIds(0, Long.MAX_VALUE);
        String url = null;
        for(String id : ids)
        {
            //versions
            System.out.println(id + " - " + util.getDocumentVersion(id));

            //modified times
            // System.out.println("time - " + util.getDocumentVersion(id));

            //URLs
            url = util.getURI(id);
            //System.out.println("URI - " + url);

            //blobs
            System.out.println("Blob Filename - " + util.getBlob(id).getFileName());

            //ACLs
            System.out.println("ACL - " + util.getACLs(id));

            //all versions
            System.out.println("all versions:");
            List<String> versions = util.getAllVersions(id);
            for(String version : versions)
            {
                System.out.println("\t" + version);
                url = util.getURI(version);
                // System.out.println("\t\ttime - " + util.getDocumentVersion(version));
            }

            //new entry
            System.out.println();
            System.out.println();
        }
    }
}
