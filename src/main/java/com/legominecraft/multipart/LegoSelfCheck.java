package com.legominecraft.multipart;

import com.legominecraft.LegoMinecraftMod;
import com.legominecraft.block.LegoCatalog;
import com.legominecraft.block.LegoPartDefinition;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Development check: every catalog part, in every orientation, must split into fragments that can be
 * placed, and every bar must go into every clip (and every clip onto every bar) somewhere.
 */
public final class LegoSelfCheck {
	private static final int MAX_TRIES = 96;

	private LegoSelfCheck() {
	}

	public static void run() {
		checkFragments();
		checkJoints();
		checkStuds();
		checkVanillaBlocks();
		exportMeshes();
	}

	/** An off-grid vanilla block's per-cell shapes must add up to the whole block, in the expected cells. */
	private static void checkVanillaBlocks() {
		int failures = 0;
		int cases = 0;
		for (net.minecraft.world.level.block.state.BlockState state : List.of(
			net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
			net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState(),
			net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState().setValue(
				net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP))) {
			double whole = VanillaBlockPart.blockShape(state).toAabbs().stream().mapToDouble(b -> b.getXsize() * b.getYsize() * b.getZsize()).sum();
			for (int[] off : new int[][] {{0, 0, 0}, {0, 12, 0}, {15, 0, 0}, {5, 30, 25}, {0, 24, 0}}) {
				cases++;
				double sum = 0;
				List<int[]> cells = VanillaBlockPart.fragments(state, off[0], off[1], off[2]);
				for (int[] cell : cells) {
					sum += VanillaBlockPart.cellShape(state, off[0], off[1], off[2], cell).toAabbs().stream()
						.mapToDouble(b -> b.getXsize() * b.getYsize() * b.getZsize()).sum();
				}
				if (Math.abs(sum - whole) > 1.0E-6 || cells.isEmpty()) {
					failures++;
					LegoMinecraftMod.LOGGER.warn("SELFCHECK vanilla block {} at {} covers {} of {} in {} cells", state, java.util.Arrays.toString(off), sum, whole, cells.size());
				}
			}
		}
		LegoMinecraftMod.LOGGER.info("SELFCHECK vanilla blocks: {} of {} placements with problems", failures, cases);
	}

	/** A 1×1 round plate must plug onto every up stud of a studless part and into every extra receptor. */
	private static void checkStuds() {
		LegoPartDefinition plate = LegoCatalog.get(85861);
		int failures = 0;
		int hosts = 0;
		for (LegoPartDefinition host : LegoCatalog.all()) {
			boolean sides = host.sideStuds() || host.bracket() != null;
			boolean vertical = !host.receptors().isEmpty() || !host.kind().hasStuds() && !host.studs().isEmpty();
			if (host.kind().isTechnicStudless() || !sides && !vertical) {
				continue;
			}
			hosts++;
			LegoPiece.Anchored h = new LegoPiece.Anchored(new LegoPiece(host, Direction.NORTH, Direction.UP, 0, 0, 0), 0, 0, 0);
			List<String> failed = new ArrayList<>();
			for (LegoPiece.Stud s : h.piece().studs(0, 0, 0)) {
				boolean check = s.dir().getAxis().isHorizontal() || vertical && s.dir() == Direction.UP;
				if (check && !plugs(plate, h, s, true)) {
					failed.add(s.dir().getSerializedName() + "@" + Math.round(s.y()));
				}
			}
			for (LegoPiece.Stud r : vertical ? h.piece().receptors(0, 0, 0) : List.<LegoPiece.Stud>of()) {
				if (!plugs(plate, h, r, false)) {
					failed.add("receptor@" + Math.round(r.y()));
				}
			}
			if (!failed.isEmpty()) {
				failures++;
				LegoMinecraftMod.LOGGER.warn("SELFCHECK plate 85861 does not plug into {} {} at {}", host.designId(), host.nameEn(), failed);
			}
		}
		LegoMinecraftMod.LOGGER.info("SELFCHECK studs: {} of {} parts with problems", failures, hosts);
	}

