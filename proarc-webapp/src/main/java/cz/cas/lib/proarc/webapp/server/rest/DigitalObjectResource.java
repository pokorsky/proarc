/*
 * Copyright (C) 2011 Jan Pokorsky
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.cas.lib.proarc.webapp.server.rest;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;
import com.yourmediashelf.fedora.client.FedoraClientException;
import cz.cas.lib.proarc.common.config.AppConfiguration;
import cz.cas.lib.proarc.common.config.AppConfigurationException;
import cz.cas.lib.proarc.common.config.AppConfigurationFactory;
import cz.cas.lib.proarc.common.dao.Batch;
import cz.cas.lib.proarc.common.dao.BatchItem.ObjectState;
import cz.cas.lib.proarc.common.dublincore.DcStreamEditor;
import cz.cas.lib.proarc.common.dublincore.DcStreamEditor.DublinCoreRecord;
import cz.cas.lib.proarc.common.fedora.AtmEditor;
import cz.cas.lib.proarc.common.fedora.AtmEditor.AtmItem;
import cz.cas.lib.proarc.common.fedora.BinaryEditor;
import cz.cas.lib.proarc.common.fedora.DigitalObjectException;
import cz.cas.lib.proarc.common.fedora.DigitalObjectNotFoundException;
import cz.cas.lib.proarc.common.fedora.FedoraObject;
import cz.cas.lib.proarc.common.fedora.FoxmlUtils;
import cz.cas.lib.proarc.common.fedora.LocalStorage;
import cz.cas.lib.proarc.common.fedora.LocalStorage.LocalObject;
import cz.cas.lib.proarc.common.fedora.PurgeFedoraObject;
import cz.cas.lib.proarc.common.fedora.PurgeFedoraObject.PurgeException;
import cz.cas.lib.proarc.common.fedora.RemoteStorage;
import cz.cas.lib.proarc.common.fedora.RemoteStorage.RemoteObject;
import cz.cas.lib.proarc.common.fedora.SearchView;
import cz.cas.lib.proarc.common.fedora.SearchView.Item;
import cz.cas.lib.proarc.common.fedora.StringEditor;
import cz.cas.lib.proarc.common.fedora.StringEditor.StringRecord;
import cz.cas.lib.proarc.common.fedora.relation.RelationEditor;
import cz.cas.lib.proarc.common.imports.ImportBatchManager;
import cz.cas.lib.proarc.common.imports.ImportBatchManager.BatchItemObject;
import cz.cas.lib.proarc.common.object.DescriptionMetadata;
import cz.cas.lib.proarc.common.object.DigitalObjectExistException;
import cz.cas.lib.proarc.common.object.DigitalObjectHandler;
import cz.cas.lib.proarc.common.object.DigitalObjectManager;
import cz.cas.lib.proarc.common.object.DisseminationHandler;
import cz.cas.lib.proarc.common.object.DisseminationInput;
import cz.cas.lib.proarc.common.object.MetadataHandler;
import cz.cas.lib.proarc.common.object.model.MetaModel;
import cz.cas.lib.proarc.common.object.model.MetaModelRepository;
import cz.cas.lib.proarc.common.user.UserProfile;
import cz.cas.lib.proarc.webapp.shared.rest.DigitalObjectResourceApi;
import cz.cas.lib.proarc.webapp.shared.rest.DigitalObjectResourceApi.SearchType;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 * Resource to manage digital objects.
 * 
 *      /object/{pid}/ GET - read DigObjDesc:{pid, displayname, date, owner};
 *      /object/ GET - lists all DigObjDesc
 *      /object/{pid}/foxml
 *      /object/{pid}/scan
 *      /object/{pid}/preview
 *      /object/{pid}/thumb
 *      /object/{pid}/ocr
 *      /object/{pid}/metadata
 *      /object/{pid}/relations
 *      /object/metamodel/ GET - lists model:{pid, displayname, type:(TOP|LEAF)}
 *
 * @author Jan Pokorsky
 */
@Path(DigitalObjectResourceApi.PATH)
public class DigitalObjectResource {

    private static final Logger LOG = Logger.getLogger(DigitalObjectResource.class.getName());

    private final AppConfiguration appConfig;
    private final MetaModelRepository metamodels = MetaModelRepository.getInstance();
    private final ImportBatchManager importManager;
    private final Request httpRequest;
    private final HttpHeaders httpHeaders;
    private final UserProfile user;
    private final SessionContext session;

    public DigitalObjectResource(
            @Context Request request,
            @Context SecurityContext securityCtx,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo uriInfo,
            @Context HttpServletRequest httpRequest
            ) throws AppConfigurationException {
        
        this.httpRequest = request;
        this.httpHeaders = httpHeaders;
        this.appConfig = AppConfigurationFactory.getInstance().defaultInstance();
        this.importManager = ImportBatchManager.getInstance(appConfig);
        session = SessionContext.from(httpRequest);
        user = session.getUser();
        LOG.fine(user.toString());
    }

