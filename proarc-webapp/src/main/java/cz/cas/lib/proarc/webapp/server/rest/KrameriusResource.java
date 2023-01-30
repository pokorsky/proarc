/*
 * Copyright (C) 2023 Lukas Sykora
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.cas.lib.proarc.webapp.server.rest;

import cz.cas.lib.proarc.common.config.AppConfiguration;
import cz.cas.lib.proarc.common.config.AppConfigurationException;
import cz.cas.lib.proarc.common.config.AppConfigurationFactory;
import cz.cas.lib.proarc.common.dao.Batch;
import cz.cas.lib.proarc.common.dao.BatchUtils;
import cz.cas.lib.proarc.common.export.mets.MetsUtils;
import cz.cas.lib.proarc.common.fedora.DigitalObjectConcurrentModificationException;
import cz.cas.lib.proarc.common.fedora.DigitalObjectException;
import cz.cas.lib.proarc.common.fedora.DigitalObjectNotFoundException;
import cz.cas.lib.proarc.common.fedora.DigitalObjectValidationException;
import cz.cas.lib.proarc.common.fedora.FedoraObject;
import cz.cas.lib.proarc.common.fedora.Storage;
import cz.cas.lib.proarc.common.fedora.StringEditor;
import cz.cas.lib.proarc.common.fedora.akubra.AkubraConfiguration;
import cz.cas.lib.proarc.common.fedora.akubra.AkubraConfigurationFactory;
import cz.cas.lib.proarc.common.fedora.relation.RelationEditor;
import cz.cas.lib.proarc.common.imports.ImportBatchManager;
import cz.cas.lib.proarc.common.kramerius.K7Authenticator;
import cz.cas.lib.proarc.common.kramerius.K7Downloader;
import cz.cas.lib.proarc.common.kramerius.KDataHandler;
import cz.cas.lib.proarc.common.kramerius.KImporter;
import cz.cas.lib.proarc.common.kramerius.KUtils;
import cz.cas.lib.proarc.common.kramerius.KrameriusOptions;
import cz.cas.lib.proarc.common.object.DescriptionMetadata;
import cz.cas.lib.proarc.common.object.DigitalObjectHandler;
import cz.cas.lib.proarc.common.object.DigitalObjectManager;
import cz.cas.lib.proarc.common.object.MetadataHandler;
import cz.cas.lib.proarc.common.object.model.MetaModelRepository;
import cz.cas.lib.proarc.common.object.ndk.NdkMetadataHandler;
import cz.cas.lib.proarc.common.object.ndk.NdkPlugin;
import cz.cas.lib.proarc.common.object.oldprint.OldPrintPlugin;
import cz.cas.lib.proarc.common.user.UserProfile;
import cz.cas.lib.proarc.webapp.client.ds.MetaModelDataSource;
import cz.cas.lib.proarc.webapp.shared.rest.DigitalObjectResourceApi;
import cz.cas.lib.proarc.webapp.shared.rest.KrameriusResourceApi;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.fcrepo.utilities.FileUtils;

import static cz.cas.lib.proarc.common.kramerius.KUtils.KRAMERIUS_PROCESS_FAILED;
import static cz.cas.lib.proarc.common.kramerius.KUtils.KRAMERIUS_PROCESS_FINISHED;
import static cz.cas.lib.proarc.common.kramerius.KUtils.KRAMERIUS_PROCESS_WARNING;
import static cz.cas.lib.proarc.common.kramerius.KUtils.findHandler;
import static cz.cas.lib.proarc.common.kramerius.KUtils.getExpectedDestinationPath;
import static cz.cas.lib.proarc.common.kramerius.KUtils.getExpectedSourcePath;
import static cz.cas.lib.proarc.common.kramerius.KUtils.transformKrameriusModel;
import static cz.cas.lib.proarc.common.kramerius.KrameriusOptions.KRAMERIUS_INSTANCE_LOCAL;
import static cz.cas.lib.proarc.common.kramerius.KrameriusOptions.findKrameriusInstance;
import static cz.cas.lib.proarc.webapp.server.rest.DigitalObjectResource.toValidationError;
import static java.net.HttpURLConnection.HTTP_OK;

@Path(KrameriusResourceApi.PATH)
public class KrameriusResource {

    private static final Logger LOG = Logger.getLogger(KrameriusResource.class.getName());

    private final AppConfiguration appConfig;
    private final AkubraConfiguration akubraConfiguration;
    private final MetaModelRepository metamodels = MetaModelRepository.getInstance();
    private final ImportBatchManager batchManager;
    private final Request httpRequest;
    private final HttpHeaders httpHeaders;
    private final UserProfile user;
    private final SessionContext session;

    public KrameriusResource(
            @Context Request request,
            @Context SecurityContext securityCtx,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo uriInfo,
            @Context HttpServletRequest httpRequest
    ) throws AppConfigurationException {

        this.httpRequest = request;
        this.httpHeaders = httpHeaders;
        this.appConfig = AppConfigurationFactory.getInstance().defaultInstance();
        if (Storage.AKUBRA.equals(appConfig.getTypeOfStorage())) {
            this.akubraConfiguration = AkubraConfigurationFactory.getInstance().defaultInstance(appConfig.getConfigHome());
        } else {
            this.akubraConfiguration = null;
        }
        this.batchManager = ImportBatchManager.getInstance(appConfig);
        session = SessionContext.from(httpRequest);
        user = session.getUser();
        LOG.fine(user.toString());
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<String> connectionTest() {
        LOG.fine(String.format("Succesfull attempt to connected to ProArc"));
        return new SmartGwtResponse<String>("Connected to ProArc");
    }

    @POST
    @Path(KrameriusResourceApi.EDIT)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<KUtils.RedirectedResult> editPid(
            @FormParam(KrameriusResourceApi.KRAMERIUS_OBJECT_PID) String krameriusPid,
            @FormParam(KrameriusResourceApi.KRAMERIUS_INSTANCE) String krameriusInstanceId
    ) {

        LOG.fine(String.format("pid: %s, krameriusInstanceId: %s", krameriusPid, krameriusInstanceId));

        if (krameriusPid == null || krameriusPid.isEmpty()) {
            return SmartGwtResponse.asError("Missing value for field: \"" + KrameriusResourceApi.KRAMERIUS_OBJECT_PID + "\".", session);
        }
        if (krameriusInstanceId == null || krameriusInstanceId.isEmpty() || KRAMERIUS_INSTANCE_LOCAL.equals(krameriusInstanceId)) {
            return SmartGwtResponse.asError("Missing value for field: \"" + KrameriusResourceApi.KRAMERIUS_INSTANCE + "\".", session);
        }

        KrameriusOptions.KrameriusInstance instance = findKrameriusInstance(appConfig.getKrameriusOptions().getKrameriusInstances(), krameriusInstanceId);
        if (instance == null) {
            return SmartGwtResponse.asError( "Not known value \"" + krameriusInstanceId + "\" for field: \"" + KrameriusResourceApi.KRAMERIUS_INSTANCE + "\".", session);
        } else {
            K7Authenticator authenticator = new K7Authenticator(instance);
            KUtils.RedirectedResult result = new KUtils.RedirectedResult(krameriusPid);
            try {
                String token = authenticator.authenticate();
                if (token != null || !token.isEmpty()) {
                    K7Downloader downloader = new K7Downloader(appConfig, instance);
                    String foxml = downloader.downloadFromK7(krameriusPid, token);
                    downloader.saveFoxml(foxml, krameriusPid);

                    String editK7Foxml = appConfig.getEditK7Foxml();
                    if (editK7Foxml == null || editK7Foxml.isEmpty()) {
                        throw new IOException("URL for edit K7 foxml was not find.");
                    } else {
                        editK7Foxml += "instance=" + krameriusInstanceId + "&pid=" + krameriusPid;
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(new URI(editK7Foxml));
                            result.setMessage("Redirected to ProArc edit view");
                            result.setUrl(editK7Foxml);
                            result.setStatus("Successful");
                        } else {
                            result.setMessage("Redirected to ProArc edit view");
                            result.setUrl(editK7Foxml);
                            result.setStatus("Successful");
                        }
                    }
                }
            } catch (Exception ex) {
                return SmartGwtResponse.asError(ex.getMessage());
            }

            return new SmartGwtResponse<KUtils.RedirectedResult>(result);
        }
    }

    @GET
    @Path(KrameriusResourceApi.VIEW_MODS)
    @Produces({MediaType.APPLICATION_JSON})
    public StringEditor.StringRecord viewMods(
            @QueryParam(KrameriusResourceApi.KRAMERIUS_OBJECT_PID) String pid,
            @QueryParam(KrameriusResourceApi.KRAMERIUS_INSTANCE) String krameriusInstanceId
    ) throws DigitalObjectException {

        LOG.fine(String.format("pid: %s, krameriusInstanceId: %s", pid, krameriusInstanceId));

        if (pid == null || pid.isEmpty()) {
            throw RestException.plainText(Response.Status.BAD_REQUEST, "Missing value for field: " + KrameriusResourceApi.KRAMERIUS_OBJECT_PID + "\".");
        }
        if (krameriusInstanceId == null || krameriusInstanceId.isEmpty() || KRAMERIUS_INSTANCE_LOCAL.equals(krameriusInstanceId)) {
            throw RestException.plainText(Response.Status.BAD_REQUEST, "Missing value for field: " + KrameriusResourceApi.KRAMERIUS_INSTANCE);
        }

        try {
            DigitalObjectHandler handler = findHandler(pid, krameriusInstanceId);
            MetadataHandler<?> metadataHandler = handler.metadata();
            DescriptionMetadata<String> metadataAsXml = metadataHandler.getMetadataAsXml();
            StringEditor.StringRecord result = new StringEditor.StringRecord(metadataAsXml.getData(), metadataAsXml.getTimestamp(), metadataAsXml.getPid());
            result.setKrameriusInstanceId(krameriusInstanceId);
            result.setModel(transformKrameriusModel(appConfig, handler.getModel().getPid()));
            return result;
        } catch (Exception ex) {
            LOG.severe("Error in getting object " + pid);
            ex.printStackTrace();
            StringEditor.StringRecord result = new StringEditor.StringRecord();
            result.setStatus(StringEditor.StringRecord.STATUS_FAILURE);
            result.setData(ex.getMessage());
            return result;
        }
    }

    @GET
    @Path(KrameriusResourceApi.VIEW_IMAGE)
    @Produces("*/*")
    public Object viewImage(
            @QueryParam(KrameriusResourceApi.KRAMERIUS_OBJECT_PID) String pid,
            @QueryParam(KrameriusResourceApi.KRAMERIUS_INSTANCE) String krameriusInstanceId
    ) {
        if (pid == null || pid.isEmpty()) {
            return SmartGwtResponse.asError("Missing value for field: \"" + KrameriusResourceApi.KRAMERIUS_OBJECT_PID + "\".", session);
        }
        if (krameriusInstanceId == null || krameriusInstanceId.isEmpty() || KRAMERIUS_INSTANCE_LOCAL.equals(krameriusInstanceId)) {
            return SmartGwtResponse.asError("Missing value for field: \"" + KrameriusResourceApi.KRAMERIUS_INSTANCE + "\".", session);
        }

        KrameriusOptions.KrameriusInstance instance = findKrameriusInstance(appConfig.getKrameriusOptions().getKrameriusInstances(), krameriusInstanceId);
        if (instance == null) {
            return SmartGwtResponse.asError("Not known value \"" + krameriusInstanceId + "\" for field: \"" + KrameriusResourceApi.KRAMERIUS_INSTANCE + "\".", session);
        }

        KUtils.RedirectedResult result = new KUtils.RedirectedResult(pid);
        try {
            DigitalObjectManager dom = DigitalObjectManager.getDefault();
            FedoraObject fedoraObject = dom.find2(pid, null, krameriusInstanceId);
            RelationEditor relationEditor = new RelationEditor(fedoraObject);
            if (relationEditor.getModel() == null || relationEditor.getModel().isEmpty()) {
                result.setStatus("Failed");
                result.setMessage("Impossible to get model of pid " + pid);
                return new SmartGwtResponse<>(result);
            }
            if (NdkPlugin.MODEL_PAGE.equals(relationEditor.getModel()) || NdkPlugin.MODEL_NDK_PAGE.equals(relationEditor.getModel()) || OldPrintPlugin.MODEL_PAGE.equals(relationEditor.getModel())) {
                K7Authenticator authenticator = new K7Authenticator(instance);
                String token = authenticator.authenticate();
                String url = instance.getUrl() + instance.getUrlImage() + pid + "/full/max/0/default.jpg";
                LOG.info("Redirected to " + url);
//                result.setStatus("Successful");
//                result.setMessage("Redirected to ProArc Image View");
//                result.setUrl(url);
                HttpClient httpClient = HttpClients.createDefault();
                HttpGet httpGet = new HttpGet(url);

                httpGet.setHeader(new BasicHeader("Keep-Alive", "timeout=600, max=1000"));
                if (token != null && !token.isEmpty()) {
                    httpGet.setHeader(new BasicHeader("Authorization", "Bearer " + token));
                }
                httpGet.setHeader(new BasicHeader("Connection", "Keep-Alive, Upgrade"));

                HttpResponse response = httpClient.execute(httpGet);
                if (HTTP_OK == response.getStatusLine().getStatusCode()) {
                    Response proarcResponse = Response.ok(response.getEntity().getContent(), response.getFirstHeader("Content-Type").getValue()).header("Content-Disposition", "default.jpg").build();
                    return proarcResponse;
                } else {
                    result.setStatus("Failed");
                    result.setMessage("Impossible to download JPG");
                    return new SmartGwtResponse<>(result);
                }
            } else {
                result.setStatus("Failed");
                result.setMessage("This object does not contain any image.");
                result.setUrl(null);
                return new SmartGwtResponse<>(result);
            }
        } catch (Exception ex) {
            LOG.severe("Error in getting object " + pid);
            ex.printStackTrace();
            result.setStatus("Failed");
            result.setUrl(null);
            result.setMessage(ex.getMessage());
            return new SmartGwtResponse<>(result);
        }
    }

    @POST
    @Path(KrameriusResourceApi.UPDATE_MODS)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<DescriptionMetadata<Object>> updateMods(
            @FormParam(KrameriusResourceApi.KRAMERIUS_OBJECT_PID) String pid,
            @FormParam(KrameriusResourceApi.KRAMERIUS_INSTANCE) String krameriusInstanceId,
            @FormParam(DigitalObjectResourceApi.MODS_CUSTOM_EDITORID) String editorId,
            @FormParam(DigitalObjectResourceApi.TIMESTAMP_PARAM) Long timestamp,
            @FormParam(DigitalObjectResourceApi.MODS_CUSTOM_CUSTOMJSONDATA) String jsonData,
            @FormParam(DigitalObjectResourceApi.MODS_CUSTOM_CUSTOMXMLDATA) String xmlData,
            @FormParam(MetaModelDataSource.FIELD_MODELOBJECT) String model,
            @DefaultValue("false")
            @FormParam(DigitalObjectResourceApi.MODS_CUSTOM_IGNOREVALIDATION) boolean ignoreValidation
    ) throws DigitalObjectException {

        LOG.fine(String.format("pid: %s, krameriusInstanceId: %s, editor: %s, timestamp: %s, ignoreValidation: %s, json: %s, xml: %s",
                pid, krameriusInstanceId, editorId, timestamp, ignoreValidation, jsonData, xmlData));

        if (pid == null || pid.isEmpty()) {
            return SmartGwtResponse.asError("Missing value for field: \"" + KrameriusResourceApi.KRAMERIUS_OBJECT_PID + "\".", session);
        }
        if (krameriusInstanceId == null || krameriusInstanceId.isEmpty() || KRAMERIUS_INSTANCE_LOCAL.equals(krameriusInstanceId)) {
            return SmartGwtResponse.asError("Missing value for field: \"" + KrameriusResourceApi.KRAMERIUS_INSTANCE + "\".", session);
        }

        if (timestamp == null) {
            return SmartGwtResponse.asError(DigitalObjectResourceApi.TIMESTAMP_PARAM, pid);
        }
        final boolean isJsonData = xmlData == null;
        String data = isJsonData ? jsonData : xmlData;
        DigitalObjectHandler doHandler = null;
        MetadataHandler<?> mHandler = null;
        try {
            doHandler = findHandler(pid, krameriusInstanceId);
            mHandler = doHandler.metadata();
            DescriptionMetadata<String> dMetadata = new DescriptionMetadata<String>();
            dMetadata.setPid(pid);
            dMetadata.setKrameriusInstanceId(krameriusInstanceId);
            dMetadata.setEditor(editorId);
            dMetadata.setData(data);
            dMetadata.setTimestamp(timestamp);
            dMetadata.setIgnoreValidation(ignoreValidation);
            if (isJsonData) {
                mHandler.setMetadataAsJson(dMetadata, session.asFedoraLog(), NdkMetadataHandler.OPERATION_UPDATE);
            } else {
                mHandler.setMetadataAsXml(dMetadata, session.asFedoraLog(), NdkMetadataHandler.OPERATION_UPDATE);
            }
        } catch (DigitalObjectValidationException ex) {
            return toValidationError(ex, session.getLocale(httpHeaders));
        } catch (DigitalObjectNotFoundException ex) {
            return SmartGwtResponse.asError(ex);
        }
//        DigitalObjectStatusUtils.setState(doHandler.getFedoraObject(), STATUS_PROCESSING);
        doHandler.commit();
        return new SmartGwtResponse<DescriptionMetadata<Object>>(mHandler.getMetadataAsJsonObject(editorId));
    }

    @POST
    @Path(KrameriusResourceApi.IMPORT_2_PROARC)
    public SmartGwtResponse<KUtils.ImportResult> import2ProArc(
        @FormParam(KrameriusResourceApi.KRAMERIUS_OBJECT_PID) String pid,
        @FormParam(KrameriusResourceApi.KRAMERIUS_INSTANCE) String krameriusInstanceId
    ) {

        LOG.fine(String.format("pid: %s, krameriusInstanceId: %s", pid, krameriusInstanceId));

        if (pid == null || pid.isEmpty()) {
            return SmartGwtResponse.asError("Missing value for field: \"" + KrameriusResourceApi.KRAMERIUS_OBJECT_PID + "\".", session);
        }
        if (krameriusInstanceId == null || krameriusInstanceId.isEmpty() || KRAMERIUS_INSTANCE_LOCAL.equals(krameriusInstanceId)) {
            return SmartGwtResponse.asError( "Missing value for field: \"" + KrameriusResourceApi.KRAMERIUS_INSTANCE+ "\".", session);
        }

        KUtils.ImportResult importResult = new KUtils.ImportResult(pid, "ProArc");

        Batch batch = BatchUtils.addNewUploadBatch(this.batchManager, pid, user, Batch.UPLOAD_PROARC);

        try {
            KDataHandler dataHandler = new KDataHandler(appConfig);
            DescriptionMetadata<String> metadata = dataHandler.getDescriptionMetadata(pid, krameriusInstanceId);
            if (metadata == null) {
                BatchUtils.finishedUploadWithError(this.batchManager, batch, "ProArc", new IOException("No metadata content for this object with pid: " + pid));
                return SmartGwtResponse.asError("No metadata content for this object with pid: " + pid);
            }
            boolean status = dataHandler.setDescriptionMetadataToProArc(pid, metadata, krameriusInstanceId);
            if (status) {
                importResult.setStatus("Successful");
                importResult.setReason(null);
                BatchUtils.finishedUploadSuccessfully(this.batchManager, batch, "ProArc");
            } else {
                importResult.setStatus("Failed");
                importResult.setReason("Reason not known.");
                BatchUtils.finishedUploadWithError(this.batchManager, batch, "ProArc", new IOException(importResult.getReason()));
            }
        } catch (DigitalObjectException ex) {
            BatchUtils.finishedUploadWithError(this.batchManager, batch, "ProArc", ex);
            if (ex instanceof DigitalObjectNotFoundException) {
                LOG.severe("Import (" + pid + ") to ProArc failed, because Object with this pid not found in storage.");
                importResult.setReason("Object with " + pid + " not found in storage.");
            } else if (ex instanceof DigitalObjectConcurrentModificationException) {
                LOG.severe("Concurrent modification for object with " + pid + ".");
                importResult.setReason("Concurrent modification for object with " + pid + ".");
            } else {
                LOG.severe("Import (" + pid + ") to ProArc failed, because " + ex.getMessage());
                importResult.setReason(ex.getMessage());
            }
            importResult.setStatus("Failed");
        } finally {
            return new SmartGwtResponse<>(importResult);
        }
    }

    @POST
    @Path(KrameriusResourceApi.IMPORT_2_KRAMERIUS)
    public SmartGwtResponse<KUtils.ImportResult> import2Kramerius(
            @FormParam(KrameriusResourceApi.KRAMERIUS_OBJECT_PID) String pid,
            @FormParam(KrameriusResourceApi.KRAMERIUS_INSTANCE) String krameriusInstanceId,
            @FormParam(KrameriusResourceApi.KRAMERIUS_IMPORT_INSTANCE) String krameriusImportInstanceId
    ) {

        LOG.fine(String.format("pid: %s, krameriusInstanceId: %s", "krameriusImportInstanceId: %s", pid, krameriusInstanceId, krameriusImportInstanceId));

        if (pid == null || pid.isEmpty()) {
            return SmartGwtResponse.asError("Missing value for field: \"" + KrameriusResourceApi.KRAMERIUS_OBJECT_PID + "\".", session);
        }
        if (krameriusInstanceId == null || krameriusInstanceId.isEmpty() || KRAMERIUS_INSTANCE_LOCAL.equals(krameriusInstanceId)) {
            return SmartGwtResponse.asError( "Missing value for field: \"" + KrameriusResourceApi.KRAMERIUS_INSTANCE + "\".", session);
        }
        if (krameriusImportInstanceId == null || krameriusImportInstanceId.isEmpty() || KRAMERIUS_INSTANCE_LOCAL.equals(krameriusImportInstanceId)) {
            return SmartGwtResponse.asError("Missing value for field: \"" + KrameriusResourceApi.KRAMERIUS_INSTANCE + "\".", session);
        }

        Batch batch = BatchUtils.addNewUploadBatch(this.batchManager, pid, user, Batch.UPLOAD_KRAMERIUS);

        KUtils.ImportResult importResult = new KUtils.ImportResult(pid, krameriusImportInstanceId);

        KrameriusOptions.KrameriusInstance instance = findKrameriusInstance(appConfig.getKrameriusOptions().getKrameriusInstances(), krameriusImportInstanceId);
        if (instance == null) {
            return SmartGwtResponse.asError("Not known value \"" + krameriusInstanceId + "\" for field: \"" + KrameriusResourceApi.KRAMERIUS_INSTANCE + "\".", session);
        }

        KDataHandler dataHandler = new KDataHandler(appConfig);
        try {
            File sourceFile = dataHandler.getSourceFile(pid, krameriusInstanceId);
            if (sourceFile == null || !sourceFile.exists()) {
                throw new IOException("Source file for " + pid + " does not exists. Expected path is (" + getExpectedSourcePath(appConfig, krameriusInstanceId, pid) + ")");
            }
            File destinationFile = dataHandler.getDestinationFile(pid, instance);
            if (destinationFile == null || !destinationFile.exists()) {
                throw new IOException("Destination file for " + pid + " does not exists. Expected path is (" + getExpectedDestinationPath(instance, pid) + ")");
            }
            if (!FileUtils.copy(sourceFile, destinationFile)) {
                throw new IOException("ProArc can not copy content of " + sourceFile.getAbsolutePath() + " to " + destinationFile.getAbsolutePath() + ".");
            }
            KImporter kImporter = new KImporter(appConfig, instance);
            String state = kImporter.importToKramerius(destinationFile.getParentFile(), true);
            if (KRAMERIUS_PROCESS_FINISHED.equals(state)) {
                if (instance.deleteAfterImport()) {
                    MetsUtils.deleteFolder(destinationFile.getParentFile());
                }
            }
            switch (state) {
                case KRAMERIUS_PROCESS_FINISHED:
                    importResult.setStatus("Successful");
                    BatchUtils.finishedUploadSuccessfully(this.batchManager, batch, instance.getUrl());
                    break;
                case KRAMERIUS_PROCESS_FAILED:
                    importResult.setStatus("Failed");
                    importResult.setReason("Import do Krameria (" + instance.getId() + " --> " + instance.getUrl() + ") selhal.");
                    BatchUtils.finishedUploadWithError(this.batchManager, batch, instance.getUrl(), new IOException("Import selhal."));
                    break;
                case KRAMERIUS_PROCESS_WARNING:
                    importResult.setStatus("Failed");
                    importResult.setReason("Import do Krameria (" + instance.getId() + " --> " + instance.getUrl() + ") prošel s chybou.");
                    BatchUtils.finishedUploadWithError(this.batchManager, batch, instance.getUrl(), new IOException("Import pro3el s chybou."));
                    break;
            }
        } catch (IOException ex) {
            LOG.severe(ex.getMessage());
            importResult.setStatus("Failed");
            importResult.setReason(ex.getMessage());
            BatchUtils.finishedUploadWithError(this.batchManager, batch, instance.getUrl(), ex);
        } finally {
            return new SmartGwtResponse<>(importResult);
        }
    }
}
