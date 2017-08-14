package mobi.eyeline.rsm.pack;

import mobi.eyeline.rsm.model.PersistableSession;
import mobi.eyeline.rsm.model.PersistedSession;
import mobi.eyeline.rsm.model.PersistedSessionMetadata;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ValueFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Notes on Redis `cmsgpack` compatibility:
 *
 * <ol>
 *   <li>Avoid using byte arrays due to encoding differences.
 * </ol>
 */
public class SessionSerializer {

  private static final Logger log = Logger.getLogger(SessionSerializer.class.getName());

  /** Context classloader, used to deserialize application-specific attribute types. */
  @SuppressWarnings({"FieldCanBeLocal", "unused"})
  private final ClassLoader loader;

  public SessionSerializer(ClassLoader loader) {
    this.loader = loader;
  }

  public long attributesHashFrom(Map<String, Object> attributes) throws IOException {
    final MessageBufferPacker pack = MessagePack.newDefaultBufferPacker();
    pack.packValue(MsgPackUtil.asValue(attributes));

    final byte[] digest = getMessageDigest().digest(pack.toByteArray());
    return new BigInteger(digest).longValue();
  }

  private byte[] serialize(PersistedSession session,
                           PersistedSessionMetadata metadata) throws IOException {

    final MessageBufferPacker pack = MessagePack.newDefaultBufferPacker();

    // Write metadata.

    pack.packValue(ValueFactory.newInteger(metadata.getAttrHash()));

    // Write session.

    pack.packLong(session.creationTime);
    pack.packLong(session.lastAccessedTime);
    pack.packInt(session.maxInactiveInterval);
    pack.packBoolean(session.isNew);
    pack.packBoolean(session.isValid);
    pack.packLong(session.thisAccessedTime);

    pack.packValue(MsgPackUtil.asValue(session.id));
    pack.packValue(MsgPackUtil.asValue(session.principalName));

    pack.packValue(MsgPackUtil.asValue(session.principalRoles));
    pack.packValue(MsgPackUtil.asValue(session.attributes));

    return pack.toByteArray();
  }

  public byte[] serialize(PersistableSession session,
                          PersistedSessionMetadata metadata) throws IOException {

    return serialize(session.asPersistedSession(), metadata);
  }

  private PersistedSession deserialize(byte[] data,
                                       PersistedSessionMetadata metadata) throws IOException {

    final MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data);

    // Read metadata.

    metadata.setAttrHash(unpacker.unpackValue().asNumberValue().toLong());

    // Read session.

    final PersistedSession session = new PersistedSession();

    session.creationTime = unpacker.unpackLong();
    session.lastAccessedTime = unpacker.unpackLong();
    session.maxInactiveInterval = unpacker.unpackInt();
    session.isNew = unpacker.unpackBoolean();
    session.isValid = unpacker.unpackBoolean();
    session.thisAccessedTime = unpacker.unpackLong();

    session.id = MsgPackUtil.asObject(unpacker.unpackValue());

    session.principalName = MsgPackUtil.asObject(unpacker.unpackValue());
    session.principalRoles = MsgPackUtil.asObject(unpacker.unpackValue());

    session.attributes = MsgPackUtil.asObject(unpacker.unpackValue());

    return session;
  }

  public <T extends PersistableSession> void deserialize(
      byte[] data,
      T session,
      PersistedSessionMetadata metadata) throws IOException {

    final PersistedSession persisted = deserialize(data, metadata);
    session.fromPersistedSession(persisted);
  }

  private static MessageDigest getMessageDigest() {
    try {
      return MessageDigest.getInstance("MD5");

    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
