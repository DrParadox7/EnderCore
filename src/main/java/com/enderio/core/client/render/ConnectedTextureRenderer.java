package com.enderio.core.client.render;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import com.enderio.core.api.client.render.IConnectedTextureRenderer;
import com.enderio.core.api.client.render.IRenderFace;
import com.enderio.core.common.util.BlockCoord;
import com.enderio.core.common.util.ForgeDirectionOffsets;
import com.enderio.core.common.vecmath.Vector2f;
import com.enderio.core.common.vecmath.Vector3d;
import com.enderio.core.common.vecmath.Vector4f;
import com.enderio.core.common.vecmath.Vertex;

public class ConnectedTextureRenderer implements IRenderFace, IConnectedTextureRenderer {

    public static interface TextureCallback {

        public IIcon getTextureForFace(ForgeDirection dir);
    }

    public static class DefaultTextureCallback implements TextureCallback {

        private final IIcon tex;

        private final Block block;
        private final int meta;

        public DefaultTextureCallback(IIcon borderTex) {
            this.tex = borderTex;
            block = null;
            meta = 0;
        }

        public DefaultTextureCallback(Block block, int meta) {
            tex = null;
            this.block = block;
            this.meta = meta;
        }

        @Override
        public IIcon getTextureForFace(ForgeDirection dir) {
            if (tex != null) {
                return tex;
            }
            if (block != null) {
                return block.getIcon(dir.ordinal(), meta);
            }
            return null;
        }

    }

    private boolean forceAllEdges = false;

    private TextureCallback edgeTexureCallback;

    private boolean matchMetaData;

    private EnumSet<ForgeDirection> sidesToRender = EnumSet.allOf(ForgeDirection.class);

    public boolean isForceAllEdges() {
        return forceAllEdges;
    }

    public void setForceAllEdges(boolean forceAllEdges) {
        this.forceAllEdges = forceAllEdges;
    }

    public TextureCallback getEdgeTexureCallback() {
        return edgeTexureCallback;
    }

    public void setEdgeTexureCallback(TextureCallback edgeTexureCallback) {
        this.edgeTexureCallback = edgeTexureCallback;
    }

    public void setEdgeTexture(IIcon texture) {
        if (texture == null) {
            edgeTexureCallback = null;
            return;
        }
        edgeTexureCallback = new DefaultTextureCallback(texture);
    }

    public void setMatchMeta(boolean matchMetaData) {
        this.matchMetaData = matchMetaData;
    }

    public void setSidesToRender(EnumSet<ForgeDirection> sides) {
        sidesToRender = sides.clone();
    }

    @Override
    public boolean matchesMetadata(int meta1, int meta2) {
        return !this.matchMetaData || meta1 == meta2;
    }

