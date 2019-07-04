package ru.kontur.vostok.hercules.sentry.sink;

import ru.kontur.vostok.hercules.http.HttpStatusCodes;
import ru.kontur.vostok.hercules.kafka.util.processing.BackendServiceFailedException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Contains information about error.
 * An object of this class is created in case of error and is needed to resolve what should be done in this case
 *
 * @author Petr Demenev
 */
public class ErrorInfo {

    private String message;
    private int code;
    private Boolean isRetryable;
    private long waitingTimeMs;

    public ErrorInfo(String message) {
        this.message = message;
    }

    public ErrorInfo(String message, int code) {
        this.message = message;
        this.code = code;
    }

    public ErrorInfo(boolean isRetryable) {
        this.isRetryable = isRetryable;
    }

    public ErrorInfo(boolean isRetryable, long waitingTimeMs) {
        this.isRetryable = isRetryable;
        this.waitingTimeMs = waitingTimeMs;
    }


    public ErrorInfo(int code) {
        this.code = code;
    }

    public ErrorInfo(int code, long waitingTimeMs) {
        this.code = code;
        this.waitingTimeMs = waitingTimeMs;
    }

    public long getWaitingTimeMs() {
        return waitingTimeMs;
    }

    public boolean isRetryable() {
        return isRetryable;
    }

    /**
     * Set the "isRetryable" field using the http code of the error
     * on the stage of Sentry client getting or creation
     *
     * @throws BackendServiceFailedException if the error is not "retryable" or "non retryable"
     */
    public void setIsRetryableForApiClient() throws BackendServiceFailedException {
        if (isRetryable != null) {
            return;
        }
        if (RETRYABLE_ERROR_CODES_FOR_API_CLIENT.contains(code) || code >= HttpStatusCodes.INTERNAL_SERVER_ERROR) {
            this.isRetryable = true;
        } else if (NON_RETRYABLE_ERROR_CODES_FOR_API_CLIENT.contains(code)) {
            this.isRetryable = false;
        } else {
            throw new BackendServiceFailedException();
        }
    }

    /**
     * @return true if need drop the event after unsuccessful retry
     */
    public boolean needDropAfterRetry() {
        return code == HttpStatusCodes.CONFLICT;
    }

    /**
     * Set the "isRetryable" field using the http code of the error
     * on the stage of event converting and sending to Sentry
     *
     * @throws BackendServiceFailedException if the error is not "retryable" or "non retryable"
     */
    public void setIsRetryableForSending() throws BackendServiceFailedException {
        if (isRetryable != null) {
            return;
        }
        if (RETRYABLE_ERROR_CODES_FOR_SENDING.contains(code) || code >= HttpStatusCodes.INTERNAL_SERVER_ERROR) {
            this.isRetryable = true;
        } else if (NON_RETRYABLE_ERROR_CODES_FOR_SENDING.contains(code)) {
            this.isRetryable = false;
        } else {
            throw new BackendServiceFailedException();
        }
    }

    /**
     * Indicate that the cache contains Sentry client which does not work with Sentry
     *
     * @return true if need to remove Sentry client from the cache
     */
    public boolean needToRemoveClientFromCache() {
        return ERROR_CODES_NEED_TO_REMOVE_CLIENT_FROM_CACHE.contains(code);
    }

    private static final Set<Integer> RETRYABLE_ERROR_CODES_FOR_API_CLIENT = new HashSet<>(Arrays.asList(
            HttpStatusCodes.NOT_FOUND,
            HttpStatusCodes.REQUEST_TIMEOUT,
            HttpStatusCodes.CONFLICT,
            HttpStatusCodes.TOO_MANY_REQUESTS
    ));

    private static final Set<Integer> NON_RETRYABLE_ERROR_CODES_FOR_API_CLIENT = new HashSet<>(Arrays.asList(
            HttpStatusCodes.BAD_REQUEST,
            HttpStatusCodes.METHOD_NOT_ALLOWED,
            HttpStatusCodes.REQUEST_ENTITY_TOO_LARGE,
            HttpStatusCodes.URI_TOO_LONG,
            HttpStatusCodes.UNSUPPORTED_MEDIA_TYPE
    ));

    private static final Set<Integer> RETRYABLE_ERROR_CODES_FOR_SENDING = new HashSet<>(Arrays.asList(
            HttpStatusCodes.UNAUTHORIZED,
            HttpStatusCodes.FORBIDDEN,
            HttpStatusCodes.NOT_FOUND,
            HttpStatusCodes.REQUEST_TIMEOUT,
            HttpStatusCodes.TOO_MANY_REQUESTS
    ));

    private static final Set<Integer> NON_RETRYABLE_ERROR_CODES_FOR_SENDING = new HashSet<>(Arrays.asList(
            HttpStatusCodes.BAD_REQUEST,
            HttpStatusCodes.METHOD_NOT_ALLOWED,
            HttpStatusCodes.REQUEST_ENTITY_TOO_LARGE,
            HttpStatusCodes.URI_TOO_LONG,
            HttpStatusCodes.UNSUPPORTED_MEDIA_TYPE
    ));

    private static final Set<Integer> ERROR_CODES_NEED_TO_REMOVE_CLIENT_FROM_CACHE = new HashSet<>(Arrays.asList(
            HttpStatusCodes.UNAUTHORIZED,
            HttpStatusCodes.FORBIDDEN,
            HttpStatusCodes.NOT_FOUND
    ));

    @Override
    public String toString() {
        String string = "";
        if (code != 0) {
            string += code + " ";
        }
        if (message != null) {
            string += message;
        }
        return string;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ErrorInfo errorInfo = (ErrorInfo) o;
        return code == errorInfo.code &&
                waitingTimeMs == errorInfo.waitingTimeMs &&
                Objects.equals(message, errorInfo.message) &&
                Objects.equals(isRetryable, errorInfo.isRetryable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, code, isRetryable, waitingTimeMs);
    }
}
