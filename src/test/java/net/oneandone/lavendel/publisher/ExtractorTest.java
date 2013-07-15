package net.oneandone.lavendel.publisher;

import net.oneandone.lavendel.index.Index;
import net.oneandone.lavendel.publisher.config.Filter;
import net.oneandone.lavendel.publisher.pustefix.PustefixExtractor;
import net.oneandone.sushi.fs.LineFormat;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExtractorTest {
    private WarEngine resourcePublisher;

    private FileNode war;

    private FileNode baseDirectory;

    private FileNode nodesFile;

    private FileNode webXml;

    private Distributor distributor;

    @Before
    public void setUp() throws IOException, URISyntaxException {
        World world;
        FileNode tmp;

        world = new World();
        war = (FileNode) world.resource("dummy.war");
        tmp = world.getTemp().createTempDirectory();
        baseDirectory = tmp.join("storage").mkdir();
        nodesFile = tmp.join("nodes.lst");
        webXml = tmp.join("web.xml");
        distributor = Distributor.forTest(baseDirectory, "notused");
        resourcePublisher = new WarEngine(WarEngine.createNullLog(), war, war, distributor, webXml,
                new Index(), nodesFile, "");
    }

    @Test
    public void testRun() throws IOException {
        Collection<String> indexFileContent;
        Index index;

        resourcePublisher.extract(new PustefixExtractor(new Filter(), war.toPath().toFile()));
        index = distributor.close();
        indexFileContent = indexLines(index);
        assertEquals(1 + 14 + 1, indexFileContent.size()); // contains properties header line
        assertTrue(indexFileContent
                .contains("modules/frontend-tools/img/delete.png=684/61ca5187cd2c6af08786467085f2b/frontend-tools"
                        + "/delete.png\\:68461ca5187cd2c6af08786467085f2b"));
        assertTrue(indexFileContent
                .contains("modules/frontend-elements/style/error.css=13e/8ca508d2063e9504497b4953f9ae3/frontend-elements"
                        + "/error.css\\:13e8ca508d2063e9504497b4953f9ae3"));
        assertTrue(indexFileContent
                .contains("modules/stageassistent/img/close.gif=d08/fcb52992b3e6da757c4e7778e70c1/stageassistent"
                        + "/close.gif\\:d08fcb52992b3e6da757c4e7778e70c1"));

        assertEquals(14 + 1, baseDirectory.list().size());
    }

    @Test
    public void testIncludedExcludes() throws IOException {
        Index index;
        Filter config = new Filter();
        config.setIncludes("*.jpg", "*.gif");
        config.setExcludes("**/close.gif");

        resourcePublisher.extract(new PustefixExtractor(config, war.toPath().toFile()));
        index = distributor.close();

        Collection<String> indexFileContent = indexLines(index);
        assertEquals(1 + 7 + 1, indexFileContent.size()); // contains properties header line
        assertFalse(indexFileContent
                .contains("modules/frontend-tools/img/delete.png=684/61ca5187cd2c6af08786467085f2b/frontend-tools"
                        + "/delete.png\\:68461ca5187cd2c6af08786467085f2b"));
        assertFalse(indexFileContent
                .contains("modules/frontend-elements/style/error.css=13e/8ca508d2063e9504497b4953f9ae3/frontend-elements"
                        + "/error.css\\:13e8ca508d2063e9504497b4953f9ae3"));
        assertFalse(indexFileContent
                .contains("modules/stageassistent/img/close.gif=d08/fcb52992b3e6da757c4e7778e70c1/stageassistent"
                        + "/close.gif\\:d08fcb52992b3e6da757c4e7778e70c1"));

        assertEquals(7 + 1, baseDirectory.list().size());
    }

    public static final Separator LF = Separator.on("\n").trim(LineFormat.Trim.SEPARATOR);

    private List<String> indexLines(Index index) {
        StringWriter str;

        str = new StringWriter();
        try {
            index.save(str);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return LF.split(str.toString());
    }

}