	/**
	 * Whether {@code guest} plugs its receptor onto (standing on the stud's direction) or its stud into
	 * (upright) {@code target} without overlapping.
	 */
	private static boolean plugs(LegoPartDefinition guest, LegoPiece.Anchored host, LegoPiece.Stud target, boolean onStud) {
		Direction up = onStud ? target.dir() : Direction.UP;
		Direction facing = Direction.NORTH;
		for (Direction f : Direction.Plane.HORIZONTAL) {
			if (up.getAxis().isHorizontal() && new LegoPiece(guest, f, up, 0, 0, 0).toWorld(Direction.NORTH) == Direction.DOWN) {
				facing = f;
			}
		}
		LegoPiece oriented = new LegoPiece(guest, facing, up, 0, 0, 0);
		for (LegoPiece.Stud own : onStud ? oriented.receptors(0, 0, 0) : oriented.studs(0, 0, 0)) {
			if (own.dir() != target.dir()) {
				continue;
			}
			double[] t = {target.x() - own.x(), target.y() - own.y(), target.z() - own.z()};
			int[] u = new int[3];
			boolean onGrid = true;
			for (int k = 0; k < 3; k++) {
				double v = t[k] / LegoPiece.unitPx(k);
				u[k] = (int) Math.round(v);
				onGrid &= Math.abs(v - u[k]) < 0.02;
			}
			if (!onGrid) {
				continue;
			}
			LegoPiece p = new LegoPiece(guest, facing, up, Math.floorMod(u[0], LegoPiece.UNITS_X),
				Math.floorMod(u[1], LegoPiece.UNITS_Y), Math.floorMod(u[2], LegoPiece.UNITS_X));
			LegoPiece.Anchored a = new LegoPiece.Anchored(p, Math.floorDiv(u[0], LegoPiece.UNITS_X),
				Math.floorDiv(u[1], LegoPiece.UNITS_Y), Math.floorDiv(u[2], LegoPiece.UNITS_X));
			if (overlaps(a, host)) {
				return false;
			}
			return (onStud ? p.receptors(a.ax(), a.ay(), a.az()) : p.studs(a.ax(), a.ay(), a.az())).stream().anyMatch(target::matches);
		}
		return false;
	}

	/** Writes every part's upright mesh (local px; material per face) to run/mesh_export/<id>.txt for offline renders. */
	private static void exportMeshes() {
		java.nio.file.Path dir = java.nio.file.Path.of("mesh_export");
		try {
			java.nio.file.Files.createDirectories(dir);
			for (LegoPartDefinition def : LegoCatalog.all()) {
				if (def.kind().isTechnicStudless()) {
					continue;
				}
				StringBuilder sb = new StringBuilder();
				for (com.legominecraft.mesh.Mesh.Raw raw : com.legominecraft.mesh.BrickMesh.local(new LegoPiece(def, Direction.NORTH, Direction.UP, 0, 0, 0)).raws()) {
					sb.append(raw.material());
					for (double[] v : raw.v()) {
						sb.append(' ').append((float) v[0]).append(' ').append((float) v[1]).append(' ').append((float) v[2]);
					}
					sb.append('\n');
				}
				java.nio.file.Files.writeString(dir.resolve(def.designId() + ".txt"), sb);
			}
		} catch (java.io.IOException e) {
			LegoMinecraftMod.LOGGER.warn("SELFCHECK mesh export failed", e);
		}
	}

	private static void checkFragments() {
		int bad = 0;
		for (LegoPartDefinition def : LegoCatalog.all()) {
			if (def.kind().isTechnicStudless()) {
				continue;
			}
			int empty = 0;
			int tried = 0;
			String example = null;
			for (Direction up : Direction.values()) {
				for (Direction facing : Direction.Plane.HORIZONTAL) {
					for (int ty : new int[] {0, LegoPiece.PLATE_UNITS, 2 * LegoPiece.PLATE_UNITS}) {
						for (int t : new int[] {0, LegoPiece.UNITS_X / 2}) {
							LegoPiece piece = new LegoPiece(def, facing, up, t, ty, t);
							tried++;
							for (int[] f : piece.fragments()) {
								if (piece.fragmentShape(f[0], f[1], f[2]).isEmpty()) {
									empty++;
									if (example == null) {
										example = "up=" + up + " facing=" + facing + " ty=" + ty + " t=" + t
											+ " cell=" + f[0] + "," + f[1] + "," + f[2];
									}
									break;
								}
							}
						}
					}
				}
			}
			if (empty > 0) {
				bad++;
				LegoMinecraftMod.LOGGER.warn("SELFCHECK {} {}: empty fragments in {}/{} poses ({})", def.designId(),
					def.nameEn(), empty, tried, example);
			}
		}
		LegoMinecraftMod.LOGGER.info("SELFCHECK fragments: {} parts with problems", bad);
	}

