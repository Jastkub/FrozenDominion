package com.jastkub.frozenfortress.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An arbitrary triangle mesh, loaded from a Wavefront OBJ and drawn directly.
 *
 * <p>WHY THIS EXISTS. GeckoLib speaks bedrock {@code .geo.json}, and that
 * format can describe exactly one thing: an axis-aligned box with an optional
 * rotation. It has no concept of a triangle or of a free vertex, so a chamfer,
 * an undercut and a bevelled recess cannot be expressed in it at all - only
 * approximated by stacking more boxes, which is why the mask kept coming out
 * as a staircase however many cubes went into it.
 *
 * <p>Minecraft itself has no such limit. Its renderer is ordinary OpenGL and
 * {@link VertexConsumer} takes any triangle you hand it. So for the pieces
 * that are RIGID - a helm does not bend - we can skip the model format
 * entirely: load a real mesh, and draw it inside the transform of whichever
 * GeckoLib bone it belongs to. The bone still drives it, so head tracking and
 * the third-phase fall keep working with no extra machinery.
 *
 * <p>Deliberately minimal: positions, texture coordinates, normals, and
 * triangles. No materials, no groups, no smoothing directives - the mesh is
 * one object with one texture, which is what a mask is.
 */
public final class ObjMesh {

    /** Nine floats per vertex is not worth an object; this is flat and final. */
    private final float[] pos;      // 3 per vertex, 9 per triangle
    private final float[] uv;       // 2 per vertex
    private final float[] normal;   // 3 per vertex
    private final int triangles;

    private ObjMesh(float[] pos, float[] uv, float[] normal, int triangles) {
        this.pos = pos;
        this.uv = uv;
        this.normal = normal;
        this.triangles = triangles;
    }

    public int triangleCount() {
        return triangles;
    }

