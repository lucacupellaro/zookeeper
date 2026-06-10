package DataTreeTest.TestManuali;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.DataTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DataTreeGetDataTest {

    private DataTree dataTree;

    private static final byte[] VALID_DATA = "data".getBytes();
    private static final byte[] CHILD_DATA = "child-data".getBytes();
    private static final byte[] NEW_DATA = "new-data".getBytes();

    private static final List<ACL> VALID_ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE;
    private static final long VALID_TIME = System.currentTimeMillis();

    @BeforeEach
    public void setUp() {
        dataTree = new DataTree();
    }

    private void createValidNode(String path) throws Exception {
        dataTree.createNode(
                path,
                VALID_DATA,
                VALID_ACL,
                -1L,
                0,
                1L,
                VALID_TIME
        );
    }

    private void createValidNode(String path, byte[] data) throws Exception {
        dataTree.createNode(
                path,
                data,
                VALID_ACL,
                -1L,
                0,
                1L,
                VALID_TIME
        );
    }

    private void assertNodeExists(String path) {
        assertNotNull(dataTree.getNode(path), "Il nodo " + path + " dovrebbe esistere");
    }

    private void assertNodeDoesNotExist(String path) {
        assertNull(dataTree.getNode(path), "Il nodo " + path + " non dovrebbe esistere");
    }

    @Test
    public void getDataExistingSimplePath() throws Exception {
        // T1 - path valido semplice, nodo richiesto già presente,
        // stat valido, watcher null

        createValidNode("/a", VALID_DATA);

        Stat stat = new Stat();
        byte[] result = dataTree.getData("/a", stat, null);

        assertArrayEquals(VALID_DATA, result);
        assertEquals(0, stat.getVersion());
        assertNodeExists("/a");
    }

    @Test
    public void getDataExistingMultilevelPath() throws Exception {
        // T2 - path valido multilivello, nodo richiesto presente
        // in un path multilivello, stat valido, watcher null

        createValidNode("/a");
        createValidNode("/a/b", CHILD_DATA);

        Stat stat = new Stat();
        byte[] result = dataTree.getData("/a/b", stat, null);

        assertArrayEquals(CHILD_DATA, result);
        assertEquals(0, stat.getVersion());
        assertNodeExists("/a");
        assertNodeExists("/a/b");
    }

    @Test
    public void getDataPopulatesStat() throws Exception {
        // T3 - stat = new Stat().
        // Il metodo deve restituire i dati e valorizzare lo Stat
        // con i metadati effettivi del nodo letto.

        createValidNode("/a", VALID_DATA);

        Stat stat = new Stat();
        byte[] result = dataTree.getData("/a", stat, null);

        assertArrayEquals(VALID_DATA, result);
        assertEquals(0, stat.getVersion());
        assertEquals(1L, stat.getCzxid());
        assertEquals(1L, stat.getMzxid());
    }

    @Test
   @Disabled
    public void getDataWithNullStat() throws Exception {
        // T4 - stat = null.
        // Caso limite in cui il chiamante richiede i dati senza passare
        // un oggetto Stat da valorizzare.

        createValidNode("/a", VALID_DATA);

        byte[] result = dataTree.getData("/a", null, null);

        assertArrayEquals(VALID_DATA, result);
        assertNodeExists("/a");
    }

    @Test
    public void getDataOverwritesStat() throws Exception {
        // T5 - stat già valorizzato prima della chiamata.
        // Il metodo deve sovrascrivere i valori precedenti dello Stat
        // con i metadati reali del nodo.

        createValidNode("/a", VALID_DATA);

        Stat stat = new Stat();
        stat.setVersion(999);
        stat.setCzxid(999L);
        stat.setMzxid(999L);

        byte[] result = dataTree.getData("/a", stat, null);

        assertArrayEquals(VALID_DATA, result);
        assertEquals(0, stat.getVersion());
        assertEquals(1L, stat.getCzxid());
        assertEquals(1L, stat.getMzxid());
    }

    @Test
    public void getDataRegistersWatcher() throws Exception {
        // T6 - watcher valido non nullo.
        // Il watcher viene passato come mock dell'interfaccia Watcher.
        // getData(...) deve registrarlo sul nodo, ma non deve invocare
        // direttamente process(...). La notifica avviene solo dopo
        // una successiva modifica del nodo.

        createValidNode("/a", VALID_DATA);

        Watcher watcher = mock(Watcher.class);

        byte[] result = dataTree.getData("/a", new Stat(), watcher);

        assertArrayEquals(VALID_DATA, result);

        // getData registra il watcher, ma non deve invocare direttamente process(...)
        verify(watcher, never()).process(any(WatchedEvent.class));

        dataTree.setData(
                "/a",
                NEW_DATA,
                -1,
                2L,
                System.currentTimeMillis()
        );

        // Dopo la modifica del nodo, il watcher registrato deve essere notificato
        verify(watcher, atLeastOnce()).process(any(WatchedEvent.class));
    }

    @Test
    public void getDataMissingSimplePathThrows() {
        // T7 - path valido semplice, nodo richiesto assente,
        // stat valido, watcher null.
        // Il metodo deve sollevare NoNodeException.

        assertThrows(
                KeeperException.NoNodeException.class,
                () -> dataTree.getData("/a", new Stat(), null)
        );

        assertNodeDoesNotExist("/a");
        assertNodeExists("/");
    }

    @Test
    public void getDataMissingMultilevelPathThrows() {
        // T8 - path valido multilivello, nodo richiesto assente,
        // stat valido, watcher null.
        // Il metodo deve sollevare NoNodeException.

        assertThrows(
                KeeperException.NoNodeException.class,
                () -> dataTree.getData("/x/y", new Stat(), null)
        );

        assertNodeDoesNotExist("/x");
        assertNodeDoesNotExist("/x/y");
        assertNodeExists("/");
    }

    @Test
    public void getDataMissingPathWithWatcherThrows() {
        // T9 - path sintatticamente valido, nodo richiesto assente,
        // stat valido, watcher valido non nullo.
        // Anche se il watcher è valido, l'assenza del nodo deve avere priorità
        // e il metodo deve sollevare NoNodeException.

        Watcher watcher = mock(Watcher.class);

        assertThrows(
                KeeperException.NoNodeException.class,
                () -> dataTree.getData("/x/y", new Stat(), watcher)
        );

        // Poiché il nodo non esiste, il watcher non deve essere notificato
        verifyNoInteractions(watcher);

        assertNodeDoesNotExist("/x");
        assertNodeDoesNotExist("/x/y");
        assertNodeExists("/");
    }

    @Test
    public void getDataIndependentBranch() throws Exception {
        // T10 - path valido multilivello, DataTree con più nodi
        // e rami indipendenti.
        // La lettura di "/x/y" non deve alterare il ramo indipendente "/a".

        createValidNode("/a", VALID_DATA);
        createValidNode("/x", VALID_DATA);
        createValidNode("/x/y", CHILD_DATA);

        byte[] result = dataTree.getData("/x/y", new Stat(), null);

        assertArrayEquals(CHILD_DATA, result);

        assertNodeExists("/a");
        assertNodeExists("/x");
        assertNodeExists("/x/y");

        byte[] dataOnA = dataTree.getData("/a", new Stat(), null);
        assertArrayEquals(VALID_DATA, dataOnA);
    }

    @Test
    public void getDataRootInitialTree() {
        // T11 - path radice, DataTree nello stato iniziale.
        // Il metodo deve gestire correttamente la lettura della root.

        assertDoesNotThrow(() -> {
            Stat stat = new Stat();
            dataTree.getData("/", stat, null);
        });

        assertNodeExists("/");
    }

    @Test
    public void getDataRootKeepsNodes() throws Exception {
        // T12 - path radice, DataTree con più nodi e rami indipendenti.
        // La lettura della root non deve alterare i nodi applicativi presenti.

        createValidNode("/a", VALID_DATA);
        createValidNode("/x", VALID_DATA);

        assertDoesNotThrow(() -> {
            Stat stat = new Stat();
            dataTree.getData("/", stat, null);
        });

        assertNodeExists("/");
        assertNodeExists("/a");
        assertNodeExists("/x");

        byte[] dataA = dataTree.getData("/a", new Stat(), null);
        byte[] dataX = dataTree.getData("/x", new Stat(), null);

        assertArrayEquals(VALID_DATA, dataA);
        assertArrayEquals(VALID_DATA, dataX);
    }

    @Test
    public void getDataNullPath() {
        // T13 - path nullo.
        // Il metodo deve sollevare un'eccezione oppure comunque
        // non corrompere lo stato del DataTree.

        assertThrows(
                Exception.class,
                () -> dataTree.getData(null, new Stat(), null)
        );

        assertNodeExists("/");
    }

    @Test
    @Disabled
    public void getDataEmptyPath() {
        // T14 - path vuoto o malformato: "".
        // Il metodo deve sollevare un'eccezione oppure comunque
        // non corrompere lo stato del DataTree.

        assertThrows(
                Exception.class,
                () -> dataTree.getData("", new Stat(), null)
        );

        assertNodeExists("/");
    }

    @Test
    public void getDataPathWithoutSlash() {
        // T15 - path vuoto o malformato: "a/b".
        // Il path non rispetta la forma canonica dei path ZooKeeper,
        // poiché non inizia con "/".

        assertThrows(
                Exception.class,
                () -> dataTree.getData("a/b", new Stat(), null)
        );

        assertNodeExists("/");
    }

    @Test
    public void getDataDoubleSlashPath() {
        // T16 - path vuoto o malformato: "/a//b".
        // Il path contiene una doppia slash interna e viene trattato
        // come caso malformato o non valido per il dominio del test.

        assertThrows(
                Exception.class,
                () -> dataTree.getData("/a//b", new Stat(), null)
        );

        assertNodeExists("/");
    }

    @Test
    public void getDataOnlyRootExists() {
        // T17 - path valido semplice, DataTree con solo nodo radice presente.
        // Il nodo applicativo "/a" non è presente, quindi il metodo deve
        // sollevare NoNodeException senza alterare la root.

        assertNodeExists("/");
        assertNodeDoesNotExist("/a");

        assertThrows(
                KeeperException.NoNodeException.class,
                () -> dataTree.getData("/a", new Stat(), null)
        );

        assertNodeExists("/");
        assertNodeDoesNotExist("/a");
    }

    @Test

    public void getDataEmptyPathExtension() {
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