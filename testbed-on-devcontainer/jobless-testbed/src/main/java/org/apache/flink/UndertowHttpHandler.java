/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.flink.statefun.sdk.java.handler.RequestReplyHandler;
import org.apache.flink.statefun.sdk.java.slice.Slice;
import org.apache.flink.statefun.sdk.java.slice.Slices;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * A simple Undertow {@link HttpHandler} that delegates requests from StateFun runtime processes to
 * a StateFun {@link RequestReplyHandler}.
 */
final class UndertowHttpHandler implements HttpHandler {
  private final RequestReplyHandler handler;

  UndertowHttpHandler(RequestReplyHandler handler) {
    this.handler = Objects.requireNonNull(handler);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) {
    exchange.getRequestReceiver().receiveFullBytes(this::onRequestBody);
  }

  private void onRequestBody(HttpServerExchange exchange, byte[] requestBytes) {
    try {
      CompletableFuture<Slice> future = handler.handle(Slices.wrap(requestBytes));
      exchange.dispatch();
      future.whenComplete(
              (responseBytes, ex) -> {
                if (ex != null) {
                  onException(exchange, ex);
                } else {
                  onSuccess(exchange, responseBytes);
                }
              });
    } catch (Throwable t) {
      onException(exchange, t);
    }
  }

  private void onException(HttpServerExchange exchange, Throwable t) {
    t.printStackTrace(System.out);
    exchange.getResponseHeaders().put(Headers.STATUS, 500);
    exchange.endExchange();
  }

  private void onSuccess(HttpServerExchange exchange, Slice result) {
    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/octet-stream");
    exchange.getResponseSender().send(result.asReadOnlyByteBuffer());
  }
}
