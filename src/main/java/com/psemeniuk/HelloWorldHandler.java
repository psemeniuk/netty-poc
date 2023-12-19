package com.psemeniuk;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

public class HelloWorldHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (request.method() == GET && "/hello".equals(request.uri())) {
            String content = "Hello, World!";
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, OK, Unpooled.copiedBuffer(content, CharsetUtil.UTF_8));

            response.headers().set(CONTENT_TYPE, "text/plain");
            response.headers().set(CONTENT_LENGTH, response.content().readableBytes());

            try {
                Thread.sleep(2); // emulates compute
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            ctx.executor().schedule(() -> {
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            }, 1, TimeUnit.MILLISECONDS); // emulates request to the upstream
        } else {
            ctx.close();
        }
    }
}
