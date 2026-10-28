package games.pixscape.runtime.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

public class HudSkinFileTest {
    @Test
    public void charsetReaderDelegatesWithoutUsingTheInheritedReadFallback() throws Exception {
        FileHandle delegate = new FileHandle() {
            @Override public Reader reader(String charset) {
                Assert.assertEquals("UTF-8", charset);
                return new StringReader("skin-json");
            }

            @Override public InputStream read() {
                throw new GdxRuntimeException("Stub");
            }
        };
        FileHandle project = new FileHandle() {
            @Override public FileHandle child(String name) {
                Assert.assertEquals("orig/skins/1701/comic-ui.json", name);
                return delegate;
            }
        };

        Reader reader = new HudSkinFile(
                project, "orig/skins/1701/comic-ui.json").reader("UTF-8");
        try {
            char[] content = new char[9];
            Assert.assertEquals(content.length, reader.read(content));
            Assert.assertEquals("skin-json", new String(content));
        } finally {
            reader.close();
        }
    }
}
