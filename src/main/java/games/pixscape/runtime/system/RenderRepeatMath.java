package games.pixscape.runtime.system;

import games.pixscape.runtime.render.RenderRepeatFlags;

final class RenderRepeatMath {
    private RenderRepeatMath() {
    }

    static boolean calculateVisibleRange(
            float viewportMinX,
            float viewportMaxX,
            float viewportMinY,
            float viewportMaxY,
            float baseMinX,
            float baseMaxX,
            float baseMinY,
            float baseMaxY,
            byte repeatFlags,
            int maxDraws,
            int[] outRange) {
        boolean repeatX = (repeatFlags & RenderRepeatFlags.REPEAT_X) != 0;
        boolean repeatY = (repeatFlags & RenderRepeatFlags.REPEAT_Y) != 0;

        if (!repeatX && !overlaps(baseMinX, baseMaxX, viewportMinX, viewportMaxX)) {
            return false;
        }
        if (!repeatY && !overlaps(baseMinY, baseMaxY, viewportMinY, viewportMaxY)) {
            return false;
        }

        float stepX = baseMaxX - baseMinX;
        float stepY = baseMaxY - baseMinY;

        if ((repeatX && stepX <= 0f) || (repeatY && stepY <= 0f)) {
            return false;
        }

        int minIx = repeatX ? floorToInt((viewportMinX - baseMaxX) / stepX) : 0;
        int maxIx = repeatX ? floorToInt((viewportMaxX - baseMinX) / stepX) : 0;
        int minIy = repeatY ? floorToInt((viewportMinY - baseMaxY) / stepY) : 0;
        int maxIy = repeatY ? floorToInt((viewportMaxY - baseMinY) / stepY) : 0;

        if (maxIx < minIx || maxIy < minIy) {
            return false;
        }

        long xCount = (long) maxIx - minIx + 1L;
        long yCount = (long) maxIy - minIy + 1L;
        long total = xCount * yCount;

        if (maxDraws > 0 && total > maxDraws) {
            if (repeatX && repeatY) {
                if (xCount >= maxDraws) {
                    maxIx = minIx + maxDraws - 1;
                    maxIy = minIy;
                } else {
                    long cappedY = Math.max(1L, maxDraws / xCount);
                    maxIy = minIy + (int) cappedY - 1;
                }
            } else if (repeatX) {
                maxIx = minIx + maxDraws - 1;
            } else if (repeatY) {
                maxIy = minIy + maxDraws - 1;
            }
        }

        outRange[0] = minIx;
        outRange[1] = maxIx;
        outRange[2] = minIy;
        outRange[3] = maxIy;
        return true;
    }

    static int visibleCount(int[] range) {
        long xCount = (long) range[1] - range[0] + 1L;
        long yCount = (long) range[3] - range[2] + 1L;
        long total = xCount * yCount;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    static int floorToInt(float value) {
        return (int) Math.floor(value);
    }

    private static boolean overlaps(float minA, float maxA, float minB, float maxB) {
        return !(maxA < minB || minA > maxB);
    }
}
