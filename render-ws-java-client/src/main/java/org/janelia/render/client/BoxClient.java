package org.janelia.render.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.janelia.alignment.Render;
import org.janelia.alignment.RenderParameters;
import org.janelia.alignment.mipmap.BoxMipmapGenerator;
import org.janelia.alignment.spec.Bounds;
import org.janelia.alignment.spec.TileBounds;
import org.janelia.alignment.spec.TileSpec;
import org.janelia.alignment.util.ImageProcessorCache;
import org.janelia.alignment.util.LabelImageProcessorCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java client for rendering uniform (but arbitrarily sized) boxes (derived tiles) to disk for one or more layers.
 * A {@link Render} instance is first used to produce the full scale (level 0) boxes.
 * {@link BoxMipmapGenerator} instances are then used to produce any requested down-sampled mipmaps.
 *
 * All generated images have the same dimensions and pixel count and are stored within a
 * CATMAID LargeDataTileSource directory structure that looks like this:
 * <pre>
 *         [root directory]/[tile width]x[tile height]/[level]/[row]/[col].[format]
 * </pre>
 *
 * Details about the CATMAID LargeDataTileSource can be found at
 * <a href="https://github.com/acardona/CATMAID/blob/master/django/applications/catmaid/static/js/tilesource.js">
 *     https://github.com/acardona/CATMAID/blob/master/django/applications/catmaid/static/js/tilesource.js
 * </a>).
 *
 * @author Eric Trautman
 */
public class BoxClient {

    /**
     * @param  args  see {@link BoxClientParameters} for command line argument details.
     */
    public static void main(String[] args) {
        try {

            final BoxClientParameters params = BoxClientParameters.parseCommandLineArgs(args);

            if (params.displayHelp()) {

                params.showUsage();

            } else {

                LOG.info("main: entry, params={}", params);

                final BoxClient client = new BoxClient(params);

                for (Double z : params.getzValues()) {
                    client.generateBoxesForZ(z);
                }

            }

        } catch (final Throwable t) {
            LOG.error("main: caught exception", t);
        }
    }

    private final BoxClientParameters params;

    private final String stack;
    private final String format;
    private final int boxWidth;
    private final int boxHeight;
    private final File boxDirectory;
    private final RenderDataClient renderDataClient;

    public BoxClient(final BoxClientParameters params) {

        this.params = params;
        this.stack = params.getStack();
        this.format = params.getFormat();
        this.boxWidth = params.getWidth();
        this.boxHeight = params.getHeight();

        String boxName = this.boxWidth + "x" + this.boxHeight;
        if (params.isLabel()) {
            boxName += "-label";
        }

        final Path boxPath = Paths.get(params.getRootDirectory(),
                                       params.getProject(),
                                       params.getStack(),
                                       boxName).toAbsolutePath();

        this.boxDirectory = boxPath.toFile();

        final File stackDirectory = this.boxDirectory.getParentFile();

        if (! stackDirectory.exists()) {
            throw new IllegalArgumentException("missing stack directory " + stackDirectory);
        }

        if (! stackDirectory.canWrite()) {
            throw new IllegalArgumentException("not allowed to write to stack directory " + stackDirectory);
        }

        this.renderDataClient = new RenderDataClient(params.getBaseDataUrl(), params.getOwner(), params.getProject());
    }

