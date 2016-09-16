/**
 * 
 */
package de.hofuniversity.iisys.cmis;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.FileableCmisObject;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.ObjectFactory;
import org.apache.chemistry.opencmis.client.api.ObjectId;
import org.apache.chemistry.opencmis.client.api.ObjectType;
import org.apache.chemistry.opencmis.client.api.Policy;
import org.apache.chemistry.opencmis.client.api.Property;
import org.apache.chemistry.opencmis.client.api.QueryResult;
import org.apache.chemistry.opencmis.client.api.QueryStatement;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.Tree;
import org.apache.chemistry.opencmis.client.runtime.ObjectIdImpl;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.Ace;
import org.apache.chemistry.opencmis.commons.data.Acl;
import org.apache.chemistry.opencmis.commons.data.AllowableActions;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.data.RepositoryCapabilities;
import org.apache.chemistry.opencmis.commons.data.RepositoryInfo;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinition;
import org.apache.chemistry.opencmis.commons.enums.AclPropagation;
import org.apache.chemistry.opencmis.commons.enums.Action;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author cstrobel
 *
 */
public class OpenCMISClient {
    private static final transient Logger LOG = LoggerFactory.getLogger(OpenCMISClient.class);
    public static final String ALL_COLLECTION_QUERY = "SELECT * FROM cmis:document WHERE cmis:objectTypeId = 'Collection'";
    private final Session session;
    public OpenCMISClient(final String url, final String user, final String password, final String repositoryId, final String bindingType) {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(SessionParameter.BINDING_TYPE, bindingType);
        // TODO implement SOAP, WSDL, etc.
        if (BindingType.ATOMPUB.value().equals(bindingType)) {
            parameters.put(SessionParameter.ATOMPUB_URL, url);
        }
        parameters.put(SessionParameter.REPOSITORY_ID, repositoryId);
        parameters.put(SessionParameter.USER, user);
        parameters.put(SessionParameter.PASSWORD, password);

        // start session
        session = SessionFactoryImpl.newInstance().createSession(parameters);
    }
    /**
     * 
     */
    public OpenCMISClient(final String url, final String user, final String password, final String repositoryId, final BindingType bindingType) {
        this(url, user, password, repositoryId, bindingType.value());
    }
    public void shutdown() {
        session.clear();
    }
    public enum CMISDocumentTypes {
        DOC("cmis:document"), COL("Collection"), FOL("cmis:folder");
        private final String identifier;
        
        CMISDocumentTypes(final String identifier) {
            this.identifier = identifier;
            
        }

        /**
         * @return the identifier
         */
        public String getIdentifier() {
            return identifier;
        }
    }

    public void printInfoAboutTheRepository() {
        // get repository info
        RepositoryInfo repInfo = session.getRepositoryInfo();
        System.out.println("Repository name: " + repInfo.getName());
        getRepositoryCapabilities(repInfo);

        // get root folder and its path
        Folder rootFolder = session.getRootFolder();
        String path = rootFolder.getPath();
        System.out.println("Root folder path: " + path);

        // list root folder children
        printAllFiles(rootFolder);
        printPropertiesOfADocument(rootFolder);

        // get an object
        ObjectId objectId = session.createObjectId("100");
        CmisObject object = session.getObject(objectId);

        if (object instanceof Folder) {
            Folder folder = (Folder) object;
            System.out.println("Is root folder: " + folder.isRootFolder());
        }

        if (object instanceof Document) {
            Document document = (Document) object;
            ContentStream content = document.getContentStream();
            System.out.println("Document MIME type: " + content.getMimeType());
        }
    }

    /**
     * @param mimeType for Example: "text/plain; charset=UTF-8"
     * @param content
     * @param filename
     * @return
     * @throws UnsupportedEncodingException
     */
    public ContentStream getContentStreamForSource(final String mimeType, final String content, final String filename) throws UnsupportedEncodingException {
        byte[] buf = content.getBytes("UTF-8");
        ByteArrayInputStream input = new ByteArrayInputStream(buf);

        ContentStream contentStream = session.getObjectFactory().createContentStream(filename, buf.length, mimeType, input);
        return contentStream;
    }

