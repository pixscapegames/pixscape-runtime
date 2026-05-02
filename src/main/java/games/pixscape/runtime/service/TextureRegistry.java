package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.render.SortKey64;

import java.util.IdentityHashMap;

public final class TextureRegistry {

    private TextureRegistry() {}

    public static final int INVALID_HANDLE = 0;
    public static final int WHITE_HANDLE   = 1; // reserved for InternalTextures

    private static final IdentityHashMap<Texture, Integer> tex2id = new IdentityHashMap<>();
    private static final Array<Texture> id2tex = new Array<>();

    // 0 invalid, 1 WHITE => start at 2
    private static int nextId = 2;

    private static void ensureCapacityForHandle(int handle) {
        int idx = handle - 1;
        while (id2tex.size <= idx) id2tex.add(null);
    }

    /** Assigns (or reassigns) a reserved handle to a given texture. */
    public static void reserveHandle(int handle, Texture t) {
        if (handle <= 0) throw new IllegalArgumentException("handle must be > 0");
        if (handle > SortKey64.MAX_TEXTURE_HANDLE) {
            throw new IllegalArgumentException("handle=" + handle + " exceeds MAX_TEXTURE_HANDLE=" + SortKey64.MAX_TEXTURE_HANDLE);
        }
        if (t == null) throw new IllegalArgumentException("texture is null");

        // If this handle was already associated with another texture, remove old entry.
        Texture oldAtHandle = getByHandle(handle);
        if (oldAtHandle != null && oldAtHandle != t) {
            tex2id.remove(oldAtHandle);
        }

        // If this texture already had another handle, this is a caller inconsistency.
        Integer prev = tex2id.get(t);
        if (prev != null && prev != handle) {
            throw new IllegalStateException("Texture already registered with handle=" + prev + ", cannot reserve " + handle);
        }

        tex2id.put(t, handle);

        ensureCapacityForHandle(handle);
        id2tex.set(handle - 1, t);

        // nextId must remain strictly above already used handles
        if (nextId <= handle) nextId = handle + 1;
        if (nextId <= WHITE_HANDLE) nextId = WHITE_HANDLE + 1;
    }

    public static int handleOf(Texture t) {
        if (t == null) return INVALID_HANDLE;

        Integer id = tex2id.get(t);
        if (id != null) return id;

        // Allocate a new handle (only here we check overflow)
        int idNew = nextId;

        if (idNew == WHITE_HANDLE) idNew++; // safety
        if (idNew > SortKey64.MAX_TEXTURE_HANDLE) {
            throw new IllegalStateException(
                    "TextureRegistry overflow: nextId=" + idNew +
                            " > MAX_TEXTURE_HANDLE=" + SortKey64.MAX_TEXTURE_HANDLE +
                            " (use atlases/TextureArray, or increase TEX_BITS)"
            );
        }

        nextId = idNew + 1;

        tex2id.put(t, idNew);
        ensureCapacityForHandle(idNew);
        id2tex.set(idNew - 1, t);

        return idNew;
    }

    public static Texture getByHandle(int handle) {
        int idx = handle - 1;
        if (idx < 0 || idx >= id2tex.size) return null;
        return id2tex.get(idx);
    }

    public static void clear() {
        tex2id.clear();
        id2tex.clear();
        nextId = 2;
    }
}