    @Override
    public void renderFace(CustomRenderBlocks rb, ForgeDirection face, Block par1Block, double x, double y, double z,
            IIcon texture, List<Vertex> refVertices, boolean translateToXYZ) {

        if (!sidesToRender.contains(face)) {
            return;
        }

        List<Vertex> finalVerts = new ArrayList<Vertex>();
        finalVerts.addAll(refVertices);

        IIcon borderTex = edgeTexureCallback == null ? null : edgeTexureCallback.getTextureForFace(face);
        if (borderTex != null) {

            texture = borderTex;
            List<ForgeDirection> edges;
            if (forceAllEdges) {
                edges = RenderUtil.getEdgesForFace(face);
            } else {
                edges = RenderUtil.getNonConectedEdgesForFace(rb.blockAccess, (int) x, (int) y, (int) z, face, this);
            }

            List<ForgeDirection> allEdges = RenderUtil.getEdgesForFace(face);

            float scaleFactor = 15f / 16f;
            Vector2f uv = new Vector2f();

            // for each that needs a border, add a geom for the border and move in the 'centre' of the face
            // so there is no overlap
            for (ForgeDirection edge : edges) {

                // We need to move the 'centre' of the face so it doesn't overlap with the border
                moveCorners(refVertices, edge, 1 - scaleFactor);

                // Now create the geometry for this edge of the border
                float xLen = 1 - Math.abs(edge.offsetX) * scaleFactor;
                float yLen = 1 - Math.abs(edge.offsetY) * scaleFactor;
                float zLen = 1 - Math.abs(edge.offsetZ) * scaleFactor;

                xLen -= 2 * (1 - par1Block.getBlockBoundsMaxX()) - par1Block.getBlockBoundsMinX();
                yLen -= 2 * (1 - par1Block.getBlockBoundsMaxY()) - par1Block.getBlockBoundsMinY();
                zLen -= 2 * (1 - par1Block.getBlockBoundsMaxZ()) - par1Block.getBlockBoundsMinZ();

                BoundingBox bb = BoundingBox.UNIT_CUBE.scale(xLen, yLen, zLen);

                List<Vector3d> corners = bb.getCornersForFaceD(face);
                for (Vector3d corner : corners) {
                    if (translateToXYZ) {
                        corner.x += x;
                        corner.y += y;
                        corner.z += z;
                    }
                    corner.x += (float) (edge.offsetX * 0.5) - Math.signum(edge.offsetX) * xLen / 2f;
                    corner.y += (float) (edge.offsetY * 0.5) - Math.signum(edge.offsetY) * yLen / 2f;
                    corner.z += (float) (edge.offsetZ * 0.5) - Math.signum(edge.offsetZ) * zLen / 2f;
                }

                int[] indices = new int[] { 0, 1, 2, 3 };
                for (int index : indices) {
                    Vector3d corner = corners.get(index);
                    if (translateToXYZ) {
                        RenderUtil.getUvForCorner(uv, corner, (int) x, (int) y, (int) z, face, texture);
                    } else {
                        RenderUtil.getUvForCorner(uv, corner, 0, 0, 0, face, texture);
                    }
                    Vertex vertex = new Vertex();
                    vertex.xyz.set(corner);
                    // vertex.xyz.add(ForgeDirectionOffsets.offsetScaled(face, 0.00005));
                    vertex.uv = new Vector2f(uv);
                    applyLighting(vertex, corner, refVertices);
                    finalVerts.add(vertex);
                }
            }

            for (int i = 0; i < allEdges.size(); i++) {
                ForgeDirection dir = allEdges.get(i);
                ForgeDirection dir2 = i + 1 < allEdges.size() ? allEdges.get(i + 1) : allEdges.get(0);
                if (needsCorner(dir, dir2, edges, face, par1Block, x, y, z, rb.blockAccess)) {

                    Vertex v = new Vertex();
                    v.uv = new Vector2f();
                    v.xyz.set(ForgeDirectionOffsets.forDir(dir));
                    v.xyz.add(ForgeDirectionOffsets.forDir(dir2));
                    // TODO: dodgy hack to just move the corner forward a bit
                    v.xyz.add(ForgeDirectionOffsets.offsetScaled(face, 0.001f));
                    v.xyz.x = Math.max(-0.001, v.xyz.x);
                    v.xyz.y = Math.max(-0.001, v.xyz.y);
                    v.xyz.z = Math.max(-0.001, v.xyz.z);

                    v.xyz.x -= (1 - par1Block.getBlockBoundsMaxX()) - par1Block.getBlockBoundsMinX();
                    v.xyz.y -= (1 - par1Block.getBlockBoundsMaxY()) - par1Block.getBlockBoundsMinY();
                    v.xyz.z -= (1 - par1Block.getBlockBoundsMaxZ()) - par1Block.getBlockBoundsMinZ();

                    if (ForgeDirectionOffsets.isPositiveOffset(face)) {
                        v.xyz.add(ForgeDirectionOffsets.forDir(face));
                    }

                    if (translateToXYZ) {
                        v.xyz.x += x;
                        v.xyz.y += y;
                        v.xyz.z += z;
                        RenderUtil.getUvForCorner(v.uv, v.xyz, (int) x, (int) y, (int) z, face, texture);
                    } else {
                        RenderUtil.getUvForCorner(v.uv, v.xyz, 0, 0, 0, face, texture);
                    }
                    applyLighting(v, v.xyz, refVertices);

                    finalVerts.add(v);

                    Vector3d corner = new Vector3d(v.xyz);
                    if (ForgeDirectionOffsets.isPositiveOffset(face)) {
                        addVertexForCorner(face, x, y, z, texture, translateToXYZ, finalVerts, dir2, null, corner);
                        addVertexForCorner(face, x, y, z, texture, translateToXYZ, finalVerts, dir, dir2, corner);
                        addVertexForCorner(face, x, y, z, texture, translateToXYZ, finalVerts, dir, null, corner);
                    } else {
                        addVertexForCorner(face, x, y, z, texture, translateToXYZ, finalVerts, dir, null, corner);
                        addVertexForCorner(face, x, y, z, texture, translateToXYZ, finalVerts, dir, dir2, corner);
                        addVertexForCorner(face, x, y, z, texture, translateToXYZ, finalVerts, dir2, null, corner);
                    }
                }
            }
        }

        RenderUtil.addVerticesToTessellator(finalVerts, Tessellator.instance);
    }

