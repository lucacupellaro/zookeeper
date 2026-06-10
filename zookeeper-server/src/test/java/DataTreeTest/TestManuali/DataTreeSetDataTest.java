package DataTreeTest.TestManuali;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.DataTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class DataTreeSetDataTest {

    private DataTree dataTree;

    private static final byte[] INITIAL_DATA = "old-data".getBytes();
    private static final byte[] NEW_DATA = "new-data".getBytes();
    private static final List<ACL> VALID_ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE;
    private static final long VALID_TIME = System.currentTimeMillis();

    @BeforeEach
    public void setUp() {
        dataTree = new DataTree();
    }

    private void createValidNode(String path)
            throws KeeperException.NoNodeException, KeeperException.NodeExistsException {

        dataTree.createNode(
                path,
                INITIAL_DATA,
                VALID_ACL,
                -1L,
                0,
                1L,
                VALID_TIME
        );
    }

    private byte[] getNodeData(String path) throws KeeperException.NoNodeException {
        Stat stat = new Stat();
        return dataTree.getData(path, stat, null);
    }

    private void assertNodeExists(String path) {
        assertNotNull(dataTree.getNode(path), "Il nodo " + path + " dovrebbe esistere");
    }

    private void assertNodeDoesNotExist(String path) {
        assertNull(dataTree.getNode(path), "Il nodo " + path + " non dovrebbe esistere");
    }

    private void assertDataEquals(String path, byte[] expectedData) throws KeeperException.NoNodeException {
        byte[] actualData = getNodeData(path);

        if (expectedData == null) {
            assertNull(actualData, "I dati del nodo " + path + " dovrebbero essere null");
        } else {
            assertArrayEquals(expectedData, actualData, "I dati del nodo " + path + " non sono corretti");
        }
    }

    private void assertTreeStillUsable() throws Exception {
        createValidNode("/safe");
        assertNodeExists("/safe");
        assertDataEquals("/safe", INITIAL_DATA);
    }

    static Stream<Arguments> validSetDataParameters() {
        return Stream.of(
                // T1 - path valido semplice, nodo da modificare presente, data valida, version = 0, zxid = 1L
                Arguments.of(
                        Arrays.asList("/a"),
                        "/a",
                        NEW_DATA,
                        0,
                        1L,
                        200L,
                        Collections.emptyList()
                ),

                // T2 - path valido multilivello, nodo da modificare presente in un path multilivello
                Arguments.of(
                        Arrays.asList("/a", "/a/b"),
                        "/a/b",
                        "new-child-data".getBytes(),
                        0,
                        1L,
                        200L,
                        Arrays.asList("/a")
                ),

                // T9 - path valido semplice, nodo presente, data = new byte[0]
                Arguments.of(
                        Arrays.asList("/a"),
                        "/a",
                        new byte[0],
                        0,
                        1L,
                        200L,
                        Collections.emptyList()
                ),

                // T10 - path valido semplice, nodo presente, data = null
                Arguments.of(
                        Arrays.asList("/a"),
                        "/a",
                        null,
                        0,
                        1L,
                        200L,
                        Collections.emptyList()
                ),

                // T11 - path valido semplice, nodo presente, data di dimensione elevata
                Arguments.of(
                        Arrays.asList("/a"),
                        "/a",
                        new byte[1024 * 1024],
                        0,
                        1L,
                        200L,
                        Collections.emptyList()
                ),

                // T12 - path valido semplice, nodo presente, version = 1
                Arguments.of(
                        Arrays.asList("/a"),
                        "/a",
                        NEW_DATA,
                        1,
                        1L,
                        200L,
                        Collections.emptyList()
                ),

                // T13 - path valido semplice, nodo presente, version = -1
                Arguments.of(
                        Arrays.asList("/a"),
                        "/a",
                        NEW_DATA,
                        -1,
                        1L,
                        200L,
                        Collections.emptyList()
                ),

                // T14 - path valido semplice, nodo presente, zxid = 0L
                Arguments.of(
                        Arrays.asList("/a"),
                        "/a",
                        NEW_DATA,
                        0,
                        0L,
                        200L,
                        Collections.emptyList()
                ),

                // T15 - path valido semplice, nodo presente, zxid = -1L
                Arguments.of(
                        Arrays.asList("/a"),
                        "/a",
                        NEW_DATA,
                        0,
                        -1L,
                        200L,
                        Collections.emptyList()
                ),

                // T16 - path valido semplice, nodo presente, time = System.currentTimeMillis()
                Arguments.of(
                        Arrays.asList("/a"),
                        "/a",
                        NEW_DATA,
                        0,
                        1L,
                        System.currentTimeMillis(),
                        Collections.emptyList()
                ),

                // T17 - path valido semplice, nodo presente, time = 0L
                Arguments.of(
                        Arrays.asList("/a"),
                        "/a",
                        NEW_DATA,
                        0,
                        1L,
                        0L,
                        Collections.emptyList()
                ),

                // T18 - path valido semplice, nodo presente, time = -1L
                Arguments.of(
                        Arrays.asList("/a"),
                        "/a",
                        NEW_DATA,
                        0,
                        1L,
                        -1L,
                        Collections.emptyList()
                ),

                // T20 - path valido multilivello, DataTree con più rami indipendenti, modifica solo del ramo target
                Arguments.of(
                        Arrays.asList("/a", "/x", "/x/y"),
                        "/x/y",
                        "new-y-data".getBytes(),
                        0,
                        1L,
                        200L,
                        Arrays.asList("/a", "/x")
                )
        );
    }

    @ParameterizedTest(name = "{index}: setData({1})")
    @MethodSource("validSetDataParameters")
    public void setDataUpdatesNodeAndStat(
            List<String> initialPaths,
            String pathToUpdate,
            byte[] newData,
            int version,
            long zxid,
            long time,
            List<String> unchangedPaths
    ) throws Exception {

        for (String initialPath : initialPaths) {
            createValidNode(initialPath);
        }

        Stat returnedStat = dataTree.setData(
                pathToUpdate,
                newData,
                version,
                zxid,
                time
        );

        assertNotNull(returnedStat, "setData dovrebbe restituire uno Stat");

        assertNodeExists(pathToUpdate);
        assertDataEquals(pathToUpdate, newData);

        assertEquals(version, returnedStat.getVersion(), "La version dello Stat non è corretta");
        assertEquals(zxid, returnedStat.getMzxid(), "Lo mzxid dello Stat non è corretto");
        assertEquals(time, returnedStat.getMtime(), "Lo mtime dello Stat non è corretto");

        for (String unchangedPath : unchangedPaths) {
            assertNodeExists(unchangedPath);
            assertDataEquals(unchangedPath, INITIAL_DATA);
        }
    }

    @Test
    public void setDataMissingMultilevelThrows() {
        // T3 - path valido multilivello, nodo da modificare assente, attesa NoNodeException

        assertThrows(
                KeeperException.NoNodeException.class,
                () -> dataTree.setData(
                        "/x/y",
                        NEW_DATA,
                        0,
                        1L,
                        200L
                )
        );

        assertNodeDoesNotExist("/x/y");
    }

    @Test
    public void setDataOnRoot() throws Exception {
        // T4 - path radice, DataTree nello stato iniziale, aggiornamento root oppure gestione corretta del caso limite

        try {
            Stat returnedStat = dataTree.setData(
                    "/",
                    NEW_DATA,
                    0,
                    1L,
                    200L
            );

            assertNotNull(returnedStat);
            assertDataEquals("/", NEW_DATA);

        } catch (Exception ignored) {
            assertTreeStillUsable();
        }
    }

    @Test
    public void setDataNullPath() throws Exception {
        // T5 - path nullo, DataTree nello stato iniziale, eccezione oppure stato non corrotto

        assertThrows(
                Exception.class,
                () -> dataTree.setData(
                        null,
                        NEW_DATA,
                        0,
                        1L,
                        200L
                )
        );

        assertTreeStillUsable();
    }

    @Test
    public void setDataEmptyPath() throws Exception {
        // T6 - path vuoto, DataTree nello stato iniziale, eccezione oppure stato non corrotto

        try {
            dataTree.setData(
                    "",
                    NEW_DATA,
                    0,
                    1L,
                    200L
            );
        } catch (Exception ignored) {
            // Caso accettabile: path vuoto gestito con eccezione.
        }

        assertTreeStillUsable();
    }

    static Stream<Arguments> malformedPathParameters() {
        return Stream.of(
                // T7 - path malformato senza slash iniziale
                Arguments.of("a/b"),

                // T8 - path malformato con doppio slash
                Arguments.of("/a//b")
        );
    }

    @ParameterizedTest(name = "{index}: malformed path = {0}")
    @MethodSource("malformedPathParameters")
    public void setDataMalformedPath(String malformedPath) {
        assertThrows(
                KeeperException.NoNodeException.class,
                () -> dataTree.setData(
                        malformedPath,
                        NEW_DATA,
                        0,
                        1L,
                        200L
                )
        );

        assertNodeDoesNotExist(malformedPath);
    }

    @Test
    public void setDataEmptyData() throws Exception {
        // Verifica specifica del caso T9 - data = new byte[0]

        createValidNode("/a");

        dataTree.setData(
                "/a",
                new byte[0],
                0,
                1L,
                200L
        );

        byte[] storedData = getNodeData("/a");

        assertNotNull(storedData);
        assertEquals(0, storedData.length);
    }

    @Test
    public void setDataNullData() throws Exception {
        // Verifica specifica del caso T10 - data = null

        createValidNode("/a");

        dataTree.setData(
                "/a",
                null,
                0,
                1L,
                200L
        );

        byte[] storedData = getNodeData("/a");

        assertNull(storedData);
    }

    @Test
    public void setDataLargeData() throws Exception {
        // Verifica specifica del caso T11 - data di dimensione elevata

        byte[] largeData = new byte[1024 * 1024];
        Arrays.fill(largeData, (byte) 7);

        createValidNode("/a");

        dataTree.setData(
                "/a",
                largeData,
                0,
                1L,
                200L
        );

        assertDataEquals("/a", largeData);
    }

    @Test
    public void setDataMissingSimpleThrows() {
        // T19 - path valido semplice, nodo da modificare assente, attesa NoNodeException

        assertThrows(
                KeeperException.NoNodeException.class,
                () -> dataTree.setData(
                        "/a",
                        NEW_DATA,
                        0,
                        1L,
                        200L
                )
        );

        assertNodeDoesNotExist("/a");
    }

    @Test
    public void setDataIndependentBranch() throws Exception {
        // Verifica specifica del caso T20 - modifica di /x/y senza alterare il ramo indipendente /a

        createValidNode("/a");
        createValidNode("/x");
        createValidNode("/x/y");

        dataTree.setData(
                "/x/y",
                "new-y-data".getBytes(),
                0,
                1L,
                200L
        );

        assertNodeExists("/a");
        assertNodeExists("/x");
        assertNodeExists("/x/y");

        assertDataEquals("/a", INITIAL_DATA);
        assertDataEquals("/x", INITIAL_DATA);
        assertDataEquals("/x/y", "new-y-data".getBytes());
    }

    @Test
    public void getDataEmptyPathNotCorrupt() {
        try {
            dataTree.getData("", new Stat(), null);
        } catch (Exception ignored) {
            // In questo test non vincoliamo il tipo di eccezione.
            // Verifichiamo solo che lo stato del DataTree rimanga consistente.
        }

        assertDoesNotThrow(() -> {
            dataTree.createNode(
                    "/valid",
                    "valid-data".getBytes(),
                    VALID_ACL,
                    -1L,
                    0,
                    1L,
                    VALID_TIME
            );

            Stat stat = new Stat();
            byte[] data = dataTree.getData("/valid", stat, null);

            assertArrayEquals("valid-data".getBytes(), data);
            assertNotNull(dataTree.getNode("/valid"));
        });
    }
}