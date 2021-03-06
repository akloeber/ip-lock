/*
 * Copyright (c) 2015 Andreas Klöber
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ipLock;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignalClient implements SignalDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalClient.class);

    private static final int MAX_LINE_LENGTH = 80;

    private EventLoopGroup workerGroup;

    private Channel channel;

    public void connect(int port, final SignalHandler handler) throws InterruptedException {
        workerGroup = new NioEventLoopGroup();

        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(
                    new LineBasedFrameDecoder(MAX_LINE_LENGTH),
                    new StringDecoder(CharsetUtil.UTF_8),
                    new StringEncoder(CharsetUtil.UTF_8),
                    new SignalDecoder(),
                    new SignalEncoder(),
                    new SignalClientHandlerAdapter(handler)
                );
            }
        });

        String host = "localhost";
        channel = b.connect(host, port).sync().channel();
        LOGGER.info("connected to signal server at tcp://{}:{}", host, port);
    }

    public void disconnect() throws InterruptedException {
        workerGroup.shutdownGracefully().sync();
        LOGGER.info("disconnected from signal server");
    }

    @Override
    public void dispatch(Signal sig) {
        try {
            channel.writeAndFlush(sig).sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private class SignalClientHandlerAdapter extends SimpleChannelInboundHandler<Signal> {

        private final SignalHandler handler;

        public SignalClientHandlerAdapter(SignalHandler handler) {
            this.handler = handler;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Signal sig) throws Exception {
            LOGGER.info("process received signal: {}", sig);
            handler.handleSignal(sig);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // Close the connection when an exception is raised.
            LOGGER.error("inbound handler caught upstream error", cause);
            ctx.close();
        }
    }
}
