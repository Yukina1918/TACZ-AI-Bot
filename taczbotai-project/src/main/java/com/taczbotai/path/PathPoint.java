package com.taczbotai.path;

import net.minecraft.world.phys.Vec3;

/**
 * 路径上的一个节点。
 *
 * @param pos         坐标
 * @param stop        true = 停留节点（bf 模式下到达后原地慢慢徘徊 N 秒再走）
 * @param staySeconds 停留秒数（仅 stop 节点有效）
 */
public record PathPoint(Vec3 pos, boolean stop, int staySeconds) {
    public static PathPoint move(Vec3 pos) {
        return new PathPoint(pos, false, 0);
    }

    public static PathPoint stay(Vec3 pos, int seconds) {
        return new PathPoint(pos, true, Math.max(0, seconds));
    }
}
