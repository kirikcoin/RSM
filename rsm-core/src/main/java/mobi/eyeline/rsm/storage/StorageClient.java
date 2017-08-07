package mobi.eyeline.rsm.storage;

import java.io.Closeable;
import java.util.concurrent.Future;

public interface StorageClient extends Closeable {

  Future<Boolean> setIfAbsent(String key, int lifetimeSeconds, byte[] payload);

  Future<Boolean> setIfAbsent(String key, byte[] payload);

  Future<Boolean> set(String key, int lifetimeSeconds, byte[] o);
    
  Future<byte[]> get(String key);

  @SuppressWarnings("UnusedReturnValue")
  Future<Boolean> delete(String key);

  void close();
}