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
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by aske on 28.06.15.
 */
public class SignalServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalServer.class);

    private static final int MAX_LINE_LENGTH = 80;

    private boolean running = false;

    private class SignalServerHandler extends SimpleChannelInboundHandler<Signal> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Signal sig) throws Exception {
            System.out.println("received signal: " + sig.toString());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // Close the connection when an exception is raised.
            cause.printStackTrace();
            ctx.close();
        }
    }

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    public void start(int port) throws InterruptedException {
        synchronized (this) {
            if (running) {
                throw new IllegalStateException("signal server already running");
            }

            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();

            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(
                                    new LineBasedFrameDecoder(MAX_LINE_LENGTH),
                                    new StringDecoder(CharsetUtil.UTF_8),
                                    new SignalDecoder(),
                                    new SignalServerHandler()
                            );
                        }

                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // Bind and start to accept incoming connections.
            b.bind(port).sync();

            LOGGER.info("signal server listening in tcp://localhost:{}", port);
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

    public static void main(String[] args) throws InterruptedException {
        final SignalServer server = new SignalServer();

        LOGGER.info("starting server");
        server.start(8080);
    }
}