    /**
     * Creates a new digital object
     *
     * @param modelId model ID (model:page, ...) of the digital object; required
     * @param pid PID of the digital object from external Kramerius. PID must not be already assigned. Optional
     * @param parentPid optional PID of parent object to link the newly created object
     * @param xmlMetadata XML used to create new object; optional
     * @return
     * @throws URISyntaxException
     * @throws IOException
     */
    @POST
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<Item> newObject(
            @FormParam(DigitalObjectResourceApi.DIGITALOBJECT_MODEL) String modelId,
            @FormParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @FormParam(DigitalObjectResourceApi.MEMBERS_ITEM_PARENT) String parentPid,
            @FormParam(DigitalObjectResourceApi.NEWOBJECT_XML_PARAM) String xmlMetadata
            ) throws DigitalObjectException {

        if (modelId == null) {
            // XXX validate modelId values
            throw RestException.plainNotFound(DigitalObjectResourceApi.DIGITALOBJECT_MODEL, modelId);
        }
        if (pid != null) {
            boolean invalid = pid.length() < 5;
            try {
                if (!invalid) {
                    UUID uuid = UUID.fromString(FoxmlUtils.pidAsUuid(pid));
                    pid = FoxmlUtils.pidFromUuid(uuid.toString());
                }
            } catch (IllegalArgumentException e) {
                invalid = true;
            }
            if (invalid) {
                return SmartGwtResponse.<Item>asError().error(
                        DigitalObjectResourceApi.DIGITALOBJECT_PID, "Invalid PID!").build();
            }
        }
        xmlMetadata = (xmlMetadata == null || xmlMetadata.isEmpty() || "null".equals(xmlMetadata)) ? null : xmlMetadata;
        LOG.log(Level.FINE, "model: {0}, pid: {3}, parent: {2}, XML: {1}",
                new Object[] {modelId, xmlMetadata, parentPid, pid});

        DigitalObjectManager dom = DigitalObjectManager.getDefault();
        try {
            Item item = dom.createDigitalObject(modelId, pid, parentPid, user, xmlMetadata, session.asFedoraLog());
            return new SmartGwtResponse<Item>(item);
        } catch (DigitalObjectExistException ex) {
            return SmartGwtResponse.<Item>asError().error("pid", "Object already exists!").build();
        }
    }

    /**
     * @see PurgeFedoraObject
     */
    @DELETE
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<DigitalObject> deleteObject(
            @QueryParam(DigitalObjectResourceApi.DELETE_PID_PARAM) List<String> pids,
            @QueryParam(DigitalObjectResourceApi.DELETE_HIERARCHY_PARAM)
            @DefaultValue("true") boolean hierarchy,
            @QueryParam(DigitalObjectResourceApi.DELETE_PURGE_PARAM)
            @DefaultValue("false") boolean purge
            ) throws IOException, PurgeException {

        RemoteStorage fedora = RemoteStorage.getInstance(appConfig);
        ArrayList<DigitalObject> result = new ArrayList<DigitalObject>(pids.size());
        PurgeFedoraObject service = new PurgeFedoraObject(fedora);
        if (purge) {
            service.purge(pids, hierarchy, session.asFedoraLog());
        } else {
            service.delete(pids, hierarchy, session.asFedoraLog());
        }
        for (String pid : pids) {
            result.add(new DigitalObject(pid, null));
        }
        return new SmartGwtResponse<DigitalObject>(result);
    }

