/**
 *  This document is a part of the source code and related artifacts
 *  for CollectionSpace, an open source collections management system
 *  for museums and related institutions:

 *  http://www.collectionspace.org
 *  http://wiki.collectionspace.org

 *  Copyright 2009 University of California at Berkeley

 *  Licensed under the Educational Community License (ECL), Version 2.0.
 *  You may not use this file except in compliance with this License.

 *  You may obtain a copy of the ECL 2.0 License at

 *  https://source.collectionspace.org/collection-space/LICENSE.txt

 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.collectionspace.services.common.vocabulary;

import org.collectionspace.services.client.IClientQueryParams;
import org.collectionspace.services.client.IQueryManager;
import org.collectionspace.services.client.PoxPayloadIn;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.client.workflow.WorkflowClient;
import org.collectionspace.services.common.ClientType;
import org.collectionspace.services.common.ResourceBase;
import org.collectionspace.services.common.ResourceMap;
import org.collectionspace.services.common.ServiceMain;
import org.collectionspace.services.common.ServiceMessages;
import org.collectionspace.services.common.XmlTools;
import org.collectionspace.services.common.api.RefName;
import org.collectionspace.services.common.api.Tools;
import org.collectionspace.services.common.authorityref.AuthorityRefDocList;
import org.collectionspace.services.common.authorityref.AuthorityRefList;
import org.collectionspace.services.common.context.JaxRsContext;
import org.collectionspace.services.common.context.MultipartServiceContext;
import org.collectionspace.services.common.context.MultipartServiceContextImpl;
import org.collectionspace.services.common.context.RemoteServiceContext;
import org.collectionspace.services.common.context.ServiceBindingUtils;
import org.collectionspace.services.common.context.ServiceContext;
import org.collectionspace.services.common.document.DocumentException;
import org.collectionspace.services.common.document.DocumentFilter;
import org.collectionspace.services.common.document.DocumentHandler;
import org.collectionspace.services.common.document.DocumentNotFoundException;
import org.collectionspace.services.common.document.DocumentWrapper;
import org.collectionspace.services.common.query.QueryManager;
import org.collectionspace.services.common.repository.RepositoryClient;
import org.collectionspace.services.common.vocabulary.nuxeo.AuthorityDocumentModelHandler;
import org.collectionspace.services.common.vocabulary.nuxeo.AuthorityItemDocumentModelHandler;
import org.collectionspace.services.common.workflow.service.nuxeo.WorkflowDocumentModelHandler;
import org.collectionspace.services.jaxb.AbstractCommonList;
import org.collectionspace.services.nuxeo.client.java.RemoteDocumentModelHandlerImpl;
import org.collectionspace.services.relation.RelationResource;
import org.collectionspace.services.relation.RelationsCommonList;
import org.collectionspace.services.relation.RelationshipType;
import org.jboss.resteasy.util.HttpResponseCodes;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * The Class AuthorityResource.
 */
/**
 * @author pschmitz
 *
 * @param <AuthCommon>
 * @param <AuthItemHandler>
 */
/**
 * @author pschmitz
 *
 * @param <AuthCommon>
 * @param <AuthItemHandler>
 */
