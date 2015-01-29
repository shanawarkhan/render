package org.janelia.render.service;

import org.janelia.alignment.RenderParameters;
import org.janelia.alignment.spec.Bounds;
import org.janelia.alignment.spec.ResolvedTileSpecCollection;
import org.janelia.alignment.spec.StackMetaData;
import org.janelia.alignment.spec.TileBounds;
import org.janelia.alignment.spec.TileSpec;
import org.janelia.alignment.spec.TransformSpec;
import org.janelia.render.service.dao.RenderDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

/**
 * APIs for accessing tile and transform data stored in the Render service database.
 *
 * @author Eric Trautman
 */
@Path("/v1/owner/{owner}")
public class RenderDataService {

    private final RenderDao renderDao;

    @SuppressWarnings("UnusedDeclaration")
    public RenderDataService()
            throws UnknownHostException {
        this(RenderServiceUtil.buildDao());
    }

    public RenderDataService(final RenderDao renderDao) {
        this.renderDao = renderDao;
    }

    @Path("stackIds")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<StackId> getStackIds(@PathParam("owner") final String owner) {

        LOG.info("getStackIds: entry, owner={}", owner);

        List<StackId> list = null;
        try {
            list = renderDao.getStackIds(owner);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }
        return list;
    }

    @Path("project/{project}/stack/{stack}/layoutFile")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getLayoutFile(@PathParam("owner") final String owner,
                                  @PathParam("project") final String project,
                                  @PathParam("stack") final String stack,
                                  @Context final UriInfo uriInfo) {

        return getLayoutFileForZRange(owner, project, stack, null, null, uriInfo);
    }

