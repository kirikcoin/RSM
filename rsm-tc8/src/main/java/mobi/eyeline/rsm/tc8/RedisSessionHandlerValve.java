package mobi.eyeline.rsm.tc8;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Optional;


public class RedisSessionHandlerValve extends ValveBase {

  private final Log log = LogFactory.getLog(RedisSessionManager.class);

  private static final ThreadLocal<Boolean> skipSession = ThreadLocal.withInitial(() -> null);

  private RedisSessionManager manager;

  RedisSessionHandlerValve(RedisSessionManager manager) {
    this.manager = manager;
  }

  @SuppressWarnings("unused") // For declarative registration.
  public RedisSessionHandlerValve() {}

  public void setManager(RedisSessionManager manager) {
    this.manager = manager;
  }

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    if (log.isTraceEnabled()) {
      log.trace("Before request: [" + getRequestId(request) + "]");
    }

    onBeforeRequest(request);

    try {
      getNext().invoke(request, response);

    } finally {
      manager.afterRequest();
      skipSession.remove();

      if (log.isTraceEnabled()) {
        log.trace("After request: [" + getRequestId(request) + "]");
      }
    }
  }

  private void onBeforeRequest(Request request) {
    if (manager.getSkipUrlsPattern() != null &&
        manager.getSkipUrlsPattern().matcher(request.getRequestURI()).matches()) {

      if (log.isTraceEnabled()) {
        log.trace("Marking request as session-less");
      }

      skipSession.set(true);
    }
  }

  private static String getRequestId(Request request) {
    final StringBuffer requestURL = request.getRequestURL();
    requestURL.insert(0, request.getMethod() + " ");

    final String queryString = request.getQueryString();
    return queryString == null ?
        requestURL.toString() :
        requestURL.append('?').append(queryString).toString();
  }

  static boolean shouldSkipSession() {
    return Optional.ofNullable(skipSession.get()).orElse(false);
  }
}