@Consumes("application/xml")
@Produces("application/xml")
public abstract class AuthorityResource<AuthCommon, AuthItemHandler>
        extends ResourceBase {

    protected Class<AuthCommon> authCommonClass;
    protected Class<?> resourceClass;
    protected String authorityCommonSchemaName;
    protected String authorityItemCommonSchemaName;
    final static ClientType CLIENT_TYPE = ServiceMain.getInstance().getClientType();
    final static String URN_PREFIX = "urn:cspace:";
    final static int URN_PREFIX_LEN = URN_PREFIX.length();
    final static String URN_PREFIX_NAME = "name(";
    final static int URN_NAME_PREFIX_LEN = URN_PREFIX_LEN + URN_PREFIX_NAME.length();
    final static String URN_PREFIX_ID = "id(";
    final static int URN_ID_PREFIX_LEN = URN_PREFIX_LEN + URN_PREFIX_ID.length();
    final static String FETCH_SHORT_ID = "_fetch_";
    final Logger logger = LoggerFactory.getLogger(AuthorityResource.class);

    public enum SpecifierForm {

        CSID, URN_NAME
    };

    public class Specifier {

        public SpecifierForm form;
        public String value;

        Specifier(SpecifierForm form, String value) {
            this.form = form;
            this.value = value;
        }
    }

    protected Specifier getSpecifier(String specifierIn, String method, String op) throws WebApplicationException {
        if (logger.isDebugEnabled()) {
            logger.debug("getSpecifier called by: " + method + " with specifier: " + specifierIn);
        }
        if (specifierIn != null) {
            if (!specifierIn.startsWith(URN_PREFIX)) {
                // We'll assume it is a CSID and complain if it does not match
                return new Specifier(SpecifierForm.CSID, specifierIn);
            } else {
                if (specifierIn.startsWith(URN_PREFIX_NAME, URN_PREFIX_LEN)) {
                    int closeParen = specifierIn.indexOf(')', URN_NAME_PREFIX_LEN);
                    if (closeParen >= 0) {
                        return new Specifier(SpecifierForm.URN_NAME,
                                specifierIn.substring(URN_NAME_PREFIX_LEN, closeParen));
                    }
                } else if (specifierIn.startsWith(URN_PREFIX_ID, URN_PREFIX_LEN)) {
                    int closeParen = specifierIn.indexOf(')', URN_ID_PREFIX_LEN);
                    if (closeParen >= 0) {
                        return new Specifier(SpecifierForm.CSID,
                                specifierIn.substring(URN_ID_PREFIX_LEN, closeParen));
                    }
                }
            }
        }
        logger.error(method + ": bad or missing specifier!");
        Response response = Response.status(Response.Status.BAD_REQUEST).entity(
                op + " failed on bad or missing Authority specifier").type(
                "text/plain").build();
        throw new WebApplicationException(response);
    }

    /**
     * Instantiates a new Authority resource.
     */
    public AuthorityResource(Class<AuthCommon> authCommonClass, Class<?> resourceClass,
            String authorityCommonSchemaName, String authorityItemCommonSchemaName) {
        this.authCommonClass = authCommonClass;
        this.resourceClass = resourceClass;
        this.authorityCommonSchemaName = authorityCommonSchemaName;
        this.authorityItemCommonSchemaName = authorityItemCommonSchemaName;
    }

    public abstract String getItemServiceName();

    @Override
    protected String getVersionString() {
        return "$LastChangedRevision: 2617 $";
    }

    @Override
    public Class<AuthCommon> getCommonPartClass() {
        return authCommonClass;
    }

    /**
     * Creates the item document handler.
     * 
     * @param ctx the ctx
     * @param inAuthority the in vocabulary
     * 
     * @return the document handler
     * 
     * @throws Exception the exception
     */
    protected DocumentHandler createItemDocumentHandler(
            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx,
            String inAuthority, String parentShortIdentifier)
            throws Exception {
        String authorityRefNameBase;
        AuthorityItemDocumentModelHandler<?> docHandler;

        if (parentShortIdentifier == null) {
            authorityRefNameBase = null;
        } else {
            ServiceContext<PoxPayloadIn, PoxPayloadOut> parentCtx =
                    createServiceContext(getServiceName());
            if (parentShortIdentifier.equals(FETCH_SHORT_ID)) {
                // Get from parent document
                parentShortIdentifier = getAuthShortIdentifier(parentCtx, inAuthority);
            }
            authorityRefNameBase = buildAuthorityRefNameBase(parentCtx, parentShortIdentifier);
        }

        docHandler = (AuthorityItemDocumentModelHandler<?>) createDocumentHandler(ctx,
                ctx.getCommonPartLabel(getItemServiceName()),
                authCommonClass);
        docHandler.setInAuthority(inAuthority);
        docHandler.setAuthorityRefNameBase(authorityRefNameBase);

        return docHandler;
    }

    public String getAuthShortIdentifier(
            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx, String authCSID)
            throws DocumentNotFoundException, DocumentException {
        String shortIdentifier = null;
        try {
            DocumentWrapper<DocumentModel> wrapDoc = getRepositoryClient(ctx).getDocFromCsid(ctx, authCSID);
            AuthorityDocumentModelHandler<?> handler =
                    (AuthorityDocumentModelHandler<?>) createDocumentHandler(ctx);
            shortIdentifier = handler.getShortIdentifier(wrapDoc, authorityCommonSchemaName);
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Caught exception ", e);
            }
            throw new DocumentException(e);
        }
        return shortIdentifier;
    }

    protected String buildAuthorityRefNameBase(
            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx, String shortIdentifier) {
        RefName.Authority authority = RefName.buildAuthority(ctx.getTenantName(),
                ctx.getServiceName(), shortIdentifier, null);
        return authority.toString();
    }

    public static class CsidAndShortIdentifier {

        String CSID;
        String shortIdentifier;
    }

    public String lookupParentCSID(String parentspecifier, String method, String op, MultivaluedMap<String, String> queryParams)
            throws Exception {
        CsidAndShortIdentifier tempResult = lookupParentCSIDAndShortIdentifer(parentspecifier, method, op, queryParams);
        return tempResult.CSID;
    }

    public CsidAndShortIdentifier lookupParentCSIDAndShortIdentifer(String parentspecifier, String method, String op, MultivaluedMap<String, String> queryParams)
            throws Exception {
        CsidAndShortIdentifier result = new CsidAndShortIdentifier();
        Specifier parentSpec = getSpecifier(parentspecifier, method, op);
        // Note that we have to create the service context for the Items, not the main service
        String parentcsid;
        String parentShortIdentifier;
        if (parentSpec.form == SpecifierForm.CSID) {
            parentShortIdentifier = null;
            parentcsid = parentSpec.value;
            // Uncomment when app layer is ready to integrate
            // Uncommented since refNames are currently only generated if not present - ADR CSPACE-3178
            parentShortIdentifier = FETCH_SHORT_ID;
        } else {
            parentShortIdentifier = parentSpec.value;
            String whereClause = buildWhereForAuthByName(parentSpec.value);
            ServiceContext ctx = createServiceContext(getServiceName(), queryParams);
            parentcsid = getRepositoryClient(ctx).findDocCSID(ctx, whereClause); //FIXME: REM - If the parent has been soft-deleted, should we be looking for the item?
        }
        result.CSID = parentcsid;
        result.shortIdentifier = parentShortIdentifier;
        return result;
    }

    public String lookupItemCSID(String itemspecifier, String parentcsid, String method, String op, ServiceContext ctx)
            throws DocumentException {
        String itemcsid;
        Specifier itemSpec = getSpecifier(itemspecifier, method, op);
        if (itemSpec.form == SpecifierForm.CSID) {
            itemcsid = itemSpec.value;
        } else {
            String itemWhereClause = buildWhereForAuthItemByName(itemSpec.value, parentcsid);
            itemcsid = getRepositoryClient(ctx).findDocCSID(ctx, itemWhereClause); //FIXME: REM - Should we be looking for the 'wf_deleted' query param and filtering on it?
        }
        return itemcsid;
    }

    /*
     * Generally, callers will first call RefName.AuthorityItem.parse with a refName, and then 
     * use the returned item.inAuthority.resource and a resourceMap to get a service-specific
     * Resource. They then call this method on that resource.
     */
    @Override
   	public DocumentModel getDocModelForAuthorityItem(RefName.AuthorityItem item) 
   			throws Exception, DocumentNotFoundException {
    	if(item == null) {
    		return null;
    	}
        String whereClause = buildWhereForAuthByName(item.getParentShortIdentifier());
        // Ensure we have the right context.
        ServiceContext ctx = createServiceContext(item.inAuthority.resource);
        
        String parentcsid = getRepositoryClient(ctx).findDocCSID(ctx, whereClause);

        String itemWhereClause = buildWhereForAuthItemByName(item.getShortIdentifier(), parentcsid);
        ctx = createServiceContext(getItemServiceName());
        DocumentWrapper<DocumentModel> docWrapper = getRepositoryClient(ctx).findDoc(ctx, itemWhereClause);
        DocumentModel docModel = docWrapper.getWrappedObject();
        return docModel;
    }


    @POST
    public Response createAuthority(String xmlPayload) {
        try {
            PoxPayloadIn input = new PoxPayloadIn(xmlPayload);
            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx = createServiceContext(input);
            DocumentHandler handler = createDocumentHandler(ctx);
            String csid = getRepositoryClient(ctx).create(ctx, handler);
            UriBuilder path = UriBuilder.fromResource(resourceClass);
            path.path("" + csid);
            Response response = Response.created(path.build()).build();
            return response;
        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.CREATE_FAILED);
        }
    }

    protected String buildWhereForAuthByName(String name) {
        return authorityCommonSchemaName
                + ":" + AuthorityJAXBSchema.SHORT_IDENTIFIER
                + "='" + name + "'";
    }

    protected String buildWhereForAuthItemByName(String name, String parentcsid) {
        return authorityItemCommonSchemaName
                + ":" + AuthorityItemJAXBSchema.SHORT_IDENTIFIER
                + "='" + name + "' AND "
                + authorityItemCommonSchemaName + ":"
                + AuthorityItemJAXBSchema.IN_AUTHORITY + "="
                + "'" + parentcsid + "'";
    }

    /**
     * Gets the authority.
     * 
     * @param specifier either a CSID or one of the urn forms
     * 
     * @return the authority
     */
    @GET
    @Path("{csid}")
    @Override
    public byte[] get( // getAuthority(
            @Context UriInfo ui,
            @PathParam("csid") String specifier) {
        PoxPayloadOut result = null;
        try {
            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx = createServiceContext(ui);
            DocumentHandler handler = createDocumentHandler(ctx);

            Specifier spec = getSpecifier(specifier, "getAuthority", "GET");
            if (spec.form == SpecifierForm.CSID) {
                if (logger.isDebugEnabled()) {
                    logger.debug("getAuthority with csid=" + spec.value);
                }
                getRepositoryClient(ctx).get(ctx, spec.value, handler);
            } else {
                String whereClause = buildWhereForAuthByName(spec.value);
                DocumentFilter myFilter = new DocumentFilter(whereClause, 0, 1);
                handler.setDocumentFilter(myFilter);
                getRepositoryClient(ctx).get(ctx, handler);
            }
            result = ctx.getOutput();

        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.GET_FAILED, specifier);
        }

        if (result == null) {
            Response response = Response.status(Response.Status.NOT_FOUND).entity(
                    "Get failed, the requested Authority specifier:" + specifier + ": was not found.").type(
                    "text/plain").build();
            throw new WebApplicationException(response);
        }

        return result.getBytes();
    }

    /**
     * Finds and populates the authority list.
     * 
     * @param ui the ui
     * 
     * @return the authority list
     */
    @GET
    @Produces("application/xml")
    public AbstractCommonList getAuthorityList(@Context UriInfo ui) {
        try {
            MultivaluedMap<String, String> queryParams = ui.getQueryParameters();
            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx = createServiceContext(queryParams);
            DocumentHandler handler = createDocumentHandler(ctx);
            DocumentFilter myFilter = handler.getDocumentFilter();
            // Need to make the default sort order for authority items
            // be on the displayName field
            String sortBy = queryParams.getFirst(IClientQueryParams.SORT_BY_PARAM);
            if (sortBy == null || sortBy.isEmpty()) {
                String qualifiedDisplayNameField = authorityCommonSchemaName + ":"
                        + AuthorityItemJAXBSchema.DISPLAY_NAME;
                myFilter.setOrderByClause(qualifiedDisplayNameField);
            }
            String nameQ = queryParams.getFirst("refName");
            if (nameQ != null) {
                myFilter.setWhereClause(authorityCommonSchemaName + ":refName='" + nameQ + "'");
            }
            getRepositoryClient(ctx).getFiltered(ctx, handler);
            return (AbstractCommonList) handler.getCommonPartList();
        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.GET_FAILED);
        }
    }

    /**
     * Update authority.
     *
     * @param specifier the csid or id
     *
     * @return the multipart output
     */
    @PUT
    @Path("{csid}")
    public byte[] updateAuthority(
            @PathParam("csid") String specifier,
            String xmlPayload) {
        PoxPayloadOut result = null;
        try {
            PoxPayloadIn theUpdate = new PoxPayloadIn(xmlPayload);
            Specifier spec = getSpecifier(specifier, "updateAuthority", "UPDATE");
            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx = createServiceContext(theUpdate);
            DocumentHandler handler = createDocumentHandler(ctx);
            String csid;
            if (spec.form == SpecifierForm.CSID) {
                csid = spec.value;
            } else {
                String whereClause = buildWhereForAuthByName(spec.value);
                csid = getRepositoryClient(ctx).findDocCSID(ctx, whereClause);
            }
            getRepositoryClient(ctx).update(ctx, csid, handler);
            result = ctx.getOutput();
        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.UPDATE_FAILED);
        }
        return result.getBytes();
    }

    /**
     * Delete authority.
     * 
     * @param csid the csid
     * 
     * @return the response
     */
    @DELETE
    @Path("{csid}")
    public Response deleteAuthority(@PathParam("csid") String csid) {
        if (logger.isDebugEnabled()) {
            logger.debug("deleteAuthority with csid=" + csid);
        }
        try {
            ensureCSID(csid, ServiceMessages.DELETE_FAILED, "Authority.csid");
            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx = createServiceContext();
            getRepositoryClient(ctx).delete(ctx, csid);
            return Response.status(HttpResponseCodes.SC_OK).build();
        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.DELETE_FAILED, csid);
        }
    }

    /*************************************************************************
     * Create an AuthorityItem - this is a sub-resource of Authority
     * @param specifier either a CSID or one of the urn forms
     * @return Authority item response
     *************************************************************************/
    @POST
    @Path("{csid}/items")
    public Response createAuthorityItem(@Context ResourceMap resourceMap, @Context UriInfo ui, 
    		@PathParam("csid") String specifier, String xmlPayload) {
        try {
            PoxPayloadIn input = new PoxPayloadIn(xmlPayload);
            ServiceContext ctx = createServiceContext(getItemServiceName(), input);
            ctx.setResourceMap(resourceMap);
            ctx.setUriInfo(ui);    //Laramie

            // Note: must have the parentShortId, to do the create.
            CsidAndShortIdentifier parent = lookupParentCSIDAndShortIdentifer(specifier, "createAuthorityItem", "CREATE_ITEM", null);
            DocumentHandler handler = createItemDocumentHandler(ctx, parent.CSID, parent.shortIdentifier);
            String itemcsid = getRepositoryClient(ctx).create(ctx, handler);
            UriBuilder path = UriBuilder.fromResource(resourceClass);
            path.path(parent.CSID + "/items/" + itemcsid);
            Response response = Response.created(path.build()).build();
            return response;
        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.CREATE_FAILED);
        }
    }

    @GET
    @Path("{csid}/items/{itemcsid}" + WorkflowClient.SERVICE_PATH)
    public byte[] getItemWorkflow(
            @PathParam("csid") String csid,
            @PathParam("itemcsid") String itemcsid) {
        PoxPayloadOut result = null;

        try {
            ServiceContext<PoxPayloadIn, PoxPayloadOut> parentCtx = createServiceContext(getItemServiceName());
            String parentWorkspaceName = parentCtx.getRepositoryWorkspaceName();

            MultipartServiceContext ctx = (MultipartServiceContext) createServiceContext(WorkflowClient.SERVICE_NAME);
            WorkflowDocumentModelHandler handler = createWorkflowDocumentHandler(ctx);
            ctx.setRespositoryWorkspaceName(parentWorkspaceName); //find the document in the parent's workspace
            getRepositoryClient(ctx).get(ctx, itemcsid, handler);
            result = ctx.getOutput();
        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.READ_FAILED + WorkflowClient.SERVICE_PAYLOAD_NAME, csid);
        }
        return result.getBytes();
    }

    @PUT
    @Path("{csid}/items/{itemcsid}" + WorkflowClient.SERVICE_PATH)
    public byte[] updateWorkflow(
            @PathParam("csid") String csid,
            @PathParam("itemcsid") String itemcsid,
            String xmlPayload) {
        PoxPayloadOut result = null;
        try {
            ServiceContext<PoxPayloadIn, PoxPayloadOut> parentCtx = createServiceContext(getItemServiceName());
            String parentWorkspaceName = parentCtx.getRepositoryWorkspaceName();

            PoxPayloadIn workflowUpdate = new PoxPayloadIn(xmlPayload);
            MultipartServiceContext ctx = (MultipartServiceContext) createServiceContext(WorkflowClient.SERVICE_NAME, workflowUpdate);
            WorkflowDocumentModelHandler handler = createWorkflowDocumentHandler(ctx);
            ctx.setRespositoryWorkspaceName(parentWorkspaceName); //find the document in the parent's workspace
            getRepositoryClient(ctx).update(ctx, itemcsid, handler);
            result = ctx.getOutput();
        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.UPDATE_FAILED + WorkflowClient.SERVICE_PAYLOAD_NAME, csid);
        }
        return result.getBytes();
    }

    /**
     * Gets the authority item.
     * 
     * @param parentspecifier either a CSID or one of the urn forms
     * @param itemspecifier either a CSID or one of the urn forms
     * 
     * @return the authority item
     */
    @GET
    @Path("{csid}/items/{itemcsid}")
    public byte[] getAuthorityItem(
            @Context Request request,
            @Context UriInfo ui,
            @PathParam("csid") String parentspecifier,
            @PathParam("itemcsid") String itemspecifier) {
        PoxPayloadOut result = null;
        try {
            JaxRsContext jaxRsContext = new JaxRsContext(request, ui);
            MultivaluedMap<String, String> queryParams = ui.getQueryParameters();
            String parentcsid = lookupParentCSID(parentspecifier, "getAuthorityItem(parent)", "GET_ITEM", queryParams);

            RemoteServiceContext<PoxPayloadIn, PoxPayloadOut> ctx = null;
            ctx = (RemoteServiceContext) createServiceContext(getItemServiceName(), queryParams);
            ctx.setJaxRsContext(jaxRsContext);

            ctx.setUriInfo(ui); //ARG!   must pass this or subsequent calls will not have a ui.

            // We omit the parentShortId, only needed when doing a create...
            DocumentHandler handler = createItemDocumentHandler(ctx, parentcsid, null);

            Specifier itemSpec = getSpecifier(itemspecifier, "getAuthorityItem(item)", "GET_ITEM");
            if (itemSpec.form == SpecifierForm.CSID) {
                getRepositoryClient(ctx).get(ctx, itemSpec.value, handler);
            } else {
                String itemWhereClause =
                        buildWhereForAuthItemByName(itemSpec.value, parentcsid);
                DocumentFilter myFilter = new DocumentFilter(itemWhereClause, 0, 1);
                handler.setDocumentFilter(myFilter);
                getRepositoryClient(ctx).get(ctx, handler);
            }
            // TODO should we assert that the item is in the passed vocab?
            result = ctx.getOutput();
        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.GET_FAILED);
        }
        if (result == null) {
            Response response = Response.status(Response.Status.NOT_FOUND).entity(
                    "Get failed, the requested AuthorityItem specifier:" + itemspecifier + ": was not found.").type(
                    "text/plain").build();
            throw new WebApplicationException(response);
        }
        return result.getBytes();
    }

    /**
     * Gets the authorityItem list for the specified authority
     * If partialPerm is specified, keywords will be ignored.
     * 
     * @param specifier either a CSID or one of the urn forms
     * @param partialTerm if non-null, matches partial terms
     * @param keywords if non-null, matches terms in the keyword index for items
     * @param ui passed to include additional parameters, like pagination controls
     * 
     * @return the authorityItem list
     */
    @GET
    @Path("{csid}/items")
    @Produces("application/xml")
    public AbstractCommonList getAuthorityItemList(@PathParam("csid") String specifier,
            @Context UriInfo ui) {
        try {
            MultivaluedMap<String, String> queryParams = ui.getQueryParameters();
            String partialTerm = queryParams.getFirst(IQueryManager.SEARCH_TYPE_PARTIALTERM);
            String keywords = queryParams.getFirst(IQueryManager.SEARCH_TYPE_KEYWORDS_KW);
            String advancedSearch = queryParams.getFirst(IQueryManager.SEARCH_TYPE_KEYWORDS_AS);

            String qualifiedDisplayNameField = authorityItemCommonSchemaName + ":"
                    + AuthorityItemJAXBSchema.DISPLAY_NAME;

            // Note that docType defaults to the ServiceName, so we're fine with that.
            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx = null;

            String parentcsid = lookupParentCSID(specifier, "getAuthorityItemList", "LIST", queryParams);

            ctx = createServiceContext(getItemServiceName(), queryParams);
            // We omit the parentShortId, only needed when doing a create...
            DocumentHandler handler = createItemDocumentHandler(ctx,
                    parentcsid, null);
            DocumentFilter myFilter = handler.getDocumentFilter();
            // Need to make the default sort order for authority items
            // be on the displayName field
            String sortBy = queryParams.getFirst(IClientQueryParams.SORT_BY_PARAM);
            if (sortBy == null || sortBy.isEmpty()) {
                myFilter.setOrderByClause(qualifiedDisplayNameField);
            }

            myFilter.appendWhereClause(authorityItemCommonSchemaName + ":"
                    + AuthorityItemJAXBSchema.IN_AUTHORITY + "="
                    + "'" + parentcsid + "'",
                    IQueryManager.SEARCH_QUALIFIER_AND);

            // AND vocabularyitems_common:displayName LIKE '%partialTerm%'
            // NOTE: Partial terms searches are mutually exclusive to keyword and advanced-search, but
            // the PT query param trumps the KW and AS query params.
            if (partialTerm != null && !partialTerm.isEmpty()) {
                String ptClause = QueryManager.createWhereClauseForPartialMatch(
                        qualifiedDisplayNameField, partialTerm);
                myFilter.appendWhereClause(ptClause, IQueryManager.SEARCH_QUALIFIER_AND);
            } else if (keywords != null || advancedSearch != null) {
//				String kwdClause = QueryManager.createWhereClauseFromKeywords(keywords);
//				myFilter.appendWhereClause(kwdClause, IQueryManager.SEARCH_QUALIFIER_AND);
                return search(ctx, handler, queryParams, keywords, advancedSearch);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("getAuthorityItemList filtered WHERE clause: "
                        + myFilter.getWhereClause());
            }
            getRepositoryClient(ctx).getFiltered(ctx, handler);
            return (AbstractCommonList) handler.getCommonPartList();
        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.LIST_FAILED);
        }
    }

    /**
     * @return the name of the property used to specify references for items in this type of
     * authority. For most authorities, it is ServiceBindingUtils.AUTH_REF_PROP ("authRef").
     * Some types (like Vocabulary) use a separate property.
     */
    protected String getRefPropName() {
    	return ServiceBindingUtils.AUTH_REF_PROP;
    }
    
    /**
     * Gets the entities referencing this Authority item instance. The service type
     * can be passed as a query param "type", and must match a configured type
     * for the service bindings. If not set, the type defaults to
     * ServiceBindingUtils.SERVICE_TYPE_PROCEDURE.
     *
     * @param parentspecifier either a CSID or one of the urn forms
     * @param itemspecifier either a CSID or one of the urn forms
     * @param ui the ui
     * 
     * @return the info for the referencing objects
     */
    @GET
    @Path("{csid}/items/{itemcsid}/refObjs")
    @Produces("application/xml")
    public AuthorityRefDocList getReferencingObjects(
            @PathParam("csid") String parentspecifier,
            @PathParam("itemcsid") String itemspecifier,
            @Context UriInfo ui) {
        AuthorityRefDocList authRefDocList = null;
        try {
            MultivaluedMap<String, String> queryParams = ui.getQueryParameters();

            String parentcsid = lookupParentCSID(parentspecifier, "getReferencingObjects(parent)", "GET_ITEM_REF_OBJS", queryParams);

            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx = createServiceContext(getItemServiceName(), queryParams);
            String itemcsid = lookupItemCSID(itemspecifier, parentcsid, "getReferencingObjects(item)", "GET_ITEM_REF_OBJS", ctx);

            // Note that we have to create the service context for the Items, not the main service
            // We omit the parentShortId, only needed when doing a create...
            DocumentHandler handler = createItemDocumentHandler(ctx, parentcsid, null);
            RepositoryClient repoClient = getRepositoryClient(ctx);
            DocumentFilter myFilter = handler.getDocumentFilter();
            String serviceType = ServiceBindingUtils.SERVICE_TYPE_PROCEDURE;
            List<String> list = queryParams.remove(ServiceBindingUtils.SERVICE_TYPE_PROP);
            if (list != null) {
                serviceType = list.get(0);
            }
            DocumentWrapper<DocumentModel> docWrapper = repoClient.getDoc(ctx, itemcsid);
            DocumentModel docModel = docWrapper.getWrappedObject();
            String refName = (String) docModel.getPropertyValue(AuthorityItemJAXBSchema.REF_NAME);

            authRefDocList = RefNameServiceUtils.getAuthorityRefDocs(ctx,
                    repoClient,
                    serviceType,
                    refName,
                    getRefPropName(),
                    myFilter.getPageSize(), myFilter.getStartPage(), true /*computeTotal*/);
        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.GET_FAILED);
        }
        if (authRefDocList == null) {
            Response response = Response.status(Response.Status.NOT_FOUND).entity(
                    "Get failed, the requested Item CSID:" + itemspecifier + ": was not found.").type(
                    "text/plain").build();
            throw new WebApplicationException(response);
        }
        return authRefDocList;
    }

    /**
     * Gets the authority terms used in the indicated Authority item.
     *
     * @param parentspecifier either a CSID or one of the urn forms
     * @param itemspecifier either a CSID or one of the urn forms
     * @param ui passed to include additional parameters, like pagination controls
     *
     * @return the authority refs for the Authority item.
     */
    @GET
    @Path("{csid}/items/{itemcsid}/authorityrefs")
    @Produces("application/xml")
    public AuthorityRefList getAuthorityItemAuthorityRefs(
            @PathParam("csid") String parentspecifier,
            @PathParam("itemcsid") String itemspecifier,
            @Context UriInfo ui) {
        AuthorityRefList authRefList = null;
        try {
            // Note that we have to create the service context for the Items, not the main service
            MultivaluedMap<String, String> queryParams = ui.getQueryParameters();
            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx = null;

            String parentcsid = lookupParentCSID(parentspecifier, "getAuthorityItemAuthRefs(parent)", "GET_ITEM_AUTH_REFS", queryParams);

            ctx = createServiceContext(getItemServiceName(), queryParams);
            // We omit the parentShortId, only needed when doing a create...
            RemoteDocumentModelHandlerImpl handler =
                    (RemoteDocumentModelHandlerImpl) createItemDocumentHandler(ctx, parentcsid, null);

            String itemcsid = lookupItemCSID(itemspecifier, parentcsid, "getAuthorityItemAuthRefs(item)", "GET_ITEM_AUTH_REFS", ctx);

            DocumentWrapper<DocumentModel> docWrapper = getRepositoryClient(ctx).getDoc(ctx, itemcsid);
            List<String> authRefFields =
                    ((MultipartServiceContextImpl) ctx).getCommonPartPropertyValues(
                    ServiceBindingUtils.AUTH_REF_PROP, ServiceBindingUtils.QUALIFIED_PROP_NAMES);
            authRefList = handler.getAuthorityRefs(docWrapper, authRefFields);
        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.GET_FAILED + " parentspecifier: " + parentspecifier + " itemspecifier:" + itemspecifier);
        }
        return authRefList;
    }

    /**
     * Update authorityItem.
     * 
     * @param parentspecifier either a CSID or one of the urn forms
     * @param itemspecifier either a CSID or one of the urn forms
     *
     * @return the multipart output
     */
    @PUT
    @Path("{csid}/items/{itemcsid}")
    public byte[] updateAuthorityItem(
    		@Context ResourceMap resourceMap, 
            @Context UriInfo ui,
            @PathParam("csid") String parentspecifier,
            @PathParam("itemcsid") String itemspecifier,
            String xmlPayload) {
        PoxPayloadOut result = null;
        try {
            PoxPayloadIn theUpdate = new PoxPayloadIn(xmlPayload);
            // Note that we have to create the service context for the Items, not the main service
            //Laramie CSPACE-3175.  passing null for queryParams, because prior to this refactor, the code moved to lookupParentCSID in this instance called the version of getServiceContext() that passes null
            String parentcsid = lookupParentCSID(parentspecifier, "updateAuthorityItem(parent)", "UPDATE_ITEM", null);

            ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx = createServiceContext(getItemServiceName(), theUpdate);
            ctx.setResourceMap(resourceMap);
            String itemcsid = lookupItemCSID(itemspecifier, parentcsid, "updateAuthorityItem(item)", "UPDATE_ITEM", ctx);

            // We omit the parentShortId, only needed when doing a create...
            DocumentHandler handler = createItemDocumentHandler(ctx, parentcsid, null);
            ctx.setUriInfo(ui);
            getRepositoryClient(ctx).update(ctx, itemcsid, handler);
            result = ctx.getOutput();

        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.UPDATE_FAILED);
        }
        return result.getBytes();
    }

    /**
     * Delete authorityItem.
     * 
     * @param parentcsid the parentcsid
     * @param itemcsid the itemcsid
     * 
     * @return the response
     */
    @DELETE
    @Path("{csid}/items/{itemcsid}")
    public Response deleteAuthorityItem(
            @PathParam("csid") String parentcsid,
            @PathParam("itemcsid") String itemcsid) {
        //try{
        if (logger.isDebugEnabled()) {
            logger.debug("deleteAuthorityItem with parentcsid=" + parentcsid + " and itemcsid=" + itemcsid);
        }
        try {
            ensureCSID(parentcsid, ServiceMessages.DELETE_FAILED, "AuthorityItem.parentcsid");
            ensureCSID(itemcsid, ServiceMessages.DELETE_FAILED, "AuthorityItem.itemcsid");
            //Laramie, removing this catch, since it will surely fail below, since itemcsid or parentcsid will be null.
            // }catch (Throwable t){
            //    System.out.println("ERROR in setting up DELETE: "+t);
            // }
            // try {
            // Note that we have to create the service context for the Items, not the main service
            ServiceContext ctx = createServiceContext(getItemServiceName());
            getRepositoryClient(ctx).delete(ctx, itemcsid);
            return Response.status(HttpResponseCodes.SC_OK).build();
        } catch (Exception e) {
            throw bigReThrow(e, ServiceMessages.DELETE_FAILED + "  itemcsid: " + itemcsid + " parentcsid:" + parentcsid);
        }
    }
    public final static String hierarchy = "hierarchy";

    @GET
    @Path("{csid}/items/{itemcsid}/" + hierarchy)
    @Produces("application/xml")
    public String getHierarchy(@PathParam("csid") String csid,
            @PathParam("itemcsid") String itemcsid,
            @Context UriInfo ui) throws Exception {
        try {
            // All items in dive can look at their child uri's to get uri.  So we calculate the very first one.  We could also do a GET and look at the common part uri field, but why...?
            String calledUri = ui.getPath();
            String uri = "/" + calledUri.substring(0, (calledUri.length() - ("/" + hierarchy).length()));
            ServiceContext ctx = createServiceContext(getItemServiceName());
            ctx.setUriInfo(ui);
            String direction = ui.getQueryParameters().getFirst(Hierarchy.directionQP);
            if (Tools.notBlank(direction) && Hierarchy.direction_parents.equals(direction)) {
                return Hierarchy.surface(ctx, itemcsid, uri);
            } else {
                return Hierarchy.dive(ctx, itemcsid, uri);
            }
        } catch (Exception e) {
            throw bigReThrow(e, "Error showing hierarchy", itemcsid);
        }
    }
}