    private void getRepositoryCapabilities(RepositoryInfo repInfo) {
        RepositoryCapabilities cap = repInfo.getCapabilities();
        System.out.println("\nNavigation Capabilities");
        System.out.println("-----------------------");
        System.out.println("Get descendants supported: " + (cap.isGetDescendantsSupported() ? "true" : "false"));
        System.out.println("Get folder tree supported: " + (cap.isGetFolderTreeSupported() ? "true" : "false"));
        System.out.println("\nObject Capabilities");
        System.out.println("-----------------------");
        System.out.println("Content Stream: " + cap.getContentStreamUpdatesCapability().value());
        System.out.println("Changes: " + cap.getChangesCapability().value());
        System.out.println("Renditions: " + cap.getRenditionsCapability().value());
        System.out.println("\nFiling Capabilities");
        System.out.println("-----------------------");
        System.out.println("Multifiling supported: " + (cap.isMultifilingSupported() ? "true" : "false"));
        System.out.println("Unfiling supported: " + (cap.isUnfilingSupported() ? "true" : "false"));
        System.out.println("Version specific filing supported: " + (cap.isVersionSpecificFilingSupported() ? "true" : "false"));
        System.out.println("\nVersioning Capabilities");
        System.out.println("-----------------------");
        System.out.println("PWC searchable: " + (cap.isPwcSearchableSupported() ? "true" : "false"));
        System.out.println("PWC updatable: " + (cap.isPwcUpdatableSupported() ? "true" : "false"));
        System.out.println("All versions searchable: " + (cap.isAllVersionsSearchableSupported() ? "true" : "false"));
        System.out.println("\nQuery Capabilities");
        System.out.println("-----------------------");
        System.out.println("Query: " + cap.getQueryCapability().value());
        System.out.println("Join: " + cap.getJoinCapability().value());
        System.out.println("\nACL Capabilities");
        System.out.println("-----------------------");
        System.out.println("ACL: " + cap.getAclCapability().value());
        System.out.println("End of  repository capabilities");
    }

    private void printAllFiles(Folder rootFolder) {
        ItemIterable<CmisObject> children = rootFolder.getChildren();
        for (CmisObject object : children) {
            System.out.println("---------------------------------");
            System.out.println("    Id:              " + object.getId());
            System.out.println("    Name:            " + object.getName());
            System.out.println("    Base Type:       " + object.getBaseTypeId());
            System.out.println("    Property 'bla':  " + object.getPropertyValue("bla"));

            ObjectType type = object.getType();
            System.out.println("    Type Id:          " + type.getId());
            System.out.println("    Type Name:        " + type.getDisplayName());
            System.out.println("    Type Query Name:  " + type.getQueryName());

            AllowableActions actions = object.getAllowableActions();
            System.out.println("    canGetProperties: " + actions.getAllowableActions().contains(Action.CAN_GET_PROPERTIES));
            System.out.println("    canDeleteObject:  " + actions.getAllowableActions().contains(Action.CAN_DELETE_OBJECT));

            if (!session.getRepositoryInfo().getCapabilities().isGetDescendantsSupported()) {
                System.out.println("getDescendants not supported in this repository");
            } else {
                System.out.println("Descendants of " + rootFolder.getName() + ":-");
                for (Tree<FileableCmisObject> t : rootFolder.getDescendants(-1)) {
                    printTree(t);
                }
            }

        }
    }
    private void printPropertiesOfADocument(final FileableCmisObject document) {
        List<Property<?>> props = document.getProperties();
        for (Property<?> p : props) {
            System.out.println(p.getDefinition().getDisplayName() + "=" + p.getValuesAsString());
        }
        printAllowableActionsOfADocument(document);
    }

    private void printAllowableActionsOfADocument(final FileableCmisObject document) {
        if (document.getAllowableActions().getAllowableActions().contains(Action.CAN_CHECK_OUT)) {
            System.out.println("can check out " + document.getName());
            System.out.println("Getting the current allowable actions for the " + document.getName() + " document object...");
            for (Action a : document.getAllowableActions().getAllowableActions()) {
                System.out.println("\t" + a.value());
            }
        } else {
            System.out.println("can not check out " + document.getName());
        }
    }

    private static void printTree(Tree<FileableCmisObject> tree) {
        System.out.println("Descendant " + tree.getItem().getName());
        for (Tree<FileableCmisObject> t : tree.getChildren()) {
            printTree(t);
        }
    }

    /**
     * @param id
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#createObjectId(java.lang.String)
     */
    public ObjectId createObjectId(String id) {
        return session.createObjectId(id);
    }

    /**
     * @param typeId
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#getTypeDefinition(java.lang.String)
     */
    public ObjectType getTypeDefinition(String typeId) {
        return session.getTypeDefinition(typeId);
    }

    /**
     * @param typeId
     * @param useCache
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#getTypeDefinition(java.lang.String, boolean)
     */
    public ObjectType getTypeDefinition(String typeId, boolean useCache) {
        return session.getTypeDefinition(typeId, useCache);
    }

    /**
     * @param typeId
     * @param includePropertyDefinitions
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#getTypeChildren(java.lang.String, boolean)
     */
    public ItemIterable<ObjectType> getTypeChildren(String typeId, boolean includePropertyDefinitions) {
        return session.getTypeChildren(typeId, includePropertyDefinitions);
    }

