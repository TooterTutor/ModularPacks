package io.github.tootertutor.ModularPacks.modules.tank;

/**
 * Vanilla player XP math helpers for converting between levels and total XP
 * points.
 */
public final class TankExperience {

    private TankExperience() {
    }

    public static int pointsToNextLevel(int level) {
        int lv = Math.max(0, level);
        if (lv <= 15)
            return 2 * lv + 7;
        if (lv <= 30)
            return 5 * lv - 38;
        return 9 * lv - 158;
    }

    public static int totalForLevel(int level) {
        int lv = Math.max(0, level);
        if (lv <= 16)
            return lv * lv + 6 * lv;
        if (lv <= 31)
            return (int) Math.floor(2.5 * lv * lv - 40.5 * lv + 360.0);
        return (int) Math.floor(4.5 * lv * lv - 162.5 * lv + 2220.0);
    }

    public static int levelFromTotal(int totalPoints) {
        int xp = Math.max(0, totalPoints);
        if (xp <= 352) {
            // level = sqrt(total + 9) - 3
            return Math.max(0, (int) Math.floor(Math.sqrt(xp + 9.0) - 3.0));
        }
        if (xp <= 1507) {
            // level = 81/10 + sqrt((2/5) * (total - 7839/40))
            double value = 8.1 + Math.sqrt((2.0 / 5.0) * (xp - (7839.0 / 40.0)));
            return Math.max(0, (int) Math.floor(value));
        }
        // level = 325/18 + sqrt((2/9) * (total - 54215/72))
        double value = (325.0 / 18.0) + Math.sqrt((2.0 / 9.0) * (xp - (54215.0 / 72.0)));
        return Math.max(0, (int) Math.floor(value));
    }

    public static int progressInCurrentLevel(int totalPoints) {
        int xp = Math.max(0, totalPoints);
        int level = levelFromTotal(xp);
        return Math.max(0, xp - totalForLevel(level));
    }

    public static int totalFromLevelAndProgress(int level, float expProgress) {
        int lv = Math.max(0, level);
        int toNext = pointsToNextLevel(lv);
        float clamped = Math.max(0.0f, Math.min(1.0f, expProgress));
        int partial = Math.min(toNext - 1, (int) Math.floor(clamped * toNext));
        return totalForLevel(lv) + Math.max(0, partial);
    }
}