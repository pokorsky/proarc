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
package cz.cas.lib.proarc.webapp.server.rest.v2;

import cz.cas.lib.proarc.common.config.AppConfigurationException;
import cz.cas.lib.proarc.common.object.DescriptionMetadata;
import cz.cas.lib.proarc.common.workflow.model.Job;
import cz.cas.lib.proarc.common.workflow.model.JobView;
import cz.cas.lib.proarc.common.workflow.model.MaterialType;
import cz.cas.lib.proarc.common.workflow.model.MaterialView;
import cz.cas.lib.proarc.common.workflow.model.Task;
import cz.cas.lib.proarc.common.workflow.model.TaskParameterView;
import cz.cas.lib.proarc.common.workflow.model.TaskView;
import cz.cas.lib.proarc.common.workflow.model.WorkflowModelConsts;
import cz.cas.lib.proarc.common.workflow.profile.JobDefinitionView;
import cz.cas.lib.proarc.common.workflow.profile.WorkflowProfileConsts;
import cz.cas.lib.proarc.webapp.client.ds.MetaModelDataSource;
import cz.cas.lib.proarc.webapp.client.ds.RestConfig;
import cz.cas.lib.proarc.webapp.server.rest.SmartGwtResponse;
import cz.cas.lib.proarc.webapp.server.rest.v1.WorkflowResourceV1;
import cz.cas.lib.proarc.webapp.shared.rest.DigitalObjectResourceApi;
import cz.cas.lib.proarc.webapp.shared.rest.WorkflowResourceApi;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
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

/**
 * It allows to manage workflow remotely.
 *
 * @author Lukas Sykora
 */
@Path(RestConfig.URL_API_VERSION_2 + "/" + WorkflowResourceApi.PATH)
public class WorkflowResource extends WorkflowResourceV1 {

    private static final Logger LOG = Logger.getLogger(WorkflowResource.class.getName());

