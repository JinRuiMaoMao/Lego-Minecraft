package com.legominecraft.multipart;

import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Clip–bar connections between placed pieces: a clip, ring or hollow stud holding a bar or handle. */
public final class LegoClips {
	/** Spacing of the probes taken along a bar to find the clips on it, in px. */
	private static final double PROBE_STEP = 8;

	private LegoClips() {
	}

	/** One clip holding one bar. */
	public record Joint(LegoStructurePart clipPart, LegoPiece.WorldClip clip, LegoStructurePart barPart, LegoPiece.WorldBar bar) {
		public LegoStructurePart other(LegoStructurePart side) {
			return side.pieceKey().equals(clipPart.pieceKey()) ? barPart : clipPart;
		}
	}

	/** Every joint {@code part} takes part in, as the clip side or the bar side. */
	public static List<Joint> joints(Level level, LegoStructurePart part) {
		List<Joint> out = new ArrayList<>();
		Object key = part.pieceKey();
		for (LegoPiece.WorldClip clip : part.clips()) {
			for (LegoFragmentPart q : LegoGrabber.partsNear(level, clip.at()[0], clip.at()[1], clip.at()[2])) {
				if (!(q instanceof LegoStructurePart qs) || qs.pieceKey().equals(key) || hasJoint(out, part, qs)) {
					continue;
				}
				for (LegoPiece.WorldBar bar : qs.bars()) {
					if (clip.holds(bar)) {
						out.add(new Joint(part, clip, qs, bar));
						break;
					}
				}
			}
		}
		for (LegoPiece.WorldBar bar : part.bars()) {
			int k = bar.axis().ordinal();
			double[] p = bar.a().clone();
			double lo = bar.lo();
			double hi = bar.hi();
			int steps = (int) Math.ceil((hi - lo) / PROBE_STEP);
			for (int i = 0; i <= steps; i++) {
				p[k] = Math.min(hi, lo + i * PROBE_STEP);
				for (LegoFragmentPart q : LegoGrabber.partsNear(level, p[0], p[1], p[2])) {
					if (!(q instanceof LegoStructurePart qs) || qs.pieceKey().equals(key) || hasJoint(out, qs, part)) {
						continue;
					}
					for (LegoPiece.WorldClip clip : qs.clips()) {
						if (clip.holds(bar)) {
							out.add(new Joint(qs, clip, part, bar));
							break;
						}
					}
				}
			}
		}
		return out;
	}

	private static boolean hasJoint(List<Joint> joints, LegoStructurePart clipPart, LegoStructurePart barPart) {
		for (Joint j : joints) {
			if (j.clipPart().pieceKey().equals(clipPart.pieceKey()) && j.barPart().pieceKey().equals(barPart.pieceKey())) {
				return true;
			}
		}
		return false;
	}
}
