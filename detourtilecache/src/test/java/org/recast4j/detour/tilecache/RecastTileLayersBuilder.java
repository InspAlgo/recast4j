package org.recast4j.detour.tilecache;

import static org.recast4j.detour.DetourCommon.vCopy;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.recast4j.recast.HeightfieldLayerSet;
import org.recast4j.recast.HeightfieldLayerSet.HeightfieldLayer;
import org.recast4j.recast.InputGeom;
import org.recast4j.recast.Recast;
import org.recast4j.recast.RecastBuilder;
import org.recast4j.recast.RecastConfig;
import org.recast4j.recast.RecastConstants.PartitionType;

public class RecastTileLayersBuilder {

	private final InputGeom geom;
	private final TileCache tc;
	private final float m_agentMaxSlope = 45.0f;
	private final int m_regionMinSize = 8;
	private final int m_regionMergeSize = 20;
	private final float m_edgeMaxLen = 12.0f;
	private final float m_edgeMaxError = 1.3f;
	private final int m_vertsPerPoly = 6;
	private final float m_detailSampleDist = 6.0f;
	private final float m_detailSampleMaxError = 1.0f;

	public RecastTileLayersBuilder(InputGeom geom, TileCache tc) {
		this.geom = geom;
		this.tc = tc;
	}

	public List<byte[]> build(ByteOrder order, boolean cCompatibility) {
		List<byte[]> layers = new ArrayList<>();
		float[] bmin = geom.getMeshBoundsMin();
		float[] bmax = geom.getMeshBoundsMax();
		int[] twh = Recast.calcTileCount(bmin, bmax, tc.getParams().cs, tc.getParams().width);
		int tw = twh[0];
		int th = twh[1];
		for (int y = 0; y < th; ++y) {
			for (int x = 0; x < tw; ++x) {
				layers.addAll(build(x, y, order, cCompatibility));
			}
		}
		return layers;
	}

	public List<byte[]> build(int tx, int ty, ByteOrder order, boolean cCompatibility) {
		RecastBuilder rcBuilder = new RecastBuilder();
		float[] bmin = geom.getMeshBoundsMin();
		float[] bmax = geom.getMeshBoundsMax();
		TileCacheParams params = tc.getParams();
		RecastConfig rcConfig = new RecastConfig(PartitionType.WATERSHED, params.cs, params.ch, 
				params.walkableHeight, params.walkableRadius, params.walkableClimb,
				m_agentMaxSlope, m_regionMinSize, 
				m_regionMergeSize, m_edgeMaxLen,
				m_edgeMaxError, m_vertsPerPoly, m_detailSampleDist, 
				m_detailSampleMaxError, bmin, bmax, params.width, tx,
				ty);

		HeightfieldLayerSet lset = rcBuilder.buildLayers(geom, rcConfig);
		List<byte[]> result = new ArrayList<>();
		if (lset != null) {
			TileCacheBuilder builder = new TileCacheBuilder();
			for (int i = 0; i < lset.layers.length; ++i) {
				HeightfieldLayer layer = lset.layers[i];

				// Store header
				TileCacheLayerHeader header = new TileCacheLayerHeader();
				header.magic = TileCacheLayerHeader.DT_TILECACHE_MAGIC;
				header.version = TileCacheLayerHeader.DT_TILECACHE_VERSION;

				// Tile layer location in the navmesh.
				header.tx = tx;
				header.ty = ty;
				header.tlayer = i;
				vCopy(header.bmin, layer.bmin);
				vCopy(header.bmax, layer.bmax);

				// Tile info.
				header.width = layer.width;
				header.height = layer.height;
				header.minx = layer.minx;
				header.maxx = layer.maxx;
				header.miny = layer.miny;
				header.maxy = layer.maxy;
				header.hmin = layer.hmin;
				header.hmax = layer.hmax;
				result.add(builder.buildTileCacheLayer(tc.getCompressor(), header, layer.heights, layer.areas, layer.cons,
						order, cCompatibility));
			}
		}
		return result;
	}
}
