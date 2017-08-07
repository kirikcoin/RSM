package mobi.eyeline.rsm.model;

import mobi.eyeline.rsm.model.PersistedSession;

public interface PersistableSession {

  PersistedSession asPersistedSession();
  void fromPersistedSession(PersistedSession session);

}