    /**
     * @param type
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#createType(org.apache.chemistry.opencmis.commons.definitions.TypeDefinition)
     */
    public ObjectType createType(TypeDefinition type) {
        return session.createType(type);
    }

    /**
     * @param typeId
     * @see org.apache.chemistry.opencmis.client.api.Session#deleteType(java.lang.String)
     */
    public void deleteType(String typeId) {
        session.deleteType(typeId);
    }

    /**
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#getRootFolder()
     */
    public Folder getRootFolder() {
        return session.getRootFolder();
    }

    /**
     * @param objectId
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#getObject(org.apache.chemistry.opencmis.client.api.ObjectId)
     */
    public CmisObject getObject(ObjectId objectId) {
        return session.getObject(objectId);
    }

    /**
     * @param objectId
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#getObject(java.lang.String)
     */
    public CmisObject getObject(String objectId) {
        return session.getObject(objectId);
    }

    /**
     * @param path
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#getObjectByPath(java.lang.String)
     */
    public CmisObject getObjectByPath(String path) {
        return session.getObjectByPath(path);
    }

    /**
     * @param parentPath
     * @param name
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#getObjectByPath(java.lang.String, java.lang.String)
     */
    public CmisObject getObjectByPath(String parentPath, String name) {
        return session.getObjectByPath(parentPath, name);
    }

    /**
     * @param objectId
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#getLatestDocumentVersion(org.apache.chemistry.opencmis.client.api.ObjectId)
     */
    public Document getLatestDocumentVersion(ObjectId objectId) {
        return session.getLatestDocumentVersion(objectId);
    }

    /**
     * @param objectId
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#getLatestDocumentVersion(java.lang.String)
     */
    public Document getLatestDocumentVersion(String objectId) {
        return session.getLatestDocumentVersion(objectId);
    }

    /**
     * https://wiki.alfresco.com/wiki/CMIS_Query_Language
     * @param statement
     * @param searchAllVersions
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#query(java.lang.String, boolean)
     */
    public ItemIterable<QueryResult> query(String statement, boolean searchAllVersions) {
        return session.query(statement, searchAllVersions);
    }

    /**
     * @param statement
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#createQueryStatement(java.lang.String)
     */
    public QueryStatement createQueryStatement(String statement) {
        return session.createQueryStatement(statement);
    }

    /**
     * @param properties
     * @param folderId
     * @param contentStream
     * @param versioningState
     * @param policies
     * @param addAces
     * @param removeAces
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#createDocument(java.util.Map, org.apache.chemistry.opencmis.client.api.ObjectId,
     *      org.apache.chemistry.opencmis.commons.data.ContentStream, org.apache.chemistry.opencmis.commons.enums.VersioningState, java.util.List,
     *      java.util.List, java.util.List)
     */
    public ObjectId createDocument(Map<String, String> properties, ObjectId folderId, ContentStream contentStream, VersioningState versioningState, List<Policy> policies,
            List<Ace> addAces, List<Ace> removeAces) {
        return session.createDocument(properties, folderId, contentStream, versioningState, policies, addAces, removeAces);
    }
    public static ObjectId getObjectId(final String stringID) {
        return new ObjectIdImpl(stringID);
    }

    /**
     * @param properties
     * @param folderId
     * @param contentStream
     * @param versioningState
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#createDocument(java.util.Map, org.apache.chemistry.opencmis.client.api.ObjectId,
     *      org.apache.chemistry.opencmis.commons.data.ContentStream, org.apache.chemistry.opencmis.commons.enums.VersioningState)
     */
    public ObjectId createDocument(Map<String, String> properties, ObjectId folderId, ContentStream contentStream, VersioningState versioningState) {
        return session.createDocument(properties, folderId, contentStream, versioningState);
    }

    /**
     * @param source
     * @param properties
     * @param folderId
     * @param versioningState
     * @param policies
     * @param addAces
     * @param removeAces
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#createDocumentFromSource(org.apache.chemistry.opencmis.client.api.ObjectId, java.util.Map,
     *      org.apache.chemistry.opencmis.client.api.ObjectId, org.apache.chemistry.opencmis.commons.enums.VersioningState, java.util.List, java.util.List,
     *      java.util.List)
     */
    public ObjectId createDocumentFromSource(ObjectId source, Map<String, ?> properties, ObjectId folderId, VersioningState versioningState, List<Policy> policies,
            List<Ace> addAces, List<Ace> removeAces) {
        return session.createDocumentFromSource(source, properties, folderId, versioningState, policies, addAces, removeAces);
    }

    /**
     * @param source
     * @param properties
     * @param folderId
     * @param versioningState
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#createDocumentFromSource(org.apache.chemistry.opencmis.client.api.ObjectId, java.util.Map,
     *      org.apache.chemistry.opencmis.client.api.ObjectId, org.apache.chemistry.opencmis.commons.enums.VersioningState)
     */
    public ObjectId createDocumentFromSource(ObjectId source, Map<String, ?> properties, ObjectId folderId, VersioningState versioningState) {
        return session.createDocumentFromSource(source, properties, folderId, versioningState);
    }