    private Vertex getClosestVertex(List<Vertex> vertices, Vector3d corner) {
        Vertex result = null;
        double d2 = Double.MAX_VALUE;
        for (Vertex v : vertices) {
            double tmp = corner.distanceSquared(v.xyz);
            if (tmp <= d2) {
                result = v;
                d2 = tmp;
            }
        }
        return result;
    }

    private void addVertexForCorner(ForgeDirection face, double x, double y, double z, IIcon texture,
            boolean translateToXYZ, List<Vertex> vertices, ForgeDirection dir, ForgeDirection dir2, Vector3d corner) {
        float scale = 1 / 16f;
        Vertex vert = new Vertex();
        vert.uv = new Vector2f();
        vert.xyz.set(corner);
        vert.xyz.sub(ForgeDirectionOffsets.offsetScaled(dir, scale));
        if (dir2 != null) {
            vert.xyz.sub(ForgeDirectionOffsets.offsetScaled(dir2, scale));
        }
        if (translateToXYZ) {
            RenderUtil.getUvForCorner(vert.uv, vert.xyz, (int) x, (int) y, (int) z, face, texture);
        } else {
            RenderUtil.getUvForCorner(vert.uv, vert.xyz, 0, 0, 0, face, texture);
        }
        applyLighting(vert, corner, vertices);
        vertices.add(vert);
    }

    private void applyLighting(Vertex vert, Vector3d samplePoint, List<Vertex> litVertices) {
        Vertex closest = getClosestVertex(litVertices, samplePoint);
        if (closest == null) {
            return;
        }
        vert.setBrightness(closest.brightness);
        Vector4f col = closest.getColor();
        if (col != null) {
            vert.setColor(col);
        }
    }

    private boolean needsCorner(ForgeDirection dir, ForgeDirection dir2, List<ForgeDirection> edges,
            ForgeDirection face, Block par1Block, double x, double y, double z, IBlockAccess blockAccess) {

        if (edges.contains(dir) || edges.contains(dir2)) {
            return false;
        }

        BlockCoord bc = new BlockCoord((int) x, (int) y, (int) z);
        BlockCoord testLoc = bc.getLocation(dir);
        if (RenderUtil.getNonConectedEdgesForFace(blockAccess, testLoc.x, testLoc.y, testLoc.z, face, this)
                .contains(dir2)) {
            return true;
        }
        testLoc = bc.getLocation(dir2);
        if (RenderUtil.getNonConectedEdgesForFace(blockAccess, testLoc.x, testLoc.y, testLoc.z, face, this)
                .contains(dir)) {
            return true;
        }
        return false;
    }

    private void moveCorners(List<Vertex> vertices, ForgeDirection edge, float scaleFactor) {
        int[] indices = getClosest(edge, vertices);
        if (indices[0] < 0) {
            return;
        }
        vertices.get(indices[0]).xyz.x -= scaleFactor * edge.offsetX;
        vertices.get(indices[1]).xyz.x -= scaleFactor * edge.offsetX;
        vertices.get(indices[0]).xyz.y -= scaleFactor * edge.offsetY;
        vertices.get(indices[1]).xyz.y -= scaleFactor * edge.offsetY;
        vertices.get(indices[0]).xyz.z -= scaleFactor * edge.offsetZ;
        vertices.get(indices[1]).xyz.z -= scaleFactor * edge.offsetZ;
    }

    private int[] getClosest(ForgeDirection edge, List<Vertex> vertices) {
        int[] res = new int[] { -1, -1 };
        boolean highest = edge.offsetX > 0 || edge.offsetY > 0 || edge.offsetZ > 0;
        double minMax = highest ? -Double.MAX_VALUE : Double.MAX_VALUE;
        int index = 0;
        for (Vertex v : vertices) {
            double val = get(v.xyz, edge);
            if (highest ? val >= minMax : val <= minMax) {
                if (val != minMax) {
                    res[0] = index;
                } else {
                    res[1] = index;
                }
                minMax = val;
            }
            index++;
        }
        return res;
    }

    private double get(Vector3d xyz, ForgeDirection edge) {
        if (edge == ForgeDirection.EAST || edge == ForgeDirection.WEST) {
            return xyz.x;
        }
        if (edge == ForgeDirection.UP || edge == ForgeDirection.DOWN) {
            return xyz.y;
        }
        return xyz.z;
    }

}
