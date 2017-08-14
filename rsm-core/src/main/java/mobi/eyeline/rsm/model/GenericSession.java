package mobi.eyeline.rsm.model;

import java.util.Enumeration;

public interface GenericSession {

  String getId();

  long getCreationTime();
  long getLastAccessedTime();
  int getMaxInactiveInterval();
  boolean isNew();

  Object getAttribute(String name);
  Enumeration<String> getAttributeNames();
  void setAttribute(String name, Object value);
  void removeAttribute(String name);

  void invalidate();

}
