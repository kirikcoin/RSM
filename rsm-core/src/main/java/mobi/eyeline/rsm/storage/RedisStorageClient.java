package mobi.eyeline.rsm.storage;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static redis.clients.util.SafeEncoder.encode;

public class RedisStorageClient implements StorageClient {

  private final Logger log = Logger.getLogger(getClass().getName());

  private final JedisPool jedisPool;

  public RedisStorageClient(String redisUrl, int timeout) {
    requireNonNull(redisUrl, "Redis database URL not set");

    timeout = timeout <= 0 ? Protocol.DEFAULT_TIMEOUT : timeout;

    try {
      final URI uri = new URI(redisUrl);
      jedisPool = new JedisPool(uri, timeout);

    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Future<Boolean> setIfAbsent(final String key, final int lifetimeSeconds, final byte[] payload) {
    if (log.isLoggable(Level.FINE)) {
      log.fine("setIfAbsent:" +
          " key = [" + key + "]," +
          " lifetimeSeconds = [" + lifetimeSeconds + "]," +
          " payload size = " + (payload == null ? "null" : payload.length));
    }

    return submit(jedis -> {
      if (jedis.setnx(encode(key), payload) != 1) {
        return false;
      }

      return lifetimeSeconds == 0 || jedis.expire(key, lifetimeSeconds) == 1;
    });
  }

  @Override
  public Future<Boolean> setIfAbsent(final String key, final byte[] payload) {
    if (log.isLoggable(Level.FINE)) {
      log.fine("setIfAbsent: key = [" + key + "]");
    }

    return submit(jedis -> jedis.setnx(encode(key), payload) == 1);
  }

  @Override
  public Future<Boolean> set(final String key, final int lifetimeSeconds, final byte[] payload) {
    if (log.isLoggable(Level.FINE)) {
      log.fine("set:" +
          " key = [" + key + "]," +
          " lifetimeSeconds = [" + lifetimeSeconds + "]," +
          " payload size = " + (payload == null ? "null" : payload.length));
    }

    return submit(jedis -> lifetimeSeconds == 0 ?
        jedis.set(encode(key), payload).equals("OK") :
        jedis.setex(encode(key), lifetimeSeconds, payload).equals("OK"));
  }

  @Override
  public Future<byte[]> get(final String key) {
    if (log.isLoggable(Level.FINE)) {
      log.fine("get: key = [" + key + "]");
    }

    return submit(jedis -> jedis.get(encode(key)));
  }

  @Override
  public Future<Boolean> delete(final String key) {
    if (log.isLoggable(Level.FINE)) {
      log.fine("delete: key = [" + key + "]");
    }

    return submit( jedis -> jedis.del(key) == 1);
  }

  @Override
  public void close() {
    jedisPool.close();
  }

  private <T> Future<T> submit(Function<Jedis, T> task) {
    final long startMillis = log.isLoggable(Level.FINER) ? System.currentTimeMillis() : 0;

    try (Jedis jedis = jedisPool.getResource()) {
      return ImmediateFuture.completedFuture(task.apply(jedis));

    } catch (Exception e) {
      log.log(Level.WARNING, "Failed executing command", e);
      return ImmediateFuture.failedFuture(e);

    } finally {
      if (startMillis > 0) {
        log.finer("Operation time, ms.: " + (System.currentTimeMillis() - startMillis));
      }
    }
  }


  //
  //
  //

  private static class ImmediateFuture<T> implements Future<T> {
    private final T value;
    private final Throwable failure;

    private ImmediateFuture(T v, Throwable failure) {
      this.value = v;
      this.failure = failure;
    }

    static <T> ImmediateFuture<T> completedFuture(T value) {
      return new ImmediateFuture<>(value, null);
    }

    static <T> ImmediateFuture<T> failedFuture(Throwable e) {
      return new ImmediateFuture<>(null, requireNonNull(e));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public T get() throws ExecutionException {
      if (this.failure != null) {
        throw new ExecutionException(this.failure);

      } else {
        return this.value;
      }
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws ExecutionException {
      return this.get();
    }
  }

}
