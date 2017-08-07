package mobi.eyeline.rsm.pack;

import org.msgpack.value.ExtensionValue;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

class MsgPackUtil {

  private static final Logger log = Logger.getLogger(SessionSerializer.class.getName());

  static Value asValue(Object v) {

    if (v == null) {
      return ValueFactory.newNil();
    }

    if (v instanceof Integer) {
      return ValueFactory.newInteger((Integer) v);
    }

    if (v instanceof Long) {
      return ValueFactory.newInteger((Long) v);
    }

    if (v instanceof String) {
      return ValueFactory.newString((String) v);
    }

    if (v instanceof Boolean) {
      return ValueFactory.newBoolean((boolean) v);
    }

    if (v instanceof UUID) {
      return UUIDValue.newUUID((UUID) v);
    }

    if (v instanceof Collection) {
      final Collection<?> collection = (Collection) v;

      return ValueFactory.newArray(
          collection
              .stream()
              .map(MsgPackUtil::asValue)
              .collect(Collectors.toList())
      );
    }

    if (v instanceof Map) {
      final Map<?, ?> map = (Map) v;

      final ValueFactory.MapBuilder builder = ValueFactory.newMapBuilder();
      map.forEach((k, kv) -> builder.put(asValue(k), asValue(kv)));
      return builder.build();
    }

    if (v.getClass().isArray()) {
      final Object[] array = (Object[]) v;
      return ValueFactory.newArray(
          Arrays.stream(array).map(MsgPackUtil::asValue).toArray(Value[]::new)
      );
    }

    log.warning("Object [" + v + "] cannot be serialized: the type is not supported");

    return ValueFactory.newNil();
  }

  @SuppressWarnings("unchecked")
  static <T> T asObject(Value v) {
    if (v.isNilValue()) {
      return null;
    }

    if (v.isNumberValue()) {
      return (T) (Long) v.asNumberValue().toLong();
    }

    if (v.isStringValue()) {
      return (T) v.asStringValue().asString();
    }

    if (v.isBooleanValue()) {
      return (T) (Boolean) v.asBooleanValue().getBoolean();
    }

    if (v.isExtensionValue()) {
      final ExtensionValue extValue = v.asExtensionValue();

      if (extValue.getType() == UUIDValue.TYPE) {
        return (T) UUIDValue.uuidFromBytes(extValue.getData());
      }
    }

    if (v.isArrayValue()) {
      final List oArray = v
          .asArrayValue()
          .list()
          .stream()
          .map(MsgPackUtil::asObject)
          .collect(Collectors.toList());

      if (!oArray.isEmpty()) {
        return (T) oArray.toArray(
            (Object[]) Array.newInstance(oArray.get(0).getClass(), oArray.size())
        );

      } else {
        // We're screwed.
        return (T) oArray;
      }
    }

    if (v.isMapValue()) {
      return (T) v
          .asMapValue()
          .map()
          .entrySet()
          .stream()
          .collect(
              toMap(
                  o -> asObject(o.getKey()),
                  o -> asObject(o.getValue())
              )
          );
    }

    log.warning("Value [" + v + "] cannot be deserialized: the type is not supported");
    return null;
  }
}