    public void generateBoxesForZ(final Double z)
            throws Exception {

        LOG.info("generateBoxesForZ: {}, entry, boxDirectory={}, dataClient={}",
                 z, boxDirectory, renderDataClient);

        final Bounds layerBounds = renderDataClient.getLayerBounds(stack, z);
        final BoxBounds boxBounds = new BoxBounds(z, layerBounds);
        final List<TileBounds> tileBoundsList = renderDataClient.getTileBounds(stack, z);
        final int tileCount = tileBoundsList.size();
        final Progress progress = new Progress(tileCount);

        final TileBounds firstTileBounds = tileBoundsList.get(0);
        final TileSpec firstTileSpec = renderDataClient.getTile(stack, firstTileBounds.getTileId());

        LOG.info("generateBoxesForZ: {}, layerBounds={}, boxBounds={}, tileCount={}",
                 z, layerBounds, boxBounds, tileCount);

        final ImageProcessorCache imageProcessorCache;
        final Integer backgroundRGBColor;
        if (params.isLabel()) {
            imageProcessorCache = new LabelImageProcessorCache(ImageProcessorCache.DEFAULT_MAX_CACHED_PIXELS,
                                                               true,
                                                               false,
                                                               firstTileSpec.getWidth(),
                                                               firstTileSpec.getHeight(),
                                                               tileCount);
            backgroundRGBColor = Color.WHITE.getRGB();
        } else {
            imageProcessorCache = new ImageProcessorCache();
            backgroundRGBColor = null;
        }

        BoxMipmapGenerator boxMipmapGenerator = new BoxMipmapGenerator(z.intValue(),
                                                              format,
                                                              boxWidth,
                                                              boxHeight,
                                                              boxDirectory,
                                                              0,
                                                              boxBounds.lastRow,
                                                              boxBounds.lastColumn);

        if (params.isCreateEmptyBox()) {
            final BufferedImage emptyImage = new BufferedImage(boxWidth, boxHeight, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D targetGraphics = emptyImage.createGraphics();

            if (backgroundRGBColor != null) {
                targetGraphics.setBackground(new Color(backgroundRGBColor));
                targetGraphics.clearRect(0, 0, boxWidth, boxHeight);
            }
        }

        RenderParameters renderParameters;
        String parametersUrl;
        BufferedImage levelZeroImage;
        File levelZeroFile;
        int row = boxBounds.firstRow;
        int column;
        for (int y = boxBounds.firstY; y < layerBounds.getMaxY(); y += boxHeight) {

            column = boxBounds.firstColumn;

            for (int x = boxBounds.firstX; x < layerBounds.getMaxX(); x += boxWidth) {

                parametersUrl = renderDataClient.getRenderParametersUrlString(stack, x, y, z, boxWidth, boxHeight, 1.0);
                renderParameters = RenderParameters.loadFromUrl(parametersUrl);
                renderParameters.setSkipInterpolation(params.isSkipInterpolation());
                renderParameters.setBackgroundRGBColor(backgroundRGBColor);

                if (renderParameters.hasTileSpecs()) {

                    levelZeroImage = renderParameters.openTargetImage();

                    Render.render(renderParameters, levelZeroImage, imageProcessorCache);

                    levelZeroFile = BoxMipmapGenerator.saveImage(levelZeroImage,
                                                                 format,
                                                                 boxDirectory,
                                                                 0,
                                                                 boxBounds.z,
                                                                 row,
                                                                 column);

                    boxMipmapGenerator.addSource(row, column, levelZeroFile);

                    progress.markProcessedTilesForRow(y, renderParameters);

                } else {
                    LOG.info("generateBoxesForZ: {}, skipping empty box for row {}, column {})", z, row, column);
                }

                column++;
            }

            LOG.info("generateBoxesForZ: {}, completed row {} of {}, {}, {}",
                     z, row, boxBounds.lastRow, progress, imageProcessorCache.getStats());

            row++;
        }

        File overviewFile = null;
        for (int level = 0; level < params.getMaxLevel(); level++) {
            boxMipmapGenerator = boxMipmapGenerator.generateNextLevel();
            if (params.isOverviewNeeded() && (overviewFile == null)) {
                overviewFile = boxMipmapGenerator.generateOverview(params.getOverviewWidth(), layerBounds);
            }
        }

        LOG.info("generateBoxesForZ: {}, exit", z);
    }

    /**
     * Simple container for a layer's derived box bounds.
     */
    private class BoxBounds {

        public final int z;

        public final int firstColumn;
        public final int firstX;
        public final int firstRow;
        public final int firstY;

        public final int lastColumn;
        public final int lastX;
        public final int lastRow;
        public final int lastY;

        public BoxBounds(final Double z,
                         final Bounds layerBounds) {

            this.z = z.intValue();

            this.firstColumn = (int) (layerBounds.getMinX() / boxWidth);
            this.firstX = firstColumn * boxWidth;
            this.firstRow = (int) (layerBounds.getMinY() / boxHeight);
            this.firstY = firstRow * boxHeight;

            this.lastColumn = (int) (layerBounds.getMaxX() / boxWidth);
            this.lastX = (lastColumn + 1) * boxWidth - 1;
            this.lastRow = (int) (layerBounds.getMaxY() / boxHeight);
            this.lastY = (lastRow + 1) * boxHeight - 1;
        }

        @Override
        public String toString() {
            return " columns " + firstColumn + " to " + lastColumn + " (x: " + firstX + " to " + lastX +
                   "), rows " + firstRow + " to " + lastRow + " (y: " + firstY + " to " + lastY + ")";
        }
    }

    /**
     * Utility to support logging of progress during long running layer render process.
     */
    private class Progress {

        private int numberOfLayerTiles;
        private Set<String> processedTileIds;
        private long startTime;
        private SimpleDateFormat sdf;
        private int currentRowY;
        private double currentRowPartialTileSum;

        public Progress(final int numberOfLayerTiles) {
            this.numberOfLayerTiles = numberOfLayerTiles;
            this.processedTileIds = new HashSet<String>(numberOfLayerTiles * 2);
            this.startTime = System.currentTimeMillis();
            this.sdf = new SimpleDateFormat("HH:mm:ss");
            this.currentRowY = -1;
            this.currentRowPartialTileSum = 0;
        }

        public void markProcessedTilesForRow(final int rowY,
                                             final RenderParameters renderParameters) {
            final int rowMaxY = rowY + boxHeight;
            if (rowY > currentRowY) {
                currentRowY = rowY;
                currentRowPartialTileSum = 0;
            }

            for (TileSpec tileSpec : renderParameters.getTileSpecs()) {
                // only add tiles that are completely above the row's max Y value
                if (tileSpec.getMaxY() <= rowMaxY) {
                    processedTileIds.add(tileSpec.getTileId());
                } else {
                    currentRowPartialTileSum += (rowMaxY - tileSpec.getMinY()) / (double) tileSpec.getHeight();
                }
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            final double numberOfProcessedTiles = processedTileIds.size() + currentRowPartialTileSum;
            final int percentComplete = (int) (numberOfProcessedTiles * 100 / numberOfLayerTiles);
            sb.append(percentComplete).append("% source tiles processed");
            if (numberOfProcessedTiles > 0) {
                final double msPerTile = (System.currentTimeMillis() - startTime) / numberOfProcessedTiles;
                final long remainingMs = (long) (msPerTile * (numberOfLayerTiles - numberOfProcessedTiles));
                final Date eta = new Date((new Date().getTime()) + remainingMs);
                sb.append(", ETA is ").append(sdf.format(eta));
            }
            return sb.toString();
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(BoxClient.class);
}