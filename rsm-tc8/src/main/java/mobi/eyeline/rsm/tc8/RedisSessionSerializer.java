package mobi.eyeline.rsm.tc8;

import mobi.eyeline.rsm.pack.SessionSerializer;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;

class RedisSessionSerializer extends SessionSerializer {

  RedisSessionSerializer(ClassLoader loader) {
    super(loader);
  }

  long getAttributesHash(RedisSession session) throws IOException {
    final HashMap<String, Object> attributes = new HashMap<>();
    for (Enumeration<String> e = session.getAttributeNames(); e.hasMoreElements(); ) {
      final String key = e.nextElement();
      attributes.put(key, session.getAttribute(key));
    }

    return super.attributesHashFrom(attributes);
  }
}
