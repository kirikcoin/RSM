package mobi.eyeline.rsm;

import java.util.Arrays;

public enum PersistenceStrategy {

  /** Persist on change (`dirty' flag set, attributes hash change etc.) */
  ON_CHANGE,

  /** Persist on every request. */
  ALWAYS;

  public static PersistenceStrategy fromName(String name) {
    return Arrays.stream(PersistenceStrategy.values())
        .filter(policy -> policy.name().equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid session persist policy: [" + name + "]"));
  }
}
