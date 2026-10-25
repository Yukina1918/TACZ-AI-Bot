package com.taczbotai.path;

import java.util.List;

/**
 * 一条录制好的巡逻路径。
 *
 * @param mode 录制时选择的节点模式：cs（到终点后 5 格徘徊）/ bf（移动-停留-移动…到头反向）
 */
public record BotPath(String name, String dimension, String mode, List<PathPoint> points) {
}
