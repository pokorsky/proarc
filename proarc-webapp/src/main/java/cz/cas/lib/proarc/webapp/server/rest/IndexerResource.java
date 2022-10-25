package cz.cas.lib.proarc.webapp.server.rest;

import com.google.common.net.HttpHeaders;
import com.google.gwt.http.client.Request;
import com.qbizm.kramerius.imp.jaxb.DigitalObject;
import cz.cas.lib.proarc.common.config.AppConfiguration;
import cz.cas.lib.proarc.common.config.AppConfigurationException;
import cz.cas.lib.proarc.common.config.AppConfigurationFactory;
import cz.cas.lib.proarc.common.fedora.DigitalObjectException;
import cz.cas.lib.proarc.common.fedora.FedoraObject;
import cz.cas.lib.proarc.common.fedora.LocalStorage;
import cz.cas.lib.proarc.common.fedora.SearchViewItem;
import cz.cas.lib.proarc.common.fedora.Storage;
import cz.cas.lib.proarc.common.fedora.akubra.AkubraConfiguration;
import cz.cas.lib.proarc.common.fedora.akubra.AkubraConfigurationFactory;
import cz.cas.lib.proarc.common.fedora.akubra.SolrFeeder;
import cz.cas.lib.proarc.common.user.Permission;
import cz.cas.lib.proarc.common.user.Permissions;
import cz.cas.lib.proarc.common.user.UserProfile;
import cz.cas.lib.proarc.webapp.client.widget.UserRole;
import cz.cas.lib.proarc.webapp.shared.rest.IndexerResourceApi;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;

@Path(IndexerResourceApi.PATH)
public class IndexerResource {

    private static final Logger LOG = Logger.getLogger(IndexerResource.class.getName());

    private final AppConfiguration appConfiguration;
    private final AkubraConfiguration akubraConfiguration;
    private final Request httpRequest;
    private final HttpHeaders httpHeaders;
    private final UserProfile user;
    private final SessionContext session;
    private static Unmarshaller unmarshaller;

    public IndexerResource(
            @Context Request httpRequest,
            @Context SecurityContext securityContext,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo uriInfo,
            @Context HttpServletRequest httpServletRequest) throws AppConfigurationException {
        this.httpRequest = httpRequest;
        this.httpHeaders = httpHeaders;
        this.appConfiguration = AppConfigurationFactory.getInstance().defaultInstance();
        if (Storage.AKUBRA.equals(appConfiguration.getTypeOfStorage())) {
            this.akubraConfiguration = AkubraConfigurationFactory.getInstance().defaultInstance(appConfiguration.getConfigHome());
        } else {
            this.akubraConfiguration = null;
        }
        this.session = SessionContext.from(httpServletRequest);
        this.user = this.session.getUser();
        LOG.fine(user.toString());

        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(DigitalObject.class);
            this.unmarshaller = jaxbContext.createUnmarshaller();
        } catch (JAXBException e) {
            LOG.log(Level.SEVERE, "Cannot init JAXB", e);
            throw new RuntimeException(e);
        }
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<SearchViewItem> indexObjects () throws SolrServerException, IOException {

        checkPermission(UserRole.ROLE_SUPERADMIN, Permissions.ADMIN);


        String objectStorePath = this.akubraConfiguration.getObjectStorePath();
        String datastreamStorePath = this.akubraConfiguration.getDatastreamStorePath();

        String processingSolrHost = this.akubraConfiguration.getSolrProcessingHost();
        SolrClient solrClient = new ConcurrentUpdateSolrClient.Builder(processingSolrHost).withQueueSize(100).build();
        SolrFeeder feeder = new SolrFeeder(solrClient);

        //processRoot(feeder, datastreamStorePath, false);
        processRoot(feeder, objectStorePath, true);


        return new SmartGwtResponse<>();
    }

    private void processRoot(SolrFeeder feeder, String storePath, boolean rebuildIndex) throws SolrServerException, IOException {
        try {
            LOG.info("Indexing documents started.");
            java.nio.file.Path storeRoot = Paths.get(storePath);
            AtomicInteger files = new AtomicInteger();
            Files.walk(storeRoot).parallel().filter(Files::isRegularFile).forEach(path -> {
                try {
                    File file = path.toFile();
                    FileInputStream inputStream = new FileInputStream(file);
                    DigitalObject digitalObject = createDigitalObject(inputStream);
                    FedoraObject fedoraObject = new LocalStorage().load(digitalObject.getPID(), file);
                    if (rebuildIndex) {
                        files.getAndIncrement();
                        feeder.feedDescriptionDocument(digitalObject, fedoraObject);
                        if (files.get() % 100 == 0) {
                            LOG.info("Proccessed " + files.get() + " objects");
                        }
                    }
                } catch (FileNotFoundException | DigitalObjectException e) {
                    LOG.log(Level.SEVERE, "Error in proccesing file: ", e);
                }
            });
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Error in processing file: ", ex);
        } finally {
            if (feeder != null) {
                feeder.commit();
                LOG.info("Feeder commited.");
            }
        }
    }

    static DigitalObject createDigitalObject(InputStream inputStream) {
        DigitalObject obj = null;
        try {
            synchronized (unmarshaller) {
                obj = (DigitalObject) unmarshaller.unmarshal(inputStream);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return obj;
    }


    @POST
    @Path(IndexerResourceApi.OBJECT_PATH)
    @Produces({MediaType.APPLICATION_JSON})
    public SmartGwtResponse<SearchViewItem> indexDocument (
            @FormParam(IndexerResourceApi.DIGITALOBJECT_PID) String pid
    ) {
        checkPermission(UserRole.ROLE_SUPERADMIN, Permissions.ADMIN);
        LOG.info("Indexing document with pid started");
        return new SmartGwtResponse<>();
    }

    private void checkPermission(String role, Permission permission, String... attributes) {
        if (!(session.checkPermission(permission) || session.checkRole(role))) {
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }
    }
}
