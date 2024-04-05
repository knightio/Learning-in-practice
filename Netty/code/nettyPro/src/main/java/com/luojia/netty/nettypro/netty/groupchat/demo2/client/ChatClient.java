package com.luojia.netty.nettypro.netty.groupchat.demo2.client;

import com.luojia.netty.nettypro.netty.groupchat.demo2.message.LoginRequestMessage;
import com.luojia.netty.nettypro.netty.groupchat.demo2.message.LoginResponseMessage;
import com.luojia.netty.nettypro.netty.groupchat.demo2.protocol.MessageCodecSharable;
import com.luojia.netty.nettypro.netty.groupchat.demo2.protocol.ProcotolFrameDecoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ChatClient {

    public static void main(String[] args) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        LoggingHandler LOGGING_HANDLER = new LoggingHandler(LogLevel.DEBUG);
        MessageCodecSharable MESSAGE_CODEC = new MessageCodecSharable();
        CountDownLatch WAIT_FOR_LOGIN = new CountDownLatch(1);
        // 登录是否成功标志
        AtomicBoolean LOGIN = new AtomicBoolean(false);

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new ProcotolFrameDecoder());
                            ch.pipeline().addLast(LOGGING_HANDLER);
                            ch.pipeline().addLast(MESSAGE_CODEC);
                            ch.pipeline().addLast("client handler", new ChannelInboundHandlerAdapter(){
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    // 连接建立后，触发 active 事件，另起一个新线程来发送各种消息，避免阻塞worker线程
                                    new Thread(() -> {
                                        Scanner scanner = new Scanner(System.in);
                                        System.out.println("请输入用户名：");
                                        String username = scanner.nextLine();
                                        System.out.println("请输入密码：");
                                        String password = scanner.nextLine();
                                        // 构造消息对象
                                        LoginRequestMessage message = new LoginRequestMessage(username, password);
                                        // 发送消息
                                        ctx.writeAndFlush(message);
                                        System.out.println("等待后续操作");
                                        try {
                                            // 登录结果未返回时，阻塞后续操作
                                            WAIT_FOR_LOGIN.await();
                                        } catch (InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }

                                        // 登录失败，关闭连接
                                        if (!LOGIN.get()) {
                                            log.info("用户名或密码错误，系统即将关闭");
                                            ctx.channel().close();
                                            return;
                                        }
                                        // 登录成功，执行其他操作
                                        while (true) {
                                            System.out.println("============ 功能菜单 ============");
                                            System.out.println("send [username] [content]");
                                            System.out.println("gsend [group name] [content]");
                                            System.out.println("gcreate [group name] [m1,m2,m3...]");
                                            System.out.println("gmembers [group name]");
                                            System.out.println("gjoin [group name]");
                                            System.out.println("gquit [group name]");
                                            System.out.println("quit");
                                            System.out.println("==================================");
                                            String command = scanner.nextLine();

                                        }
                                    }, "system in").start();
                                }

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    log.debug("msg: {}", msg);
                                    if (msg instanceof LoginResponseMessage) {
                                        LoginResponseMessage response = (LoginResponseMessage) msg;
                                        if (response.isSuccess()) {
                                            // 登录成功则将标志位置为 true
                                            LOGIN.set(true);
                                        }
                                        // 唤醒 system in 线程
                                        WAIT_FOR_LOGIN.countDown();
                                    }
                                }
                            });
                        }
                    });
            Channel channel = bootstrap.connect("localhost", 7001).sync().channel();
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            group.shutdownGracefully();
        }
    }

}