    @GET
    @Path(DigitalObjectResourceApi.SEARCH_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<Item> search(
            @QueryParam(DigitalObjectResourceApi.SEARCH_OWNER_PARAM) String owner,
            @DefaultValue(SearchType.DEFAULT)
            @QueryParam(DigitalObjectResourceApi.SEARCH_TYPE_PARAM) SearchType type,
            @QueryParam(DigitalObjectResourceApi.SEARCH_PID_PARAM) List<String> pids,
            @QueryParam(DigitalObjectResourceApi.SEARCH_BATCHID_PARAM) Integer batchId,
            @QueryParam(DigitalObjectResourceApi.SEARCH_PHRASE_PARAM) String phrase,
            @QueryParam(DigitalObjectResourceApi.SEARCH_QUERY_IDENTIFIER_PARAM) String queryIdentifier,
            @QueryParam(DigitalObjectResourceApi.SEARCH_QUERY_LABEL_PARAM) String queryLabel,
            @QueryParam(DigitalObjectResourceApi.SEARCH_QUERY_MODEL_PARAM) String queryModel,
            @QueryParam(DigitalObjectResourceApi.SEARCH_QUERY_TITLE_PARAM) String queryTitle,
            @QueryParam(DigitalObjectResourceApi.SEARCH_START_ROW_PARAM) int startRow
            ) throws FedoraClientException, IOException {

        Locale locale = session.getLocale(httpHeaders);
        SearchView search = RemoteStorage.getInstance(appConfig).getSearch(locale);
        List<Item> items;
        int page = 20;
        switch (type) {
            case LAST_MODIFIED:
                items = search.findLastModified(startRow, queryModel, 100);
                break;
            case QUERY:
                items = search.findQuery(queryTitle, queryLabel, queryIdentifier, owner, queryModel);
                page = 1;
                break;
            case PIDS:
                items = search.find(pids);
                page = 1;
                break;
            case PHRASE:
                items = search.findPhrase(phrase);
                page = 1;
                break;
            case PARENT:
                items = searchParent(batchId, pids, search);
                page = 1;
                break;
            default:
                items = search.findLastCreated(startRow, queryModel);
        }
        int count = items.size();
        int endRow = startRow + count - 1;
        int total = count == 0 ? startRow : endRow + page;
        return new SmartGwtResponse<Item>(SmartGwtResponse.STATUS_SUCCESS, startRow, endRow, total, items);
    }

    private List<Item> searchParent(Integer batchId, List<String> pids, SearchView search)
            throws IOException, FedoraClientException {
        
        if (batchId != null) {
            Batch batch = importManager.get(batchId);
            String parentPid = batch == null ? null : batch.getParentPid();
            if (parentPid == null) {
                return Collections.emptyList();
            } else {
                return search.find(parentPid);
            }
        } else {
            if (pids == null || pids.size() != 1) {
                throw RestException.plainText(Status.BAD_REQUEST, "parent search requires single pid parameter");
            }
            return search.findReferrers(pids.get(0));
        }
    }

    /**
     * Gets members of a digital object.
     * @param parent PID of digital object to query its members. {@code root} parameter is ignored.
     * @param root PID of digital object to return itself as a member with {@link Item#parent} as {@code null}.
     *          Useful to show root of the member hierarchy.
     * @return ordered list of members
     */
    @GET
    @Path(DigitalObjectResourceApi.MEMBERS_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<Item> findMembers(
            @QueryParam(DigitalObjectResourceApi.MEMBERS_ITEM_PARENT) String parent,
            @QueryParam(DigitalObjectResourceApi.MEMBERS_ROOT_PARAM) String root
            ) throws FedoraClientException, IOException, DigitalObjectException {

        SearchView search = RemoteStorage.getInstance(appConfig).getSearch(session.getLocale(httpHeaders));
        List<Item> items;
        String parentPid;
        if (parent == null || "null".equals(parent)) {
            items = search.find(root);
            parentPid = null;
        } else {
            items = search.findSortedChildren(parent);
            parentPid = parent;
        }
        for (Item item : items) {
            item.setParentPid(parentPid);
        }
        return new SmartGwtResponse<Item>(items);
    }

    /**
     * Sets new member sequence of given parent digital object.
     *
     * @param parentPid parent PID
     * @param batchId batch import ID
     * @param toSetPids list of member PIDS
     * @return ordered list of members
     * @throws RestException
     */
    @PUT
    @Path(DigitalObjectResourceApi.MEMBERS_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<Item> setMembers(
            @FormParam(DigitalObjectResourceApi.MEMBERS_ITEM_PARENT) String parentPid,
            @FormParam(DigitalObjectResourceApi.MEMBERS_ITEM_BATCHID) Integer batchId,
            @FormParam(DigitalObjectResourceApi.MEMBERS_ITEM_PID) List<String> toSetPids
            // XXX long timestamp
            ) throws IOException, FedoraClientException, DigitalObjectException {

        if (batchId == null && parentPid == null) {
            throw RestException.plainNotFound(DigitalObjectResourceApi.MEMBERS_ITEM_PARENT, null);
        }
        boolean batchImportMembers = batchId != null;
        if (toSetPids == null || toSetPids.isEmpty()) {
            throw RestException.plainNotFound(DigitalObjectResourceApi.MEMBERS_ITEM_PID, null);
        }
        if (!batchImportMembers && toSetPids.contains(parentPid)) {
            throw RestException.plainText(Status.BAD_REQUEST, "parent and pid are same!");
        }

        HashSet<String> toSetPidSet = new HashSet<String>(toSetPids);
        if (toSetPidSet.size() != toSetPids.size()) {
            throw RestException.plainText(Status.BAD_REQUEST, "duplicates in PIDs to set!\n" + toSetPids.toString());
        }

        // fetch PID[] -> Item[]
        Map<String, Item> memberSearchMap;
        if (batchImportMembers) {
            memberSearchMap = loadLocalSearchItems(batchId);
        } else {
            memberSearchMap = loadSearchItems(toSetPidSet);
        }
        checkSetMembers(toSetPidSet, memberSearchMap);
        // load current members
        FedoraObject fobject = findFedoraObject(parentPid, batchId, false);
        RelationEditor editor = new RelationEditor(fobject);
        List<String> members = editor.getMembers();
        members.clear();
        // add new members
        ArrayList<Item> added = new ArrayList<Item>();
        for (String addPid : toSetPids) {
            if (!members.contains(addPid)) {
                members.add(addPid);
                Item item = memberSearchMap.get(addPid);
                if (item == null) {
                    throw RestException.plainNotFound(DigitalObjectResourceApi.MEMBERS_ITEM_PID,
                            toSetPids.toString());
                }
                item.setParentPid(parentPid);
                added.add(item);
            }
        }
        editor.setMembers(members);
        editor.write(editor.getLastModified(), session.asFedoraLog());
        fobject.flush();
        return new SmartGwtResponse<Item>(added);
    }

    private Map<String, Item> loadSearchItems(Set<String> pids) throws IOException, FedoraClientException {
        RemoteStorage storage = RemoteStorage.getInstance(appConfig);
        HashMap<String, Item> memberSearchMap = new HashMap<String, Item>();
        List<Item> memberSearch = storage.getSearch().find(new ArrayList<String>(pids));
        for (Item item : memberSearch) {
            memberSearchMap.put(item.getPid(), item);
        }
        return memberSearchMap;
    }

    private Map<String, Item> loadLocalSearchItems(int batchId) throws IOException, DigitalObjectException {
        HashMap<String, Item> memberSearchMap = new HashMap<String, Item>();
        List<BatchItemObject> batchObjects = importManager.findBatchObjects(batchId, null);
        for (BatchItemObject batchObject : batchObjects) {
            if (batchObject.getState() != ObjectState.LOADED) {
                continue;
            }
            LocalObject lfo = (LocalObject) findFedoraObject(batchObject.getPid(), batchId);
            Item item = new Item(batchObject.getPid());
            item.setBatchId(batchId);
            item.setLabel(lfo.getLabel());
            item.setOwner(lfo.getOwner());
            RelationEditor relationEditor = new RelationEditor(lfo);
            item.setModel(relationEditor.getModel());
            memberSearchMap.put(batchObject.getPid(), item);
        }
        return memberSearchMap;
    }

    private void checkSetMembers(Set<String> pids, Map<String, Item> memberSearchMap) throws IllegalStateException {
        if (!pids.equals(memberSearchMap.keySet())) {
            HashSet<String> notMembers = new HashSet<String>(pids);
            notMembers.removeAll(memberSearchMap.keySet());
            HashSet<String> missingPids = new HashSet<String>(memberSearchMap.keySet());
            missingPids.removeAll(pids);
            throw new IllegalStateException("PIDs not members: " + notMembers.toString()
                    + "\nMissing PIDs: " + missingPids.toString());
        }
    }

    /**
     * Adds new object members. Members that already exists remain untouched.
     * 
     * @param parentPid PID of parent object
     * @param toAddPids list of PIDs to add; cannot contain parent PID
     * @return list of added members
     */
    @POST
    @Path(DigitalObjectResourceApi.MEMBERS_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<Item> addMembers(
            @FormParam(DigitalObjectResourceApi.MEMBERS_ITEM_PARENT) String parentPid,
            @FormParam(DigitalObjectResourceApi.MEMBERS_ITEM_PID) List<String> toAddPids
            ) throws IOException, FedoraClientException, DigitalObjectException {

        if (parentPid == null) {
            throw RestException.plainNotFound(DigitalObjectResourceApi.MEMBERS_ITEM_PARENT, null);
        }
        if (toAddPids == null || toAddPids.isEmpty()) {
            throw RestException.plainNotFound(DigitalObjectResourceApi.MEMBERS_ITEM_PID, null);
        }
        if (toAddPids.contains(parentPid)) {
            throw RestException.plainText(Status.BAD_REQUEST, "parent and pid are same!");
        }

        Locale locale = session.getLocale(httpHeaders);
        HashSet<String> toAddPidSet = new HashSet<String>(toAddPids);
        if (toAddPidSet.isEmpty()) {
            return new SmartGwtResponse<Item>(Collections.<Item>emptyList());
        }

        RemoteStorage storage = RemoteStorage.getInstance(appConfig);
        // fetch PID[] -> Item[]
        List<Item> memberSearch = storage.getSearch(locale).find(toAddPids);
        HashMap<String, Item> memberSearchMap = new HashMap<String, Item>(memberSearch.size());
        for (Item item : memberSearch) {
            memberSearchMap.put(item.getPid(), item);
        }

        // check if fetched items corresponds to queried pids
        if (memberSearch.size() != toAddPidSet.size()) {
            ArrayList<String> memberSearchAsPids = new ArrayList<String>(memberSearch.size());
            for (Item item : memberSearch) {
                memberSearchAsPids.add(item.getPid());
            }
            toAddPidSet.removeAll(memberSearchAsPids);
            throw RestException.plainNotFound(
                    DigitalObjectResourceApi.MEMBERS_ITEM_PID, toAddPidSet.toString());
        }
        // load current members
        RemoteObject remote = storage.find(parentPid);
        RelationEditor editor = new RelationEditor(remote);
        List<String> members = editor.getMembers();
        // add new members
        ArrayList<Item> added = new ArrayList<Item>();
        for (String addPid : toAddPids) {
            if (!members.contains(addPid)) {
                members.add(addPid);
                Item item = memberSearchMap.get(addPid);
                if (item == null) {
                    throw RestException.plainNotFound("pid", toAddPidSet.toString());
                }
                item.setParentPid(parentPid);
                added.add(item);
            }
        }
        // write if any change
        if (!added.isEmpty()) {
            editor.setMembers(members);
            editor.write(editor.getLastModified(), session.asFedoraLog());
            remote.flush();
        }
        return new SmartGwtResponse<Item>(added);
    }

    /**
     * Deletes object members from digital object.
     * @param parentPid digital object ID
     * @param toRemovePids member IDs to remove
     * @return list of removed IDs
     */
    @DELETE
    @Path(DigitalObjectResourceApi.MEMBERS_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<Item> deleteMembers(
            @QueryParam(DigitalObjectResourceApi.MEMBERS_ITEM_PARENT) String parentPid,
            @QueryParam(DigitalObjectResourceApi.MEMBERS_ITEM_PID) List<String> toRemovePids
            ) throws IOException, DigitalObjectException {

        if (parentPid == null) {
            throw RestException.plainText(Status.BAD_REQUEST, "Missing parent parameter!");
        }
        if (toRemovePids == null || toRemovePids.isEmpty()) {
            throw RestException.plainText(Status.BAD_REQUEST, "Missing pid parameter!");
        }
        if (toRemovePids.contains(parentPid)) {
            throw RestException.plainText(Status.BAD_REQUEST, "parent and pid are same!");
        }

        HashSet<String> toRemovePidSet = new HashSet<String>(toRemovePids);
        if (toRemovePidSet.isEmpty()) {
            return new SmartGwtResponse<Item>(Collections.<Item>emptyList());
        }

        RemoteStorage storage = RemoteStorage.getInstance(appConfig);
        RemoteObject remote = storage.find(parentPid);
        RelationEditor editor = new RelationEditor(remote);
        List<String> members = editor.getMembers();
        // check that PIDs being removed are members of parent object
        HashSet<String> toRemovePidSetCopy = new HashSet<String>(toRemovePidSet);
        toRemovePidSetCopy.removeAll(members);
        if (!toRemovePidSetCopy.isEmpty()) {
            String msg = String.format("Parent: %s does not contain members: %s",
                    parentPid, toRemovePidSetCopy.toString());
            throw RestException.plainText(Status.BAD_REQUEST, msg);
        }
        // remove
        if (members.removeAll(toRemovePidSet)) {
            editor.setMembers(members);
            editor.write(editor.getLastModified(), session.asFedoraLog());
            remote.flush();
        }

        ArrayList<Item> removed = new ArrayList<Item>(toRemovePidSet.size());
        for (String removePid : toRemovePidSet) {
            Item item = new Item(removePid);
            item.setParentPid(parentPid);
            removed.add(item);
        }

        return new SmartGwtResponse<Item>(removed);
    }
    
    @GET
    @Path(DigitalObjectResourceApi.DC_PATH)
    @Produces(MediaType.APPLICATION_XML)
    public DublinCoreRecord getDublinCore(
            @QueryParam(DigitalObjectResourceApi.DUBLINCORERECORD_PID) String pid,
            @QueryParam(DigitalObjectResourceApi.DUBLINCORERECORD_BATCHID) Integer batchId
            ) throws IOException, DigitalObjectException {

        FedoraObject fobject = findFedoraObject(pid, batchId);
        DcStreamEditor dcEditor = new DcStreamEditor(fobject);
        try {
            DublinCoreRecord dc = dcEditor.read();
            dc.setBatchId(batchId);
            return dc;
        } catch (DigitalObjectNotFoundException ex) {
            throw RestException.plainNotFound(DigitalObjectResourceApi.DIGITALOBJECT_PID, pid);
        }
    }

    @GET
    @Path(DigitalObjectResourceApi.DC_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    public DublinCoreRecord getDublinCoreJson(
            @QueryParam(DigitalObjectResourceApi.DUBLINCORERECORD_PID) String pid,
            @QueryParam(DigitalObjectResourceApi.DUBLINCORERECORD_BATCHID) Integer batchId
            ) throws IOException, DigitalObjectException {

        FedoraObject fobject = findFedoraObject(pid, batchId);
        DcStreamEditor dcEditor = new DcStreamEditor(fobject);
        try {
            DublinCoreRecord dc = dcEditor.read();
            dc.setBatchId(batchId);
            return dc;
        } catch (DigitalObjectNotFoundException ex) {
            throw RestException.plainNotFound(DigitalObjectResourceApi.DIGITALOBJECT_PID, pid);
        }
    }

    @PUT
    @Path(DigitalObjectResourceApi.DC_PATH)
    @Consumes({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Produces(MediaType.APPLICATION_XML)
    public DublinCoreRecord updateDublinCore(DublinCoreRecord update) throws IOException, DigitalObjectException {
        if (update == null || update.getDc() == null) {
            throw new IllegalArgumentException();
        }
        FedoraObject fobject = findFedoraObject(update.getPid(), update.getBatchId(), false);
        DcStreamEditor dcEditor = new DcStreamEditor(fobject);
        dcEditor.write(update, session.asFedoraLog());

        fobject.flush();
        DublinCoreRecord result = dcEditor.read();
        result.setBatchId(update.getBatchId());
        return result;
    }

    /**
     * Gets subset of MODS properties in JSON.
     *
     * @param pid PID of requested digital object
     * @param editorId view defining subset of MODS properties
     */
    @GET
    @Path(DigitalObjectResourceApi.MODS_PATH + '/' + DigitalObjectResourceApi.MODS_CUSTOM_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    public DescriptionMetadata<Object> getDescriptionMetadata(
            @QueryParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @QueryParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId,
            @QueryParam(DigitalObjectResourceApi.MODS_CUSTOM_EDITORID) String editorId
            ) throws IOException, DigitalObjectException {
        
        if (pid == null || pid.isEmpty()) {
            throw RestException.plainNotFound(DigitalObjectResourceApi.DIGITALOBJECT_PID, pid);
        }

        DigitalObjectHandler doHandler = findHandler(pid, batchId);
        DescriptionMetadata<Object> metadata = doHandler.metadata().getMetadataAsJsonObject(editorId);
        metadata.setBatchId(batchId);
        return metadata;
    }

    @PUT
    @Path(DigitalObjectResourceApi.MODS_PATH + '/' + DigitalObjectResourceApi.MODS_CUSTOM_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public DescriptionMetadata<?> updateDescriptionMetadata(
            @FormParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @FormParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId,
            @FormParam(DigitalObjectResourceApi.MODS_CUSTOM_EDITORID) String editorId,
            @FormParam(DigitalObjectResourceApi.TIMESTAMP_PARAM) Long timestamp,
            @FormParam(DigitalObjectResourceApi.MODS_CUSTOM_CUSTOMJSONDATA) String customJsonData
            ) throws IOException, DigitalObjectException {

        LOG.fine(String.format("pid: %s, editor: %s, timestamp: %s, json: %s", pid, editorId, timestamp, customJsonData));
        if (pid == null || pid.isEmpty()) {
            throw RestException.plainNotFound(DigitalObjectResourceApi.DIGITALOBJECT_PID, pid);
        }
        if (timestamp == null) {
            throw RestException.plainNotFound(DigitalObjectResourceApi.TIMESTAMP_PARAM, pid);
        }
        DigitalObjectHandler doHandler = findHandler(pid, batchId, false);
        MetadataHandler<?> mHandler = doHandler.metadata();
        DescriptionMetadata<String> metadataAsJson = new DescriptionMetadata<String>();
        metadataAsJson.setPid(pid);
        metadataAsJson.setBatchId(batchId);
        metadataAsJson.setEditor(editorId);
        metadataAsJson.setData(customJsonData);
        metadataAsJson.setTimestamp(timestamp);
        mHandler.setMetadataAsJson(metadataAsJson, session.asFedoraLog());
        doHandler.commit();
        return mHandler.getMetadataAsJsonObject(editorId);
    }

    @GET
    @Path(DigitalObjectResourceApi.METAMODEL_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<AnnotatedMetaModel> listModels() {
        Locale locale = session.getLocale(httpHeaders);

        Collection<MetaModel> models = metamodels.find();
        ArrayList<AnnotatedMetaModel> result = new ArrayList<AnnotatedMetaModel>(models.size());
        for (MetaModel model : models) {
            result.add(new AnnotatedMetaModel(model, locale));
        }
        return new SmartGwtResponse<AnnotatedMetaModel>(result);
    }

    @GET
    @Path(DigitalObjectResourceApi.PREVIEW_PATH)
    @Produces("*/*")
    public Response getPreview(
            @QueryParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @QueryParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId
            ) throws IOException, DigitalObjectException {

        return getDissemination(pid, batchId, BinaryEditor.PREVIEW_ID);
    }

    /**
     * Default alias for FULL dissemination.
     *
     * @param pid digital object PID (required)
     * @param batchId import batch ID (optional)
     * @return raw version of the archived object
     */
    @GET
    @Path(DigitalObjectResourceApi.FULL_PATH)
    @Produces("*/*")
    public Response getFull(
            @QueryParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @QueryParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId
            ) throws IOException, DigitalObjectException {

        return getDissemination(pid, batchId, BinaryEditor.FULL_ID);
    }

    /**
     * Default alias for raw dissemination.
     *
     * @param pid digital object PID (required)
     * @param batchId import batch ID (optional)
     * @return raw version of the archived object
     */
    @GET
    @Path(DigitalObjectResourceApi.RAW_PATH)
    @Produces("*/*")
    public Response getRaw(
            @QueryParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @QueryParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId
            ) throws IOException, DigitalObjectException {

        return getDissemination(pid, batchId, BinaryEditor.RAW_ID);
    }

    /**
     * Gets digital object dissemination.
     *
     * @param pid PID (required)
     * @param batchId import batch ID (optional)
     * @param dsId data stream ID. If missing the whole digital object is returned as XML.
     * @return digital object dissemination
     * @throws IOException
     */
    @GET
    @Path(DigitalObjectResourceApi.DISSEMINATION_PATH)
    @Produces("*/*")
    public Response getDissemination(
            @QueryParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @QueryParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId,
            @QueryParam(DigitalObjectResourceApi.DISSEMINATION_DATASTREAM) String dsId
            ) throws DigitalObjectException {

        DigitalObjectHandler doHandler = findHandler(pid, batchId);
        DisseminationHandler dissemination = doHandler.dissemination(dsId);
        return dissemination.getDissemination(httpRequest);
    }

    /**
     * Updates dissemination of digital object with binary data sent as
     * {@link MediaType#MULTIPART_FORM_DATA}. It allows to upload file from
     * client.
     * <p>For now only RAW stream is supported.
     *
     * @param pid PID (required)
     * @param batchId import batch ID (optional)
     * @param dsId data stream ID.
     * @param file contents
     * @param fileInfo contents description metadata (injected by the server)
     * @param mimeType MIME type of the sent contents (optional)
     * @return HTTP status
     * @throws IOException failure
     * @throws DigitalObjectException failure
     */
    @POST
    @Path(DigitalObjectResourceApi.DISSEMINATION_PATH)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response updateDissemination(
            @FormDataParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @FormDataParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId,
            @FormDataParam(DigitalObjectResourceApi.DISSEMINATION_DATASTREAM) String dsId,
            @FormDataParam(DigitalObjectResourceApi.DISSEMINATION_FILE) File file,
            @FormDataParam(DigitalObjectResourceApi.DISSEMINATION_FILE) FormDataContentDisposition fileInfo,
            @FormDataParam(DigitalObjectResourceApi.DISSEMINATION_MIME) String mimeType
            ) throws IOException, DigitalObjectException {

        if (pid == null) {
            throw RestException.plainText(Status.BAD_REQUEST, "Missing pid!");
        }
        if (file == null) {
            throw RestException.plainText(Status.BAD_REQUEST, "Missing file!");
        }
        // XXX add config property or user permission
        if (file.length() > 1*1024 * 1024 * 1024) { // 1GB
            throw RestException.plainText(Status.BAD_REQUEST, "File contents too large!");
        }
        if (dsId != null && !dsId.equals(BinaryEditor.RAW_ID)) {
            throw RestException.plainText(Status.BAD_REQUEST, "Missing or unsupported datastream ID: " + dsId);
        }
        String filename = getFilename(fileInfo.getFileName());
        try {
            if (mimeType == null) {
                // XXX workaround; replace with Dron or Pronom to get mimetype
                mimeType = fileInfo.getType();
            }
            MediaType mime;
            try {
                mime = MediaType.valueOf(mimeType);
            } catch (IllegalArgumentException ex) {
                throw RestException.plainText(Status.BAD_REQUEST, "Invalid MIME type!");
            }
            DigitalObjectHandler doHandler = findHandler(pid, batchId);
            DisseminationHandler dissemination = doHandler.dissemination(BinaryEditor.RAW_ID);
            DisseminationInput input = new DisseminationInput(file, filename, mime);
            dissemination.setDissemination(input, session.asFedoraLog());
            doHandler.commit();
        } finally {
            file.delete();
        }
        return Response.ok().build();
    }

    @GET
    @Path(DigitalObjectResourceApi.THUMB_PATH)
    @Produces("image/*")
    public Response getThumbnail(
            @QueryParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @QueryParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId
            ) throws IOException, DigitalObjectException {

        return getDissemination(pid, batchId, BinaryEditor.THUMB_ID);
    }

    @GET
    @Path(DigitalObjectResourceApi.MODS_PATH + '/' + DigitalObjectResourceApi.MODS_PLAIN_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    public StringRecord getDescriptionMetadataTxt(
            @QueryParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @QueryParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId
            ) throws DigitalObjectException {

        DigitalObjectHandler handler = findHandler(pid, batchId, false);
        MetadataHandler<?> metadataHandler = handler.metadata();
        DescriptionMetadata<String> metadataAsXml = metadataHandler.getMetadataAsXml();
        StringRecord result = new StringRecord(
                metadataAsXml.getData(), metadataAsXml.getTimestamp(), metadataAsXml.getPid());
        result.setBatchId(batchId);
        return result;
    }

    @GET
    @Path(DigitalObjectResourceApi.OCR_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    public StringRecord getOcr(
            @QueryParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @QueryParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId
            ) throws IOException, DigitalObjectException {

        FedoraObject fobject = findFedoraObject(pid, batchId);
        StringEditor ocrEditor = StringEditor.ocr(fobject);
        try {
            StringRecord ocr = ocrEditor.readRecord();
            ocr.setBatchId(batchId);
            return ocr;
        } catch (DigitalObjectNotFoundException ex) {
            throw RestException.plainNotFound(DigitalObjectResourceApi.DIGITALOBJECT_PID, pid);
        }
    }

    @PUT
    @Path(DigitalObjectResourceApi.OCR_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    public StringRecord updateOcr(
            @FormParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @FormParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId,
            @FormParam(DigitalObjectResourceApi.TIMESTAMP_PARAM) Long timestamp,
            @FormParam(DigitalObjectResourceApi.STRINGRECORD_CONTENT) String content
            ) throws IOException, DigitalObjectException {

        if (timestamp == null) {
            throw RestException.plainText(Status.BAD_REQUEST, "Missing timestamp!");
        }
        FedoraObject fobject = findFedoraObject(pid, batchId, false);
        StringEditor ocrEditor = StringEditor.ocr(fobject);
        try {
            ocrEditor.write(content, timestamp, session.asFedoraLog());
            fobject.flush();
            StringRecord result = ocrEditor.readRecord();
            result.setBatchId(batchId);
            return result;
        } catch (DigitalObjectNotFoundException ex) {
            throw RestException.plainNotFound(DigitalObjectResourceApi.DIGITALOBJECT_PID, pid);
        }
    }

    @GET
    @Path(DigitalObjectResourceApi.PRIVATENOTE_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    public StringRecord getPrivateNote(
            @QueryParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @QueryParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId
            ) throws IOException, DigitalObjectException {

        FedoraObject fobject = findFedoraObject(pid, batchId);
        StringEditor editor = StringEditor.privateNote(fobject);
        try {
            StringRecord content = editor.readRecord();
            content.setBatchId(batchId);
            return content;
        } catch (DigitalObjectNotFoundException ex) {
            throw RestException.plainNotFound(DigitalObjectResourceApi.DIGITALOBJECT_PID, pid);
        }
    }

    @PUT
    @Path(DigitalObjectResourceApi.PRIVATENOTE_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    public StringRecord updatePrivateNote(
            @FormParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) String pid,
            @FormParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId,
            @FormParam(DigitalObjectResourceApi.TIMESTAMP_PARAM) Long timestamp,
            @FormParam(DigitalObjectResourceApi.STRINGRECORD_CONTENT) String content
            ) throws IOException, DigitalObjectException {

        if (timestamp == null) {
            throw RestException.plainText(Status.BAD_REQUEST, "Missing timestamp!");
        }
        FedoraObject fobject = findFedoraObject(pid, batchId, false);
        StringEditor editor = StringEditor.privateNote(fobject);
        editor.write(content, timestamp, session.asFedoraLog());
        fobject.flush();
        StringRecord result = editor.readRecord();
        result.setBatchId(batchId);
        return result;
    }

    /**
     * Gets digital object administration and technical data.
     *
     * @param pid PID (required)
     * @param batchId import batch ID (optional)
     * @return digital object dissemination
     * @throws IOException
     */
    @GET
    @Path(DigitalObjectResourceApi.ATM_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    public SmartGwtResponse<AtmItem> getAtm(
            @QueryParam(DigitalObjectResourceApi.ATM_ITEM_PID) String pid,
            @QueryParam(DigitalObjectResourceApi.ATM_ITEM_BATCHID) Integer batchId
            ) throws IOException, DigitalObjectException, FedoraClientException {

        if (pid == null) {
            return new SmartGwtResponse<AtmItem>();
        }
        FedoraObject fobject = findFedoraObject(pid, batchId);
        Locale locale = session.getLocale(httpHeaders);
        RemoteStorage storage = RemoteStorage.getInstance(appConfig);
        AtmEditor editor = new AtmEditor(fobject, storage.getSearch(locale));
        AtmItem atm = editor.read();
        atm.setBatchId(batchId);
        return new SmartGwtResponse<AtmItem>(atm);
    }

    @PUT
    @Path(DigitalObjectResourceApi.ATM_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    public SmartGwtResponse<AtmItem> updateAtm(
            @FormParam(DigitalObjectResourceApi.DIGITALOBJECT_PID) Set<String> pids,
            @FormParam(DigitalObjectResourceApi.BATCHID_PARAM) Integer batchId,
            @FormParam(DigitalObjectResourceApi.MEMBERS_ITEM_OWNER) String owner,
            @FormParam(DigitalObjectResourceApi.ATM_ITEM_DEVICE) String deviceId
            ) throws IOException, DigitalObjectException {

        ArrayList<AtmItem> result = new ArrayList<AtmItem>(pids.size());
        Locale locale = session.getLocale(httpHeaders);
        RemoteStorage storage = RemoteStorage.getInstance(appConfig);
        SearchView search = storage.getSearch(locale);
        for (String pid : pids) {
            FedoraObject fobject = findFedoraObject(pid, batchId);
            AtmEditor editor = new AtmEditor(fobject, search);
            editor.write(deviceId, session.asFedoraLog());
            fobject.flush();
            AtmItem atm = editor.read();
            atm.setBatchId(batchId);
            result.add(atm);
        }
        return new SmartGwtResponse<AtmItem>(result);
    }

    private DigitalObjectHandler findHandler(String pid, Integer batchId) throws DigitalObjectNotFoundException {
        return findHandler(pid, batchId, true);
    }

    private DigitalObjectHandler findHandler(String pid, Integer batchId, boolean readonly)
            throws DigitalObjectNotFoundException {

        DigitalObjectManager dom = DigitalObjectManager.getDefault();
        FedoraObject fobject = dom.find(pid, batchId);
        if (!readonly && fobject instanceof LocalObject) {
            Batch batch = importManager.get(batchId);
            ImportResource.checkBatchState(batch);
        }
        return dom.createHandler(fobject);
    }

    @Deprecated
    private FedoraObject findFedoraObject(String pid, Integer batchId) throws IOException {
        return findFedoraObject(pid, batchId, true);
    }

    @Deprecated
    private FedoraObject findFedoraObject(String pid, Integer batchId, boolean readonly) throws IOException {
        FedoraObject fobject;
        if (batchId != null) {
            Batch batch = importManager.get(batchId);
            if (batch == null) {
                throw RestException.plainNotFound(DigitalObjectResourceApi.MEMBERS_ITEM_BATCHID, String.valueOf(batchId));
            }
            if (!readonly) {
                ImportResource.checkBatchState(batch);
            }
            if (pid == null || ImportBatchManager.ROOT_ITEM_PID.equals(pid)) {
                fobject = importManager.getRootObject(batch);
            } else {
                BatchItemObject item = importManager.findBatchObject(batchId, pid);
                if (item == null) {
                    throw RestException.plainNotFound(DigitalObjectResourceApi.DIGITALOBJECT_PID, pid);
                }
                fobject = new LocalStorage().load(pid, item.getFile());
            }
        } else {
            if (pid == null) {
                throw new NullPointerException("pid");
            }
            fobject = RemoteStorage.getInstance(appConfig).find(pid);
        }
        return fobject;
    }

    /**
     * Removes extension from the file name.
     * @param filename file name
     * @return name without extension
     */
    private static String getBareFilename(String filename) {
        int index = filename.lastIndexOf('.');
        return index <= 0 ? filename : filename.substring(0, index);
    }

    /**
     * Remove path from file name sent by client. It searches for platform path
     * delimiters.
     * @param filepath file path
     * @return the file name
     */
    private static String getFilename(String filepath) {
        int slashIndex = filepath.lastIndexOf('/');
        int backslashIndex = filepath.lastIndexOf('\\');
        int index = Math.max(slashIndex, backslashIndex);
        if (index > 0) {
            filepath = filepath.substring(index);
        }
        return filepath;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DigitalObject {
        @XmlElement(name = DigitalObjectResourceApi.DIGITALOBJECT_PID)
        private String pid;
        @XmlElement(name = DigitalObjectResourceApi.DIGITALOBJECT_MODEL)
        private String model;

        public DigitalObject(String pid, String model) {
            this.pid = pid;
            this.model = model;
        }

        public DigitalObject() {
        }
    }
}