    public WorkflowResource(
            @Context HttpHeaders httpHeaders,
            @Context HttpServletRequest httpRequest
    ) throws AppConfigurationException {
        super(httpHeaders, httpRequest);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<JobView> getJob(
            @QueryParam(WorkflowModelConsts.JOB_FILTER_ID) BigDecimal id,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_CREATED) List<String> created,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_LABEL) String label,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_MODIFIED) List<String> modified,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_PRIORITY) Integer priority,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_PROFILENAME) String profileName,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_STATE) Job.State state,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_OWNERID) BigDecimal userId,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_PARENTID) BigDecimal parentId,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_MATERIAL_BARCODE) String mBarcode,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_MATERIAL_DETAIL) String mDetail,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_MATERIAL_FIELD001) String mField001,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_MATERIAL_ISSUE) String mIssue,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_MATERIAL_SIGLA) String mSigla,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_MATERIAL_SIGNATURE) String mSignature,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_MATERIAL_VOLUME) String mVolume,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_MATERIAL_YEAR) String mYear,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_MATERIAL_EDITION) String mEdition,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_OFFSET) int startRow,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_FINANCED) String financed,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_SORTBY) String sortBy,
            @QueryParam(WorkflowModelConsts.JOB_TASK_NAME) String taskName,
            @QueryParam(WorkflowModelConsts.JOB_TASK_CHANGE_DATE) Timestamp taskDate,
            @QueryParam(WorkflowModelConsts.JOB_TASK_CHANGE_USER) String taskUser,
            @QueryParam(WorkflowModelConsts.JOB_TASK_CHANGE_USERNAME) String taskUserName,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_DIGOBJ_PID) String pid,
            @QueryParam(WorkflowModelConsts.JOB_FILTER_RAW_PATH) String rawPath
    ) {
        try {
            return super.getJob(id, created, label, modified, priority, profileName, state, userId, parentId, mBarcode,
                    mDetail, mField001, mIssue, mSigla, mSignature, mVolume, mYear, mEdition, startRow, financed,
                    sortBy, taskName, taskDate, taskUser, taskUserName, pid, rawPath);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);
            return SmartGwtResponse.asError(t);
        }
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<JobView> addJob(
            @FormParam(WorkflowResourceApi.NEWJOB_PROFILE) String profileName,
            @FormParam(WorkflowResourceApi.NEWJOB_METADATA) String metadata,
            @FormParam(WorkflowResourceApi.NEWJOB_CATALOGID) String catalogId,
            @FormParam(WorkflowResourceApi.NEWJOB_PARENTID) BigDecimal parentId,
            @FormParam(WorkflowResourceApi.NEWJOB_RDCZID) BigDecimal rdczId
    ) {
        try {
            return super.addJob(profileName, metadata, catalogId, parentId, rdczId);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);
            return SmartGwtResponse.asError(t);
        }
    }

    @PUT
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<JobView> updateJob(
            @FormParam(WorkflowModelConsts.JOB_ID) BigDecimal id,
            @FormParam(WorkflowModelConsts.JOB_LABEL) String label,
            @FormParam(WorkflowModelConsts.JOB_NOTE) String note,
            @FormParam(WorkflowModelConsts.JOB_FINANCED) String financed,
            @FormParam(WorkflowModelConsts.JOB_OWNERID) BigDecimal userId,
            @FormParam(WorkflowModelConsts.JOB_PARENTID) BigDecimal parentId,
            @FormParam(WorkflowModelConsts.JOB_PRIORITY) Integer priority,
            @FormParam(WorkflowModelConsts.JOB_STATE) Job.State state,
            @FormParam(WorkflowModelConsts.JOB_TIMESTAMP) long timestamp
    ) {
        try {
            return super.updateJob(id, label, note, financed, userId, parentId, priority, state, timestamp);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);
            return SmartGwtResponse.asError(t);
        }
    }

    @GET
    @Path(WorkflowResourceApi.TASK_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<TaskView> getTask(
            @QueryParam(WorkflowModelConsts.TASK_FILTER_CREATED) List<String> created,
            @QueryParam(WorkflowModelConsts.TASK_FILTER_ID) BigDecimal id,
            @QueryParam(WorkflowModelConsts.TASK_FILTER_JOBID) BigDecimal jobId,
            @QueryParam(WorkflowModelConsts.TASK_FILTER_JOBLABEL) String jobLabel,
            @QueryParam(WorkflowModelConsts.TASK_FILTER_MODIFIED) List<String> modified,
            @QueryParam(WorkflowModelConsts.TASK_FILTER_PRIORITY) List<Integer> priority,
            @QueryParam(WorkflowModelConsts.TASK_FILTER_PROFILENAME) List<String> profileName,
            @QueryParam(WorkflowModelConsts.TASK_FILTER_STATE) List<Task.State> state,
            @QueryParam(WorkflowModelConsts.TASK_FILTER_OWNERID) List<BigDecimal> userId,
            @QueryParam(WorkflowModelConsts.TASK_FILTER_OFFSET) int startRow,
            @QueryParam(WorkflowModelConsts.TASK_FILTER_SORTBY) String sortBy,
            @QueryParam(WorkflowModelConsts.MATERIAL_BARCODE) String barcode
    ) {
        try {
            return super.getTask(created, id, jobId, jobLabel, modified, priority, profileName, state, userId, startRow, sortBy, barcode);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);
            return SmartGwtResponse.asError(t);
        }
    }

    @POST
    @Path(WorkflowResourceApi.TASK_PATH)
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED})
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<TaskView> addTask(
            @FormParam(WorkflowModelConsts.TASK_JOBID) BigDecimal jobId,
            @FormParam(WorkflowModelConsts.TASK_PROFILENAME) String taskName
    ) {
        try {
            return super.addTask(jobId, taskName);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);
            return SmartGwtResponse.asError(t);
        }
    }

    @PUT
    @Path(WorkflowResourceApi.TASK_PATH)
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<TaskView> updateTask(TaskUpdate task
    ) {
        try {
            return super.updateTask(task);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);
            return SmartGwtResponse.asError(t);
        }
    }

    @GET
    @Path(WorkflowResourceApi.MATERIAL_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<MaterialView> getMaterial(
            @QueryParam(WorkflowModelConsts.MATERIALFILTER_ID) BigDecimal id,
            @QueryParam(WorkflowModelConsts.MATERIALFILTER_JOBID) BigDecimal jobId,
            @QueryParam(WorkflowModelConsts.MATERIALFILTER_TASKID) BigDecimal taskId,
            @QueryParam(WorkflowModelConsts.MATERIAL_TYPE) MaterialType materialType,
            @QueryParam(WorkflowModelConsts.MATERIALFILTER_OFFSET) int startRow,
            @QueryParam(WorkflowModelConsts.MATERIALFILTER_SORTBY) String sortBy
    ) {
        try {
            return super.getMaterial(id, jobId, taskId, materialType, startRow, sortBy);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);
            return SmartGwtResponse.asError(t);
        }
    }

    @PUT
    @Path(WorkflowResourceApi.MATERIAL_PATH)
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<MaterialView> updateMaterial(
            MaterialView mv
    ) {
        try {
            return super.updateMaterial(mv);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);
            return SmartGwtResponse.asError(t);
        }
    }

    @GET
    @Path(WorkflowResourceApi.MODS_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<DescriptionMetadata<Object>> getDescriptionMetadata(
            @QueryParam(WorkflowModelConsts.PARAMETER_JOBID) BigDecimal jobId,
            @QueryParam(MetaModelDataSource.FIELD_EDITOR) String editorId,
            @QueryParam(MetaModelDataSource.FIELD_MODELOBJECT) String modelId
    ) {
        try {
            return super.getDescriptionMetadata(jobId, editorId, modelId);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);
            return SmartGwtResponse.asError(t);
        }
    }

    @PUT
    @Path(WorkflowResourceApi.MODS_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<DescriptionMetadata<Object>> updateDescriptionMetadata(
            @FormParam(WorkflowModelConsts.PARAMETER_JOBID) BigDecimal jobId,
            @FormParam(DigitalObjectResourceApi.MODS_CUSTOM_EDITORID) String editorId,
            @FormParam(DigitalObjectResourceApi.TIMESTAMP_PARAM) Long timestamp,
            @FormParam(DigitalObjectResourceApi.MODS_CUSTOM_CUSTOMJSONDATA) String jsonData,
            @FormParam(DigitalObjectResourceApi.MODS_CUSTOM_CUSTOMXMLDATA) String xmlData,
            @FormParam(MetaModelDataSource.FIELD_MODELOBJECT) String modelId,
            @DefaultValue("false")
            @FormParam(DigitalObjectResourceApi.MODS_CUSTOM_IGNOREVALIDATION) boolean ignoreValidation,
            @FormParam(DigitalObjectResourceApi.MODS_CUSTOM_STANDARD) String standard
    ) {
        try {
            return super.updateDescriptionMetadata(jobId, editorId, timestamp, jsonData, xmlData, modelId, ignoreValidation, standard);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);
            return SmartGwtResponse.asError(t);
        }
    }

    @GET
    @Path(WorkflowResourceApi.PARAMETER_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<TaskParameterView> getParameter(
            @QueryParam(WorkflowModelConsts.PARAMETERPROFILE_TASKID) BigDecimal taskId
    ) {
        try {
            return super.getParameter(taskId);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);
            return SmartGwtResponse.asError(t);
        }
    }

    @GET
    @Path(WorkflowResourceApi.PROFILE_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<JobDefinitionView> getProfiles(
            @QueryParam(WorkflowProfileConsts.NAME) String name,
            @QueryParam(WorkflowProfileConsts.DISABLED) Boolean disabled
    ) {
        try {
            return super.getProfiles(name, disabled);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);
            return SmartGwtResponse.asError(t);
        }
    }
}
