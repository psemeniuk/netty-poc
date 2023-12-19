package com.psemeniuk;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.netty4.NettyEventExecutorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.netty.util.concurrent.EventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

public class Application {

    public static final Logger log = LoggerFactory.getLogger(Application.class);

    private static final int PORT = 8080;

    public static void main(String[] args) throws InterruptedException {
        PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics.globalRegistry.add(prometheusRegistry);
        MeasureWaitTimeSelectorProvider selectorProvider = new MeasureWaitTimeSelectorProvider();
        EventLoopGroup bossGroup = new NioEventLoopGroup(1, (Executor) null, selectorProvider);

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpServerExpectContinueHandler());
                            pipeline.addLast(new HttpObjectAggregator(1024 * 1024));
                            pipeline.addLast(new HelloWorldHandler());
                        }
                    });

            Channel channel = serverBootstrap.bind(PORT).sync().channel();
            setupMicrometer(bossGroup, prometheusRegistry);

            log.info("Server started at http://127.0.0.1:{}/", PORT);
            channel.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
        }
    }

    private static void setupMicrometer(Iterable<EventExecutor> eventExecutors, PrometheusMeterRegistry registry) {

        new NettyEventExecutorMetrics(eventExecutors).bindTo(registry);

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
            server.createContext("/prometheus", httpExchange -> {
                String response = registry.scrape();
                httpExchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = httpExchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });

            new Thread(server::start).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
