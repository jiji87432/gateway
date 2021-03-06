/**
 * Copyright 2007-2016, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kaazing.gateway.transport.wseb.filter;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.kaazing.gateway.transport.BridgeSession;
import org.kaazing.gateway.util.codec.PassThroughDecoder;
import org.kaazing.mina.core.buffer.IoBufferAllocatorEx;
import org.kaazing.mina.core.session.IoSessionEx;

public class WsebTextAsBinaryEncodingCodecFilter extends WsebEncodingCodecFilter {

    public WsebTextAsBinaryEncodingCodecFilter() {
        super(new WsCodecFactory());
    }

    private static class WsCodecFactory implements ProtocolCodecFactory {

        @Override
        public ProtocolEncoder getEncoder(IoSession session) {
            IoSessionEx sessionEx = (IoSessionEx) session;
            IoBufferAllocatorEx<?> allocator = sessionEx.getBufferAllocator();

            if (session instanceof BridgeSession) {
                BridgeSession bridgeSession = (BridgeSession)session;
                return new WsebTextAsBinaryFrameEncoder(bridgeSession.getMessageEncoder(), allocator);
            }

            return new WsebTextAsBinaryFrameEncoder(allocator);
        }

        @Override
        public ProtocolDecoder getDecoder(IoSession session) {
            return PassThroughDecoder.PASS_THROUGH_DECODER;
        }
    }


}
