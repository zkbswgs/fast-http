package org.neo4j.smack.test.util;

import static org.jboss.netty.channel.Channels.pipeline;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;
import org.jboss.netty.util.CharsetUtil;
import org.neo4j.smack.pipeline.http.NettyChannelTrackingHandler;

/** 
 * Incomplete implementation of a HTTP client that does pipelining.
 * Used as a lab tool to see how we can maximize performance.
 */
public class PipelinedHttpClient {
    
    static final byte SP = 32;

    //tab ' '
    static final byte HT = 9;

    /**
     * Carriage return
     */
    static final byte CR = 13;

    /**
     * Equals '='
     */
    static final byte EQUALS = 61;

    /**
     * Line feed character
     */
    static final byte LF = 10;

    /**
     * carriage return line feed
     */
    static final byte[] CRLF = new byte[] { CR, LF };

    /**
    * Colon ':'
    */
    static final byte COLON = 58;

    /**
    * Semicolon ';'
    */
    static final byte SEMICOLON = 59;

     /**
    * comma ','
    */
    static final byte COMMA = 44;

    static final byte DOUBLE_QUOTE = '"';

    static final Charset DEFAULT_CHARSET = CharsetUtil.UTF_8;
    
    public class HttpClientPipelineFactory implements ChannelPipelineFactory {

        private HttpResponseHandler responseHandler;
        private ChannelGroup openChannels;

        public HttpClientPipelineFactory(ChannelGroup openChannels, HttpResponseHandler responseHandler) {
            this.responseHandler = responseHandler;
            this.openChannels = openChannels;
        }
        
        public ChannelPipeline getPipeline() throws Exception {
            // Create a default pipeline implementation.
            ChannelPipeline pipeline = pipeline();

            //pipeline.addLast("codec", new HttpClientCodec());

            // Uncomment the following line if you don't want to handle HttpChunks.
            //pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));

            pipeline.addLast("channeltracker",new NettyChannelTrackingHandler(openChannels));
            pipeline.addLast("handler", responseHandler);
            return pipeline;
        }

    } 
    
    public static class HttpResponseHandler extends SimpleChannelUpstreamHandler  {
        
        static class HttpDecoder extends  ReplayingDecoder<HttpDecoder.State> 
        {
            enum State {
                SKIP_CONTROL_CHARS,
                READ_INITIAL,
                READ_HEADERS;
            }

            private AtomicLong responseCount;
            
            HttpDecoder(AtomicLong responseCount) {
                super(State.SKIP_CONTROL_CHARS, true);
                this.responseCount = responseCount;
            }

            /**
             * Capable of parsing a single type of HTTP responses, namely:
             * 
             * HTTP/1.1 200 OK
             * Content-Length: 0
             * 
             * Used to count responses for performance testing.
             */
            @Override
            @SuppressWarnings ("fallthrough")
            protected Object decode(ChannelHandlerContext ctx, Channel channel,
                    ChannelBuffer buffer, State state) throws Exception
            {
                switch (state) {
                case SKIP_CONTROL_CHARS: {
                    try {
                        skipControlCharacters(buffer);
                        checkpoint(State.READ_INITIAL);
                    } finally {
                        checkpoint();
                    }
                }
                case READ_INITIAL: {
                    readLine(buffer, 1000);
                    checkpoint(State.READ_HEADERS);
                }
                case READ_HEADERS: {
                    readLine(buffer, 1000);
                    
                    responseCount.incrementAndGet();
                    return reset();
                }
                default: {
                    throw new Error("Shouldn't reach here.");
                }

                }
            }
            
            private Object reset()
            {
                checkpoint(State.SKIP_CONTROL_CHARS);
                return null;
            }

            private void skipControlCharacters(ChannelBuffer buffer) {
                for (;;) {
                    char c = (char) buffer.readUnsignedByte();
                    if (!Character.isISOControl(c) &&
                        !Character.isWhitespace(c)) {
                        buffer.readerIndex(buffer.readerIndex() - 1);
                        break;
                    }
                }
            }
            
            private void readLine(ChannelBuffer buffer, int maxLineLength) throws TooLongFrameException {
                //StringBuilder sb = new StringBuilder(64);
                int lineLength = 0;
                while (true) {
                    byte nextByte = buffer.readByte();
                    if (nextByte == CR) {
                        nextByte = buffer.readByte();
                        if (nextByte == LF) {
                            return;
                        }
                    }
                    else if (nextByte == LF) {
                        return;
                    }
                    else {
                        if (lineLength >= maxLineLength) {
                            // TODO: Respond with Bad Request and discard the traffic
                            //    or close the connection.
                            //       No need to notify the upstream handlers - just log.
                            //       If decoding a response, just throw an exception.
                            throw new TooLongFrameException(
                                    "An HTTP line is larger than " + maxLineLength +
                                    " bytes.");
                        }
                        lineLength ++;
                        //sb.append((char) nextByte);
                    }
                }
            }
        }
        
        public AtomicLong responseCount = new AtomicLong();

        public HttpResponse lastResponse;
        public Throwable lastException = null;
        
        HttpDecoder decode = new HttpDecoder(responseCount);
        
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            decode.messageReceived(ctx, e);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            lastException = e.getCause();
        }
    }
    
    protected Channel channel;
    private ClientBootstrap bootstrap;
    
    public HttpResponseHandler responseHandler = new HttpResponseHandler();
    private ChannelGroup openChannels = new DefaultChannelGroup("SmackClient");

    public PipelinedHttpClient(String host, int port) {
        
        // Configure the client.
        bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool()));

        // Set up the event pipeline factory.
        bootstrap.setPipelineFactory(new HttpClientPipelineFactory(openChannels, responseHandler));

        // Start the connection attempt.
        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host,
                port));

        // Wait until the connection attempt succeeds or fails.
        channel = future.awaitUninterruptibly().getChannel();
        if (!future.isSuccess()) {
            bootstrap.releaseExternalResources();
            throw new RuntimeException(future.getCause());
        }
    }

    public ChannelFuture handle(HttpMethod method, URI uri, String payload) {

        if(responseHandler.lastException != null) {
            throw new RuntimeException(responseHandler.lastException);
        }
        
        String host = uri.getHost() == null ? "localhost" : uri.getHost();

        // Prepare the HTTP request.
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                method, uri.getPath());
        request.setHeader(HttpHeaders.Names.HOST, host);
        request.setHeader(HttpHeaders.Names.CONNECTION, "keep-alive");
        
        if(payload != null) {
            request.setContent(ChannelBuffers.copiedBuffer(payload, CharsetUtil.UTF_8));
            request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, request.getContent().readableBytes());
        } else {
            request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, 0);
        }
        
        // Send the HTTP request.
        return channel.write(request);
    }

    // Quick hack to wait for responses
    public void waitForXResponses(long count) {
        while(responseHandler.responseCount.get() < count) {
            if(responseHandler.lastException != null) {
                throw new RuntimeException(responseHandler.lastException);
            }
            try {
                Thread.sleep(0, 10);
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void close() {
        if (openChannels!=null) openChannels.close().awaitUninterruptibly();
        bootstrap.releaseExternalResources();
    }
    
}