    /**
     * @param properties
     * @param folderId
     * @param policies
     * @param addAces
     * @param removeAces
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#createFolder(java.util.Map, org.apache.chemistry.opencmis.client.api.ObjectId, java.util.List,
     *      java.util.List, java.util.List)
     */
    public ObjectId createFolder(Map<String, ?> properties, ObjectId folderId, List<Policy> policies, List<Ace> addAces, List<Ace> removeAces) {
        return session.createFolder(properties, folderId, policies, addAces, removeAces);
    }

    /**
     * @param properties
     * @param folderId
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#createFolder(java.util.Map, org.apache.chemistry.opencmis.client.api.ObjectId)
     */
    public ObjectId createFolder(Map<String, ?> properties, ObjectId folderId) {
        return session.createFolder(properties, folderId);
    }

    /**
     * @param properties
     * @param folderId
     * @param policies
     * @param addAces
     * @param removeAces
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#createItem(java.util.Map, org.apache.chemistry.opencmis.client.api.ObjectId, java.util.List,
     *      java.util.List, java.util.List)
     */
    public ObjectId createItem(Map<String, ?> properties, ObjectId folderId, List<Policy> policies, List<Ace> addAces, List<Ace> removeAces) {
        return session.createItem(properties, folderId, policies, addAces, removeAces);
    }

    /**
     * @param properties
     * @param folderId
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#createItem(java.util.Map, org.apache.chemistry.opencmis.client.api.ObjectId)
     */
    public ObjectId createItem(Map<String, ?> properties, ObjectId folderId) {
        return session.createItem(properties, folderId);
    }

    /**
     * @param objectId
     * @see org.apache.chemistry.opencmis.client.api.Session#delete(org.apache.chemistry.opencmis.client.api.ObjectId)
     */
    public void delete(ObjectId objectId) {
        session.delete(objectId);
    }

    /**
     * @param objectId
     * @param allVersions
     * @see org.apache.chemistry.opencmis.client.api.Session#delete(org.apache.chemistry.opencmis.client.api.ObjectId, boolean)
     */
    public void delete(ObjectId objectId, boolean allVersions) {
        session.delete(objectId, allVersions);
    }

    /**
     * @param objectId
     * @param onlyBasicPermissions
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#getAcl(org.apache.chemistry.opencmis.client.api.ObjectId, boolean)
     */
    public Acl getAcl(ObjectId objectId, boolean onlyBasicPermissions) {
        return session.getAcl(objectId, onlyBasicPermissions);
    }

    /**
     * @param objectId
     * @param addAces
     * @param removeAces
     * @param aclPropagation
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#applyAcl(org.apache.chemistry.opencmis.client.api.ObjectId, java.util.List, java.util.List,
     *      org.apache.chemistry.opencmis.commons.enums.AclPropagation)
     */
    public Acl applyAcl(ObjectId objectId, List<Ace> addAces, List<Ace> removeAces, AclPropagation aclPropagation) {
        return session.applyAcl(objectId, addAces, removeAces, aclPropagation);
    }

    /**
     * @param objectId
     * @param aces
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#setAcl(org.apache.chemistry.opencmis.client.api.ObjectId, java.util.List)
     */
    public Acl setAcl(ObjectId objectId, List<Ace> aces) {
        return session.setAcl(objectId, aces);
    }

    /**
     * @param objectId
     * @param policyIds
     * @see org.apache.chemistry.opencmis.client.api.Session#applyPolicy(org.apache.chemistry.opencmis.client.api.ObjectId,
     *      org.apache.chemistry.opencmis.client.api.ObjectId[])
     */
    public void applyPolicy(ObjectId objectId, ObjectId... policyIds) {
        session.applyPolicy(objectId, policyIds);
    }
    public void addDocumentToCollection(Document collection, Document document2) {

        throw new RuntimeException("needs to be implemented");

    }
    public FileableCmisObject moveDocument(String documentId, String currentFolder, String newFolderId) {
        Document document =getLatestDocumentVersion(documentId);
        // Document currFolder = getLatestDocumentVersion(currentFolder);
        // Document newFolder = getLatestDocumentVersion(newFolderId);
        FileableCmisObject newCmisObject = document.move(new ObjectIdImpl(currentFolder), new ObjectIdImpl(newFolderId));
        return newCmisObject;
    }
    /**
     * @return
     * @see org.apache.chemistry.opencmis.client.api.Session#getObjectFactory()
     */
    public ObjectFactory getObjectFactory() {
        return session.getObjectFactory();
    }
}
