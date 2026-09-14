package com.botwap.infrastructure.whatsapp;

import io.netty.handler.codec.http.HttpHeaders;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servidor HTTP stub local que emula la Graph API de Meta (solo tests).
 *
 * <p>Permite ejercitar {@link MetaWhatsAppClient} contra HTTP real sin red, sin
 * credenciales reales y sin dependencias nuevas: se levanta en un puerto efímero
 * y su respuesta (status, cuerpo y retardo) es configurable por test.</p>
 *
 * <p>Está construido sobre Reactor Netty, ya presente vía
 * {@code spring-boot-starter-webflux}. No requiere WireMock ni MockWebServer.</p>
 */
final class StubMetaGraphServer implements AutoCloseable {

    /** Petición recibida; solo lo necesario para las aserciones de contrato. */
    record RecordedRequest(String method, String uri, HttpHeaders headers, String body) {

        String header(CharSequence name) {
            return headers.get(name);
        }
    }

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final DisposableServer server;
    private final AtomicReference<RecordedRequest> lastRequest = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    private volatile int status = 200;
    private volatile String responseBody = "{}";
    private volatile Duration responseDelay = Duration.ZERO;

    StubMetaGraphServer() {
        this.server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle(this::handle)
                .bindNow();
    }

    private Publisher<Void> handle(HttpServerRequest request, HttpServerResponse response) {
        return request.receive()
                .aggregate()
                .asString(StandardCharsets.UTF_8)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    requestCount.incrementAndGet();
                    lastRequest.set(new RecordedRequest(
                            request.method().name(), request.uri(), request.requestHeaders(), body));

                    Mono<Void> send = response.status(status)
                            .header(HttpHeaders.Names.CONTENT_TYPE, JSON_CONTENT_TYPE)
                            .sendString(Mono.just(responseBody))
                            .then();

                    return responseDelay.isZero() ? send : Mono.delay(responseDelay).then(send);
                })
                // El cliente puede cerrar la conexión al expirar su timeout.
                .onErrorResume(error -> Mono.empty());
    }

    /** Responde de inmediato con el status y cuerpo indicados. */
    void respond(int status, String body) {
        respondAfter(status, body, Duration.ZERO);
    }

    /**
     * Responde tras un retardo; sirve para forzar el timeout del cliente.
     */
    void respondAfter(int status, String body, Duration delay) {
        this.status = status;
        this.responseBody = body;
        this.responseDelay = delay;
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.port();
    }

    int requestCount() {
        return requestCount.get();
    }

    RecordedRequest lastRequest() {
        return lastRequest.get();
    }

    @Override
    public void close() {
        server.disposeNow();
    }
}