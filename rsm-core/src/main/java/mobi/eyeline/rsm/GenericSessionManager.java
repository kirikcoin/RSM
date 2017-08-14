package mobi.eyeline.rsm;

import mobi.eyeline.rsm.model.GenericSession;

import java.io.IOException;
import java.util.regex.Pattern;

public interface GenericSessionManager<T extends GenericSession> {

  Pattern getSkipUrlsPattern();
  Pattern getSkipAttributesPattern();

  boolean doSaveImmediate();
  boolean doSaveAlways();

  void save(T session, boolean force) throws IOException;
  void remove(T session);
}
