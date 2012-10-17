package fr.proline.core.orm.utils;

public final class MathUtils {

    /**
     * For <code>float</code> computations.
     */
    public static final float EPSILON_FLOAT = 1e-6f;

    /**
     * For <code>double</code> with <em>float precision</em> computations.
     */
    public static final double EPSILON_LOW_PRECISION = 1e-6;

    /**
     * For <code>double</code> with <em>double precision</em> computations.
     */
    public static final double EPSILON_HIGH_PRECISION = 1e-14;

    private MathUtils() {
    }
}
