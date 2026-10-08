package com.legominecraft.client.renderer;

import com.legominecraft.item.LegoModelItem;
import com.legominecraft.item.ModItems;
import com.legominecraft.mesh.Mesh;
import com.legominecraft.multipart.LegoModel;
import com.legominecraft.multipart.LegoModelPlacer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.List;

/**
 * Translucent ghost of a held LEGO model where it would be placed; tinted red when it does not fit.
 */
public final class LegoModelPreview {
	private static final int RECHECK_TICKS = 10;
	private static final float ALPHA = 0.5F;
	private static final int BLOCKED_RGB = 0xFF3030;

	private static CustomData data;
	private static LegoModel model;
	/** Quads per orientation: 12 floats (4 vertices) each, with one packed RGB per quad. */
	private static final float[][] quads = new float[LegoModelPlacer.ORIENTATIONS][];
	private static final int[][] colors = new int[LegoModelPlacer.ORIENTATIONS][];

	/** Aim positions closer than 1/AIM_STEPS block share the cached plans. */
	private static final int AIM_STEPS = 32;

	private static Object fitKey;
	private static long fitTime;
	private static List<LegoModelPlacer.Plan> plans;
	/** Index of the first fitting plan for {@link #fitKey}, or -1 when none fits. */
	private static int fitIndex;

	private LegoModelPreview() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(LegoModelPreview::render);
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || player.isSpectator()) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		if (!stack.is(ModItems.LEGO_MODEL) || !(mc.hitResult instanceof BlockHitResult hit)
			|| hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		CustomData current = stack.get(DataComponents.CUSTOM_DATA);
		if (current == null) {
			return;
		}
		if (current != data) {
			data = current;
			model = LegoModelItem.read(stack);
			Arrays.fill(quads, null);
			Arrays.fill(colors, null);
			fitKey = null;
		}
		if (model.size() == 0) {
			return;
		}

		long time = mc.level.getGameTime();
		Vec3 at = hit.getLocation();
		Object key = Arrays.asList(model, hit.getBlockPos(), hit.getDirection(), player.getDirection(),
			Math.round(at.x * AIM_STEPS), Math.round(at.y * AIM_STEPS), Math.round(at.z * AIM_STEPS));
		if (!key.equals(fitKey) || time - fitTime >= RECHECK_TICKS || time < fitTime) {
			fitKey = key;
			fitTime = time;
			plans = LegoModelPlacer.plans(mc.level, player.getDirection(), hit, model, LegoModelItem.turn(stack));
			fitIndex = LegoModelPlacer.firstFitting(mc.level, plans);
		}
		LegoModelPlacer.Plan first = plans.getFirst();
		boolean fits = fitIndex >= 0 && fitIndex < plans.size();
		LegoModelPlacer.Plan plan = fits ? plans.get(fitIndex) : first;

		int orientation = plan.orientation();
		if (quads[orientation] == null) {
			build(plan.rotated(), orientation);
		}
		draw(ctx, plan, quads[orientation], colors[orientation], fits);
	}

	private static void build(LegoModel rotated, int orientation) {
		float[][] q = {new float[1024]};
		int[][] c = {new int[86]};
		int[] n = {0};
		rotated.polys((color, v) -> {
			int rgb = shade(color.getRgb(), Mesh.normal(v));
			for (int i = 1; i + 1 < v.length; i += 2) {
				int third = Math.min(i + 2, v.length - 1);
				if ((n[0] + 1) * 12 > q[0].length) {
					q[0] = Arrays.copyOf(q[0], q[0].length * 2);
					c[0] = Arrays.copyOf(c[0], c[0].length * 2 + 1);
				}
				int o = n[0] * 12;
				int[] idx = {0, i, i + 1, third};
				for (int k = 0; k < 4; k++) {
					q[0][o + k * 3] = (float) v[idx[k]][0];
					q[0][o + k * 3 + 1] = (float) v[idx[k]][1];
					q[0][o + k * 3 + 2] = (float) v[idx[k]][2];
				}
				c[0][n[0]] = rgb;
				n[0]++;
			}
		});
		quads[orientation] = Arrays.copyOf(q[0], n[0] * 12);
		colors[orientation] = Arrays.copyOf(c[0], n[0]);
	}

	/** Vanilla-like directional shading: top full, bottom half, z sides 0.8, x sides 0.6. */
	private static int shade(int rgb, double[] normal) {
		double len = Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
		if (len < 1.0E-9) {
			return rgb;
		}
		double nx = normal[0] / len;
		double ny = normal[1] / len;
		double nz = normal[2] / len;
		double f = nx * nx * 0.6 + nz * nz * 0.8 + ny * ny * (ny > 0 ? 1.0 : 0.5);
		int r = (int) (((rgb >> 16) & 0xFF) * f);
		int g = (int) (((rgb >> 8) & 0xFF) * f);
		int b = (int) ((rgb & 0xFF) * f);
		return (r << 16) | (g << 8) | b;
	}

	private static void draw(WorldRenderContext ctx, LegoModelPlacer.Plan plan, float[] q, int[] c, boolean fits) {
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = new PoseStack();
		double[] offset = plan.offsetBlocks();
		pose.translate(offset[0] - cam.x, offset[1] - cam.y, offset[2] - cam.z);
		pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
		Matrix4f m = pose.last().pose();

		MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
		int a = (int) (ALPHA * 255);
		for (int i = 0; i < c.length; i++) {
			int rgb = fits ? c[i] : mix(c[i], BLOCKED_RGB);
			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8) & 0xFF;
			int b = rgb & 0xFF;
			int o = i * 12;
			for (int k = 0; k < 4; k++) {
				vc.addVertex(m, q[o + k * 3], q[o + k * 3 + 1], q[o + k * 3 + 2]).setColor(r, g, b, a);
			}
		}
		buffers.endBatch(RenderType.debugQuads());
	}

	private static int mix(int rgb, int tint) {
		int r = (((rgb >> 16) & 0xFF) + ((tint >> 16) & 0xFF) * 2) / 3;
		int g = (((rgb >> 8) & 0xFF) + ((tint >> 8) & 0xFF) * 2) / 3;
		int b = ((rgb & 0xFF) + (tint & 0xFF) * 2) / 3;
		return (r << 16) | (g << 8) | b;
	}
}