    /**
     * Reads an OBJ out of the resource pack.
     *
     * <p>Returns empty rather than throwing when the file is absent: a missing
     * mesh should degrade to "the cuboid model is still there", not to a
     * crash on world join.
     */
    public static Optional<ObjMesh> load(ResourceLocation where) {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(where);
        if (res.isEmpty()) {
            return Optional.empty();
        }
        List<float[]> v = new ArrayList<>();
        List<float[]> vt = new ArrayList<>();
        List<float[]> vn = new ArrayList<>();
        List<int[]> faces = new ArrayList<>();   // v/vt/vn per corner, triangulated

        try (BufferedReader in = res.get().openAsReader()) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] p = line.split("\\s+");
                switch (p[0]) {
                    case "v" -> v.add(new float[]{f(p, 1), f(p, 2), f(p, 3)});
                    case "vt" -> vt.add(new float[]{f(p, 1), f(p, 2)});
                    case "vn" -> vn.add(new float[]{f(p, 1), f(p, 2), f(p, 3)});
                    case "f" -> {
                        // fan-triangulate, so quads and n-gons both work
                        int[][] corner = new int[p.length - 1][];
                        for (int i = 1; i < p.length; i++) {
                            corner[i - 1] = corner(p[i], v.size(), vt.size(), vn.size());
                        }
                        for (int i = 1; i + 1 < corner.length; i++) {
                            faces.add(new int[]{
                                    corner[0][0], corner[0][1], corner[0][2],
                                    corner[i][0], corner[i][1], corner[i][2],
                                    corner[i + 1][0], corner[i + 1][1], corner[i + 1][2]});
                        }
                    }
                    default -> {
                        // mtllib, usemtl, o, g, s - all irrelevant to one mask
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
        if (faces.isEmpty()) {
            return Optional.empty();
        }

        int n = faces.size();
        float[] pos = new float[n * 9];
        float[] uvs = new float[n * 6];
        float[] nrm = new float[n * 9];
        for (int t = 0; t < n; t++) {
            int[] tri = faces.get(t);
            for (int c = 0; c < 3; c++) {
                float[] pv = v.get(tri[c * 3]);
                pos[t * 9 + c * 3] = pv[0];
                pos[t * 9 + c * 3 + 1] = pv[1];
                pos[t * 9 + c * 3 + 2] = pv[2];

                int ti = tri[c * 3 + 1];
                float[] tc = ti >= 0 && ti < vt.size() ? vt.get(ti) : new float[]{0.0F, 0.0F};
                uvs[t * 6 + c * 2] = tc[0];
                // OBJ counts V up from the bottom, textures count down from
                // the top. Getting this backwards flips every face vertically
                // and is the single most common way an imported mesh looks
                // like it has the wrong texture on it.
                uvs[t * 6 + c * 2 + 1] = 1.0F - tc[1];

                int ni = tri[c * 3 + 2];
                float[] nv = ni >= 0 && ni < vn.size() ? vn.get(ni) : null;
                if (nv == null) {
                    nv = faceNormal(v, tri);
                }
                nrm[t * 9 + c * 3] = nv[0];
                nrm[t * 9 + c * 3 + 1] = nv[1];
                nrm[t * 9 + c * 3 + 2] = nv[2];
            }
        }
        return Optional.of(new ObjMesh(pos, uvs, nrm, n));
    }

    private static float f(String[] p, int i) {
        return Float.parseFloat(p[i]);
    }

    /** "v", "v/vt", "v//vn" or "v/vt/vn", one-based and possibly negative. */
    private static int[] corner(String s, int nv, int nt, int nn) {
        String[] q = s.split("/", -1);
        return new int[]{idx(q, 0, nv), idx(q, 1, nt), idx(q, 2, nn)};
    }

    /**
     * One index out of a face corner.
     *
     * <p>OBJ counts from one, and a NEGATIVE index counts back from the end
     * of the list so far - which several exporters emit by default. Left
     * unresolved those land as huge negative array offsets and the mesh
     * either vanishes or crashes on load, so they are turned into absolute
     * positions here against the counts passed in.
     */
    private static int idx(String[] q, int i, int count) {
        if (i >= q.length || q[i].isEmpty()) {
            return -1;
        }
        int n = Integer.parseInt(q[i]);
        return n > 0 ? n - 1 : count + n;
    }

    /** For meshes exported without normals - flat shading beats no shading. */
    private static float[] faceNormal(List<float[]> v, int[] tri) {
        float[] a = v.get(tri[0]);
        float[] b = v.get(tri[3]);
        float[] c = v.get(tri[6]);
        Vector3f u = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
        Vector3f w = new Vector3f(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
        Vector3f nn = u.cross(w).normalize();
        return new float[]{nn.x, nn.y, nn.z};
    }

    /**
     * Emits the whole mesh into the buffer under the current pose.
     *
     * <p>`scale` converts the mesh's own units into model units. Blockbench
     * and most sculpting tools export in units where 1.0 is a block, while
     * this model is built in sixteenths, so the caller normally passes 16.
     */
    public void render(PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
                       float scale, float r, float g, float b, float a) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f nrm = pose.normal();
        for (int t = 0; t < triangles; t++) {
            for (int c = 0; c < 3; c++) {
                int pi = t * 9 + c * 3;
                int ui = t * 6 + c * 2;
                buffer.vertex(mat, pos[pi] * scale, pos[pi + 1] * scale, pos[pi + 2] * scale)
                        .color(r, g, b, a)
                        .uv(uv[ui], uv[ui + 1])
                        .overlayCoords(overlay)
                        .uv2(light)
                        .normal(nrm, normal[pi], normal[pi + 1], normal[pi + 2])
                        .endVertex();
            }
            // a triangle into a quad buffer: repeat the last corner, which is
            // what vanilla does for its own triangular geometry
            int pi = t * 9 + 6;
            int ui = t * 6 + 4;
            buffer.vertex(mat, pos[pi] * scale, pos[pi + 1] * scale, pos[pi + 2] * scale)
                    .color(r, g, b, a)
                    .uv(uv[ui], uv[ui + 1])
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(nrm, normal[pi], normal[pi + 1], normal[pi + 2])
                    .endVertex();
        }
    }
}
