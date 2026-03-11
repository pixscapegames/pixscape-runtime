package games.pixscape.runtime.render;

/**
 * Liste plate des entités à dessiner pour la frame courante.
 * <p>
 * La capacité est fixée via le constructeur ou {@link #setCapacity(int)}
 * au moment de l'initialisation. Aucun agrandissement dynamique n'est effectué
 * au-delà de cette capacité : un dépassement est considéré comme un bug.
 */
public final class DrawList {

    private int[] entities;
    public int size = 0;

    public DrawList() {
        // On attend un setCapacity(...) explicite plus tard.
    }

    public DrawList(int initialCapacity) {
        setCapacity(initialCapacity);
    }

    public void clear() {
        size = 0;
    }

    public int get(int index) {
        return entities[index];
    }

    public int[] data() {
        return entities;
    }

    public void add(int entity) {
        if (entities == null) {
            throw new IllegalStateException(
                    "DrawList capacity not initialized. " +
                            "Call setCapacity(...) after World creation."
            );
        }
        if (size >= entities.length) {
            throw new IllegalStateException(
                    "DrawList overflow: size=" + size +
                            ", capacity=" + entities.length
            );
        }
        entities[size++] = entity;
    }

    /**
     * Fixe (ou refixe) complètement la capacité de la DrawList.
     * <p>
     * À appeler typiquement juste après la création du World, avec
     * world.getEntityManager().getCapacity().
     */
    public void setCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("DrawList capacity must be > 0");
        }
        entities = new int[capacity];
        size = 0;
    }
}
