package mobi.eyeline.rsm.pack;

import org.msgpack.value.impl.ImmutableExtensionValueImpl;

import java.nio.ByteBuffer;
import java.util.UUID;

class UUIDValue extends ImmutableExtensionValueImpl {

  static final byte TYPE = (byte) 0x01;

  private UUIDValue(byte type, byte[] data) {
    super(type, data);
  }

  static UUIDValue newUUID(UUID uuid) {
    return new UUIDValue(TYPE, uuidToBytes(uuid));
  }

  private static byte[] uuidToBytes(UUID uuid) {
    final long hi = uuid.getMostSignificantBits();
    final long lo = uuid.getLeastSignificantBits();
    return ByteBuffer.allocate(16).putLong(hi).putLong(lo).array();
  }

  static UUID uuidFromBytes(byte[] bytes) {
    final ByteBuffer buf = ByteBuffer.wrap(bytes);
    final long hi = buf.getLong();
    final long lo = buf.getLong();
    return new UUID(hi, lo);
  }
}
