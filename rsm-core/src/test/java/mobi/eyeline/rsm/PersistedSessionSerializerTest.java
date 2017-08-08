package mobi.eyeline.rsm;

import mobi.eyeline.rsm.model.PersistedSession;
import mobi.eyeline.rsm.model.PersistedSessionMetadata;
import mobi.eyeline.rsm.pack.SessionSerializer;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class PersistedSessionSerializerTest {

  private SessionSerializer serializer;

  @Before
  public void setUp() {
    serializer = new SessionSerializer(null);
  }

  @Test
  public void test1() throws Exception {

    //
    //  Write data.
    //

    final byte[] bytes;
    {
      final PersistedSessionMetadata metadata = new PersistedSessionMetadata();
      metadata.setAttrHash(-10L);

      final PersistedSession session = new PersistedSession();
      session.creationTime = 123456L;
      session.isNew = false;
      session.isValid = true;

      session.id = "test-session-id";

      session.principalName = "principal-name";
      session.principalRoles = new String[]{"ROLE-1", "ROLE-2"};

      bytes = serializer.serialize(session, metadata);
    }

    //
    //  Read data and ensure it's OK.
    //

    final PersistedSessionMetadata metadata = new PersistedSessionMetadata();
    final PersistedSession session = new PersistedSession();
    serializer.deserialize(bytes, session, metadata);

    assertEquals(-10L, metadata.getAttrHash());

    assertEquals(123456L, session.creationTime);
    assertEquals("test-session-id", session.id);
    assertArrayEquals(new String[]{"ROLE-1", "ROLE-2"}, session.principalRoles);
  }


  @Test
  public void test2() throws Exception {

    final UUID refUUID = UUID.fromString("43305804-cd5b-4b42-8e40-124743140d00");

    //
    //  Write data.
    //

    final byte[] bytes;
    {
      final PersistedSessionMetadata metadata = new PersistedSessionMetadata();

      final PersistedSession session = new PersistedSession();
      session.attributes = new HashMap<String, Object>() {{
        put("intKey", 1);
        put("longKey", 42L);

        put("stringKey", "foo");

        put("uuidKey", refUUID);
        put("uuidArrayKey", new UUID[] {refUUID});
      }};

      bytes = serializer.serialize(session, metadata);
    }

    //
    //  Read data and ensure it's OK.
    //

    final PersistedSessionMetadata metadata = new PersistedSessionMetadata();
    final PersistedSession session = new PersistedSession();
    serializer.deserialize(bytes, session, metadata);

    // Note: getting numeric values as Long no matter what the initial types were.
    assertEquals(1L, session.attributes.get("intKey"));
    assertEquals(42L, session.attributes.get("longKey"));

    assertEquals("foo", session.attributes.get("stringKey"));
    assertEquals(refUUID, session.attributes.get("uuidKey"));
    assertArrayEquals(new UUID[] { refUUID }, (Object[]) session.attributes.get("uuidArrayKey"));
  }


}