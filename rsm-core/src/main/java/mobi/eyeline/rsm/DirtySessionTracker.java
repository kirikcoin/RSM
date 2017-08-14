package mobi.eyeline.rsm;

public class DirtySessionTracker {

  private boolean dirty = false;

  public void reset() {
    dirty = false;
  }

  public boolean isChanged(Object prevValue, Object value) {
    return (value != null || prevValue != null) &&
        (value == null || prevValue == null || !value.getClass().isInstance(prevValue) || !value.equals(prevValue));
  }

  public void markDirty() {
    dirty = true;
  }

  public boolean isDirty() {
    return dirty;
  }

}
