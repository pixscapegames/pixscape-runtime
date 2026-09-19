package games.pixscape.runtime.hud;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.files.FileHandle;
import java.io.InputStream;
import java.io.Reader;

/** Skin's normal serializer reads through canonical project keys, including relative FNT paths. */
final class HudSkinFile extends FileHandle {
    private final FileHandle project;
    private final String id;
    private final FileHandle delegate;

    HudSkinFile(FileHandle project, String id) {
        this.project = project;
        this.id = id;
        this.delegate = id.length() == 0 ? project : project.child(id);
    }

    @Override public FileHandle parent() {
        int slash = id.lastIndexOf('/');
        return new HudSkinFile(project, slash < 0 ? "" : id.substring(0, slash));
    }

    @Override public FileHandle child(String name) {
        return new HudSkinFile(project, HudResourceId.resolveRelative(id.length() == 0 ? "" : id + "/", name));
    }

    @Override public FileHandle sibling(String name) { return parent().child(name); }
    @Override public String path() { return delegate.path(); }
    @Override public FileType type() { return delegate.type(); }
    @Override public String name() { return delegate.name(); }
    @Override public String extension() { return delegate.extension(); }
    @Override public String nameWithoutExtension() { return delegate.nameWithoutExtension(); }
    @Override public InputStream read() { return delegate.read(); }
    @Override public Reader reader(String charset) { return delegate.reader(charset); }
    @Override public String readString() { return delegate.readString(); }
    @Override public String readString(String charset) { return delegate.readString(charset); }
    @Override public boolean exists() { return delegate.exists(); }
    @Override public boolean isDirectory() { return delegate.isDirectory(); }
    @Override public long length() { return delegate.length(); }
    @Override public String toString() { return path(); }
}