	private static void checkJoints() {
		List<LegoPartDefinition> barParts = new ArrayList<>();
		List<LegoPartDefinition> clipParts = new ArrayList<>();
		List<LegoPartDefinition> socketParts = new ArrayList<>();
		for (LegoPartDefinition def : LegoCatalog.all()) {
			if (def.kind().isTechnicStudless()) {
				continue;
			}
			if (def.fittings().bars().stream().anyMatch(LegoPartDefinition.Bar::grip)) {
				barParts.add(def);
			}
			if (def.fittings().clips().stream().anyMatch(c -> !c.blind())) {
				clipParts.add(def);
			}
			if (def.openStuds()) {
				socketParts.add(def);
			}
		}
		int failures = 0;
		for (LegoPartDefinition guest : barParts) {
			List<String> failed = new ArrayList<>();
			List<LegoPartDefinition> hosts = new ArrayList<>(socketParts);
			for (LegoPartDefinition def : LegoCatalog.all()) {
				if (!def.kind().isTechnicStudless() && !def.fittings().clips().isEmpty() && !hosts.contains(def)) {
					hosts.add(def);
				}
			}
			for (LegoPartDefinition host : hosts) {
				LegoPiece.Anchored h = new LegoPiece.Anchored(new LegoPiece(host, Direction.NORTH, Direction.UP, 0, 0, 0), 0, 0, 0);
				boolean any = false;
				boolean ok = false;
				for (LegoPiece.WorldClip clip : h.piece().clips(0, 0, 0)) {
					// Only loose bars go into hollow studs, and not ones ending in studs (hilts).
					if (clip.socket() != null && (guest.kind() != com.legominecraft.block.LegoPartType.BAR || !guest.studs().isEmpty())) {
						continue;
					}
					any = true;
					double[] eye = {clip.at()[0], clip.at()[1] + 40, clip.at()[2] + 30};
					List<LegoClipPlacer.Candidate> candidates = LegoClipPlacer.intoClip(guest, clip, eye);
					if (fits(candidates, h, c -> c.piece().bars(c.ax(), c.ay(), c.az()).stream().anyMatch(clip::holds))) {
						ok = true;
						break;
					}
				}
				if (any && !ok) {
					failed.add(host.designId() + "");
				}
			}
			if (!failed.isEmpty()) {
				failures++;
				LegoMinecraftMod.LOGGER.warn("SELFCHECK bar {} {} goes into none of the clips/hollow studs of {}", guest.designId(), guest.nameEn(), failed);
			}
		}
		for (LegoPartDefinition guest : clipParts) {
			List<String> failed = new ArrayList<>();
			for (LegoPartDefinition host : barParts) {
				LegoPiece.Anchored h = new LegoPiece.Anchored(new LegoPiece(host, Direction.NORTH, Direction.UP, 0, 0, 0), 0, 0, 0);
				boolean ok = false;
				for (LegoPiece.WorldBar bar : h.piece().bars(0, 0, 0)) {
					if (!bar.grip()) {
						continue;
					}
					double[] mid = new double[3];
					for (int i = 0; i < 3; i++) {
						mid[i] = (bar.a()[i] + bar.b()[i]) / 2;
					}
					double[] eye = {mid[0], mid[1] + 40, mid[2] + 30};
					List<LegoClipPlacer.Candidate> candidates = LegoClipPlacer.onBar(guest, bar, mid, eye);
					if (fits(candidates, h, c -> c.piece().clips(c.ax(), c.ay(), c.az()).stream().anyMatch(cl -> cl.holds(bar)))) {
						ok = true;
						break;
					}
				}
				if (!ok) {
					failed.add(host.designId() + "");
				}
			}
			if (!failed.isEmpty()) {
				failures++;
				LegoMinecraftMod.LOGGER.warn("SELFCHECK clip {} {} goes onto none of the bars of {}", guest.designId(), guest.nameEn(), failed);
			}
		}
		LegoMinecraftMod.LOGGER.info("SELFCHECK joints: {} parts with problems ({} bar parts, {} clip parts, {} hollow-stud parts)",
			failures, barParts.size(), clipParts.size(), socketParts.size());
	}

	/** Whether the best-first candidate the placer would take clears {@code host} and makes the joint. */
	private static boolean fits(List<LegoClipPlacer.Candidate> candidates, LegoPiece.Anchored host,
		java.util.function.Predicate<LegoPiece.Anchored> joined) {
		candidates.sort(Comparator.comparingDouble(LegoClipPlacer.Candidate::score));
		for (LegoClipPlacer.Candidate c : candidates.subList(0, Math.min(MAX_TRIES, candidates.size()))) {
			if (!overlaps(c.anchored(), host)) {
				return joined.test(c.anchored());
			}
		}
		return false;
	}

	private static boolean overlaps(LegoPiece.Anchored a, LegoPiece.Anchored b) {
		for (int[] f : a.piece().fragments()) {
			for (int[] g : b.piece().fragments()) {
				if (a.ax() + f[0] == b.ax() + g[0] && a.ay() + f[1] == b.ay() + g[1] && a.az() + f[2] == b.az() + g[2]
					&& Shapes.joinIsNotEmpty(a.piece().fragmentShape(f[0], f[1], f[2]), b.piece().fragmentShape(g[0], g[1], g[2]), BooleanOp.AND)) {
					return true;
				}
			}
		}
		return false;
	}
}