    @Path("project/{project}/stack/{stack}/zRange/{minZ},{maxZ}/layoutFile")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getLayoutFileForZRange(@PathParam("owner") final String owner,
                                           @PathParam("project") final String project,
                                           @PathParam("stack") final String stack,
                                           @PathParam("minZ") final Double minZ,
                                           @PathParam("maxZ") final Double maxZ,
                                           @Context final UriInfo uriInfo) {

        LOG.info("getLayoutFileForZRange: entry, owner={}, project={}, stack={}, minZ={}, maxZ={}",
                 owner, project, stack, minZ, maxZ);

        Response response = null;
        try {
            final StackId stackId = new StackId(owner, project, stack);

            final String requestUri = uriInfo.getRequestUri().toString();
            final String stackUri = "/stack/" + stack + "/";
            final int stackEnd = requestUri.indexOf(stackUri) + stackUri.length() - 1;
            final String stackRequestUri = requestUri.substring(0, stackEnd);
            final StreamingOutput responseOutput = new StreamingOutput() {
                @Override
                public void write(final OutputStream output)
                        throws IOException, WebApplicationException {
                    renderDao.writeLayoutFileData(stackId, stackRequestUri, minZ, maxZ, output);
                }
            };
            response = Response.ok(responseOutput).build();
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return response;
    }

    @Path("project/{project}/stack/{stack}/zValues")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Double> getZValues(@PathParam("owner") final String owner,
                                   @PathParam("project") final String project,
                                   @PathParam("stack") final String stack) {

        LOG.info("getZValues: entry, owner={}, project={}, stack={}",
                 owner, project, stack);

        List<Double> list = null;
        try {
            final StackId stackId = new StackId(owner, project, stack);
            list = renderDao.getZValues(stackId);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }
        return list;
    }

    @Path("project/{project}/stack/{stack}/bounds")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Bounds getStackBounds(@PathParam("owner") final String owner,
                                 @PathParam("project") final String project,
                                 @PathParam("stack") final String stack) {

        LOG.info("getStackBounds: entry, owner={}, project={}, stack={}",
                 owner, project, stack);

        Bounds bounds = null;
        try {
            final StackId stackId = new StackId(owner, project, stack);
            bounds = renderDao.getStackBounds(stackId);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }
        return bounds;
    }

    @Path("project/{project}/stack/{stack}/z/{z}/bounds")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Bounds getLayerBounds(@PathParam("owner") final String owner,
                                 @PathParam("project") final String project,
                                 @PathParam("stack") final String stack,
                                 @PathParam("z") final Double z) {

        LOG.info("getLayerBounds: entry, owner={}, project={}, stack={}, z={}",
                 owner, project, stack, z);

        Bounds bounds = null;
        try {
            final StackId stackId = new StackId(owner, project, stack);
            bounds = renderDao.getLayerBounds(stackId, z);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }
        return bounds;
    }

    @Path("project/{project}/stack/{stack}/z/{z}/tileBounds")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<TileBounds> getTileBounds(@PathParam("owner") final String owner,
                                          @PathParam("project") final String project,
                                          @PathParam("stack") final String stack,
                                          @PathParam("z") final Double z) {

        LOG.info("getTileBounds: entry, owner={}, project={}, stack={}, z={}",
                 owner, project, stack, z);

        List<TileBounds> list = null;
        try {
            final StackId stackId = new StackId(owner, project, stack);
            list = renderDao.getTileBounds(stackId, z);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }
        return list;
    }

    @Path("project/{project}/stack/{stack}/z/{z}/resolvedTiles")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ResolvedTileSpecCollection getResolvedTiles(@PathParam("owner") final String owner,
                                                       @PathParam("project") final String project,
                                                       @PathParam("stack") final String stack,
                                                       @PathParam("z") final Double z) {

        LOG.info("getResolvedTiles: entry, owner={}, project={}, stack={}, z={}",
                 owner, project, stack, z);

        ResolvedTileSpecCollection resolvedTiles = null;
        try {
            final StackId stackId = new StackId(owner, project, stack);
            resolvedTiles = renderDao.getResolvedTiles(stackId, z);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }
        return resolvedTiles;
    }

    @Path("project/{project}/stack/{stack}/z/{z}/resolvedTiles")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveResolvedTiles(@PathParam("owner") final String owner,
                                      @PathParam("project") final String project,
                                      @PathParam("stack") final String stack,
                                      @PathParam("z") final Double z,
                                      @Context final UriInfo uriInfo,
                                      ResolvedTileSpecCollection resolvedTiles) {

        LOG.info("saveResolvedTiles: entry, owner={}, project={}, stack={}, z={}",
                 owner, project, stack, z);

        if (resolvedTiles == null) {
            throw new IllegalServiceArgumentException("no resolved tiles provided");
        }

        if (z == null) {
            throw new IllegalServiceArgumentException("no z value provided");
        }

        try {
            final StackId stackId = new StackId(owner, project, stack);
            resolvedTiles.verifyAllTileSpecsHaveZValue(z);
            renderDao.saveResolvedTiles(stackId, resolvedTiles);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        final Response.ResponseBuilder responseBuilder = Response.created(uriInfo.getRequestUri());

        LOG.info("saveResolvedTiles: exit");

        return responseBuilder.build();
    }

    @Path("project/{project}/stack/{stack}/tile/{tileId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public TileSpec getTileSpec(@PathParam("owner") final String owner,
                                @PathParam("project") final String project,
                                @PathParam("stack") final String stack,
                                @PathParam("tileId") final String tileId) {

        LOG.info("getTileSpec: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        TileSpec tileSpec = null;
        try {
            tileSpec = getTileSpec(owner, project, stack, tileId, false);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return tileSpec;
    }

    @Path("project/{project}/stack/{stack}/tile/{tileId}/render-parameters")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RenderParameters getRenderParameters(@PathParam("owner") final String owner,
                                                @PathParam("project") final String project,
                                                @PathParam("stack") final String stack,
                                                @PathParam("tileId") final String tileId,
                                                @QueryParam("scale") Double scale,
                                                @QueryParam("filter") final Boolean filter) {

        LOG.info("getRenderParameters: entry, owner={}, project={}, stack={}, tileId={}, scale={}",
                 owner, project, stack, tileId, scale);

        RenderParameters parameters = null;
        try {
            final TileSpec tileSpec = getTileSpec(owner, project, stack, tileId, true);
            tileSpec.flattenTransforms();
            if (scale == null) {
                scale = 1.0;
            }

            final StackId stackId = new StackId(owner, project, stack);
            final StackMetaData stackMetaData = renderDao.getStackMetaData(stackId);

            final int margin = 0;
            final Double x = getLayoutMinValue(tileSpec.getMinX(), margin);
            final Double y = getLayoutMinValue(tileSpec.getMinY(), margin);
            final Integer width = getLayoutSizeValue(stackMetaData.getLayoutWidth(), tileSpec.getWidth(), margin);
            final Integer height = getLayoutSizeValue(stackMetaData.getLayoutHeight(), tileSpec.getHeight(), margin);

            parameters = new RenderParameters(null, x, y, width, height, scale);
            parameters.setDoFilter(filter);
            parameters.addTileSpec(tileSpec);

        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return parameters;
    }

    public TileSpec getTileSpec(final String owner,
                                final String project,
                                final String stack,
                                final String tileId,
                                final boolean resolveTransformReferences) {
        final StackId stackId = new StackId(owner, project, stack);
        return renderDao.getTileSpec(stackId, tileId, resolveTransformReferences);
    }

    @Path("project/{project}/stack/{stack}/tile/{tileId}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveTileSpec(@PathParam("owner") final String owner,
                                 @PathParam("project") final String project,
                                 @PathParam("stack") final String stack,
                                 @PathParam("tileId") final String tileId,
                                 @Context final UriInfo uriInfo,
                                 @QueryParam("meshCellSize") final Double meshCellSize,
                                 TileSpec tileSpec) {

        LOG.info("saveTileSpec: entry, owner={}, project={}, stack={}, tileId={}, meshCellSize={}",
                 owner, project, stack, tileId, meshCellSize);

        if (tileSpec == null) {
            throw new IllegalServiceArgumentException("no tile spec provided");
        } else if (! tileId.equals(tileSpec.getTileId())) {
            throw new IllegalServiceArgumentException("request tileId value (" + tileId +
                                                      ") does not match tile spec tileId value (" +
                                                      tileSpec.getTileId() + ")");
        }

        try {
            final StackId stackId = new StackId(owner, project, stack);

            // resolve all transform references and re-derive bounding box before saving ...
            tileSpec = renderDao.resolveTransformReferencesForTiles(stackId, tileSpec);
            tileSpec.deriveBoundingBox(
                    meshCellSize == null ? RenderParameters.DEFAULT_MESH_CELL_SIZE : meshCellSize,
                    true);

            renderDao.saveTileSpec(stackId, tileSpec);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        final Response.ResponseBuilder responseBuilder = Response.created(uriInfo.getRequestUri());

        return responseBuilder.build();
    }

    @Path("project/{project}/stack/{stack}/tile/{tileId}/transform/{transformIndex}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public TransformSpec getTransformSpecForTile(@PathParam("owner") final String owner,
                                                 @PathParam("project") final String project,
                                                 @PathParam("stack") final String stack,
                                                 @PathParam("tileId") final String tileId,
                                                 @PathParam("transformIndex") final Integer transformIndex) {

        LOG.info("getTransformSpecForTile: entry, owner={}, project={}, stack={}, tileId={}, transformIndex={}",
                 owner, project, stack, tileId, transformIndex);

        TransformSpec transformSpec = null;
        try {
            final TileSpec tileSpec = getTileSpec(owner, project, stack, tileId, false);
            transformSpec = tileSpec.getTransforms().getSpec(transformIndex);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return transformSpec;
    }

    @Path("project/{project}/stack/{stack}/tile/{tileId}/transform/{transformIndex}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveTransformSpecForTile(@PathParam("owner") final String owner,
                                             @PathParam("project") final String project,
                                             @PathParam("stack") final String stack,
                                             @PathParam("tileId") final String tileId,
                                             @PathParam("transformIndex") final Integer transformIndex,
                                             @Context final UriInfo uriInfo,
                                             @QueryParam("meshCellSize") final Double meshCellSize,
                                             final TransformSpec transformSpec) {

        LOG.info("saveTransformSpecForTile: entry, owner={}, project={}, stack={}, tileId={}, transformIndex={}, meshCellSize={}",
                 owner, project, stack, tileId, transformIndex, meshCellSize);

        if (transformSpec == null) {
            throw new IllegalServiceArgumentException("no transform spec provided");
        }

        if ((transformIndex == null) || (transformIndex < 0)) {
            throw new IllegalServiceArgumentException("non-negative transformIndex must be provided");
        }

        try {
            final StackId stackId = new StackId(owner, project, stack);
            TileSpec tileSpec = getTileSpec(owner, project, stack, tileId, false);
            tileSpec.setTransformSpec(transformIndex, transformSpec);

            // Resolve all transform references and re-derive bounding box before saving.
            // NOTE: resolution is different from flattening, so referential data remains intact
            tileSpec = renderDao.resolveTransformReferencesForTiles(stackId, tileSpec);
            tileSpec.deriveBoundingBox(
                    meshCellSize == null ? RenderParameters.DEFAULT_MESH_CELL_SIZE : meshCellSize,
                    true);

            renderDao.saveTileSpec(stackId, tileSpec);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        final Response.ResponseBuilder responseBuilder = Response.created(uriInfo.getRequestUri());

        return responseBuilder.build();
    }

    @Path("project/{project}/stack/{stack}/tile/{tileId}/transform-count")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public int getTransformCountForTile(@PathParam("owner") final String owner,
                                        @PathParam("project") final String project,
                                        @PathParam("stack") final String stack,
                                        @PathParam("tileId") final String tileId) {

        LOG.info("getTransformCountForTile: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        int count = 0;
        try {
            final TileSpec tileSpec = getTileSpec(owner, project, stack, tileId, false);
            count = tileSpec.numberOfTransforms();
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return count;
    }

    @Path("project/{project}/stack/{stack}/tile/{tileId}/last-transform")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public TransformSpec getLastTransformSpecForTile(@PathParam("owner") final String owner,
                                                     @PathParam("project") final String project,
                                                     @PathParam("stack") final String stack,
                                                     @PathParam("tileId") final String tileId) {

        LOG.info("getLastTransformSpecForTile: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        TransformSpec transformSpec = null;
        try {
            final TileSpec tileSpec = getTileSpec(owner, project, stack, tileId, false);
            final int lastTransformIndex = tileSpec.numberOfTransforms() - 1;
            if (lastTransformIndex >= 0) {
                transformSpec = tileSpec.getTransforms().getSpec(lastTransformIndex);
            }
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return transformSpec;
    }

    @Path("project/{project}/stack/{stack}/tile/{tileId}/last-transform")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveLastTransformSpecForTile(@PathParam("owner") final String owner,
                                                 @PathParam("project") final String project,
                                                 @PathParam("stack") final String stack,
                                                 @PathParam("tileId") final String tileId,
                                                 @Context final UriInfo uriInfo,
                                                 @QueryParam("meshCellSize") final Double meshCellSize,
                                                 final TransformSpec transformSpec) {

        LOG.info("saveLastTransformSpecForTile: entry, owner={}, project={}, stack={}, tileId={}, transformIndex={}, meshCellSize={}",
                 owner, project, stack, tileId, meshCellSize);

        if (transformSpec == null) {
            throw new IllegalServiceArgumentException("no transform spec provided");
        }

        try {
            final StackId stackId = new StackId(owner, project, stack);
            TileSpec tileSpec = getTileSpec(owner, project, stack, tileId, false);
            if (tileSpec.hasTransforms()) {
                final int lastTransformIndex = tileSpec.numberOfTransforms() - 1;
                tileSpec.setTransformSpec(lastTransformIndex, transformSpec);
            } else {
                tileSpec.setTransformSpec(0, transformSpec);
            }

            // Resolve all transform references and re-derive bounding box before saving.
            // NOTE: resolution is different from flattening, so referential data remains intact
            tileSpec = renderDao.resolveTransformReferencesForTiles(stackId, tileSpec);
            tileSpec.deriveBoundingBox(
                    meshCellSize == null ? RenderParameters.DEFAULT_MESH_CELL_SIZE : meshCellSize,
                    true);

            renderDao.saveTileSpec(stackId, tileSpec);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        final Response.ResponseBuilder responseBuilder = Response.created(uriInfo.getRequestUri());

        return responseBuilder.build();
    }

    @Path("project/{project}/stack/{stack}/tile/{tileId}/derivedFrom/{derivedFromStack}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveDerivedTile(@PathParam("owner") String owner,
                                    @PathParam("project") String project,
                                    @PathParam("stack") String stack,
                                    @PathParam("tileId") String tileId,
                                    @PathParam("derivedFromStack") String derivedFromStack,
                                    @QueryParam("replaceLastTransform") Boolean replaceLastTransform,
                                    @Context UriInfo uriInfo,
                                    @QueryParam("meshCellSize") final Double meshCellSize,
                                    List<TransformSpec> transformSpecs) {

        LOG.info("saveDerivedTile: entry, owner={}, project={}, stack={}, tileId={}, derivedFromStack={}, replaceLastTransform={}, meshCellSize={}",
                 owner, project, stack, tileId, derivedFromStack, replaceLastTransform, meshCellSize);

        if (transformSpecs == null) {
            throw new IllegalServiceArgumentException("array of transform specs must be provided");
        }

        if (derivedFromStack == null) {
            throw new IllegalServiceArgumentException("derived from stack must be provided");
        }

        try {
            final StackId stackId = new StackId(owner, project, stack);
            final StackId derivedFromStackId = new StackId(owner, project, derivedFromStack);

            TileSpec tileSpec = getTileSpec(owner, project, derivedFromStackId.getStack(), tileId, false);
            if ((replaceLastTransform != null) && replaceLastTransform) {
                tileSpec.removeLastTransformSpec();
            }

            tileSpec.addTransformSpecs(transformSpecs);

            // Resolve all transform references and re-derive bounding box before saving.
            // NOTE: resolution is different from flattening, so referential data remains intact
            tileSpec = renderDao.resolveTransformReferencesForTiles(stackId, tileSpec);
            tileSpec.deriveBoundingBox(
                    meshCellSize == null ? RenderParameters.DEFAULT_MESH_CELL_SIZE : meshCellSize,
                    true);

            renderDao.saveTileSpec(stackId, tileSpec);
        } catch (Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        final URI requestUri = uriInfo.getRequestUri();
        final String requestPath = requestUri.getPath();
        final int stop = requestPath.indexOf("/derivedFrom/");
        final String tilePath = requestPath.substring(0, stop);
        final UriBuilder uriBuilder = UriBuilder.fromUri(requestUri);
        uriBuilder.replacePath(tilePath);
        final URI tileUri = uriBuilder.build();
        final Response.ResponseBuilder responseBuilder = Response.created(tileUri);

        return responseBuilder.build();
    }

    @Path("project/{project}/stack/{stack}/transform/{transformId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public TransformSpec getTransformSpec(@PathParam("owner") final String owner,
                                          @PathParam("project") final String project,
                                          @PathParam("stack") final String stack,
                                          @PathParam("transformId") final String transformId) {

        LOG.info("getTransformSpec: entry, owner={}, project={}, stack={}, transformId={}",
                 owner, project, stack, transformId);

        TransformSpec transformSpec = null;
        try {
            final StackId stackId = new StackId(owner, project, stack);
            transformSpec = renderDao.getTransformSpec(stackId, transformId);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        return transformSpec;
    }

    @Path("project/{project}/stack/{stack}/transform/{transformId}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveTransformSpec(@PathParam("owner") final String owner,
                                      @PathParam("project") final String project,
                                      @PathParam("stack") final String stack,
                                      @PathParam("transformId") final String transformId,
                                      @Context final UriInfo uriInfo,
                                      final TransformSpec transformSpec) {

        LOG.info("saveTransformSpec: entry, owner={}, project={}, stack={}, transformId={}",
                 owner, project, stack, transformId);

        if (transformSpec == null) {
            throw new IllegalServiceArgumentException("no transform spec provided");
        } else if (! transformId.equals(transformSpec.getId())) {
            throw new IllegalServiceArgumentException("request transformId value (" + transformId +
                                                      ") does not match transform spec id value (" +
                                                      transformSpec.getId() + ")");
        }

        try {
            final StackId stackId = new StackId(owner, project, stack);
            renderDao.saveTransformSpec(stackId, transformSpec);

            // TODO: re-derive bounding boxes for all tiles that reference this transform

        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }

        final Response.ResponseBuilder responseBuilder = Response.created(uriInfo.getRequestUri());

        return responseBuilder.build();
    }

    /**
     * @return number of tiles within the specified bounding box.
     */
    @Path("project/{project}/stack/{stack}/z/{z}/box/{x},{y},{width},{height}/tile-count")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Integer getTileCount(@PathParam("owner") final String owner,
                                @PathParam("project") final String project,
                                @PathParam("stack") final String stack,
                                @PathParam("x") final Double x,
                                @PathParam("y") final Double y,
                                @PathParam("z") final Double z,
                                @PathParam("width") final Integer width,
                                @PathParam("height") final Integer height) {

        LOG.info("getTileCount: entry, owner={}, project={}, stack={}, x={}, y={}, z={}, width={}, height={}",
                 owner, project, stack, x, y, z, width, height);

        int count = 0;
        try {
            final StackId stackId = new StackId(owner, project, stack);
            count = renderDao.getTileCount(stackId, x, y, z, width, height);
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }
        return count;
    }

    /**
     * @return render parameters for specified bounding box with flattened (and therefore resolved)
     *         transform specs suitable for external use.
     */
    @Path("project/{project}/stack/{stack}/z/{z}/box/{x},{y},{width},{height},{scale}/render-parameters")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RenderParameters getExternalRenderParameters(@PathParam("owner") final String owner,
                                                        @PathParam("project") final String project,
                                                        @PathParam("stack") final String stack,
                                                        @PathParam("x") final Double x,
                                                        @PathParam("y") final Double y,
                                                        @PathParam("z") final Double z,
                                                        @PathParam("width") final Integer width,
                                                        @PathParam("height") final Integer height,
                                                        @PathParam("scale") final Double scale) {

        LOG.info("getExternalRenderParameters: entry, owner={}, project={}, stack={}, x={}, y={}, z={}, width={}, height={}, scale={}",
                 owner, project, stack, x, y, z, width, height, scale);

        RenderParameters parameters = null;
        try {
            final StackId stackId = new StackId(owner, project, stack);
            parameters = getInternalRenderParameters(stackId, x, y, z, width, height, scale);
            parameters.flattenTransforms();
        } catch (final Throwable t) {
            RenderServiceUtil.throwServiceException(t);
        }
        return parameters;
    }

    /**
     * @return render parameters for specified bounding box with in-memory resolved
     *         transform specs suitable for internal use.
     */
    public RenderParameters getInternalRenderParameters(final StackId stackId,
                                                        final Double x,
                                                        final Double y,
                                                        final Double z,
                                                        final Integer width,
                                                        final Integer height,
                                                        final Double scale) {

        return renderDao.getParameters(stackId, x, y, z, width, height, scale);
    }

    private Double getLayoutMinValue(final Double minValue,
                                     final int margin) {
        Double layoutValue = null;
        if (minValue != null) {
            layoutValue = minValue - margin;
        }
        return layoutValue;
    }

    private Integer getLayoutSizeValue(final Integer stackValue,
                                       final Integer tileValue,
                                       final int margin) {
        Integer layoutValue = null;

        if (stackValue != null) {
            layoutValue = stackValue + margin;
        } else if ((tileValue != null) && (tileValue >= 0)) {
            layoutValue = tileValue + margin;
        }

        if ((layoutValue != null) && ((layoutValue % 2) != 0)) {
            layoutValue = layoutValue + 1;
        }

        return layoutValue;
    }

    private static final Logger LOG = LoggerFactory.getLogger(RenderDataService.class);
}