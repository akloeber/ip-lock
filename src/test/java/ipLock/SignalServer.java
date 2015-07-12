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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignalServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalServer.class);

    private static final int MAX_LINE_LENGTH = 80;

    private boolean running = false;

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    private ChannelPipeline pipeline;

    public static void main(String[] args) throws InterruptedException {
        final SignalServer server = new SignalServer();

        LOGGER.info("starting server");
        server.start(8080, new SignalHandler() {

            @Override
            public void handleSignal(Signal sig) {
                System.out.println("received sig: " + sig.toString());
            }
        });
    }

    public void send(Signal sig) throws InterruptedException {
        if (!running) {
            throw new IllegalStateException("server is not running");
        }

        pipeline.writeAndFlush(sig).sync();
        LOGGER.info("sent signal: {}", sig);
    }

    public void start(final int port, final SignalHandler handler) throws InterruptedException {
        synchronized (this) {
            if (running) {
                throw new IllegalStateException("signal server already running");
            }

            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();

            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new SignalChannelInitializer(handler))
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

            // Bind and start to accept incoming connections.
            b.bind(port).sync();

            LOGGER.info("signal server listening on tcp://localhost:{}", port);
            running = true;
        }
    }

    public void stop() throws InterruptedException {
        synchronized (this) {
            if (!running) {
                throw new IllegalStateException("signal server not running");
            }

            LOGGER.info("shutting down signal server");
            workerGroup.shutdownGracefully().sync();
            bossGroup.shutdownGracefully().sync();
            running = false;
        }
    }

    private class SignalChannelInitializer extends ChannelInitializer<SocketChannel> {

        private SignalHandler handler;

        public SignalChannelInitializer(SignalHandler handler) {
            this.handler = handler;
        }

        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline p = ch.pipeline();
            p.addLast(
                new LineBasedFrameDecoder(MAX_LINE_LENGTH),
                new StringDecoder(CharsetUtil.UTF_8),
                new StringEncoder(CharsetUtil.UTF_8),
                new SignalEncoder(),
                new SignalDecoder(),
                new SignalHandlerAdapter(handler)
            );

            pipeline = p;
        }

    }

}
